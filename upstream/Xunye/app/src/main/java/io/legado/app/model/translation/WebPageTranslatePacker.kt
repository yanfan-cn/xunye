package io.legado.app.model.translation

/**
 * SPEC-254/255: pack DOM text nodes for in-page translate.
 * SPEC-255: prefer same blockId; numbered placeholders ⟦n⟧ survive Bergamot/Google better
 * than private-use separators.
 * SPEC-259: mask Han runs inside mixed nodes so only Latin fragments are translated.
 */
object WebPageTranslatePacker {

    private const val PACK_MAX_CHARS = 900
    private val HAN_RANGE = '\u4e00'..'\u9fff'

    data class Pack(
        val from: Int,
        val toInclusive: Int,
        val text: String,
        val skipTranslate: Boolean,
        val partCount: Int = 1,
    )

    data class HanMask(
        val masked: String,
        val hanRuns: List<String>,
        val skipTranslate: Boolean,
    )

    fun isNonTranslatable(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (t.length <= 1) return true
        if (t.all { !it.isLetter() }) return true
        if (t.matches(Regex("""https?://\S+|www\.\S+|[\w.+-]+@[\w.-]+\.\w+"""))) return true
        if (t.matches(Regex("""^[\d\s.,:%$€¥+/\\-]+$"""))) return true
        // SPEC-259: pure CJK (no Latin) — skip engine; avoids mangling Chinese.
        val hasLatin = t.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasHan = t.any { it in HAN_RANGE }
        if (hasHan && !hasLatin) return true
        return false
    }

    /**
     * SPEC-259: replace consecutive Han with ⟦Hk⟧ so MT only sees Latin; restore after.
     */
    fun maskHanRuns(text: String): HanMask {
        if (isNonTranslatable(text)) {
            return HanMask(masked = text, hanRuns = emptyList(), skipTranslate = true)
        }
        if (text.none { it in HAN_RANGE }) {
            return HanMask(masked = text, hanRuns = emptyList(), skipTranslate = false)
        }
        val runs = ArrayList<String>()
        val masked = buildString(text.length + 8) {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c in HAN_RANGE) {
                    val start = i
                    while (i < text.length && text[i] in HAN_RANGE) i++
                    append('⟦').append('H').append(runs.size).append('⟧')
                    runs += text.substring(start, i)
                } else {
                    append(c)
                    i++
                }
            }
        }
        val stillLatin = masked.any { it in 'A'..'Z' || it in 'a'..'z' }
        if (!stillLatin) {
            return HanMask(masked = text, hanRuns = emptyList(), skipTranslate = true)
        }
        return HanMask(masked = masked, hanRuns = runs, skipTranslate = false)
    }

    fun unmaskHanRuns(translated: String, hanRuns: List<String>): String {
        if (hanRuns.isEmpty()) return translated
        var out = translated
        hanRuns.forEachIndexed { i, run ->
            val marker = "⟦H$i⟧"
            out = if (out.contains(marker)) out.replace(marker, run) else out
        }
        return out
    }

    /** True when text still looks like Latin source while target is CJK. */
    fun looksLikeUntranslatedLatin(text: String): Boolean {
        val t = text.trim()
        if (t.length < 12) return false
        if (isNonTranslatable(t)) return false
        val letters = t.count { it.isLetter() }.coerceAtLeast(1)
        val latin = t.count { it in 'A'..'Z' || it in 'a'..'z' }
        val han = t.count { it in '\u4e00'..'\u9fff' }
        return latin.toFloat() / letters >= 0.55f && han < 4 && latin >= 10
    }

    fun joinWithMarkers(parts: List<String>): String {
        if (parts.isEmpty()) return ""
        if (parts.size == 1) return parts[0]
        return buildString {
            parts.forEachIndexed { i, p ->
                if (i > 0) append('⟦').append(i).append('⟧')
                append(p)
            }
        }
    }

    fun splitByMarkers(translated: String, partCount: Int): List<String>? {
        if (partCount <= 1) return listOf(translated)
        val positions = ArrayList<Pair<Int, String>>(partCount - 1)
        for (i in 1 until partCount) {
            val marker = "⟦$i⟧"
            val at = translated.indexOf(marker)
            if (at < 0) return null
            if (positions.isNotEmpty() && at <= positions.last().first) return null
            positions += at to marker
        }
        val parts = ArrayList<String>(partCount)
        var start = 0
        for ((at, marker) in positions) {
            parts += translated.substring(start, at)
            start = at + marker.length
        }
        parts += translated.substring(start)
        return if (parts.size == partCount) parts else null
    }

    /**
     * Pack by shared [blockIds] (same paragraph/heading), keeping node order.
     * Falls back to sequential packing when blockIds missing/mismatched.
     */
    fun pack(texts: List<String>, blockIds: List<Int>? = null): List<Pack> {
        if (texts.isEmpty()) return emptyList()
        val blocks = if (blockIds != null && blockIds.size == texts.size) blockIds else null
        val packs = ArrayList<Pack>()
        var i = 0
        while (i < texts.size) {
            if (isNonTranslatable(texts[i])) {
                packs += Pack(i, i, texts[i], skipTranslate = true, partCount = 1)
                i++
                continue
            }
            val block = blocks?.getOrNull(i)
            val parts = ArrayList<String>()
            var end = i
            var chars = 0
            while (end < texts.size) {
                if (blocks != null && blocks[end] != block) break
                val piece = texts[end]
                // Always include non-translatable glue inside the same block (spaces, "·").
                val addCost = piece.length + if (parts.isEmpty()) 0 else 4
                if (parts.isNotEmpty() && chars + addCost > PACK_MAX_CHARS) break
                if (parts.isNotEmpty() &&
                    !isNonTranslatable(piece) &&
                    blocks == null &&
                    piece.length > 160
                ) {
                    break
                }
                parts += piece
                chars += addCost
                end++
            }
            val last = (end - 1).coerceAtLeast(i)
            val allSkip = parts.all { isNonTranslatable(it) }
            packs += Pack(
                from = i,
                toInclusive = last,
                text = joinWithMarkers(parts),
                skipTranslate = allSkip,
                partCount = parts.size,
            )
            i = last + 1
        }
        return packs
    }

    fun unpack(pack: Pack, translated: String, originals: List<String>): List<String>? {
        val count = pack.toInclusive - pack.from + 1
        if (count == 1) return listOf(translated)
        splitByMarkers(translated, pack.partCount)?.let { return it }
        // Marker lost — signal caller to retry per-node (do not leave English tails).
        return null
    }

    fun applyPacks(
        originals: List<String>,
        packs: List<Pack>,
        translatedPackTexts: List<String>,
    ): List<String> {
        require(packs.size == translatedPackTexts.size)
        val out = originals.toMutableList()
        packs.forEachIndexed { idx, pack ->
            val piece = if (pack.skipTranslate) pack.text else translatedPackTexts[idx]
            val nodes = unpack(pack, piece, originals)
                ?: List(pack.toInclusive - pack.from + 1) { j ->
                    // Temporary: keep original until caller fills via per-node retry.
                    originals[pack.from + j]
                }
            for (j in nodes.indices) {
                out[pack.from + j] = nodes[j]
            }
        }
        return out
    }
}
