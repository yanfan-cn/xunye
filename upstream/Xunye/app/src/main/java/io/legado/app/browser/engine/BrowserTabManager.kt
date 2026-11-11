package io.legado.app.browser.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI
import java.util.UUID

data class BrowserTab(
    val id: String,
    val url: String?,
    val title: String,
    val incognito: Boolean,
    /** Last typed/search text (edit buffer); SPEC-125 idle omnibox shows [title]/host. */
    val omniboxLabel: String? = null,
    /**
     * SPEC-094: when true, show NTP even if [url] is set (user pressed back to home).
     * Keeps url/title so the tabs grid can restore the page.
     */
    val showHome: Boolean = false,
    /**
     * SPEC-125: bumped on explicit openUrlInActive navigation so Compose can force
     * loadUrl after park (must not confuse with in-page lastUrl "ahead of draft").
     */
    val navEpoch: Long = 0L,
)

/**
 * In-memory multi-tab MVP (SPEC-012/120/122).
 * Inactive tab AwContents are parked (not destroyed) so switch does not reload.
 * SPEC-122: optionally restore tab list across process death.
 */
object BrowserTabManager {
    @Volatile
    private var appContext: Context? = null

    private val _tabs = MutableStateFlow(
        listOf(BrowserTab(newId(), null, "新标签页", false, showHome = true)),
    )
    private val _activeId = MutableStateFlow(_tabs.value.first().id)

    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()
    val activeTabId: StateFlow<String> = _activeId.asStateFlow()

