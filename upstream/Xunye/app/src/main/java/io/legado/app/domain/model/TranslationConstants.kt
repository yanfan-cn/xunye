package io.legado.app.domain.model

object TranslationConstants {

    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_APP_AI = "app_ai"
    const val PROVIDER_GOOGLE = "google"
    /** SPEC-251: Mozilla Bergamot via Foxlet (default). */
    const val PROVIDER_BERGAMOT = "bergamot"
    /** @deprecated SPEC-249/251 removed from product UI; migrate to [PROVIDER_BERGAMOT]. */
    const val PROVIDER_BAIDU = "baidu"
    /** @deprecated SPEC-249/251 removed from product UI; migrate to [PROVIDER_BERGAMOT]. */
    const val PROVIDER_MLKIT = "mlkit"
    /** Reserved; not exposed in UI. */
    const val PROVIDER_MICROSOFT = "microsoft"

    const val MIN_TEMPERATURE = 0f
    const val MAX_TEMPERATURE = 2f
    const val DEFAULT_TEMPERATURE = 0.7f

    val providerDisplayNames = listOf(
        "Bergamot（本机）",
        "应用 AI 接口",
        "Google Translate",
    )
    val providerValues = listOf(
        PROVIDER_BERGAMOT,
        PROVIDER_APP_AI,
        PROVIDER_GOOGLE,
    )

    val targetLanguages = listOf(
        "zh" to "简体中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "ru" to "Русский",
        "ar" to "العربية"
    )

    const val DEFAULT_PROMPT =
        """You are a professional literary translator, please translate according to the following requirements:

1. Keep the original paragraph count and order unchanged
2. Maintain the literary style and tone of the original text
3. Do not summarize, condense, or omit any content
4. Only output the translation result, do not add comments or explanations
5. Keep name consistency across abbreviations/nicknames (e.g., Alexander → Alex → same name). Add nickname mapping to dictionary.

"""

    const val OUTPUT_FORMAT = """Output is divided into two parts:

**New** proper nouns, place names that need to be recorded for context, and the translation result.

Only select the most common and important terms (max 10) to include in the dictionary.

Output format as follows, IMPORTANT, **dictionary** part must begin with english word **[dictionary]**, MUST NOT start with any other words. **result** part must begin with english word **[result]**,  MUST NOT start with any other words:
<example>
[dictionary]
Jack -> 杰克
Harry Port -> 哈利波特

[result]
...
</example>
    """
}
