package io.legado.app.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPEC-267: share-to-favorite URL extraction. */
class SharedReceiverFavoriteTest {

    @Test
    fun firstHttpUrlFromPlainLink() {
        assertEquals(
            "https://example.com/path",
            SharedReceiverActivity.firstHttpUrl("https://example.com/path"),
        )
    }

    @Test
    fun firstHttpUrlFromMixedText() {
        assertEquals(
            "http://a.example/x",
            SharedReceiverActivity.firstHttpUrl("看看这个 http://a.example/x 好吗"),
        )
    }

    @Test
    fun firstHttpUrlAbsent() {
        assertNull(SharedReceiverActivity.firstHttpUrl("没有链接只有文字"))
        assertNull(SharedReceiverActivity.firstHttpUrl(""))
    }

    @Test
    fun firstHttpUrlTrimsTrailingPunctuation() {
        assertEquals(
            "https://example.com/a",
            SharedReceiverActivity.firstHttpUrl("见 https://example.com/a。"),
        )
    }
}
