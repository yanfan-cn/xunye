package io.legado.app.browser.engine

import android.graphics.Bitmap
import android.graphics.Picture
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.View
import com.alohamobile.bromium.BromiumClient
import org.chromium.android_webview.AwConsoleMessage
import org.chromium.android_webview.AwContentsClient
import org.chromium.android_webview.AwContentsClientBridge
import org.chromium.android_webview.AwGeolocationPermissions
import org.chromium.android_webview.AwHttpAuthHandler
import org.chromium.android_webview.AwRenderProcess
import org.chromium.android_webview.AwRenderProcessGoneDetail
import org.chromium.android_webview.AwWebResourceError
import org.chromium.android_webview.AwWebResourceRequest
import org.chromium.android_webview.JsPromptResultReceiver
import org.chromium.android_webview.JsResultReceiver
import org.chromium.android_webview.SafeBrowsingAction
import org.chromium.android_webview.permission.AwPermissionRequest
import org.chromium.android_webview.safe_browsing.AwSafeBrowsingResponse
import org.chromium.base.Callback
import org.chromium.components.embedder_support.util.WebResourceResponseInfo
import java.io.ByteArrayInputStream
import java.security.Principal
import java.util.concurrent.atomic.AtomicInteger

/**
 * Aloha AwContentsClient with Xunye shield: ad intercept, external-app block, popup block.
 */
