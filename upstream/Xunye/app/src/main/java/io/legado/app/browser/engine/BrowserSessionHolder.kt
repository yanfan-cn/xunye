package io.legado.app.browser.engine

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps Aloha hosts alive across Compose dispose and tab switches (SPEC-011/012/120).
 * Active session is attached to a container; inactive tabs are [park]ed (paused, detached)
 * so switching tabs does not reload unless the user refreshes.
 */
object BrowserSessionHolder {
    private const val TAG = "BrowserSessionHolder"
    private const val MAX_PARKED = 8

    /** SPEC-266: serialize attach/park/destroy under rapid tab / NTP switches. */
    private val sessionLock = Any()

    private data class SessionSlot(
        val host: AwContentsHostView,
        val client: BrowserAwContentsClient,
        var hasLoadedInitialUrl: Boolean,
    )

    @Volatile
    private var active: SessionSlot? = null

    /** Parked (inactive) sessions keyed by tab id. Insertion order ≈ LRU. */
    private val parked = linkedMapOf<String, SessionSlot>()

    @Volatile
    private var appContext: Context? = null

    /** SPEC-267: true while Activity is backgrounded — skip resume until foreground. */
    @Volatile
    private var appBackgrounded: Boolean = false

    @Volatile
    var sessionKey: String? = null
        private set

    val hasLoadedInitialUrl: Boolean
        get() = active?.hasLoadedInitialUrl == true

    /**
     * SPEC-242: set when leaving NTP into a fresh host; cleared after first
     * [AwContentsHostView.clearHistory] so底栏后退可回主页（不被跳转链挡住）。
     */
    @Volatile
    var clearHistoryAfterNtpExit: Boolean = false

    /** SPEC-257: sessionKey → URL to load as soon as the host is created (cold restore). */
    private val eagerLoadUrls = ConcurrentHashMap<String, String>()

    /** SPEC-267: sessionKey → AwContents/WebView navigation Bundle for cold restore. */
    private val eagerRestoreStates = ConcurrentHashMap<String, Bundle>()

    /** SPEC-267: application context for persisting navigation state on park. */
    fun init(context: Context) {
        appContext = context.applicationContext
        BrowserTabWebStateStore.init(context.applicationContext)
    }

    /** SPEC-257: queue restore URL so [attachTo] can start network before Compose settles. */
    fun queueEagerLoad(sessionKey: String, url: String) {
        if (sessionKey.isBlank() || url.isBlank()) return
        eagerLoadUrls[sessionKey] = url
    }

    /** SPEC-267: queue navigation Bundle so [attachTo] can restore back/forward without loadUrl. */
    fun queueRestoreState(sessionKey: String, state: Bundle) {
        if (sessionKey.isBlank()) return
        eagerRestoreStates[sessionKey] = state
    }

    /**
     * SPEC-267: Activity ON_PAUSE — pause active AwContents (parked already paused).
     * Missing this caused release crashes when gesture-switching back onto a live page.
     */
    fun pauseActiveForAppBackground() {
        synchronized(sessionLock) {
            appBackgrounded = true
            val key = sessionKey
            val slot = active
            if (key != null && slot != null) {
                persistNavState(key, slot.host)
            }
            slot?.host?.let { host ->
                try {
                    host.onHostPause()
                } catch (t: Throwable) {
                    Log.w(TAG, "pauseActiveForAppBackground: ${t.message}")
                }
            }
        }
    }

    /** SPEC-267: Activity ON_RESUME — resume active host if still attached. */
    fun resumeActiveForAppForeground() {
        synchronized(sessionLock) {
            appBackgrounded = false
            active?.host?.let { resumeHost(it) }
        }
    }

