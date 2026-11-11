package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.PartialTranslationAssembler
import io.legado.app.domain.model.PartialTranslationAssembler.PartialChunkTranslation
import io.legado.app.domain.model.RetryReason
import io.legado.app.domain.model.TextChunk
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationConstants.OUTPUT_FORMAT
import io.legado.app.domain.model.TranslationDictionaryPolicy
import io.legado.app.domain.model.TranslationResultPreviewParser
import io.legado.app.help.book.BookHelp
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class TranslateChapterUseCase(
    private val aiTextGateway: AiTextGateway,
    private val translationCacheGateway: TranslationCacheGateway,
    private val dictionaryGateway: DictionaryGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val translationSettingsGateway: TranslationSettingsGateway,
) {

    data class TranslationProgress(
        val currentChunk: Int,
        val totalChunks: Int,
        val mixedContent: String? = null,
        val translatedChunkIndices: Set<Int> = emptySet()
    )

    suspend fun execute(
        book: Book,
        bookChapter: BookChapter,
        onProgress: (TranslationProgress) -> Unit,
        onTranslateStarted: () -> Unit,
        onThinkingChanged: (Boolean) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = translationSettingsGateway.currentSettings
            val provider = settings.provider
            val preset = if (provider == TranslationConstants.PROVIDER_APP_AI) {
                resolveTranslationPreset()
                    ?: return@withContext Result.failure(Exception("No AI translation preset configured"))
            } else {
                null
            }
            val targetLanguage = settings.targetLanguage

            val originalContent = BookHelp.getContent(book, bookChapter)
                ?: return@withContext Result.failure(Exception("Failed to read original content"))

            val cachedTranslation =
                translationCacheGateway.readTranslation(book, bookChapter, targetLanguage)
            if (cachedTranslation != null) {
                onProgress(TranslationProgress(1, 1, cachedTranslation, emptySet()))
                return@withContext Result.success(cachedTranslation)
            }

            // SPEC-251: Bergamot/Foxlet on-device chapter translation.
            if (provider == TranslationConstants.PROVIDER_BERGAMOT) {
                onTranslateStarted()
                onProgress(TranslationProgress(0, 1, null, emptySet()))
                val bergResult = translateWithBergamot(originalContent, targetLanguage)
                bergResult.onSuccess { translated ->
                    translationCacheGateway.writeTranslation(
                        book, bookChapter, targetLanguage, translated,
                    )
                    onProgress(TranslationProgress(1, 1, translated, setOf(0)))
                }
                return@withContext bergResult
            }

            // SPEC-249/251: ML Kit removed from product; keep path for legacy prefs that slip through.
            if (provider == TranslationConstants.PROVIDER_MLKIT) {
                return@withContext Result.failure(
                    Exception("端上 ML Kit 已移除，请在翻译设置中改用 Bergamot（本机）"),
                )
            }

            val contentHash = translationCacheGateway.computeContentHash(originalContent)

            val chunkLimit = when (provider) {
                // Baidu free web API is short-text friendly.
                TranslationConstants.PROVIDER_BAIDU ->
                    settings.maxCharsPerChunk.coerceIn(500, 1800)
                else -> settings.maxCharsPerChunk.coerceAtLeast(1000)
            }
            val chunks = ContentChunker.chunk(originalContent, chunkLimit)
            if (chunks.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to chunk content"))
            }

            val cachedChunks =
                translationCacheGateway.getCachedChunks(book, bookChapter, targetLanguage, contentHash)
            val cachedChunkMap = cachedChunks.filter { it.isSuccess }.associateBy { it.chunkIndex }

            val translatedChunks = mutableMapOf<Int, String>()
            val pendingChunks = mutableListOf<TextChunk>()

            // Load already cached chunks
            for (chunk in chunks) {
                val cached = cachedChunkMap[chunk.index]
                if (cached != null && cached.translatedChunkContent != null) {
                    translatedChunks[chunk.index] = cached.translatedChunkContent
                } else {
                    pendingChunks.add(chunk)
                }
            }

            // If we have partial cached chunks, report initial mixed content
            if (translatedChunks.isNotEmpty()) {
                val mixedContent = PartialTranslationAssembler.assemble(chunks, translatedChunks)
                onProgress(TranslationProgress(
                    translatedChunks.size,
                    chunks.size,
                    mixedContent,
                    translatedChunks.keys.toSet()
                ))
            }

            if (pendingChunks.isEmpty()) {
                val sortedChunks = chunks.sortedBy { it.index }.mapNotNull { translatedChunks[it.index]?.let { content -> TextChunk(it.index, content, it.paragraphIndices) } }
                val mergedContent = ContentChunker.merge(sortedChunks)
                translationCacheGateway.writeTranslation(
                    book,
                    bookChapter,
                    targetLanguage,
                    mergedContent
                )
                onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
                return@withContext Result.success(mergedContent)
            }

            onTranslateStarted()
            for (chunk in pendingChunks.sortedBy { it.index }) {
                val publishWithoutPartial = {
                    val mixedContent = PartialTranslationAssembler.assemble(chunks, translatedChunks)
                    onProgress(
                        TranslationProgress(
                            currentChunk = translatedChunks.size,
                            totalChunks = chunks.size,
                            mixedContent = mixedContent,
                            translatedChunkIndices = translatedChunks.keys.toSet(),
                        )
                    )
                }
                val result = try {
                    translateAndCacheChunk(
                        chunk = chunk,
                        book = book,
                        bookChapter = bookChapter,
                        targetLanguage = targetLanguage,
                        contentHash = contentHash,
                        provider = provider,
                        preset = preset,
                        retryCount = settings.retryCount,
                        onPreviewSnapshot = { preview ->
                            val mixedContent = PartialTranslationAssembler.assemble(
                                originalChunks = chunks,
                                translatedMap = translatedChunks,
                                partialChunk = PartialChunkTranslation(chunk.index, preview),
                            )
                            onProgress(
                                TranslationProgress(
                                    currentChunk = translatedChunks.size,
                                    totalChunks = chunks.size,
                                    mixedContent = mixedContent,
                                    translatedChunkIndices = translatedChunks.keys.toSet(),
                                )
                            )
                        },
                        onPreviewReset = publishWithoutPartial,
                        onThinkingChanged = onThinkingChanged,
                    )
                } catch (e: CancellationException) {
                    publishWithoutPartial()
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                if (result.isFailure) {
                    publishWithoutPartial()
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Translation failed")
                    )
                }

                translatedChunks[chunk.index] = result.getOrThrow()
                publishWithoutPartial()
            }

            if (translatedChunks.size != chunks.size) {
                return@withContext Result.failure(Exception("Translation incomplete"))
            }

            val allTranslatedChunks = chunks.sortedBy { it.index }.mapNotNull { chunk ->
                translatedChunks[chunk.index]?.let { content -> TextChunk(chunk.index, content, chunk.paragraphIndices) }
            }
            val mergedContent = ContentChunker.merge(allTranslatedChunks)
            translationCacheGateway.writeTranslation(book, bookChapter, targetLanguage, mergedContent)

            onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
            Result.success(mergedContent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun translateAndCacheChunk(
        chunk: TextChunk,
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        provider: String,
        preset: AiTaskPresetConfig?,
        retryCount: Int,
        onPreviewSnapshot: (String) -> Unit,
        onPreviewReset: () -> Unit,
        onThinkingChanged: (Boolean) -> Unit,
    ): Result<String> {
        val existingCache =
            translationCacheGateway.getCachedChunk(book, bookChapter, targetLanguage, chunk.index)
        if (existingCache?.isSuccess == true && existingCache.translatedChunkContent != null) {
            return Result.success(existingCache.translatedChunkContent)
        }

        val result = translateChunkWithRetry(
            chunk = chunk,
            book = book,
            targetLanguage = targetLanguage,
            provider = provider,
            preset = preset,
            retryCount = retryCount,
            onPreviewSnapshot = onPreviewSnapshot,
            onPreviewReset = onPreviewReset,
            onThinkingChanged = onThinkingChanged,
        )
        if (result.isSuccess) {
            val translated = result.getOrThrow()
            if (translated.discoveredPairs.isNotEmpty()) {
                dictionaryGateway.mergeDiscoveredPairs(book, translated.discoveredPairs)
            }
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, contentHash,
                provider,
                TranslationCache.STATUS_SUCCESS, translated.text, null
            )
        }
        return result.map { it.text }
    }

    private data class ChunkTranslation(
        val text: String,
        val discoveredPairs: List<DictPair> = emptyList(),
    )

    private suspend fun translateChunkWithRetry(
        chunk: TextChunk,
        book: Book,
        targetLanguage: String,
        provider: String,
        preset: AiTaskPresetConfig?,
        retryCount: Int,
        onPreviewSnapshot: (String) -> Unit,
        onPreviewReset: () -> Unit,
        onThinkingChanged: (Boolean) -> Unit,
    ): Result<ChunkTranslation> {
        var lastError: Exception? = null
        var lastRetryReason: RetryReason? = null
        try {
            for (attempt in 0..retryCount.coerceIn(0, 5)) {
                val result = when (provider) {
                    TranslationConstants.PROVIDER_GOOGLE ->
                        translateWithGoogle(chunk.content, targetLanguage).map { ChunkTranslation(it) }
                    TranslationConstants.PROVIDER_BAIDU ->
                        Result.failure(Exception("百度翻译已移除，请改用 Bergamot（本机）"))
                    TranslationConstants.PROVIDER_APP_AI -> {
                        val allDictionaries = dictionaryGateway.getBookDictionaries(book).pairs
                        translateWithAiGateway(
                            text = chunk.content,
                            targetLanguage = targetLanguage,
                            preset = preset
                                ?: return Result.failure(Exception("No AI translation preset configured")),
                            allDictionaries = allDictionaries,
                            relevantDictionaries = TranslationDictionaryPolicy.selectRelevantPairs(
                                allDictionaries,
                                chunk.content,
                            ),
                            retryReason = lastRetryReason,
                            onPreviewSnapshot = onPreviewSnapshot,
                            onThinkingChanged = onThinkingChanged,
                        )
                    }
                    else -> Result.failure(
                        IllegalArgumentException("Unknown translation provider: $provider")
                    )
                }
                if (result.isSuccess) {
                    return result
                }
                onPreviewReset()
                lastError = result.exceptionOrNull() as? Exception
                lastRetryReason = parseRetryReason(lastError)
            }
        } catch (e: CancellationException) {
            onPreviewReset()
            throw e
        }
        return Result.failure(lastError ?: Exception("Translation failed after retries"))
    }

    private suspend fun resolveTranslationPreset(): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
    }

    /** SPEC-251: short plaintext for webpage in-place batches (Google / App AI). */
    suspend fun translatePlainText(text: String, targetLanguage: String): Result<String> =
        withContext(Dispatchers.IO) {
            when (val provider = translationSettingsGateway.currentSettings.provider) {
                TranslationConstants.PROVIDER_GOOGLE -> translateWithGoogle(text, targetLanguage)
                TranslationConstants.PROVIDER_APP_AI -> {
                    val preset = resolveTranslationPreset()
                        ?: return@withContext Result.failure(Exception("未配置应用 AI 翻译模型"))
                    translateWithAiGateway(
                        text = text,
                        targetLanguage = targetLanguage,
                        preset = preset,
                        allDictionaries = emptyList(),
                        relevantDictionaries = emptyList(),
                        retryReason = null,
                        onPreviewSnapshot = {},
                        onThinkingChanged = {},
                    ).map { it.text }
                }
                TranslationConstants.PROVIDER_BERGAMOT ->
                    translateWithBergamot(text, targetLanguage)
                else -> Result.failure(Exception("不支持的翻译提供方: $provider"))
            }
        }

    /** SPEC-251: Bergamot chapter / plain text. Source guessed as English unless clearly CJK. */
    private suspend fun translateWithBergamot(
        text: String,
        targetLanguage: String,
    ): Result<String> = runCatching {
        val source = when {
            text.any { it in '\u3040'..'\u30ff' } -> "ja"
            text.any { it in '\uac00'..'\ud7af' } -> "ko"
            text.count { it in '\u4e00'..'\u9fff' } > text.length / 5 -> "zh"
            else -> "en"
        }
        io.legado.app.model.translation.FoxletTranslateClient.translateText(
            text = text,
            sourceAppLang = source,
            targetAppLang = targetLanguage,
        )
    }

    /** SPEC-254: delegate to GoogleTranslateClient (POST/chunk/retry). */
    private suspend fun translateWithGoogle(text: String, targetLanguage: String): Result<String> =
        io.legado.app.model.translation.GoogleTranslateClient.translateText(
            text = text,
            targetAppLang = targetLanguage,
            sourceAppLang = "auto",
        )

    /** SPEC-048: Baidu free web translate (mainland-friendly). */
    private suspend fun translateWithBaidu(text: String, targetLanguage: String): Result<String> {
        val to = baiduLangCode(targetLanguage)
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
        val url = "https://fanyi.baidu.com/transapi?from=auto&to=$to&query=$encoded"
        val response = okHttpClient.newCallStrResponse {
            url(url)
            addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            addHeader("Referer", "https://fanyi.baidu.com/")
        }
        val body = response.body.orEmpty()
        if (!response.isSuccessful()) {
            return Result.failure(Exception("百度翻译 HTTP ${response.code()}"))
        }
        return runCatching {
            val root = com.google.gson.JsonParser.parseString(body).asJsonObject
            val data = root.getAsJsonArray("data")
            val out = buildString {
                if (data != null) {
                    for (el in data) {
                        el.asJsonObject.get("dst")?.asString?.let { append(it) }
                    }
                }
            }
            if (out.isBlank()) {
                root.get("result")?.asString?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
                error("Empty Baidu translation")
            }
            out
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception("百度翻译失败: ${it.message}", it)) },
        )
    }

    private fun baiduLangCode(lang: String): String = when (lang.lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "zh"
        "en" -> "en"
        "ja" -> "jp"
        "ko" -> "kor"
        "fr" -> "fra"
        "de" -> "de"
        "es" -> "spa"
        "ru" -> "ru"
        "ar" -> "ara"
        else -> "zh"
    }

    private suspend fun translateWithAiGateway(
        text: String,
        targetLanguage: String,
        preset: AiTaskPresetConfig,
        allDictionaries: List<DictPair>,
        relevantDictionaries: List<DictPair>,
        retryReason: RetryReason?,
        onPreviewSnapshot: (String) -> Unit,
        onThinkingChanged: (Boolean) -> Unit,
    ): Result<ChunkTranslation> {
        if (targetLanguage == "en" && isMostlyEnglish(text)) {
            return Result.success(ChunkTranslation(text))
        }
        if (targetLanguage == "zh" && isMostlyChinese(text)) {
            return Result.success(ChunkTranslation(text))
        }
        if (preset.model.provider.baseUrl.isBlank() ||
            preset.model.provider.apiKey.isBlank() ||
            preset.model.modelId.isBlank()
        ) {
            return Result.failure(IllegalArgumentException("AI model configuration is incomplete"))
        }

        val dictionaryInstruction = buildDictionaryInstruction(relevantDictionaries)
        val retryInstruction = buildRetryInstruction(retryReason)
        val systemPrompt = buildSystemPrompt(
            prompt = preset.promptTemplate,
            targetLanguage = targetLanguage,
            dictionaryInstruction = dictionaryInstruction,
            retryInstruction = retryInstruction,
            outputFormat = OUTPUT_FORMAT
        )
        val params = preset.params.copy(
            temperature = preset.params.temperature
                ?: preset.model.defaultParams.temperature
                ?: TranslationConstants.DEFAULT_TEMPERATURE
        )
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                AiMessage(AiMessageRole.USER, "Translate the following text:\n\n$text"),
            ),
            params = params,
        )
        val rawContent = StringBuilder()
        val previewParser = TranslationResultPreviewParser()
        try {
            aiTextGateway.generateStream(request).collect { event ->
                when (event) {
                    is AiStreamEvent.Reasoning -> onThinkingChanged(true)
                    is AiStreamEvent.Content -> {
                        onThinkingChanged(false)
                        rawContent.append(event.text)
                        previewParser.feed(event.text).forEach(onPreviewSnapshot)
                    }
                    is AiStreamEvent.ToolCallDelta -> Unit
                    is AiStreamEvent.Usage -> Unit
                }
            }
            previewParser.finish().forEach(onPreviewSnapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            onThinkingChanged(false)
        }
        if (rawContent.isBlank()) {
            return Result.failure(Exception("Empty translation result"))
        }
        val parseResult = parseLlmOutput(rawContent.toString(), allDictionaries)
        val finalText = if (targetLanguage == "zh") {
            filterHighEnglishParagraphs(parseResult.translatedText)
        } else {
            parseResult.translatedText
        }
        if (finalText.isBlank()) {
            return Result.failure(Exception("Empty translation result"))
        }
        return Result.success(ChunkTranslation(finalText, parseResult.extractedPairs.take(10)))
    }

    private data class ParseOutputResult(
        val translatedText: String,
        val extractedPairs: List<DictPair>
    )

    private fun parseLlmOutput(
        rawOutput: String,
        existingDictionaries: List<DictPair>
    ): ParseOutputResult {
        val existingOriginals = existingDictionaries
            .map { TranslationDictionaryPolicy.normalizeOriginal(it.original) }
            .toSet()
        var dictionarySection: String? = null
        var resultSection: String? = null
        var currentSection: String? = null

        for (line in rawOutput.split('\n')) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("[dictionary]", ignoreCase = true) -> {
                    currentSection = "dictionary"
                    trimmedLine.drop("[dictionary]".length).trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { dictionarySection = it + "\n" }
                }
                trimmedLine.startsWith("[result]", ignoreCase = true) -> {
                    currentSection = "result"
                    trimmedLine.drop("[result]".length).trimStart()
                        .takeIf { it.isNotEmpty() }
                        ?.let { resultSection = it + "\n" }
                }
                currentSection == "dictionary" -> dictionarySection = (dictionarySection ?: "") + line + "\n"
                currentSection == "result" -> resultSection = (resultSection ?: "") + line + "\n"
            }
        }

        val extractedPairs = dictionarySection
            ?.let { parseDictionarySection(it, existingOriginals) }
            .orEmpty()
        val translatedText = resultSection?.trim() ?: rawOutput.trim()
        return ParseOutputResult(translatedText, extractedPairs)
    }

    private fun parseDictionarySection(
        section: String,
        existingOriginals: Set<String>
    ): List<DictPair> {
        val pairs = mutableListOf<DictPair>()
        for (line in section.split('\n')) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            if (trimmedLine.startsWith("[") || trimmedLine.startsWith("dictionary", ignoreCase = true)) {
                continue
            }

            val separator = when {
                trimmedLine.contains(" -> ") -> " -> "
                trimmedLine.contains(" ->") -> " ->"
                trimmedLine.contains("-> ") -> "-> "
                trimmedLine.contains("->") -> "->"
                trimmedLine.contains(" : ") -> " : "
                trimmedLine.contains(": ") -> ": "
                trimmedLine.contains(" :") -> " :"
                trimmedLine.contains(":") -> ":"
                else -> null
            }
            if (separator != null) {
                val parts = trimmedLine.split(separator, limit = 2)
                if (parts.size == 2) {
                    val original = parts[0].trim()
                    val translation = parts[1].trim()
                    if (original.isNotEmpty() &&
                        translation.isNotEmpty() &&
                        TranslationDictionaryPolicy.normalizeOriginal(original) !in existingOriginals &&
                        TranslationDictionaryPolicy.isValidNewOriginal(original)
                    ) {
                        pairs.add(DictPair(original, translation))
                        if (pairs.size >= 10) break
                    }
                }
            }
        }
        return pairs
    }

    private fun buildDictionaryInstruction(dictionaries: List<DictPair>): String {
        if (dictionaries.isEmpty()) return ""
        val terms = dictionaries.joinToString("\n") { "${it.original} -> ${it.translation}" }
        return """

Terminology Dictionary (use these exact translations):
$terms
"""
    }

    private fun buildRetryInstruction(retryReason: RetryReason?): String {
        return when (retryReason) {
            RetryReason.EMPTY_RESPONSE -> "\nPrevious attempt returned empty content. Return only the required formatted translation."
            RetryReason.PARSE_ERROR -> "\nPrevious attempt used an invalid format. Follow the [dictionary] and [result] format exactly."
            RetryReason.RATE_LIMIT,
            RetryReason.SERVER_ERROR,
            RetryReason.AUTH_ERROR,
            RetryReason.TIMEOUT,
            RetryReason.NETWORK_ERROR,
            RetryReason.UNKNOWN,
            RetryReason.PERMANENT_FAILURE,
            null -> ""
        }
    }

    private fun buildSystemPrompt(
        prompt: String,
        targetLanguage: String,
        dictionaryInstruction: String,
        retryInstruction: String,
        outputFormat: String
    ): String {
        return buildString {
            append(prompt)
            append("\nTarget language: ").append(getLanguageDisplayName(targetLanguage))
            if (dictionaryInstruction.isNotEmpty()) {
                append(dictionaryInstruction)
            }
            if (retryInstruction.isNotEmpty()) {
                append(retryInstruction)
            }
            append("\n").append(outputFormat)
        }
    }

    private fun getLanguageDisplayName(code: String): String {
        return TranslationConstants.targetLanguages.find { it.first == code }?.second ?: code
    }

    private fun isMostlyEnglish(text: String): Boolean {
        if (text.isEmpty()) return false
        val englishChars =
            text.count { it in 'A'..'Z' || it in 'a'..'z' || it in ".,!?;:'\"-()[]{}-" }
        return englishChars.toDouble() / text.length > 0.8
    }

    private fun isMostlyChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        val chinesePunctuation = "。，！？；：“”‘’（）【】《》"
        val chineseChars = text.count {
            it in '一'..'鿿' || it in chinesePunctuation
        }
        return chineseChars.toDouble() / text.length > 0.8
    }

    private fun filterHighEnglishParagraphs(text: String): String {
        return text.split("\n")
            .filter { paragraph -> !isMostlyEnglish(paragraph) }
            .joinToString("\n")
    }

    private fun parseRetryReason(error: Exception?): RetryReason? {
        val message = error?.message ?: return null
        return when {
            message.contains("429") -> RetryReason.RATE_LIMIT
            message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504") -> RetryReason.SERVER_ERROR
            message.contains("401") || message.contains("403") -> RetryReason.AUTH_ERROR
            message.contains("timeout", ignoreCase = true) -> RetryReason.TIMEOUT
            message.contains("HTTP") -> RetryReason.UNKNOWN
            else -> null
        }
    }
}

