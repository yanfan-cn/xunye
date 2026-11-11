package io.legado.app.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tab previews: memory + disk (SPEC-257).
 * Disk survives process death so restore-on-launch tabs can show thumbnails again.
 */
object BrowserTabThumbnailStore {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun put(tabId: String, bitmap: Bitmap) {
        bitmaps[tabId] = bitmap
        _revision.update { it + 1 }
        val ctx = appContext ?: return
        ioScope.launch {
            runCatching {
                val dir = dir(ctx)
                dir.mkdirs()
                val tmp = File(dir, "$tabId.webp.tmp")
                val out = File(dir, "$tabId.webp")
                val format = if (Build.VERSION.SDK_INT >= 30) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                tmp.outputStream().use { os ->
                    bitmap.compress(format, 70, os)
                }
                if (!tmp.renameTo(out)) {
                    tmp.copyTo(out, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    fun get(tabId: String): Bitmap? {
        bitmaps[tabId]?.let { return it }
        val ctx = appContext ?: return null
        val file = File(dir(ctx), "$tabId.webp")
        if (!file.isFile) return null
        val bmp = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull() ?: return null
        bitmaps[tabId] = bmp
        return bmp
    }

    /** SPEC-257: warm memory cache from disk for restored tab ids (async). */
    fun preloadFromDisk(tabIds: Collection<String>) {
        val ctx = appContext ?: return
        if (tabIds.isEmpty()) return
        ioScope.launch {
            var any = false
            for (id in tabIds) {
                if (bitmaps.containsKey(id)) continue
                val file = File(dir(ctx), "$id.webp")
                if (!file.isFile) continue
                val bmp = runCatching {
                    BitmapFactory.decodeFile(file.absolutePath)
                }.getOrNull() ?: continue
                bitmaps[id] = bmp
                any = true
            }
            if (any) {
                withContext(Dispatchers.Main.immediate) {
                    _revision.update { it + 1 }
                }
            }
        }
    }

    fun remove(tabId: String) {
        bitmaps.remove(tabId)
        _revision.update { it + 1 }
        val ctx = appContext ?: return
        ioScope.launch {
            runCatching { File(dir(ctx), "$tabId.webp").delete() }
        }
    }

    fun clear() {
        bitmaps.clear()
        _revision.update { it + 1 }
        val ctx = appContext ?: return
        ioScope.launch {
            runCatching {
                dir(ctx).listFiles()?.forEach { it.delete() }
            }
        }
    }

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "tab_thumbs")
}
