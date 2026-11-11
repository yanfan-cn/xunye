package io.legado.app.model.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC-254/255: packing / markers for in-page translate. */
class WebPageTranslatePackerTest {

    @Test
    fun nonTranslatable_skips_urls_and_numbers() {
        assertTrue(WebPageTranslatePacker.isNonTranslatable("https://example.com/a"))
        assertTrue(WebPageTranslatePacker.isNonTranslatable("12.5%"))
        assertTrue(WebPageTranslatePacker.isNonTranslatable("  "))
        assertFalse(WebPageTranslatePacker.isNonTranslatable("Hello world"))
    }

    @Test
    fun markers_round_trip_across_link_split() {
        val parts = listOf(
            "While RX sales surge, the top-selling ",
            "Toyota RAV4",
            ", has seen sales fall nearly 30%.",
        )
        val joined = WebPageTranslatePacker.joinWithMarkers(parts)
        assertTrue(joined.contains("⟦1⟧"))
        assertTrue(joined.contains("⟦2⟧"))
        val split = WebPageTranslatePacker.splitByMarkers(joined, 3)
        assertNotNull(split)
        assertEquals(parts, split)
    }

    @Test
    fun pack_by_same_block_keeps_link_neighbors_together() {
        val texts = listOf(
            "While RX sales surge, the top-selling ",
            "Toyota RAV4",
            ", has seen sales fall nearly 30%.",
            "Next paragraph alone.",
        )
        val blocks = listOf(0, 0, 0, 1)
        val packs = WebPageTranslatePacker.pack(texts, blocks)
        assertEquals(2, packs.size)
        assertEquals(0, packs[0].from)
        assertEquals(2, packs[0].toInclusive)
        assertEquals(3, packs[0].partCount)
        assertTrue(packs[0].text.contains("⟦1⟧"))
    }

    @Test
    fun missing_markers_returns_null_for_retry() {
        assertNull(
            WebPageTranslatePacker.splitByMarkers("整段中文没有占位符", 3),
        )
    }

    @Test
    fun leftover_latin_detect() {
        assertTrue(
            WebPageTranslatePacker.looksLikeUntranslatedLatin(
                "While RX sales surge, its more mainstream counterpart",
            ),
        )
        assertFalse(
            WebPageTranslatePacker.looksLikeUntranslatedLatin("由于丰田的销量不足，其销量已下降。"),
        )
    }

    @Test
    fun pure_han_is_non_translatable() {
        assertTrue(WebPageTranslatePacker.isNonTranslatable("由于丰田的销量不足"))
        assertFalse(WebPageTranslatePacker.isNonTranslatable("Hello 世界"))
    }

    @Test
    fun mask_han_keeps_latin_for_translate() {
        val prepared = WebPageTranslatePacker.maskHanRuns("The 中国 market is growing")
        assertFalse(prepared.skipTranslate)
        assertEquals(listOf("中国"), prepared.hanRuns)
        assertTrue(prepared.masked.contains("⟦H0⟧"))
        assertTrue(prepared.masked.contains("market"))
        val restored = WebPageTranslatePacker.unmaskHanRuns(
            prepared.masked.replace("market", "市场"),
            prepared.hanRuns,
        )
        assertEquals("The 中国 市场 is growing", restored)
    }

    @Test
    fun resolve_web_dark_auto_follows_night() {
        assertEquals(
            io.legado.app.browser.engine.BrowserChromePrefs.WEB_DARK_ON,
            io.legado.app.browser.engine.BrowserChromePrefs.resolveWebContentDarkMode(
                io.legado.app.browser.engine.BrowserChromePrefs.WEB_DARK_AUTO,
                nightMode = true,
            ),
        )
        assertEquals(
            io.legado.app.browser.engine.BrowserChromePrefs.WEB_DARK_OFF,
            io.legado.app.browser.engine.BrowserChromePrefs.resolveWebContentDarkMode(
                io.legado.app.browser.engine.BrowserChromePrefs.WEB_DARK_AUTO,
                nightMode = false,
            ),
        )
    }
}
