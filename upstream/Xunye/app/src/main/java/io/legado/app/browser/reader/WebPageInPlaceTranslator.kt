package io.legado.app.browser.reader

import io.legado.app.browser.engine.AwContentsHostView
import io.legado.app.browser.engine.evalJs
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.model.translation.FoxletTranslateClient
import io.legado.app.model.translation.GoogleTranslateClient
import io.legado.app.model.translation.WebPageTranslatePacker
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * SPEC-251/254/255/261: Chrome-like in-page webpage translate (DOM text nodes; URL unchanged).
 * SPEC-255: block-level packing with numbered markers + leftover Latin rescan.
 * SPEC-261: batch packs / retries / leftover rescan (restore speed vs serial translateOnePack).
 */
object WebPageInPlaceTranslator : KoinComponent {

    private val settingsGateway: TranslationSettingsGateway by inject()
    private val translateChapterUseCase: TranslateChapterUseCase by inject()

    private const val AI_CONCURRENCY = 2
    private const val BATCH_CHUNK = 48

    data class Result(
        val ok: Boolean,
        val message: String,
        val translated: Boolean = false,
    )

    suspend fun toggle(
        host: AwContentsHostView,
        onStatus: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.Main.immediate) {
        val activeRaw = host.evalJs(WebPageInPlaceTranslateJs.IS_ACTIVE)?.trim().orEmpty()
        if (activeRaw.contains("1")) {
            host.evalJs(WebPageInPlaceTranslateJs.RESTORE)
            return@withContext Result(ok = true, message = "已还原原文", translated = false)
        }
        translate(host, onStatus)
    }

    suspend fun translate(
        host: AwContentsHostView,
        onStatus: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        onStatus("分析页面…")
        val collectRaw = withContext(Dispatchers.Main) {
            host.evalJs(WebPageInPlaceTranslateJs.COLLECT)
        }
        val meta = parseCollect(collectRaw)
            ?: return@withContext Result(false, "无法读取页面文本")
        when (meta.state) {
            "translated" -> {
                withContext(Dispatchers.Main) { host.evalJs(WebPageInPlaceTranslateJs.RESTORE) }
                return@withContext Result(true, "已还原原文", translated = false)
            }
            "empty" -> return@withContext Result(false, "页面无可翻译文本")
            "error" -> return@withContext Result(false, meta.message.ifBlank { "页面分析失败" })
        }
        if (meta.count <= 0) {
            return@withContext Result(false, "页面无可翻译文本")
        }

        val settings = settingsGateway.currentSettings
        val target = settings.targetLanguage
        val source = FoxletTranslateClient.guessSourceLang(meta.lang, meta.sample)
        if (FoxletTranslateClient.toFoxletTag(source) ==
            FoxletTranslateClient.toFoxletTag(target)
        ) {
            return@withContext Result(false, "页面已是目标语言")
        }

        onStatus("翻译中 0/${meta.count}…")
        val nodeBatch = 120
        var done = 0
        var failedPacks = 0
        while (done < meta.count) {
            val end = (done + nodeBatch).coerceAtMost(meta.count)
            val sliceRaw = withContext(Dispatchers.Main) {
                host.evalJs(WebPageInPlaceTranslateJs.batchSlice(done, end))
            }
            val slice = parseSlice(sliceRaw) ?: break
            if (slice.texts.isEmpty()) break
            val (translated, fails) = translatePacked(
                texts = slice.texts,
                blockIds = slice.blocks,
                source = source,
                target = target,
                onStatus = onStatus,
            )
            failedPacks += fails
            val json = GSON.toJson(translated)
            withContext(Dispatchers.Main) {
                host.evalJs(WebPageInPlaceTranslateJs.applyBatch(done, json))
            }
            done = end
            onStatus("翻译中 $done/${meta.count}…")
        }

        // SPEC-255: second pass — leftover Latin when targeting Chinese.
        val targetTag = FoxletTranslateClient.toFoxletTag(target)
        if (targetTag.startsWith("zh")) {
            onStatus("补译残留英文…")
            failedPacks += rescanLeftoverLatin(host, meta.count, source, target, onStatus)
        }

        withContext(Dispatchers.Main) {
            host.evalJs(WebPageInPlaceTranslateJs.MARK_ACTIVE)
        }
        val msg = if (failedPacks > 0) {
            "翻译完成（$failedPacks 段曾失败/补译）"
        } else {
            "翻译完成"
        }
        Result(ok = true, message = msg, translated = true)
    }