    /** SPEC-122: load persisted tabs when restore-on-launch is ON. Call once from App. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        BrowserTabThumbnailStore.init(app)
        val chrome = BrowserChromePrefs(app)
        if (!chrome.restoreSessionOnLaunch) return
        val restored = BrowserTabPersist.load(app) ?: return
        val (list, active) = restored
        if (list.isEmpty()) return
        _tabs.value = list
        _activeId.value = active
        // SPEC-257: restore thumbnails from disk; queue eager load for active URL.
        BrowserTabThumbnailStore.preloadFromDisk(list.map { it.id })
        val activeTab = list.firstOrNull { it.id == active }
        val restoreUrl = activeTab?.url
        if (activeTab != null &&
            !activeTab.showHome &&
            !restoreUrl.isNullOrBlank() &&
            restoreUrl.startsWith("http")
        ) {
            BrowserSessionHolder.queueEagerLoad(activeTab.id, restoreUrl)
        }
    }

    fun activeTab(): BrowserTab? = _tabs.value.firstOrNull { it.id == _activeId.value }

    fun openUrlInActive(
        url: String,
        incognito: Boolean? = null,
        omniboxLabel: String? = null,
    ) {
        if (_tabs.value.isEmpty()) {
            openNewTab(url = url, incognito = incognito ?: false, select = true)
            val id = _activeId.value
            val label = omniboxLabel?.trim()?.takeIf { it.isNotEmpty() }
                ?: BrowserOmniboxLabel.fromSearchUrl(url)
            _tabs.update { list ->
                list.map {
                    if (it.id == id) {
                        it.copy(
                            omniboxLabel = label ?: it.omniboxLabel,
                            // Prefer host until page title arrives (SPEC-125).
                            title = displayTitle(null, url, label ?: it.title),
                            showHome = false,
                            navEpoch = it.navEpoch + 1L,
                        )
                    } else it
                }
            }
            persist()
            return
        }
        val id = _activeId.value
        // SPEC-129: leaving NTP — destroy parked host so next load is fresh (no flash A /
        // blank-after-clearHistory). Remount loads [url] via onReady.
        val wasHome = _tabs.value.firstOrNull { it.id == id }?.showHome == true
        if (wasHome) {
            BrowserSessionHolder.destroyKey(id)
            // SPEC-242: first pageFinished after NTP exit clears WebView history.
            BrowserSessionHolder.clearHistoryAfterNtpExit = true
        }
        val label = omniboxLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: BrowserOmniboxLabel.fromSearchUrl(url)
        _tabs.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        url = url,
                        title = displayTitle(null, url, label ?: it.title),
                        incognito = incognito ?: it.incognito,
                        omniboxLabel = label ?: it.omniboxLabel,
                        showHome = false,
                        navEpoch = it.navEpoch + 1L,
                    )
                } else it
            }
        }
        // SPEC-021: live session → load now (only if still attached; NTP path destroyed above).
        // SPEC-125: when parked/destroyed, remount onReady / LaunchedEffect(navEpoch) must load.
        if (BrowserSessionHolder.sessionKey == id) {
            BrowserSessionHolder.loadUrl(url)
        }
        persist()
    }

    fun openNewTab(url: String? = null, incognito: Boolean = false, select: Boolean = true): String {
        val tab = BrowserTab(
            id = newId(),
            url = url,
            title = if (url.isNullOrBlank()) "新标签页" else displayTitle(null, url, "新标签页"),
            incognito = incognito,
            omniboxLabel = BrowserOmniboxLabel.fromSearchUrl(url),
            showHome = url.isNullOrBlank(),
        )
        _tabs.update { it + tab }
        if (select) {
            // SPEC-188: leaving current page context (new tab).
            BrowserMediaSessionHooks.notifyWebContextLeft()
            BrowserSessionHolder.captureActiveThumbnail()
            // SPEC-120: park previous tab session (do not destroy/reload).
            BrowserSessionHolder.parkActive()
            _activeId.value = tab.id
        }
        persist()
        return tab.id
    }

    fun switchTo(tabId: String) {
        if (_tabs.value.none { it.id == tabId }) return
        val already = _activeId.value == tabId
        if (!already) {
            // SPEC-188: switch tab → stop page/native video (听音频例外).
            BrowserMediaSessionHooks.notifyWebContextLeft()
            BrowserSessionHolder.captureActiveThumbnail()
            // SPEC-120: park instead of destroy — restore without network reload.
            BrowserSessionHolder.parkActive()
            _activeId.value = tabId
        }
        // SPEC-094: opening a tab from the grid restores the page when url is known.
        _tabs.update { list ->
            list.map {
                if (it.id == tabId && !it.url.isNullOrBlank()) it.copy(showHome = false) else it
            }
        }
        persist()
    }

    fun close(tabId: String) {
        val list = _tabs.value
        if (list.none { it.id == tabId }) return
        // SPEC-188: closing a tab leaves its media context.
        if (tabId == _activeId.value || list.size <= 1) {
            BrowserMediaSessionHooks.notifyWebContextLeft()
        }
        BrowserTabEmbeddedReaderStore.clear(tabId)
        BrowserTabThumbnailStore.remove(tabId)
        BrowserSessionHolder.destroyKey(tabId)
        if (list.size <= 1) {
            // SPEC-234: 不允许 0 标签——关最后一个后保底一个主页标签。
            resetToSingleHomeTab()
            return
        }
        val idx = list.indexOfFirst { it.id == tabId }
        val next = list.filterNot { it.id == tabId }
        _tabs.value = next
        if (_activeId.value == tabId) {
            BrowserSessionHolder.captureActiveThumbnail()
            val fallback = next.getOrNull(idx.coerceAtMost(next.lastIndex)) ?: next.first()
            BrowserSessionHolder.parkActive()
            _activeId.value = fallback.id
        }
        persist()
    }

    /**
     * SPEC-234: 重置为单个主页标签（取代 SPEC-053 的允许空列表）。
     * 清空全部会话/缩略图/内嵌净读存档后，保底一个「新标签页」（浏览器主页）。
     */
    private fun resetToSingleHomeTab() {
        BrowserMediaSessionHooks.notifyWebContextLeft()
        BrowserSessionHolder.captureActiveThumbnail()
        BrowserTabThumbnailStore.clear()
        BrowserTabEmbeddedReaderStore.clearAll()
        BrowserSessionHolder.destroy()
        val home = BrowserTab(newId(), null, "新标签页", false, showHome = true)
        _tabs.value = listOf(home)
        _activeId.value = home.id
        persist()
    }

    /** SPEC-234: 全部关闭后保底一个主页标签，计数不为 0。 */
    fun closeAll() {
        resetToSingleHomeTab()
    }

