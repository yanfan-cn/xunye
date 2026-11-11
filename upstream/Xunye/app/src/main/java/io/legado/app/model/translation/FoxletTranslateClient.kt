package io.legado.app.model.translation

import io.github.yinvoker.foxlet.Foxlet
import io.github.yinvoker.foxlet.FoxletConfig
import io.github.yinvoker.foxlet.LanguagePair
import io.github.yinvoker.foxlet.ModelSource
import io.github.yinvoker.foxlet.ModelsConfig
import io.github.yinvoker.foxlet.TextFormat
import io.github.yinvoker.foxlet.Threading
import io.github.yinvoker.foxlet.TranslationConfig
import io.github.yinvoker.foxlet.UnsupportedLanguagePairException
import io.github.yinvoker.foxlet.Workload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx

/**
 * SPEC-251/254: process-wide Foxlet (Bergamot) client.
 * Models download on first use; Bergamot path keeps text on-device.
 * SPEC-254: richer TranslationConfig, Plain format, batch pivot, stronger lang guess.
 */
object FoxletTranslateClient {

    private val mutex = Mutex()
    @Volatile
    private var client: Foxlet? = null

    suspend fun get(): Foxlet = mutex.withLock {
        client ?: Foxlet.create(
            appCtx,
            FoxletConfig(
                models = ModelsConfig(source = ModelSource.Mozilla),
                translation = TranslationConfig(
                    threading = Threading.Auto(Workload.BATCH),
                    // Larger mini-batches + prefix table → better sentence splits.
                    miniBatchWords = 1024,
                    cacheSize = 2048,
                    nonbreakingPrefixes = true,
                ),
            ),
        ).also { client = it }
    }

    suspend fun translateText(
        text: String,
        sourceAppLang: String,
        targetAppLang: String,
        onStatus: ((String) -> Unit)? = null,
    ): String {
        if (text.isBlank()) return text
        return translateBatch(listOf(text), sourceAppLang, targetAppLang, onStatus).first()
    }

    suspend fun translateBatch(
        texts: List<String>,
        sourceAppLang: String,
        targetAppLang: String,
        onStatus: ((String) -> Unit)? = null,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val source = toFoxletTag(sourceAppLang)
        val target = toFoxletTag(targetAppLang)
        if (source.equals(target, ignoreCase = true)) return texts
        val foxlet = get()
        return try {
            onStatus?.invoke("准备模型 $source→$target…")
            val model = foxlet.models.prepare(LanguagePair(source, target))
            foxlet.translator.translate(texts, model, TextFormat.Plain)
        } catch (e: UnsupportedLanguagePairException) {
            if (source == "en" || target == "en") throw e
            onStatus?.invoke("准备中转 $source→en→$target…")
            val first = foxlet.models.prepare(LanguagePair(source, "en"))
            val second = foxlet.models.prepare(LanguagePair("en", target))
            // SPEC-254: batch pivot keeps list alignment and is faster than per-line.
            foxlet.translator.translatePivot(texts, first, second, TextFormat.Plain)
        }
    }

    fun toFoxletTag(appLang: String): String {
        val raw = appLang.trim().lowercase().substringBefore(',').substringBefore(';')
        return when {
            raw.isBlank() -> "en"
            raw == "zh" || raw.startsWith("zh-cn") || raw.startsWith("zh-hans") -> "zh-Hans"
            raw.startsWith("zh-tw") || raw.startsWith("zh-hk") ||
                raw.startsWith("zh-hant") || raw.startsWith("zh-mo") -> "zh-Hant"
            raw.startsWith("nb") || raw.startsWith("nn") || raw.startsWith("no") -> "nb"
            else -> raw.substringBefore('-').ifBlank { "en" }
        }
    }

    /**
     * SPEC-254: prefer document lang when credible; else script heuristics on a larger sample.
     * Avoid defaulting mixed CJK pages to English.
     */
    fun guessSourceLang(htmlLang: String, sample: String): String {
        val l = htmlLang.lowercase().substringBefore(',').substringBefore(';').trim()
        if (l.isNotBlank()) {
            when {
                l.startsWith("zh-tw") || l.startsWith("zh-hk") ||
                    l.startsWith("zh-hant") || l.startsWith("zh-mo") -> return "zh-Hant"
                l.startsWith("zh") -> return "zh-Hans"
                l.startsWith("ja") -> return "ja"
                l.startsWith("ko") -> return "ko"
                l.startsWith("ru") -> return "ru"
                l.startsWith("ar") -> return "ar"
                l.startsWith("fr") -> return "fr"
                l.startsWith("de") -> return "de"
                l.startsWith("es") -> return "es"
                l.startsWith("pt") -> return "pt"
                l.startsWith("it") -> return "it"
                l.startsWith("vi") -> return "vi"
                l.startsWith("th") -> return "th"
                l.startsWith("id") || l.startsWith("ms") -> return "id"
                l.startsWith("en") -> {
                    // html lang=en is often wrong on localized sites — verify sample.
                    val script = detectByScript(sample)
                    if (script != null && script != "en") return script
                    return "en"
                }
                else -> {
                    val fromAttr = toFoxletTag(htmlLang)
                    if (fromAttr != "en") return fromAttr
                }
            }
        }
        return detectByScript(sample) ?: "en"
    }

    private fun detectByScript(sample: String): String? {
        if (sample.isBlank()) return null
        if (sample.any { it in '\u3040'..'\u30ff' }) return "ja"
        if (sample.any { it in '\uac00'..'\ud7af' }) return "ko"
        if (sample.count { it in '\u0400'..'\u04ff' } >= 8) return "ru"
        if (sample.count { it in '\u0600'..'\u06ff' } >= 8) return "ar"
        if (sample.count { it in '\u0e00'..'\u0e7f' } >= 8) return "th"
        val letters = sample.count { it.isLetter() }.coerceAtLeast(1)
        val han = sample.count { it in '\u4e00'..'\u9fff' }
        if (han >= 12 && han.toFloat() / letters >= 0.15f) {
            // Cheap traditional hint: common TW/HK forms.
            val tradHits = sample.count { it in "國語門東車馬風魚鳥龍龜會學點廣體後發長對" }
            return if (tradHits >= 2) "zh-Hant" else "zh-Hans"
        }
        val latin = sample.count { it in 'A'..'Z' || it in 'a'..'z' }
        if (latin >= 24 && latin.toFloat() / letters >= 0.55f) return "en"
        return null
    }
}