class BrowserAwContentsClient(
    private val onStatus: (String) -> Unit = {},
    private val onNavigationOverride: ((String) -> Unit)? = null,
    private val onPageFinishedExtra: ((String, String) -> Unit)? = null,
    /** SPEC-094: document title as soon as Chromium reports it (tabs grid label). */
    private val onTitleChanged: ((String, String) -> Unit)? = null,
    /** SPEC-023: omnibox sync as soon as navigation starts. */
    private val onUrlChanged: ((String) -> Unit)? = null,
    private val onProgress: ((Int) -> Unit)? = null,
    private val onMainFrameLoadStarted: (() -> Unit)? = null,
    /** Return true if URL handled (cancel load). */
    private val onOverrideUrl: ((String) -> Boolean)? = null,
    private val isAdBlockEnabled: () -> Boolean = { true },
    private val isPopupBlockEnabled: () -> Boolean = { true },
    private val isExternalBlockEnabled: () -> Boolean = { true },
    private val onExternalBlocked: ((String) -> Unit)? = null,
    /**
     * SPEC-156/268: custom-scheme / intent:// — host may show chooser (gesture +
     * resolvable) or silently follow browser_fallback_url. Second arg is user gesture.
     */
    private val onExternalAppOpen: ((String, Boolean) -> Unit)? = null,
    private val onPopupBlocked: (() -> Unit)? = null,
    private val onConsoleBlockPick: ((String) -> Unit)? = null,
    /** SPEC-134: when true, block all URL navigations (pick-to-block mode). */
    private val isPickModeActive: () -> Boolean = { false },
    /** SPEC-093: long-press link/image context payload (JSON after prefix). */
    private val onContextMenuHit: ((String) -> Unit)? = null,
    private val onScrollDirection: ((String) -> Unit)? = null,
    private val userAgentProvider: () -> String = { BrowserUa.MOBILE },
    /** SPEC-058: page-triggered downloads. */
    private val onDownloadRequested: ((
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
    ) -> Unit)? = null,
    /**
     * SPEC-095: main-frame load error. Return true if the host handled it
     * (e.g. HTTPS→HTTP fallback) so the default error snack is skipped.
     */
    private val onMainFrameError: ((url: String, netError: Int, description: String) -> Boolean)? = null,
    /** SPEC-131/222: HTML5 custom fullscreen view (flavor-agnostic callback). */
    private val customViewShowHandler: ((View, BrowserCustomViewCallback) -> Unit)? = null,
    private val customViewHideHandler: (() -> Unit)? = null,
    /** SPEC-132: playable stream sniffed (HLS / media URL). */
    private val onPlayableMedia: ((
        url: String,
        headersRaw: String,
        documentUrl: String,
        isHls: Boolean,
    ) -> Unit)? = null,
    /**
     * SPEC-188: in-page HTML5 play/pause/destroy for media notification + leave-to-stop.
     * playing=true on play; false on pause; null on destroy.
     */
    private val onPageMediaState: ((
        playing: Boolean?,
        mediaUrl: String,
        documentUrl: String,
        durationSec: Double,
        isAudioOnly: Boolean,
    ) -> Unit)? = null,
) : AwContentsClient() {

    val interceptHits = AtomicInteger(0)
    val navOverrideHits = AtomicInteger(0)
    val pageFinishedCount = AtomicInteger(0)
    @Volatile var lastTitle: String = ""
    @Volatile var lastUrl: String = ""
    @Volatile var lastError: String? = null
    /** SPEC-235: 疑似媒体但分类不认的 URL 去重（每 URL 只记一次，防刷屏）。 */
    private val diagMediaishLogged: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** SPEC-073: true when this main-frame load is back/forward history navigation. */
    @Volatile var historyNavigationThisLoad: Boolean = false
        private set

    override fun hasWebViewClient(): Boolean = true

    override fun shouldInterceptRequest(request: AwWebResourceRequest): WebResourceResponseInfo? {
        val url = request.url ?: return null
        if (BrowserAdRules.shouldBlock(url, isAdBlockEnabled())) {
            val n = interceptHits.incrementAndGet()
            Log.i(TAG, "ad-intercept #$n $url")
            onStatus("广告拦截 #$n")
            return emptyBlockedResponse()
        }
        // SPEC-166: notice playable media, then let the page load it unchanged.
        val headerLines = request.requestHeaders.orEmpty().entries.joinToString("\n") { (k, v) ->
            "$k:$v"
        }
        if (BrowserVideoOfferBus.classifyPlayableUrl(url, lastUrl) != null) {
            onPlayableMedia?.invoke(url, headerLines, lastUrl, url.contains(".m3u8", true))
        } else {
            // SPEC-241: 腾讯 P2P — 优先用已抓 .ts 写本地 m3u8；否则反推远程列表。
            val local = TencentHlsLocalPlaylist.ingest(url)
            if (local != null) {
                onPlayableMedia?.invoke(local, headerLines, lastUrl, true)
            } else {
                val inferred = BrowserVideoOfferBus.inferTencentPlaylistFromSegment(url)
                if (inferred != null) {
                    Log.i("Diag146", "infer-m3u8 from-ts=$url -> $inferred")
                    onPlayableMedia?.invoke(inferred, headerLines, lastUrl, true)
                } else if (
                    BrowserVideoOfferBus.looksPlayable(url) &&
                    diagMediaishLogged.add(url)
                ) {
                    Log.i("Diag146", "media-ish rejected url=$url")
                }
            }
        }
        return null
    }

    override fun shouldOverrideUrlLoading(request: AwWebResourceRequest): Boolean {
        val url = request.url ?: return false
        // SPEC-134: pick mode must not navigate away (e.g. clm.la link clicks).
        if (isPickModeActive()) {
            Log.i(TAG, "pick-block-nav $url")
            return true
        }
        if (url.contains(MIDPAGE_MARKER)) {
            val n = navOverrideHits.incrementAndGet()
            Log.i(TAG, "nav-override #$n $url")
            onStatus("跳转干预 #$n")
            val target = extractSkipTarget(url) ?: "https://example.com/"
            onNavigationOverride?.invoke(target)
            return true
        }
        if (BrowserUrlPolicy.isExternalAppLaunch(url)) {
            if (isExternalBlockEnabled()) {
                Log.i(TAG, "external-block $url")
                onExternalBlocked?.invoke(url)
                onStatus("已阻止打开其它应用")
                return true
            }
            // SPEC-156/268: 宿主决定 chooser / 静默 fallback；无手势不弹选择器。
            val gesture = request.hasUserGesture()
            Log.i(TAG, if (gesture) "external-open (gesture) $url" else "external-fallback-only $url")
            onExternalAppOpen?.invoke(url, gesture)
            return true
        }
        if (onOverrideUrl?.invoke(url) == true) return true
        return false
    }

    override fun onPageStarted(url: String) {
        historyNavigationThisLoad = BrowserScrollMemory.consumeHistoryNavigation()
        lastUrl = url
        lastError = null
        onStatus("加载中: $url")
        onMainFrameLoadStarted?.invoke()
        if (url.isNotBlank()) onUrlChanged?.invoke(url)
    }

    override fun onPageFinished(url: String) {
        lastUrl = url
        pageFinishedCount.incrementAndGet()
        onStatus("加载完成: $url")
        if (url.isNotBlank()) onUrlChanged?.invoke(url)
        onPageFinishedExtra?.invoke(url, lastTitle)
        historyNavigationThisLoad = false
    }

    override fun onReceivedTitle(title: String) {
        lastTitle = title
        val url = lastUrl
        if (url.isNotBlank()) {
            onTitleChanged?.invoke(url, title)
        }
    }

    override fun onReceivedError(request: AwWebResourceRequest, error: AwWebResourceError) {
        if (request.isOutermostMainFrame) {
            val url = request.url.orEmpty().ifBlank { lastUrl }
            lastError = "${error.description} (net=${error.netError})"
            val handled = onMainFrameError?.invoke(url, error.netError, error.description) == true
            // SPEC-094/095: short tip unless host already recovered (e.g. HTTP fallback).
            if (!handled) {
                onStatus("错误: $lastError")
            }
        }
    }

    override fun getVisitedHistory(callback: Callback<Array<String>>) {}
    override fun doUpdateVisitedHistory(url: String, isReload: Boolean) {}
    override fun onProgressChanged(progress: Int) {
        onProgress?.invoke(progress)
    }
    override fun shouldOverrideKeyEvent(event: KeyEvent): Boolean = false
    override fun onLoadResource(url: String) {}
    override fun onUnhandledKeyEvent(event: KeyEvent) {}

    override fun onConsoleMessage(consoleMessage: AwConsoleMessage): Boolean {
        val msg = consoleMessage.message() ?: return false
        if (msg.startsWith(BLOCK_PICK_PREFIX)) {
            // SPEC-134: "OFF:selector" deselects; plain selector selects.
            val payload = msg.removePrefix(BLOCK_PICK_PREFIX).trim()
            if (payload.isNotEmpty()) {
                onConsoleBlockPick?.invoke(payload)
                return true
            }
        }
        if (msg.startsWith(BrowserContextMenuJs.PREFIX)) {
            val json = msg.removePrefix(BrowserContextMenuJs.PREFIX).trim()
            if (json.isNotEmpty()) {
                onContextMenuHit?.invoke(json)
                return true
            }
        }
        if (msg.startsWith(SCROLL_Y_PREFIX)) {
            val y = msg.removePrefix(SCROLL_Y_PREFIX).trim().toIntOrNull()
            if (y != null) {
                BrowserScrollMemory.remember(lastUrl, y)
            }
            return true
        }
        if (msg.startsWith(VIDEO_SRC_PREFIX)) {
            val src = msg.removePrefix(VIDEO_SRC_PREFIX).trim()
            if (src.startsWith("http://") || src.startsWith("https://")) {
                onPlayableMedia?.invoke(src, "", lastUrl, src.contains(".m3u8", true))
            }
            return true
        }
        if (msg.startsWith(VIDEO_SRC_BLOB_PREFIX)) {
            // SPEC-235 诊断：页面视频走 MSE（blob: 源）时留痕，解释「检测不到视频」。
            Log.i("Diag146", "videoSrcBlob src=${msg.removePrefix(VIDEO_SRC_BLOB_PREFIX).trim()} doc=$lastUrl")
            return true
        }
        if (msg.startsWith(SCROLL_PREFIX)) {
            onScrollDirection?.invoke(msg.removePrefix(SCROLL_PREFIX).trim())
            return true
        }
        return false
    }

    override fun onReceivedHttpAuthRequest(handler: AwHttpAuthHandler, host: String, realm: String) {
        handler.cancel()
    }

    override fun onReceivedSslError(callback: Callback<Boolean>, error: SslError) {
        callback.onResult(false)
    }

    override fun onReceivedClientCertRequest(
        callback: AwContentsClientBridge.ClientCertificateRequestCallback,
        keyTypes: Array<String>,
        principals: Array<Principal>,
        host: String,
        port: Int,
    ) {
        callback.proceed(null, null)
    }

    override fun onReceivedLoginRequest(realm: String, account: String, args: String) {}
    override fun onFormResubmission(dontResend: Message, resend: Message) {
        dontResend.sendToTarget()
    }

    override fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
    ) {
        onDownloadRequested?.invoke(url, userAgent, contentDisposition, mimeType, contentLength)
            ?: onStatus("开始下载…")
    }

    override fun showFileChooser(
        uploadFilePathsCallback: Callback<Array<String>>,
        fileChooserParams: FileChooserParamsImpl,
    ) {}

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: AwGeolocationPermissions.Callback,
    ) {}

    override fun onGeolocationPermissionsHidePrompt() {}
    override fun onPermissionRequest(awPermissionRequest: AwPermissionRequest) {
        awPermissionRequest.deny()
    }

    override fun onPermissionRequestCanceled(awPermissionRequest: AwPermissionRequest) {}
    override fun onScaleChangedScaled(oldScale: Float, newScale: Float) {}
    override fun onReceivedTouchIconUrl(url: String, precomposed: Boolean) {}
    override fun onReceivedIcon(bitmap: Bitmap) {
        // SPEC-221: persist host favicon for NTP shortcuts / history.
        BrowserFaviconCache.put(lastUrl, bitmap)
    }
    override fun onPageCommitVisible(url: String) {}
    override fun onReceivedHttpError(request: AwWebResourceRequest, response: WebResourceResponseInfo) {}
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        // SPEC-131: host must attach [view] and later invoke callback / onHideCustomView.
        customViewShowHandler?.invoke(
            view,
            BrowserCustomViewCallback { callback.onCustomViewHidden() },
        ) ?: run {
            Log.w(TAG, "onShowCustomView: no host handler")
            runCatching { callback.onCustomViewHidden() }
        }
    }

    override fun onHideCustomView() {
        customViewHideHandler?.invoke()
    }

    /**
     * SPEC-178: must return a non-null bitmap. Null (or missing chrome fullscreen
     * hooks) makes Chromium report fullscreen as unsupported — the page's
     * fullscreen control stays gray / unclickable.
     */
    override fun getDefaultVideoPoster(): Bitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {}
    override fun onNewPicture(picture: Picture) {}
    override fun onRendererUnresponsive(process: AwRenderProcess) {}
    override fun onRendererResponsive(process: AwRenderProcess) {}
    override fun onRenderProcessGone(detail: AwRenderProcessGoneDetail): Boolean = false

    override fun handleJsAlert(url: String, message: String, receiver: JsResultReceiver) {
        receiver.cancel()
    }

    override fun handleJsBeforeUnload(url: String, message: String, receiver: JsResultReceiver) {
        receiver.confirm()
    }

    override fun handleJsConfirm(url: String, message: String, receiver: JsResultReceiver) {
        receiver.cancel()
    }

    override fun handleJsPrompt(
        url: String,
        message: String,
        defaultValue: String,
        receiver: JsPromptResultReceiver,
    ) {
        receiver.cancel()
    }

    override fun onCreateWindow(
        isDialog: Boolean,
        isUserGesture: Boolean,
        targetUrl: String,
        isGoogleAuth3PCookiesRequired: Boolean,
    ): Boolean {
        if (isPopupBlockEnabled()) {
            onPopupBlocked?.invoke()
            onStatus("已阻止弹出窗口")
            return false
        }
        return false
    }

    override fun onCloseWindow() {}
    override fun onRequestFocus() {}
    override fun getVideoLoadingProgressView(): View? = null

    override fun onSafeBrowsingHit(
        request: AwWebResourceRequest,
        threatType: Int,
        callback: Callback<AwSafeBrowsingResponse>,
    ) {
        callback.onResult(AwSafeBrowsingResponse(SafeBrowsingAction.SHOW_INTERSTITIAL, true))
    }

    override fun onMediaPlay(
        playerId: BromiumClient.MediaPlayerId,
        mediaUrl: String,
        documentUrl: String,
        durationSec: Double,
        isAudioOnly: Boolean,
    ) {
        // SPEC-132: page media started — offer to native player when URL looks playable.
        // SPEC-145 R9 diag: 页面媒体起播即记录，用于排查「网页自带播放器失效」。
        Log.i(TAG, "Diag146 mediaPlay url=$mediaUrl doc=$documentUrl audioOnly=$isAudioOnly dur=$durationSec")
        if (!isAudioOnly && mediaUrl.isNotBlank()) {
            onPlayableMedia?.invoke(mediaUrl, "", documentUrl, mediaUrl.contains(".m3u8", true))
        }
        // SPEC-188: notify host for system media notification (video + page audio).
        onPageMediaState?.invoke(true, mediaUrl, documentUrl, durationSec, isAudioOnly)
    }

    override fun onMediaPause(
        playerId: BromiumClient.MediaPlayerId,
        mediaUrl: String,
        documentUrl: String,
        durationSec: Double,
        isAudioOnly: Boolean,
    ) {
        onPageMediaState?.invoke(false, mediaUrl, documentUrl, durationSec, isAudioOnly)
    }

    override fun onMediaDestroy(playerId: BromiumClient.MediaPlayerId, mediaUrl: String, documentUrl: String) {
        onPageMediaState?.invoke(null, mediaUrl, documentUrl, 0.0, false)
    }

    override fun onMediaError(
        playerId: BromiumClient.MediaPlayerId,
        pipelineStatus: String,
        mediaUrl: String,
        documentUrl: String,
        currentTimeSec: Double,
        durationSec: Double,
    ) {
        // SPEC-145 R9 diag: 页面媒体错误全量记录，装机复测定位「网页自带播放器失效」。
        Log.i(
            TAG,
            "Diag146 mediaError pipeline=$pipelineStatus url=$mediaUrl doc=$documentUrl " +
                "t=$currentTimeSec/$durationSec player=$playerId",
        )
    }

    override fun shouldHideControlsInFullscreen(
        playerId: BromiumClient.MediaPlayerId,
        url: String,
        durationSec: Double,
        playerClass: String,
    ): Boolean = false

    // SPEC-145 R9 diag: HTML5 全屏事件记录；宿主当前不接管全屏（内核 AwContents 自带
    // custom view 全屏走 onShowCustomView 路径），先留观察点，装机复测再决定是否接线。
    override fun onVideoEnterFullscreen(playerId: BromiumClient.MediaPlayerId, url: String, mediaControlsIsHidden: Boolean) {
        Log.i(TAG, "Diag146 videoEnterFullscreen url=$url controlsHidden=$mediaControlsIsHidden")
    }

    override fun onVideoExitFullscreen(playerId: BromiumClient.MediaPlayerId, url: String) {
        Log.i(TAG, "Diag146 videoExitFullscreen url=$url")
    }

    override fun onEnterFullscreen() {
        Log.i(TAG, "Diag146 enterFullscreen")
    }

    override fun onExitFullscreen() {
        Log.i(TAG, "Diag146 exitFullscreen")
    }
    override fun onPageLoaded(url: String, isError: Boolean) {}
    override fun onInternalDownloadStarted(httpMethod: String, sizeInBytes: Int) {}

    override fun onDownloadToCacheFinished(
        url: String,
        originalUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        suggestedFilename: String,
        downloadedFilePath: String,
    ) {}

    override fun onRequestedDownloadUrl(url: String) {}
    // SPEC-188: leave-to-stop — Chromium must pause page video when backgrounded.
    // 「听音频」uses a separate ExoPlayer service and is unaffected.
    override fun shouldPlayBackgroundVideo(): Boolean = false
    override fun onHlsDetected(url: String, requestHeaders: String) {
        // SPEC-132
        onPlayableMedia?.invoke(url, requestHeaders.orEmpty(), lastUrl, true)
    }
    override fun getUserAgent(): String =
        // SPEC-018: form-factor default + toolbox override (no Xunye token).
        userAgentProvider()

    companion object {
        private const val TAG = "XunyeBrowser"
        const val AD_PROBE_MARKER = "browsesth-ad-probe"
        const val MIDPAGE_MARKER = "browsesth-midpage-skip"
        const val BLOCK_PICK_PREFIX = "BROWSESTH_BLOCK:"
        const val SCROLL_PREFIX = "BROWSESTH_SCROLL:"
        /** SPEC-073: throttled scrollY for history restore. */
        const val SCROLL_Y_PREFIX = "BROWSESTH_SCROLLY:"
        /** SPEC-166: http(s) currentSrc from an in-page video element. */
        const val VIDEO_SRC_PREFIX = "BROWSESTH_VIDEO:"
        /** SPEC-235: MSE 播放器的 blob: 源（ExoPlayer 不可播，仅日志用于定位「检测不到视频」）。 */
        const val VIDEO_SRC_BLOB_PREFIX = "BROWSESTH_VIDEO_BLOB:"
        const val VIDEO_HOOK_JS =
            "(function(){if(window.__browsesthVideoHook)return;window.__browsesthVideoHook=1;" +
                "document.addEventListener('play',function(e){var t=e.target;" +
                "if(!t||String(t.tagName)!=='VIDEO')return;" +
                "var s=t.currentSrc||t.src||'';" +
                "if(s.indexOf('http')===0)console.log('BROWSESTH_VIDEO:'+s);" +
                "else if(s.indexOf('blob:')===0)console.log('BROWSESTH_VIDEO_BLOB:'+s);" +
                "},true);})();"
        const val SCROLL_HOOK_JS =
            "(function(){if(window.__browsesthScrollHook)return;window.__browsesthScrollHook=1;" +
                "var last=0;var t=null;" +
                "window.addEventListener('scroll',function(){var y=window.scrollY||0;" +
                "if(y-last>24){console.log('BROWSESTH_SCROLL:down');}else if(last-y>24){console.log('BROWSESTH_SCROLL:up');}" +
                "last=y;" +
                "if(t)return;t=setTimeout(function(){t=null;" +
                "console.log('BROWSESTH_SCROLLY:'+(window.scrollY||0));},180);" +
                "},{passive:true});})();"

        fun extractSkipTarget(url: String): String? {
            val marker = "to="
            val idx = url.indexOf(marker)
            if (idx < 0) return null
            return url.substring(idx + marker.length)
        }

        private fun emptyBlockedResponse(): WebResourceResponseInfo =
            WebResourceResponseInfo(
                "text/plain",
                "utf-8",
                ByteArrayInputStream(ByteArray(0)),
                404,
                "Blocked",
                emptyMap(),
            )
    }
}
