package io.legado.app.domain.model.settings

import io.legado.app.domain.model.TranslationConstants

data class TranslationSettings(
    val provider: String = TranslationConstants.PROVIDER_BERGAMOT,
    val targetLanguage: String = "zh",
    val maxCharsPerChunk: Int = 10000,
    val concurrentChunks: Int = 1,
    val retryCount: Int = 2,
    /** SPEC-251: auto in-page translate for foreign web pages; default off. */
    val autoTranslateWebpage: Boolean = false,
    /** SPEC-251: auto full-text translate in 净读; default off. */
    val autoTranslateJingdu: Boolean = false,
)
