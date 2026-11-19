package io.legado.app.receiver

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.browser.engine.BrowseStore
import io.legado.app.browser.engine.BrowserOpenBridge
import io.legado.app.ui.main.MainActivity
import splitties.init.appCtx
import java.net.URI

/**
 * System share / PROCESS_TEXT entry.
 * SPEC-267: default label「巡页·搜索」; activity-alias「巡页·收藏」adds browser bookmark.
 */
class SharedReceiverActivity : AppCompatActivity() {

    private val receivingType = "text/plain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initIntent()
        finish()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun initIntent() {
        val text = when {
            intent.action == Intent.ACTION_SEND && intent.type == receivingType ->
                intent.getStringExtra(Intent.EXTRA_TEXT)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && intent.action == Intent.ACTION_PROCESS_TEXT
                && intent.type == receivingType ->
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            intent.getStringExtra("action") == "readAloud" -> {
                MediaButtonReceiver.readAloud(appCtx, false)
                return
            }
            else -> null
        }
        if (text.isNullOrBlank()) return
        if (isFavoriteShare()) {
            disposeFavorite(text)
        } else {
            disposeSearch(text)
        }
    }

    /** SPEC-267: alias component name ends with SharedFavoriteReceiver. */
    private fun isFavoriteShare(): Boolean {
        val name = intent.component?.className.orEmpty()
        return name.endsWith("SharedFavoriteReceiver") ||
            intent.getBooleanExtra(EXTRA_FAVORITE, false)
    }

    private fun disposeFavorite(text: String) {
        val url = firstHttpUrl(text)
        if (url == null) {
            Toast.makeText(this, "无法收藏", Toast.LENGTH_SHORT).show()
            return
        }
        val title = runCatching {
            URI(url).host?.removePrefix("www.").orEmpty()
        }.getOrNull().orEmpty().ifBlank { url }
        BrowseStore(this).addBookmark(url, title)
        Toast.makeText(this, "已加入收藏", Toast.LENGTH_SHORT).show()
    }

    private fun disposeSearch(text: String) {
        val urls = text.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val result = StringBuilder()
        for (url in urls) {
            if (url.matches("http.+".toRegex()))
                result.append("\n").append(url.trim { it <= ' ' })
        }
        if (result.length > 1) {
            val firstUrl = result.toString().trim().lineSequence().firstOrNull { it.startsWith("http") }
            if (!firstUrl.isNullOrBlank()) {
                BrowserOpenBridge.offer(firstUrl)
            }
            startActivity(MainActivity.createHomeIntent(this))
        } else {
            startActivity(MainActivity.createSearchIntent(this, text))
        }
    }

    companion object {
        const val EXTRA_FAVORITE = "io.legado.app.SHARE_FAVORITE"

        /** First http(s) URL in [text], or null. */
        fun firstHttpUrl(text: String): String? {
            val matcher = Regex("""https?://[^\s<>"'，。；、）】」』]+""", RegexOption.IGNORE_CASE)
            val raw = matcher.find(text)?.value?.trim()
                ?.trimEnd('.', ',', ';', ':', ')', ']', '}', '"', '\'')
                ?: return null
            return raw.takeIf { it.startsWith("http", ignoreCase = true) }
        }
    }
}
