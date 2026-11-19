package io.legado.app.browser.engine

import android.content.Intent
import android.net.Uri
import java.net.URLDecoder

/** Decide whether a navigation URL tries to leave the in-app browser for another app. */
object BrowserUrlPolicy {
    fun isHttpOrHttps(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    /**
     * True when the URL should be blocked as an external-app launch.
     * Same-app deep links (`legado` / `yuedu`) are not treated as external apps.
     */
    fun isExternalAppLaunch(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return false
        return when (scheme) {
            "http", "https", "about", "data", "blob", "javascript" -> false
            "legado", "yuedu" -> false
            else -> true
        }
    }

    fun isSameAppImport(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase()
        return scheme == "legado" || scheme == "yuedu"
    }

    /**
     * SPEC-268: Chrome-style `intent://` browser fallback (http/https only).
     * Prefers [Intent.parseUri] extras; falls back to scanning `S.browser_fallback_url=`.
     */
    fun intentBrowserFallbackUrl(url: String): String? {
        if (!url.startsWith("intent:", ignoreCase = true)) return null
        val fromIntent = runCatching {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.getStringExtra(EXTRA_BROWSER_FALLBACK_URL)
                ?: intent.getStringExtra("browser_fallback_url")
        }.getOrNull()
        val raw = fromIntent?.takeIf { it.isNotBlank() } ?: parseFallbackFromIntentString(url)
        val decoded = raw?.let { decodeFallback(it) } ?: return null
        return decoded.takeIf { isHttpOrHttps(it) }
    }

    private fun parseFallbackFromIntentString(url: String): String? {
        val marker = "S.browser_fallback_url="
        val idx = url.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val start = idx + marker.length
        val endMarkers = charArrayOf(';', '#')
        var end = url.length
        for (c in endMarkers) {
            val p = url.indexOf(c, start)
            if (p >= 0 && p < end) end = p
        }
        return url.substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun decodeFallback(raw: String): String {
        var cur = raw.trim()
        // Intent strings often percent-encode the fallback once or twice.
        repeat(2) {
            val next = runCatching { URLDecoder.decode(cur, Charsets.UTF_8.name()) }.getOrDefault(cur)
            if (next == cur) return cur
            cur = next
            if (isHttpOrHttps(cur)) return cur
        }
        return cur
    }

    /** Android / Chrome Intent extra key for browser fallback. */
    const val EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url"
}
