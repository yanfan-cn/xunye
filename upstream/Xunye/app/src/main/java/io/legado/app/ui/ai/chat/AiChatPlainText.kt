package io.legado.app.ui.ai.chat

/**
 * SPEC-263: strip common Markdown markers for copy / share plain text.
 */
object AiChatPlainText {

    private val fenceRegex = Regex(
        """```[^\n]*\n([\s\S]*?)```|~~~[^\n]*\n([\s\S]*?)~~~""",
    )
    private val inlineCodeRegex = Regex("""`([^`]+)`""")
    private val headingRegex = Regex("""(?m)^#{1,6}\s+""")
    private val boldItalicStarRegex = Regex("""\*+([^*]+)\*+""")
    private val boldItalicUnderscoreRegex = Regex("""_+([^_]+)_+""")
    private val linkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
    private val imageRegex = Regex("""!\[([^\]]*)]\([^)]+\)""")
    private val blockQuoteRegex = Regex("""(?m)^>\s?""")
    private val unorderedMarkerRegex = Regex("""(?m)^(\s*)[-*+]\s+""")
    private val leftoverHashRegex = Regex("""#{1,6}""")
    private val leftoverStarRegex = Regex("""\*+""")

    fun stripMarkdown(raw: String): String {
        if (raw.isBlank()) return raw
        var text = raw.replace("\r\n", "\n")
        text = fenceRegex.replace(text) { mr ->
            mr.groupValues[1].ifEmpty { mr.groupValues[2] }
        }
        text = imageRegex.replace(text) { it.groupValues[1] }
        text = linkRegex.replace(text) { it.groupValues[1] }
        text = inlineCodeRegex.replace(text) { it.groupValues[1] }
        text = headingRegex.replace(text, "")
        text = boldItalicStarRegex.replace(text) { it.groupValues[1] }
        text = boldItalicUnderscoreRegex.replace(text) { it.groupValues[1] }
        text = blockQuoteRegex.replace(text, "")
        text = unorderedMarkerRegex.replace(text) { "${it.groupValues[1]}• " }
        // Drop residual # / * that remain after structural strip.
        text = leftoverHashRegex.replace(text, "")
        text = leftoverStarRegex.replace(text, "")
        return text
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")
    }
}