    private suspend fun translatePacked(
        texts: List<String>,
        blockIds: List<Int>,
        source: String,
        target: String,
        onStatus: (String) -> Unit,
    ): Pair<List<String>, Int> {
        val packs = WebPageTranslatePacker.pack(texts, blockIds)
        val out = texts.toMutableList()
        var failed = 0

        data class PackWork(
            val pack: WebPageTranslatePacker.Pack,
            val prepared: WebPageTranslatePacker.HanMask,
        )

        val works = ArrayList<PackWork>()
        for (pack in packs) {
            if (pack.skipTranslate) continue
            val prepared = WebPageTranslatePacker.maskHanRuns(pack.text)
            if (prepared.skipTranslate) continue
            works += PackWork(pack, prepared)
        }

        val translatedPacks = translateTextsBatch(
            texts = works.map { it.prepared.masked },
            source = source,
            target = target,
            onStatus = onStatus,
        )

        val retryNodes = LinkedHashMap<Int, String>()
        works.forEachIndexed { i, work ->
            val raw = translatedPacks.getOrNull(i)
            if (raw.isNullOrBlank()) {
                failed += 1
                queuePackNodes(work.pack, texts, retryNodes)
                return@forEachIndexed
            }
            val value = WebPageTranslatePacker.unmaskHanRuns(raw, work.prepared.hanRuns)
            val parts = WebPageTranslatePacker.unpack(work.pack, value, texts)
            if (parts == null) {
                // Markers dropped (common with Bergamot) — batch per-node retry.
                failed += 1
                queuePackNodes(work.pack, texts, retryNodes)
            } else {
                for (j in parts.indices) {
                    out[work.pack.from + j] = parts[j]
                }
            }
        }

        if (retryNodes.isNotEmpty()) {
            val indices = retryNodes.keys.toList()
            val originals = indices.map { retryNodes.getValue(it) }
            val prepared = originals.map { WebPageTranslatePacker.maskHanRuns(it) }
            val send = prepared.mapIndexed { idx, p ->
                if (p.skipTranslate) originals[idx] else p.masked
            }
            val retried = translateTextsBatch(send, source, target, onStatus)
            indices.forEachIndexed { i, nodeIdx ->
                val p = prepared[i]
                if (p.skipTranslate) {
                    out[nodeIdx] = originals[i]
                    return@forEachIndexed
                }
                val raw = retried.getOrNull(i)
                if (raw.isNullOrBlank()) {
                    failed += 1
                    out[nodeIdx] = originals[i]
                } else {
                    out[nodeIdx] = WebPageTranslatePacker.unmaskHanRuns(raw, p.hanRuns)
                }
            }
        }
        return out to failed
    }

    private fun queuePackNodes(
        pack: WebPageTranslatePacker.Pack,
        texts: List<String>,
        retryNodes: LinkedHashMap<Int, String>,
    ) {
        for (idx in pack.from..pack.toInclusive) {
            val original = texts[idx]
            if (WebPageTranslatePacker.isNonTranslatable(original)) continue
            retryNodes.putIfAbsent(idx, original)
        }
    }

    /**
     * SPEC-261: one provider batch call (chunked) instead of serial translateOnePack.
     * Null entry = hard failure for that item.
     */
    private suspend fun translateTextsBatch(
        texts: List<String>,
        source: String,
        target: String,
        onStatus: (String) -> Unit,
    ): List<String?> {
        if (texts.isEmpty()) return emptyList()
        val out = ArrayList<String?>(texts.size)
        var offset = 0
        while (offset < texts.size) {
            val end = (offset + BATCH_CHUNK).coerceAtMost(texts.size)
            val chunk = texts.subList(offset, end)
            out += translateTextsBatchChunk(chunk, source, target, onStatus)
            offset = end
        }
        return out
    }

    private suspend fun translateTextsBatchChunk(
        texts: List<String>,
        source: String,
        target: String,
        onStatus: (String) -> Unit,
    ): List<String?> {
        if (texts.isEmpty()) return emptyList()
        val provider = settingsGateway.currentSettings.provider
        return when (provider) {
            TranslationConstants.PROVIDER_GOOGLE -> {
                val result = GoogleTranslateClient.translateBatch(
                    texts = texts,
                    targetAppLang = target,
                    sourceAppLang = source,
                    onStatus = onStatus,
                )
                result.texts.map { value -> value.takeIf { it.isNotBlank() } }
            }
            TranslationConstants.PROVIDER_APP_AI -> {
                val semaphore = Semaphore(AI_CONCURRENCY)
                coroutineScope {
                    texts.map { text ->
                        async {
                            if (text.isBlank() || WebPageTranslatePacker.isNonTranslatable(text)) {
                                return@async text
                            }
                            semaphore.withPermit {
                                translateChapterUseCase.translatePlainText(text, target)
                                    .getOrNull()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: run {
                                        // one retry
                                        translateChapterUseCase.translatePlainText(text, target)
                                            .getOrNull()?.takeIf { it.isNotBlank() }
                                    }
                            }
                        }
                    }.awaitAll()
                }
            }
            else -> {
                val first = runCatching {
                    FoxletTranslateClient.translateBatch(texts, source, target, onStatus)
                }.getOrNull()
                if (first != null && first.size == texts.size) {
                    first.map { it.takeIf { s -> s.isNotBlank() } }
                } else {
                    onStatus("重试翻译…")
                    val second = runCatching {
                        FoxletTranslateClient.translateBatch(texts, source, target, onStatus)
                    }.getOrNull()
                    if (second != null && second.size == texts.size) {
                        second.map { it.takeIf { s -> s.isNotBlank() } }
                    } else {
                        List(texts.size) { null }
                    }
                }
            }
        }
    }