    /** SPEC-055: close only tabs in the given mode (常规 / 无痕 list). */
    fun closeAllOfMode(incognito: Boolean) {
        val list = _tabs.value
        val closedIds = list.filter { it.incognito == incognito }.map { it.id }.toSet()
        if (closedIds.isEmpty()) return
        closedIds.forEach {
            BrowserTabEmbeddedReaderStore.clear(it)
            BrowserTabThumbnailStore.remove(it)
            BrowserSessionHolder.destroyKey(it)
        }
        val next = list.filter { it.incognito != incognito }
        val activeWasClosed = _activeId.value in closedIds
        if (next.isEmpty()) {
            // SPEC-234: 全模式清空后保底一个主页标签。
            resetToSingleHomeTab()
            return
        }
        _tabs.value = next
        if (activeWasClosed) {
            BrowserSessionHolder.captureActiveThumbnail()
            BrowserSessionHolder.parkActive()
            _activeId.value = next.firstOrNull()?.id.orEmpty()
        }
        persist()
    }

    fun updateActiveMeta(url: String, title: String) {
        val id = _activeId.value
        _tabs.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        url = url.ifBlank { it.url },
                        title = displayTitle(title, url, it.title),
                        // Keep omniboxLabel (search text); never replace with page URL.
                        // Keep showHome — in-page updates while on NTP should not force session.
                    )
                } else it
            }
        }
        persist()
    }

    fun updateActiveOmniboxLabel(label: String) {
        val id = _activeId.value
        val trimmed = label.trim()
        _tabs.update { list ->
            list.map {
                if (it.id == id) it.copy(omniboxLabel = trimmed.ifEmpty { null }) else it
            }
        }
        persist()
    }

    fun setActiveIncognito(incognito: Boolean) {
        if (_tabs.value.isEmpty()) {
            openNewTab(url = null, incognito = incognito, select = true)
            return
        }
        val id = _activeId.value
        _tabs.update { list ->
            list.map { if (it.id == id) it.copy(incognito = incognito) else it }
        }
        persist()
    }

    /**
     * SPEC-094: return to NTP without wiping restorable url/title.
     * SPEC-100: do **not** destroy AwContents here — keep the page painted until the
     * session exit slide finishes, then call [releaseSessionHostIfParked].
     */
    fun clearActiveToNtp() {
        val id = _activeId.value
        if (id.isEmpty() || _tabs.value.none { it.id == id }) return
        // SPEC-188: back to NTP leaves the webpage.
        BrowserMediaSessionHooks.notifyWebContextLeft()
        BrowserSessionHolder.captureActiveThumbnail()
        _tabs.update { list ->
            list.map {
                if (it.id == id) it.copy(showHome = true) else it
            }
        }
        persist()
    }

    /**
     * SPEC-100/120: after NTP exit animation, park (not destroy) so returning to the
     * same tab does not reload. Destroy only when there is no active tab.
     */
    fun releaseSessionHostIfParked() {
        val tab = activeTab() ?: run {
            BrowserSessionHolder.destroy()
            return
        }
        if (!tab.showHome) return
        if (BrowserSessionHolder.sessionKey != null &&
            BrowserSessionHolder.sessionKey != tab.id
        ) {
            return
        }
        BrowserSessionHolder.parkActive()
    }

    /**
     * SPEC-097: web session chrome is showing (not NTP).
     * Used to hide the main app bottom bar only while a page is visible.
     */
    fun isWebSessionShowing(tab: BrowserTab?): Boolean =
        tab != null && !tab.showHome && !tab.url.isNullOrBlank()

    /** Label for tabs grid / strip. */
    fun tabDisplayTitle(tab: BrowserTab): String = displayTitle(tab.title, tab.url, "新标签页")

    /**
     * SPEC-125: idle address bar / chrome label — page title, else host, else fallback.
     */
    fun pageDisplayLabel(title: String?, url: String?, fallback: String = ""): String =
        displayTitle(title, url, fallback)

    private fun persist() {
        val ctx = appContext ?: return
        if (!BrowserChromePrefs(ctx).restoreSessionOnLaunch) {
            BrowserTabPersist.clear(ctx)
            return
        }
        BrowserTabPersist.save(ctx, _tabs.value, _activeId.value)
    }

    private fun displayTitle(title: String?, url: String?, fallback: String): String {
        val t = title?.trim().orEmpty()
        if (t.isNotEmpty() && t != "新标签页") return t
        val u = url?.trim().orEmpty()
        if (u.isNotEmpty()) {
            val host = runCatching { URI(u).host?.removePrefix("www.") }.getOrNull()
            if (!host.isNullOrBlank()) return host
            return u.take(40)
        }
        return fallback.ifBlank { "新标签页" }
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
