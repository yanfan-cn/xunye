package io.legado.app.browser.engine

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * SPEC-222: system WebView client with the same constructor surface as the Aloha flavor
 * so [BrowserSessionScreen] stays shared. Wires [WebViewClient] / [WebChromeClient].
 */
class BrowserAwContentsClient(
    private val onStatus: (String) -> Unit = {},
    private val onNavigationOverride: ((String) -> Unit)? = null,
    private val onPageFinishedExtra: ((String, String) -> Unit)? = null,
    private val onTitleChanged: ((String, String) -> Unit)? = null,
    private val onUrlChanged: ((String) -> Unit)? = null,
    private val onProgress: ((Int) -> Unit)? = null,
    private val onMainFrameLoadStarted: (() -> Unit)? = null,
    private val onOverrideUrl: ((String) -> Boolean)? = null,
    private val isAdBlockEnabled: () -> Boolean = { true },
    private val isPopupBlockEnabled: () -> Boolean = { true },
    private val isExternalBlockEnabled: () -> Boolean = { true },
    private val onExternalBlocked: ((String) -> Unit)? = null,
    /** SPEC-268: (url, hasUserGesture) — host may chooser or silent fallback. */
    private val onExternalAppOpen: ((String, Boolean) -> Unit)? = null,
    private val onPopupBlocked: (() -> Unit)? = null,
    private val onConsoleBlockPick: ((String) -> Unit)? = null,
    private val isPickModeActive: () -> Boolean = { false },
    private val onContextMenuHit: ((String) -> Unit)? = null,
    private val onScrollDirection: ((String) -> Unit)? = null,
    private val userAgentProvider: () -> String = { BrowserUa.MOBILE },
    private val onDownloadRequested: ((
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
    ) -> Unit)? = null,
    private val onMainFrameError: ((url: String, netError: Int, description: String) -> Boolean)? = null,
    private val customViewShowHandler: ((View, BrowserCustomViewCallback) -> Unit)? = null,
    private val customViewHideHandler: (() -> Unit)? = null,
    private val onPlayableMedia: ((
        url: String,
        headersRaw: String,
        documentUrl: String,
        isHls: Boolean,
    ) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER")
    private val onPageMediaState: ((
        playing: Boolean?,
        mediaUrl: String,
        documentUrl: String,
        durationSec: Double,
        isAudioOnly: Boolean,
    ) -> Unit)? = null,
) {
    val interceptHits = AtomicInteger(0)
    val navOverrideHits = AtomicInteger(0)
    val pageFinishedCount = AtomicInteger(0)
    @Volatile var lastTitle: String = ""
    @Volatile var lastUrl: String = ""
    @Volatile var lastError: String? = null
    /** SPEC-235: 疑似媒体但分类不认的 URL 去重（每 URL 只记一次，防刷屏）。 */
    private val diagMediaishLogged: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile var historyNavigationThisLoad: Boolean = false
        private set

    fun bind(webView: WebView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (BrowserAdRules.shouldBlock(url, isAdBlockEnabled())) {
                    val n = interceptHits.incrementAndGet()
                    Log.i(TAG, "ad-intercept #$n $url")
                    onStatus("广告拦截 #$n")
                    return emptyBlockedResponse()
                }
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

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return false
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
                    val gesture = request.hasGesture()
                    Log.i(
                        TAG,
                        if (gesture) "external-open (gesture) $url" else "external-fallback-only $url",
                    )
                    onExternalAppOpen?.invoke(url, gesture)
                    return true
                }
                if (onOverrideUrl?.invoke(url) == true) return true
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                historyNavigationThisLoad = BrowserScrollMemory.consumeHistoryNavigation()
                val u = url.orEmpty()
                lastUrl = u
                lastError = null
                onStatus("加载中: $u")
                onMainFrameLoadStarted?.invoke()
                if (u.isNotBlank()) onUrlChanged?.invoke(u)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val u = url.orEmpty()
                lastUrl = u
                pageFinishedCount.incrementAndGet()
                onStatus("加载完成: $u")
                if (u.isNotBlank()) onUrlChanged?.invoke(u)
                onPageFinishedExtra?.invoke(u, lastTitle)
                historyNavigationThisLoad = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    val u = request.url?.toString().orEmpty().ifBlank { lastUrl }
                    val desc = error?.description?.toString().orEmpty()
                    val code = error?.errorCode ?: -1
                    lastError = "$desc (net=$code)"
                    val handled = onMainFrameError?.invoke(u, code, desc) == true
                    if (!handled) onStatus("错误: $lastError")
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                handler?.cancel()
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?,
            ) {
                handler?.cancel()
            }

            override fun onFormResubmission(view: WebView?, dontResend: Message?, resend: Message?) {
                dontResend?.sendToTarget()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                // no-op
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress?.invoke(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                lastTitle = title.orEmpty()
                val url = lastUrl
                if (url.isNotBlank()) onTitleChanged?.invoke(url, lastTitle)
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                if (icon != null) BrowserFaviconCache.put(lastUrl, icon)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: return false
                if (msg.startsWith(BLOCK_PICK_PREFIX)) {
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
                    if (y != null) BrowserScrollMemory.remember(lastUrl, y)
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
                    Log.i(
                        "Diag146",
                        "videoSrcBlob src=${msg.removePrefix(VIDEO_SRC_BLOB_PREFIX).trim()} doc=$lastUrl",
                    )
                    return true
                }
                if (msg.startsWith(SCROLL_PREFIX)) {
                    onScrollDirection?.invoke(msg.removePrefix(SCROLL_PREFIX).trim())
                    return true
                }
                return false
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || callback == null) return
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

            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                if (isPopupBlockEnabled()) {
                    onPopupBlocked?.invoke()
                    onStatus("已阻止弹出窗口")
                    return false
                }
                return false
            }
        }

        webView.setDownloadListener(
            DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                onDownloadRequested?.invoke(
                    url.orEmpty(),
                    userAgent.orEmpty().ifBlank { userAgentProvider() },
                    contentDisposition.orEmpty(),
                    mimeType.orEmpty(),
                    contentLength,
                ) ?: onStatus("开始下载…")
            },
        )

        // Keep CookieManager warm for third-party policy.
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
    }

    companion object {
        private const val TAG = "XunyeBrowser"
        const val AD_PROBE_MARKER = "browsesth-ad-probe"
        const val MIDPAGE_MARKER = "browsesth-midpage-skip"
        const val BLOCK_PICK_PREFIX = "BROWSESTH_BLOCK:"
        const val SCROLL_PREFIX = "BROWSESTH_SCROLL:"
        const val SCROLL_Y_PREFIX = "BROWSESTH_SCROLLY:"
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
            val raw = url.substring(idx + marker.length)
            val end = raw.indexOf('&').takeIf { it >= 0 } ?: raw.length
            return runCatching {
                java.net.URLDecoder.decode(raw.substring(0, end), Charsets.UTF_8.name())
            }.getOrNull()?.takeIf { it.startsWith("http") }
        }

        private fun emptyBlockedResponse(): WebResourceResponse =
            WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream(ByteArray(0)),
            )
    }
}