    private suspend fun rescanLeftoverLatin(
        host: AwContentsHostView,
        total: Int,
        source: String,
        target: String,
        onStatus: (String) -> Unit,
    ): Int {
        var patched = 0
        var failed = 0
        val batch = 100
        var start = 0
        while (start < total) {
            val end = (start + batch).coerceAtMost(total)
            val liveRaw = withContext(Dispatchers.Main) {
                host.evalJs(WebPageInPlaceTranslateJs.liveSlice(start, end))
            }
            val live = parseStringList(liveRaw)
            if (live.isEmpty()) {
                start = end
                continue
            }
            val hitIndices = live.indices.filter {
                WebPageTranslatePacker.looksLikeUntranslatedLatin(live[it])
            }
            if (hitIndices.isEmpty()) {
                start = end
                continue
            }
            val originals = hitIndices.map { live[it] }
            val prepared = originals.map { WebPageTranslatePacker.maskHanRuns(it) }
            val send = prepared.mapIndexed { i, p ->
                if (p.skipTranslate) originals[i] else p.masked
            }
            val translated = translateTextsBatch(send, source, target, onStatus)
            val out = live.toMutableList()
            var dirty = false
            hitIndices.forEachIndexed { i, localIdx ->
                val p = prepared[i]
                if (p.skipTranslate) return@forEachIndexed
                val raw = translated.getOrNull(i)
                if (raw.isNullOrBlank()) {
                    failed++
                    return@forEachIndexed
                }
                val value = WebPageTranslatePacker.unmaskHanRuns(raw, p.hanRuns)
                if (value != live[localIdx]) {
                    out[localIdx] = value
                    dirty = true
                    patched++
                }
            }
            if (dirty) {
                val json = GSON.toJson(out)
                withContext(Dispatchers.Main) {
                    host.evalJs(WebPageInPlaceTranslateJs.applyBatch(start, json))
                }
            }
            start = end
        }
        if (patched > 0) onStatus("已补译 $patched 处")
        return failed
    }

    private data class CollectDto(
        @SerializedName("state") val state: String? = null,
        @SerializedName("count") val count: Int? = null,
        @SerializedName("lang") val lang: String? = null,
        @SerializedName("sample") val sample: String? = null,
        @SerializedName("message") val message: String? = null,
    )

    private data class CollectMeta(
        val state: String,
        val count: Int,
        val lang: String,
        val sample: String,
        val message: String,
    )

    private data class SliceDto(
        @SerializedName("texts") val texts: List<String>? = null,
        @SerializedName("blocks") val blocks: List<Int>? = null,
    )

    private data class Slice(
        val texts: List<String>,
        val blocks: List<Int>,
    )

    private fun parseCollect(raw: String?): CollectMeta? {
        val json = unwrapJs(raw) ?: return null
        val dto = GSON.fromJsonObject<CollectDto>(json).getOrNull() ?: return null
        return CollectMeta(
            state = dto.state.orEmpty(),
            count = dto.count ?: 0,
            lang = dto.lang.orEmpty(),
            sample = dto.sample.orEmpty(),
            message = dto.message.orEmpty(),
        )
    }

    private fun parseSlice(raw: String?): Slice? {
        val json = unwrapJs(raw) ?: return null
        val dto = GSON.fromJsonObject<SliceDto>(json).getOrNull() ?: return null
        val texts = dto.texts.orEmpty()
        val blocks = dto.blocks ?: List(texts.size) { it }
        return Slice(texts, blocks)
    }

    private fun parseStringList(raw: String?): List<String> {
        val json = unwrapJs(raw) ?: return emptyList()
        return runCatching {
            GSON.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun unwrapJs(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val s = raw.trim()
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return runCatching { GSON.fromJson(s, String::class.java) }.getOrNull() ?: s.trim('"')
        }
        return s
    }
}
