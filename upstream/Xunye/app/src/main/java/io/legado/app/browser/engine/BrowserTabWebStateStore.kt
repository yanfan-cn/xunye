package io.legado.app.browser.engine

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import java.io.File

/**
 * SPEC-267: persist per-tab AwContents/WebView navigation [Bundle] across process death
 * so restore-on-launch can keep back/forward history.
 */
object BrowserTabWebStateStore {
    private const val TAG = "BrowserTabWebState"
    private const val DIR = "browser_tab_web_state"

    @Volatile
    private var root: File? = null

    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, DIR)
        if (!dir.isDirectory) dir.mkdirs()
        root = dir
    }

    fun save(context: Context, tabId: String, state: Bundle) {
        if (tabId.isBlank()) return
        val dir = root ?: File(context.applicationContext.filesDir, DIR).also {
            it.mkdirs()
            root = it
        }
        val file = File(dir, fileName(tabId))
        runCatching {
            val parcel = Parcel.obtain()
            try {
                state.writeToParcel(parcel, 0)
                val bytes = parcel.marshall()
                file.writeBytes(bytes)
            } finally {
                parcel.recycle()
            }
        }.onFailure {
            Log.w(TAG, "save $tabId: ${it.message}")
            runCatching { file.delete() }
        }
    }

    fun load(context: Context, tabId: String): Bundle? {
        if (tabId.isBlank()) return null
        val dir = root ?: File(context.applicationContext.filesDir, DIR)
        val file = File(dir, fileName(tabId))
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            val bytes = file.readBytes()
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                Bundle.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        }.onFailure {
            Log.w(TAG, "load $tabId: ${it.message}")
        }.getOrNull()
    }

    fun remove(context: Context, tabId: String) {
        if (tabId.isBlank()) return
        val dir = root ?: File(context.applicationContext.filesDir, DIR)
        runCatching { File(dir, fileName(tabId)).delete() }
    }

    fun clear(context: Context) {
        val dir = root ?: File(context.applicationContext.filesDir, DIR)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun fileName(tabId: String): String =
        tabId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".bin"
}
