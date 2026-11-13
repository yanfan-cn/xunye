package io.legado.app.ui.ai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiChatPlainTextTest {

    @Test
    fun stripMarkdown_removesHashesAndStars() {
        val raw = """
            ## 标题
            这是 **加粗** 与 *斜体* 内容
            - 条目一
            - 条目二
            见 [链接](https://example.com)
        """.trimIndent()

        val plain = AiChatPlainText.stripMarkdown(raw)

        assertFalse(plain.contains("#"))
        assertFalse(plain.contains("*"))
        assertEquals(true, plain.contains("标题"))
        assertEquals(true, plain.contains("加粗"))
        assertEquals(true, plain.contains("斜体"))
        assertEquals(true, plain.contains("链接"))
        assertEquals(true, plain.contains("• 条目一"))
    }

    @Test
    fun stripMarkdown_keepsCodeFenceBody() {
        val raw = """
            说明
            ```
            val x = 1
            ```
            结束
        """.trimIndent()

        val plain = AiChatPlainText.stripMarkdown(raw)
        assertEquals(true, plain.contains("val x = 1"))
        assertFalse(plain.contains("```"))
    }
}
