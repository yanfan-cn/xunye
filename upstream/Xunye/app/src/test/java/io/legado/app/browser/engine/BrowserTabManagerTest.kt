package io.legado.app.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowserTabManagerTest {

    @Before
    fun resetTabs() {
        BrowserTabManager.closeAll()
    }

    @Test
    fun closeAllLeavesSingleHomeTab() {
        // SPEC-234: 不允许 0 标签——closeAll 后保底一个主页标签。
        BrowserTabManager.openNewTab(select = true)
        BrowserTabManager.openNewTab(select = false)
        BrowserTabManager.closeAll()
        assertEquals(1, BrowserTabManager.tabs.value.size)
        val home = BrowserTabManager.tabs.value.first()
        assertTrue(home.showHome)
        assertTrue(home.url.isNullOrBlank())
        assertEquals(home.id, BrowserTabManager.activeTabId.value)
    }

    @Test
    fun closeLastTabResetsToHomeTab() {
        // SPEC-234: 关闭最后一个标签 → 重置为主页标签，而非空列表。
        val id = BrowserTabManager.openNewTab(select = true)
        BrowserTabManager.close(id)
        assertEquals(1, BrowserTabManager.tabs.value.size)
        assertTrue(BrowserTabManager.tabs.value.first().showHome)
    }

    @Test
    fun closeAllOfModeLeavesHomeTabWhenEmpty() {
        // SPEC-234: 按模式全关后若列表为空，保底一个常规主页标签。
        BrowserTabManager.openNewTab(incognito = true, select = true)
        BrowserTabManager.closeAllOfMode(incognito = true)
        assertEquals(1, BrowserTabManager.tabs.value.size)
        val home = BrowserTabManager.tabs.value.first()
        assertTrue(home.showHome)
        assertTrue(!home.incognito)
    }

    @Test
    fun openUrlWhenEmptyCreatesTab() {
        BrowserTabManager.openUrlInActive("https://example.com")
        assertEquals(1, BrowserTabManager.tabs.value.size)
        assertEquals("https://example.com", BrowserTabManager.tabs.value.first().url)
    }

    @Test
    fun tabCountIsExactPastNine() {
        // SPEC-234: reset(@Before) 后保底 1 个主页标签，再开 12 个共 13。
        repeat(12) { BrowserTabManager.openNewTab(select = false) }
        assertEquals(13, BrowserTabManager.tabs.value.size)
    }

    @Test
    fun closeAllOfModeKeepsOtherMode() {
        BrowserTabManager.openNewTab(incognito = false, select = true)
        BrowserTabManager.openNewTab(incognito = false, select = false)
        BrowserTabManager.openNewTab(incognito = true, select = false)
        BrowserTabManager.closeAllOfMode(incognito = false)
        val left = BrowserTabManager.tabs.value
        assertEquals(1, left.size)
        assertTrue(left.all { it.incognito })
    }

    @Test
    fun openNewTabRespectsIncognitoFlag() {
        val id = BrowserTabManager.openNewTab(incognito = true, select = true)
        val tab = BrowserTabManager.tabs.value.first { it.id == id }
        assertTrue(tab.incognito)
    }

    @Test
    fun clearActiveToNtpKeepsUrlForTabRestore() {
        BrowserTabManager.openUrlInActive("https://example.com/path", omniboxLabel = "example")
        BrowserTabManager.updateActiveMeta("https://example.com/path", "Example Domain")
        assertTrue(BrowserTabManager.isWebSessionShowing(BrowserTabManager.activeTab()))
        BrowserTabManager.clearActiveToNtp()
        val parked = BrowserTabManager.activeTab()!!
        assertTrue(parked.showHome)
        assertEquals("https://example.com/path", parked.url)
        assertEquals("Example Domain", parked.title)
        // SPEC-267: showHome tab label is always「新标签页」(url/title kept for restore).
        assertEquals("新标签页", BrowserTabManager.tabDisplayTitle(parked))
        // SPEC-097: parked NTP must not count as web session (main bottom bar).
        assertTrue(!BrowserTabManager.isWebSessionShowing(parked))
        // SPEC-100: release is deferred until exit animation; explicit call is safe.
        BrowserTabManager.releaseSessionHostIfParked()
        BrowserTabManager.switchTo(parked.id)
        val restored = BrowserTabManager.activeTab()!!
        assertTrue(!restored.showHome)
        assertEquals("https://example.com/path", restored.url)
        assertTrue(BrowserTabManager.isWebSessionShowing(restored))
    }

    @Test
    fun releaseSessionHostIfParkedSkippedWhenBackOnWeb() {
        BrowserTabManager.openUrlInActive("https://example.com/", omniboxLabel = "example")
        BrowserTabManager.clearActiveToNtp()
        assertTrue(BrowserTabManager.activeTab()!!.showHome)
        BrowserTabManager.switchTo(BrowserTabManager.activeTab()!!.id)
        assertTrue(!BrowserTabManager.activeTab()!!.showHome)
        // Must not destroy a live web session after user already left NTP.
        BrowserTabManager.releaseSessionHostIfParked()
        assertTrue(BrowserTabManager.isWebSessionShowing(BrowserTabManager.activeTab()))
    }

    @Test
    fun openUrlInActiveBumpsNavEpochClearHomeDoesNot() {
        BrowserTabManager.openUrlInActive("https://a.example/", omniboxLabel = "a")
        val e1 = BrowserTabManager.activeTab()!!.navEpoch
        assertTrue(e1 >= 1L)
        BrowserTabManager.clearActiveToNtp()
        assertEquals(e1, BrowserTabManager.activeTab()!!.navEpoch)
        BrowserTabManager.openUrlInActive("https://b.example/", omniboxLabel = "b")
        val after = BrowserTabManager.activeTab()!!
        assertEquals("https://b.example/", after.url)
        assertTrue(!after.showHome)
        assertEquals(e1 + 1L, after.navEpoch)
        // Idle label prefers host until real page title arrives.
        assertEquals("b.example", BrowserTabManager.tabDisplayTitle(after))
    }

    @Test
    fun switchToClearsShowHomeWithoutBumpingNavEpoch() {
        BrowserTabManager.openUrlInActive("https://a.example/")
        val e1 = BrowserTabManager.activeTab()!!.navEpoch
        BrowserTabManager.clearActiveToNtp()
        BrowserTabManager.switchTo(BrowserTabManager.activeTab()!!.id)
        val t = BrowserTabManager.activeTab()!!
        assertTrue(!t.showHome)
        assertEquals(e1, t.navEpoch)
        assertEquals("https://a.example/", t.url)
    }

    @Test
    fun pageDisplayLabelPrefersTitleThenHost() {
        assertEquals(
            "Example Domain",
            BrowserTabManager.pageDisplayLabel("Example Domain", "https://example.com/x"),
        )
        assertEquals(
            "example.com",
            BrowserTabManager.pageDisplayLabel(null, "https://www.example.com/x"),
        )
    }

    @Test
    fun updateActiveMetaIgnoresMismatchedSessionKey() {
        // SPEC-267: stale page callbacks must not clobber the newly active tab.
        BrowserTabManager.openUrlInActive("https://a.example/")
        val aId = BrowserTabManager.activeTabId.value
        BrowserTabManager.updateActiveMeta("https://a.example/", "A", sessionKey = aId)
        val bId = BrowserTabManager.openNewTab(url = "https://b.example/", select = true)
        BrowserTabManager.updateActiveMeta("https://a.example/stale", "STALE", sessionKey = aId)
        val b = BrowserTabManager.tabs.value.first { it.id == bId }
        assertEquals("https://b.example/", b.url)
        assertTrue(b.title != "STALE")
        BrowserTabManager.updateActiveMeta("https://b.example/ok", "B-OK", sessionKey = bId)
        assertEquals("https://b.example/ok", BrowserTabManager.activeTab()!!.url)
        assertEquals("B-OK", BrowserTabManager.activeTab()!!.title)
    }

    @Test
    fun tabDisplayTitleOnHomeIsNewTab() {
        BrowserTabManager.openUrlInActive("https://example.com/", omniboxLabel = "ex")
        BrowserTabManager.updateActiveMeta("https://example.com/", "Example")
        BrowserTabManager.clearActiveToNtp()
        assertEquals("新标签页", BrowserTabManager.tabDisplayTitle(BrowserTabManager.activeTab()!!))
    }
}
