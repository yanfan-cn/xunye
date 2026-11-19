package io.legado.app.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC-268: external-app scheme + intent browser_fallback_url. */
class BrowserUrlPolicyTest {

    @Test
    fun httpHttpsAreNotExternal() {
        assertFalse(BrowserUrlPolicy.isExternalAppLaunch("https://example.com/a"))
        assertFalse(BrowserUrlPolicy.isExternalAppLaunch("http://example.com/a"))
        assertFalse(BrowserUrlPolicy.isExternalAppLaunch("about:blank"))
        assertFalse(BrowserUrlPolicy.isExternalAppLaunch("javascript:void(0)"))
    }

    @Test
    fun customSchemesAreExternal() {
        assertTrue(BrowserUrlPolicy.isExternalAppLaunch("baiduboxapp://foo"))
        assertTrue(BrowserUrlPolicy.isExternalAppLaunch("bilibili://video/1"))
        assertTrue(BrowserUrlPolicy.isExternalAppLaunch("intent://host#Intent;end"))
        assertFalse(BrowserUrlPolicy.isExternalAppLaunch("legado://import"))
        assertFalse(BrowserUrlPolicy.isExternalAppLaunch("yuedu://import"))
    }

    @Test
    fun intentFallbackDecodedOnce() {
        val url =
            "intent://example.com/path#Intent;scheme=https;" +
                "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Ffallback;end"
        assertEquals(
            "https://example.com/fallback",
            BrowserUrlPolicy.intentBrowserFallbackUrl(url),
        )
    }

    @Test
    fun intentFallbackRejectsNonHttp() {
        val url =
            "intent://x#Intent;S.browser_fallback_url=baiduboxapp%3A%2F%2Fx;end"
        assertNull(BrowserUrlPolicy.intentBrowserFallbackUrl(url))
    }

    @Test
    fun nonIntentHasNoFallback() {
        assertNull(BrowserUrlPolicy.intentBrowserFallbackUrl("baiduboxapp://open"))
        assertNull(BrowserUrlPolicy.intentBrowserFallbackUrl("https://example.com"))
    }
}
