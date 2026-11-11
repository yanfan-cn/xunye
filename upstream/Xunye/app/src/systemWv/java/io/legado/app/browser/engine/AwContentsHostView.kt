package io.legado.app.browser.engine

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.ActionMode
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * SPEC-222: system [WebView] host with the same public API as the Aloha [AwContentsHostView].
 */
class AwContentsHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var webView: WebView? = null
    private var boundClient: BrowserAwContentsClient? = null
    private var activeActionMode: ActionMode? = null

    private val selectionGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (activeActionMode != null) {
                    clearTextSelection()
                } else {
                    evaluateJavaScript(CLEAR_SELECTION_JS, null)
                }
                return false
            }
        },
    )

    init {
        overScrollMode = OVER_SCROLL_ALWAYS
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun attach(client: BrowserAwContentsClient, incognito: Boolean = false): WebView {
        destroyContents()
        BrowserIncognitoRuntime.activateCookieJar(incognito)
        val wv = WebView(context)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = !incognito
        settings.databaseEnabled = !incognito
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false

        val chromePrefs = BrowserChromePrefs(context)
        val ua = BrowserUa.resolve(context)
        settings.userAgentString = ua
        settings.textZoom = chromePrefs.textZoomPercent

        BrowserPrivacyControls.applyCookieManagers(context)
        BrowserPrivacyControls.applyToSettings(context, settings)
        BrowserPrivacyControls.applyForceZoom(context, settings)
        BrowserPrivacyControls.applyThirdPartyCookies(wv, context)
        applyWebContentDarkMode(
            settings,
            chromePrefs.webContentDarkMode,
            BrowserChromePrefs.isSystemNightMode(context),
        )

        if (incognito) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            CookieManagerSafe.setAcceptCookie(true)
        }

        client.bind(wv)
        addView(
            wv,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        webView = wv
        boundClient = client
        Log.i(TAG, "system WebView attached (incognito=$incognito)")
        return wv
    }

    fun contentsOrNull(): WebView? = webView

    fun applyForceDarkOff() {
        val settings = webView?.settings ?: return
        applyWebContentDarkMode(settings, BrowserChromePrefs.WEB_DARK_OFF, nightMode = false)
    }

    fun applyWebContentDarkMode(mode: String) {
        val settings = webView?.settings ?: return
        applyWebContentDarkMode(
            settings,
            mode,
            BrowserChromePrefs.isSystemNightMode(context),
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val prefs = BrowserChromePrefs(context)
        if (prefs.webContentDarkMode == BrowserChromePrefs.WEB_DARK_AUTO) {
            applyWebContentDarkMode(BrowserChromePrefs.WEB_DARK_AUTO)
        }
    }

    fun applyTextZoom(percent: Int) {
        val settings = webView?.settings ?: return
        settings.textZoom = percent.coerceIn(
            BrowserChromePrefs.TEXT_ZOOM_MIN,
            BrowserChromePrefs.TEXT_ZOOM_MAX,
        )
    }

    fun applyForcePageZoom(enabled: Boolean) {
        val settings = webView?.settings ?: return
        BrowserChromePrefs(context).forcePageZoom = enabled
        BrowserPrivacyControls.applyForceZoom(context, settings)
        if (enabled) {
            evaluateJavaScript(BrowserPrivacyControls.forceZoomViewportScript(true), null)
        }
    }

    fun applyPrivacySettings() {
        val wv = webView ?: return
        val settings = wv.settings
        BrowserPrivacyControls.applyCookieManagers(context)
        BrowserPrivacyControls.applyToSettings(context, settings)
        BrowserPrivacyControls.applyThirdPartyCookies(wv, context)
    }

    fun loadUrl(url: String) {
        // SPEC-242: 浏览导航不再按站抬桌面 UA；播放端见 BrowserVideoMedia.playbackUserAgent。
        webView?.loadUrl(url) ?: Log.w(TAG, "loadUrl before attach: $url")
    }

    fun loadDataWithBaseURL(
        baseUrl: String,
        data: String,
        mimeType: String,
        encoding: String,
        historyUrl: String?,
    ) {
        webView?.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    fun evaluateJavaScript(script: String, callback: BrowserJsCallback?) {
        webView?.evaluateJavascript(script) { value ->
            callback?.onResult(unwrapJsString(value))
        }
    }

    fun saveWebArchive(
        basename: String,
        autoname: Boolean,
        callback: BrowserJsCallback?,
    ) {
        val wv = webView
        if (wv == null) {
            callback?.onResult(null)
            return
        }
        runCatching {
            wv.saveWebArchive(basename, autoname) { path ->
                callback?.onResult(path)
            }
        }.onFailure {
            Log.w(TAG, "saveWebArchive: ${it.message}")
            callback?.onResult(null)
        }
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true
    fun canGoForward(): Boolean = webView?.canGoForward() == true

    fun clearHistory() {
        runCatching { webView?.clearHistory() }
            .onFailure { Log.w(TAG, "clearHistory: ${it.message}") }
    }

    fun goBack() {
        BrowserScrollMemory.markHistoryNavigation()
        webView?.goBack()
    }

    fun goForward() {
        BrowserScrollMemory.markHistoryNavigation()
        webView?.goForward()
    }

    fun goBackOrForward(steps: Int) {
        if (steps == 0) return
        BrowserScrollMemory.markHistoryNavigation()
        webView?.goBackOrForward(steps)
    }

    fun navigationHistoryItems(direction: BrowserNavHistory.Direction): List<BrowserNavHistory.Item> {
        val list = webView?.copyBackForwardList() ?: return emptyList()
        val entries = buildList {
            for (i in 0 until list.size) {
                val item = list.getItemAtIndex(i) ?: continue
                add(
                    BrowserNavHistory.Entry(
                        title = item.title.orEmpty(),
                        url = item.url.orEmpty(),
                        isInitial = false,
                    ),
                )
            }
        }
        return BrowserNavHistory.slice(
            entries = entries,
            currentIndex = list.currentIndex,
            direction = direction,
        )
    }

    fun reload() {
        webView?.reload()
    }

    fun scrollToTop() {
        evaluateJavaScript(io.legado.app.browser.reader.WebChapterExpandJs.SCROLL_TOP, null)
    }

    fun applyUserAgent(mode: BrowserUa.Mode) {
        val settings = webView?.settings ?: return
        settings.userAgentString = BrowserUa.stringFor(mode)
    }

    fun findInPage(query: String, findNext: Boolean, forward: Boolean, callback: BrowserJsCallback?) {
        evaluateJavaScript(
            BrowserFindInPageScript.build(query, findNext = findNext, forward = forward),
            callback,
        )
    }

    fun restoreScrollY(scrollY: Int) {
        if (scrollY <= 0) return
        evaluateJavaScript(
            "(function(){var y=$scrollY;var go=function(){window.scrollTo(0,y);};" +
                "go();requestAnimationFrame(function(){go();setTimeout(go,50);setTimeout(go,200);});})();",
            null,
        )
    }

    fun onHostResume() {
        webView?.onResume()
    }

    fun onHostPause() {
        webView?.onPause()
    }

    fun capturePreviewAsync(maxWidthPx: Int = 360, onDone: (Bitmap?) -> Unit) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            onDone(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAttachedToWindow) {
            val window = resolveHostWindow()
            if (window != null) {
                val requested = runCatching {
                    val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val loc = IntArray(2)
                    getLocationInWindow(loc)
                    val srcRect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
                    PixelCopy.request(
                        window,
                        srcRect,
                        full,
                        PixelCopy.OnPixelCopyFinishedListener { result ->
                            if (result == PixelCopy.SUCCESS) {
                                val scaled = downscaleOrNull(full, w, h, maxWidthPx)
                                if (scaled != null) onDone(scaled) else {
                                    full.recycle()
                                    onDone(capturePreviewLowRes(maxWidthPx))
                                }
                            } else {
                                full.recycle()
                                onDone(capturePreviewLowRes(maxWidthPx))
                            }
                        },
                        Handler(Looper.getMainLooper()),
                    )
                }
                if (requested.isSuccess) return
            }
        }
        onDone(capturePreviewLowRes(maxWidthPx))
    }

    private fun resolveHostWindow(): Window? {
        var ctx: Context? = context
        while (ctx != null) {
            if (ctx is Activity) return ctx.window
            ctx = (ctx as? ContextWrapper)?.baseContext
        }
        return null
    }

    private fun downscaleOrNull(full: Bitmap, w: Int, h: Int, maxWidthPx: Int): Bitmap? = runCatching {
        if (w <= maxWidthPx) full
        else {
            val nh = (h.toFloat() / w * maxWidthPx).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(full, maxWidthPx, nh, true)
        }
    }.getOrNull()

    private fun capturePreviewLowRes(maxWidthPx: Int): Bitmap? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val bw = maxWidthPx.coerceAtMost(w)
            val bh = (h.toFloat() / w * bw).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(bw.toFloat() / w, bh.toFloat() / h)
            draw(canvas)
            bmp
        }.getOrNull()
    }

    fun destroyContents() {
        val wv = webView ?: return
        runCatching { wv.onPause() }
        runCatching { wv.stopLoading() }
        runCatching { wv.loadUrl("about:blank") }
        runCatching { wv.webChromeClient = null }
        runCatching {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
        }
        runCatching { wv.destroy() }
        webView = null
        boundClient = null
    }

    fun clearTextSelection() {
        val mode = activeActionMode
        activeActionMode = null
        runCatching { mode?.finish() }
        evaluateJavaScript(CLEAR_SELECTION_JS, null)
    }

    fun clearTextSelectionIfActive() {
        if (activeActionMode != null) clearTextSelection()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        selectionGestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        val mode = super.startActionMode(wrapActionModeCallback(callback))
        activeActionMode = mode
        return mode
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val mode = super.startActionMode(wrapActionModeCallback(callback), type)
        activeActionMode = mode
        return mode
    }

    private fun wrapActionModeCallback(callback: ActionMode.Callback?): ActionMode.Callback? {
        if (callback == null) return null
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: android.view.Menu?): Boolean =
                callback.onCreateActionMode(mode, menu)

            override fun onPrepareActionMode(mode: ActionMode?, menu: android.view.Menu?): Boolean =
                callback.onPrepareActionMode(mode, menu)

            override fun onActionItemClicked(mode: ActionMode?, item: android.view.MenuItem?): Boolean =
                callback.onActionItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode?) {
                if (activeActionMode === mode) activeActionMode = null
                callback.onDestroyActionMode(mode)
            }
        }
    }

    companion object {
        private const val TAG = "AwContentsHostView"
        private const val CLEAR_SELECTION_JS =
            "(function(){try{var s=window.getSelection();if(s)s.removeAllRanges();}catch(e){}return '';})();"

        private fun unwrapJsString(value: String?): String? {
            if (value == null || value == "null") return null
            if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
                return runCatching {
                    // evaluateJavascript returns a JSON-encoded string.
                    org.json.JSONTokener(value).nextValue() as? String ?: value
                }.getOrElse { value }
            }
            return value
        }

        /** SPEC-259: AUTO → ON/OFF via [nightMode]; never leave FORCE_DARK_AUTO. */
        private fun applyWebContentDarkMode(
            settings: WebSettings,
            mode: String,
            nightMode: Boolean,
        ) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) &&
                !WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
            ) {
                return
            }
            val resolved = BrowserChromePrefs.resolveWebContentDarkMode(mode, nightMode)
            when (resolved) {
                BrowserChromePrefs.WEB_DARK_ON -> {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        @Suppress("DEPRECATION")
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                    }
                }
                else -> {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        @Suppress("DEPRECATION")
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                }
            }
        }
    }
}

/** Tiny helper so CookieManager is not imported twice in attach. */
private object CookieManagerSafe {
    fun setAcceptCookie(accept: Boolean) {
        runCatching { android.webkit.CookieManager.getInstance().setAcceptCookie(accept) }
    }
}
