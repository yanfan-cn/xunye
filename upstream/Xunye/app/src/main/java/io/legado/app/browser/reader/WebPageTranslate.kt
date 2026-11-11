package io.legado.app.browser.reader

/**
 * SPEC-047/048/085/249/251: webpage translate helpers.
 * Product path is in-page DOM translate ([WebPageInPlaceTranslator]); shell URL wrap is forbidden.
 */
object WebPageTranslate {

    /** SPEC-251: webpage translate UI / toolbox entry enabled (in-page only). */
    const val WEB_PAGE_TRANSLATE_UI_ENABLED = true

    fun isTranslateServiceUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("translate.google.") ||
            u.contains("translate.goog/") ||
            u.contains("googleusercontent.com/translate") ||
            u.contains("fanyi.baidu.com/transpage") ||
            u.contains("microsofttranslator.com") ||
            u.contains("bing.com/translator")
    }

    /**
     * SPEC-249/251: shell wrap removed. Always returns empty so callers cannot navigate away.
     */
    @Deprecated("SPEC-251: use WebPageInPlaceTranslator; shell wrap forbidden")
    fun pageTranslateUrl(
        pageUrl: String,
        provider: String,
        targetLanguage: String = "zh",
    ): String {
        // Keep signature for legacy call sites; never produce a wrap URL.
        return ""
    }
}
