package io.legado.app.model.translation

import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postForm
import io.legado.app.utils.GSON
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/**
 * SPEC-254: Google webpage/chapter translate via unofficial gtx endpoint.
 * Uses form POST (avoids GET URL length truncation), chunks long text, limited
 * concurrency + one retry — so whole pages are less likely to stay half-original.
 */
object GoogleTranslateClient {

    private const val ENDPOINT =
        "https://translate.googleapis.com/translate_a/single"
    private const val MAX_CHUNK_CHARS = 1800
    private const val MAX_CONCURRENCY = 4

    data class BatchResult(
        val texts: List<String>,
        val failed: Int,
    )

    suspend fun translateText(
        text: String,
        targetAppLang: String,
        sourceAppLang: String = "auto",
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(text)
        runCatching {
            val chunks = chunkText(text, MAX_CHUNK_CHARS)
            buildString {
                for (chunk in chunks) {
                    append(translateChunk(chunk, targetAppLang, sourceAppLang))
                }
            }
        }
    }

    suspend fun translateBatch(
        texts: List<String>,
        targetAppLang: String,
        sourceAppLang: String = "auto",
        onStatus: ((String) -> Unit)? = null,
    ): BatchResult = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext BatchResult(texts, 0)
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val failed = AtomicInteger(0)
        val out = coroutineScope {
            texts.mapIndexed { index, line ->
                async {
                    if (line.isBlank() || WebPageTranslatePacker.isNonTranslatable(line)) {
                        return@async line
                    }
                    semaphore.withPermit {
                        onStatus?.invoke("Google ${index + 1}/${texts.size}…")
                        translateText(line, targetAppLang, sourceAppLang)
                            .getOrElse {
                                delay(200)
                                translateText(line, targetAppLang, sourceAppLang)
                                    .getOrElse {
                                        failed.incrementAndGet()
                                        line
                                    }
                            }
                    }
                }
            }.awaitAll()
        }
        BatchResult(out, failed.get())
    }

    private suspend fun translateChunk(
        text: String,
        targetAppLang: String,
        sourceAppLang: String,
    ): String {
        val tl = normalizeTarget(targetAppLang)
        val sl = sourceAppLang.ifBlank { "auto" }.let {
            if (it.equals("auto", true)) "auto" else FoxletTranslateClient.toFoxletTag(it)
                .let { tag ->
                    when {
                        tag.equals("zh-Hans", true) -> "zh-CN"
                        tag.equals("zh-Hant", true) -> "zh-TW"
                        else -> tag.substringBefore('-')
                    }
                }
        }
        // Prefer POST body so long paragraphs are not clipped by URL length.
        val response = okHttpClient.newCallStrResponse {
            url(ENDPOINT)
            postForm(
                mapOf(
                    "client" to "gtx",
                    "sl" to sl,
                    "tl" to tl,
                    "dt" to "t",
                    "dj" to "1",
                    "ie" to "UTF-8",
                    "oe" to "UTF-8",
                    "q" to text,
                ),
            )
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
            )
        }
        if (!response.isSuccessful()) {
            // Fallback GET for environments that reject POST to gtx.
            val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
            val getUrl =
                "$ENDPOINT?client=gtx&sl=$sl&tl=$tl&dj=1&dt=t&ie=UTF-8&q=$encoded"
            val getResp = okHttpClient.newCallStrResponse { url(getUrl) }
            if (!getResp.isSuccessful()) {
                error("Google HTTP ${response.code()}/${getResp.code()}")
            }
            return parseSentences(getResp.body.orEmpty())
        }
        return parseSentences(response.body.orEmpty())
    }

    private fun parseSentences(body: String): String {
        val json = GSON.fromJson(body, GoogleTranslateResponse::class.java)
        val joined = json?.sentences?.mapNotNull { it.trans }?.joinToString("").orEmpty()
        if (joined.isEmpty()) error("Empty Google translation")
        return joined
    }

    private fun normalizeTarget(targetAppLang: String): String {
        val tag = FoxletTranslateClient.toFoxletTag(targetAppLang)
        return when {
            tag.equals("zh-Hans", true) -> "zh-CN"
            tag.equals("zh-Hant", true) -> "zh-TW"
            else -> tag.substringBefore('-')
        }
    }

    private fun chunkText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            var end = (i + maxChars).coerceAtMost(text.length)
            if (end < text.length) {
                val window = text.substring(i, end)
                val breakAt = listOf('\n', '。', '！', '？', '.', '!', '?', ';', '；', ' ')
                    .map { window.lastIndexOf(it) }
                    .filter { it >= maxChars / 3 }
                    .maxOrNull()
                if (breakAt != null) end = i + breakAt + 1
            }
            out += text.substring(i, end)
            i = end
        }
        return out
    }

    private data class GoogleTranslateResponse(
        @SerializedName("sentences") val sentences: List<GoogleSentence>? = null,
    )

    private data class GoogleSentence(
        @SerializedName("trans") val trans: String? = null,
    )
}