    fun attachTo(
        container: FrameLayout,
        incognito: Boolean,
        clientFactory: () -> BrowserAwContentsClient,
        sessionKey: String,
    ): Pair<AwContentsHostView, BrowserAwContentsClient> = synchronized(sessionLock) {
        // SPEC-124 diag: attachTo branch timing.
        val t0 = android.os.SystemClock.uptimeMillis()
        val current = active
        if (current != null && this.sessionKey == sessionKey) {
            (current.host.parent as? ViewGroup)?.removeView(current.host)
            container.addView(
                current.host,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            resumeHost(current.host)
            Log.i(TAG, "reattach existing session host key=$sessionKey")
            Log.i(TAG, "diag124 reattach took=${android.os.SystemClock.uptimeMillis() - t0}ms")
            return current.host to current.client
        }

        // SPEC-120: park previous tab instead of destroying (no reload on switch).
        if (current != null) {
            val tPark = android.os.SystemClock.uptimeMillis()
            val oldKey = this.sessionKey
            if (!oldKey.isNullOrBlank()) {
                parkSlot(oldKey, current)
            } else {
                destroySlot(current)
            }
            Log.i(TAG, "diag124 park-old took=${android.os.SystemClock.uptimeMillis() - tPark}ms")
            active = null
            this.sessionKey = null
        }

        val restored = parked.remove(sessionKey)
        if (restored != null) {
            val tR = android.os.SystemClock.uptimeMillis()
            (restored.host.parent as? ViewGroup)?.removeView(restored.host)
            container.addView(
                restored.host,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            resumeHost(restored.host)
            active = restored
            this.sessionKey = sessionKey
            Log.i(TAG, "restored parked session key=$sessionKey")
            Log.i(TAG, "diag124 restore took=${android.os.SystemClock.uptimeMillis() - tR}ms")
            return restored.host to restored.client
        }

        val tC = android.os.SystemClock.uptimeMillis()
        val newHost = AwContentsHostView(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val newClient = clientFactory()
        newHost.attach(newClient, incognito = incognito)
        container.addView(newHost)
        // SPEC-267/257: prefer navigation Bundle restore (keeps back/forward); else eager loadUrl.
        val restoreBundle = eagerRestoreStates.remove(sessionKey)
            ?: appContext?.let { BrowserTabWebStateStore.load(it, sessionKey) }
        var loaded = false
        if (restoreBundle != null && newHost.restoreNavigationState(restoreBundle)) {
            loaded = true
            val restoredUrl = newHost.currentUrl().orEmpty()
                .ifBlank { eagerLoadUrls.remove(sessionKey).orEmpty() }
            if (restoredUrl.isNotBlank()) {
                newClient.lastUrl = restoredUrl
            }
            Log.i(TAG, "restoreState key=$sessionKey url=$restoredUrl")
            eagerLoadUrls.remove(sessionKey)
        } else {
            val eager = eagerLoadUrls.remove(sessionKey)
            if (!eager.isNullOrBlank()) {
                newClient.lastUrl = eager
                runCatching { newHost.loadUrl(eager) }
                loaded = true
                Log.i(TAG, "eager loadUrl key=$sessionKey url=$eager")
            }
        }
        active = SessionSlot(newHost, newClient, hasLoadedInitialUrl = loaded)
        this.sessionKey = sessionKey
        if (!appBackgrounded) {
            resumeHost(newHost)
        } else {
            try {
                newHost.onHostPause()
            } catch (_: Throwable) {
            }
        }
        Log.i(TAG, "created session host key=$sessionKey")
        Log.i(
            TAG,
            "diag124 create+attach took=${android.os.SystemClock.uptimeMillis() - tC}ms " +
                "(attachTo total=${android.os.SystemClock.uptimeMillis() - t0}ms)",
        )
        return newHost to newClient
    }

    fun markLoaded() {
        synchronized(sessionLock) {
            active?.hasLoadedInitialUrl = true
        }
    }

    /** SPEC-149: 只读查询——活跃槽位属于 [key] 时返回其宿主；key 为 null 不校验。 */
    fun activeHostFor(key: String?): AwContentsHostView? {
        val slot = active ?: return null
        if (key != null && sessionKey != key) return null
        return slot.host
    }

    /** SPEC-149: 只读查询——活跃槽位属于 [key] 时返回其客户端。 */
    fun activeClientFor(key: String?): BrowserAwContentsClient? {
        val slot = active ?: return null
        if (key != null && sessionKey != key) return null
        return slot.client
    }

    /** SPEC-021: navigate the retained host without requiring Compose remount. */
    fun loadUrl(url: String): Boolean {
        val h = active?.host ?: return false
        h.loadUrl(url)
        active?.hasLoadedInitialUrl = true
        Log.i(TAG, "loadUrl via holder: $url")
        return true
    }

    /**
     * SPEC-124: async PixelCopy on the render thread; the tabs grid picks the
     * update up via [BrowserTabThumbnailStore.revision]. Callers must invoke
     * this **before** [parkActive] so the copy still sees the attached page.
     */
    fun captureActiveThumbnail() {
        val id = sessionKey ?: return
        val h = active?.host ?: return
        h.capturePreviewAsync { bmp ->
            if (bmp != null) BrowserTabThumbnailStore.put(id, bmp)
        }
    }

    /**
     * SPEC-120: detach + pause active host without destroying (tab switch / NTP park).
     */
    fun parkActive() {
        synchronized(sessionLock) {
            val key = sessionKey ?: return
            val slot = active ?: return
            parkSlot(key, slot)
            active = null
            sessionKey = null
            Log.i(TAG, "parked active session key=$key")
        }
    }

    /**
     * SPEC-075: before tabs enter transition, stop drawing AwContents so Honor/API35
     * RenderThread does not walk WebView under scale/fade graphicsLayer.
     */
    fun pauseForTabsOverlay() {
        val h = active?.host ?: return
        try {
            h.onHostPause()
        } catch (t: Throwable) {
            Log.w(TAG, "pauseForTabsOverlay pause: ${t.message}")
        }
        try {
            h.visibility = View.INVISIBLE
        } catch (t: Throwable) {
            Log.w(TAG, "pauseForTabsOverlay hide: ${t.message}")
        }
    }

    fun resumeFromTabsOverlay() {
        val h = active?.host ?: return
        try {
            h.visibility = View.VISIBLE
        } catch (t: Throwable) {
            Log.w(TAG, "resumeFromTabsOverlay show: ${t.message}")
        }
        try {
            h.onHostResume()
        } catch (t: Throwable) {
            Log.w(TAG, "resumeFromTabsOverlay resume: ${t.message}")
        }
    }

    /** SPEC-120: push current privacy prefs to active + parked hosts. */
    fun applyPrivacyToAllSessions() {
        active?.host?.applyPrivacySettings()
        parked.values.forEach { slot ->
            runCatching { slot.host.applyPrivacySettings() }
        }
    }

    fun destroyKey(key: String) {
        synchronized(sessionLock) {
            if (sessionKey == key) {
                destroyActiveOnly()
                return
            }
            parked.remove(key)?.let { destroySlot(it) }
        }
    }

    fun destroy() {
        synchronized(sessionLock) {
            destroyActiveOnly()
            val keys = parked.keys.toList()
            keys.forEach { k ->
                parked.remove(k)?.let { destroySlot(it) }
            }
            // runCatching: JVM unit tests have no android.util.Log mock.
            runCatching { Log.i(TAG, "all session hosts destroyed") }
        }
    }

    private fun destroyActiveOnly() {
        val current = active
        active = null
        sessionKey = null
        if (current != null) {
            destroySlot(current)
            Log.i(TAG, "active session host destroyed")
        }
    }

    private fun parkSlot(key: String, slot: SessionSlot) {
        persistNavState(key, slot.host)
        try {
            slot.host.onHostPause()
        } catch (_: Throwable) {
        }
        try {
            slot.host.visibility = View.INVISIBLE
        } catch (_: Throwable) {
        }
        (slot.host.parent as? ViewGroup)?.removeView(slot.host)
        parked.remove(key)
        parked[key] = slot
        trimParked()
    }

    private fun persistNavState(key: String, host: AwContentsHostView) {
        val ctx = appContext ?: return
        val bundle = host.saveNavigationState() ?: return
        BrowserTabWebStateStore.save(ctx, key, bundle)
    }

    private fun trimParked() {
        while (parked.size > MAX_PARKED) {
            val oldest = parked.entries.firstOrNull() ?: break
            parked.remove(oldest.key)
            destroySlot(oldest.value)
            appContext?.let { BrowserTabWebStateStore.remove(it, oldest.key) }
            Log.i(TAG, "evicted parked session key=${oldest.key}")
        }
    }

    private fun resumeHost(host: AwContentsHostView) {
        if (appBackgrounded) return
        try {
            host.visibility = View.VISIBLE
        } catch (_: Throwable) {
        }
        try {
            host.onHostResume()
        } catch (_: Throwable) {
        }
    }

    private fun destroySlot(slot: SessionSlot) {
        try {
            slot.host.onHostPause()
        } catch (_: Throwable) {
        }
        try {
            slot.host.destroyContents()
        } catch (_: Throwable) {
        }
        (slot.host.parent as? ViewGroup)?.removeView(slot.host)
    }

    /**
     * SPEC-257: touch Aloha/Chromium once so the first real session attach is cheaper.
     * Safe no-op if engine is already up.
     */
    fun warm(context: Context) {
        runCatching {
            val app = context.applicationContext
            val host = AwContentsHostView(app)
            val client = BrowserAwContentsClient()
            host.attach(client, incognito = false)
            host.onHostPause()
            host.destroyContents()
            Log.i(TAG, "warm engine done")
        }.onFailure { Log.w(TAG, "warm engine: ${it.message}") }
    }
}
