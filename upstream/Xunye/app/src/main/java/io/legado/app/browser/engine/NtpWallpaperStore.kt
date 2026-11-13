package io.legado.app.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID

/** SPEC-158/239: built-in homepage backgrounds and locally imported photos. */
object NtpWallpaperIds {
    const val GRAD_BLUE = "grad_blue"
    const val GRAD_GREEN = "grad_green"
    const val GRAD_PINK = "grad_pink"
    const val MORNING = "morning"
    const val MIST = "mist"
    const val LINEN = "linen"
    /** SPEC-239: dark tech / cyberpunk presets. */
    const val CYBER = "cyber"
    const val NEON = "neon"
    /** SPEC-223/266: fallback when unset; first-launch seed uses [defaultForTheme]. */
    const val DEFAULT = MIST

    val builtins = listOf(
        GRAD_BLUE, GRAD_GREEN, GRAD_PINK, MORNING, MIST, LINEN, CYBER, NEON,
    )

    fun isBuiltin(id: String) = id in builtins

    fun isGradient(id: String) = id == GRAD_BLUE || id == GRAD_GREEN || id == GRAD_PINK

    /** SPEC-239: dark image presets need light logo / shortcut labels. */
    fun isDarkPreset(id: String) = id == CYBER || id == NEON

    /** SPEC-266: new-user default — dark → neon, light → mist. */
    fun defaultForTheme(isDark: Boolean): String = if (isDark) NEON else MIST

    fun label(id: String) = when (id) {
        // SPEC-239: rename only — still vertical gradients.
        GRAD_BLUE -> "蓝天"
        GRAD_GREEN -> "绿茵"
        GRAD_PINK -> "落樱"
        MORNING -> "晨纸"
        MIST -> "薄雾"
        LINEN -> "窗纱"
        CYBER -> "赛博"
        NEON -> "霓虹"
        else -> "相册"
    }
}

class NtpWallpaperStore(context: Context) {
    private val appContext = context.applicationContext
    private val chromePrefs = BrowserChromePrefs(appContext)
    private val dir = File(appContext.filesDir, "ntp_wallpapers").apply { mkdirs() }

    var selectedId: String
        get() {
            val id = chromePrefs.ntpWallpaperId
            if (NtpWallpaperIds.isBuiltin(id)) return id
            if (fileFor(id).isFile) return id
            return NtpWallpaperIds.DEFAULT
        }
        set(value) {
            chromePrefs.ntpWallpaperId = value
        }

    /**
     * SPEC-266: only when onboarding finishes and no wallpaper id was ever written.
     * Does not override an existing preference (including upgrades that already stored an id).
     */
    fun seedDefaultIfNeeded(isDark: Boolean) {
        if (chromePrefs.hasNtpWallpaperId) return
        chromePrefs.ntpWallpaperId = NtpWallpaperIds.defaultForTheme(isDark)
    }

    fun userIds(): List<String> = chromePrefs.ntpUserWallpaperIds.filter { fileFor(it).isFile }

    fun fileFor(id: String): File = File(dir, "$id.jpg")

    /** Copy a picked image into app storage, select it, and return its id. */
    fun import(uri: Uri): String? {
        val bitmap = decodeScaled(uri) ?: return null
        val id = UUID.randomUUID().toString()
        return try {
            val outFile = fileFor(id)
            val wrote = outFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (!wrote) {
                outFile.delete()
                return null
            }
            chromePrefs.ntpUserWallpaperIds = chromePrefs.ntpUserWallpaperIds + id
            chromePrefs.ntpWallpaperId = id
            id
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(id: String) {
        if (NtpWallpaperIds.isBuiltin(id)) return
        fileFor(id).delete()
        chromePrefs.ntpUserWallpaperIds = chromePrefs.ntpUserWallpaperIds.filter { it != id }
        if (chromePrefs.ntpWallpaperId == id) {
            chromePrefs.ntpWallpaperId = NtpWallpaperIds.DEFAULT
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / sample > 1920) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { input ->
            when (
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true,
        )
        if (rotated != decoded) decoded.recycle()
        return rotated
    }
}
