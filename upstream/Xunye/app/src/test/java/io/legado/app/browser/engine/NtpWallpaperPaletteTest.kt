package io.legado.app.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC-270：预置壁纸平均色 + 遮罩合成。 */
class NtpWallpaperPaletteTest {

    @Test
    fun `未知与相册壁纸取保守深值`() {
        val unknown = NtpWallpaperPalette.averageArgb("user-uuid-photo")
        assertTrue(NtpCapsuleInk.luminance(unknown) < 0.05f)
    }

    @Test
    fun `深色预置比浅色预置更暗`() {
        val cyber = NtpCapsuleInk.luminance(NtpWallpaperPalette.averageArgb(NtpWallpaperIds.CYBER))
        val neon = NtpCapsuleInk.luminance(NtpWallpaperPalette.averageArgb(NtpWallpaperIds.NEON))
        val mist = NtpCapsuleInk.luminance(NtpWallpaperPalette.averageArgb(NtpWallpaperIds.MIST))
        assertTrue(cyber < mist)
        assertTrue(neon < mist)
        assertTrue(neon < cyber)
    }

    @Test
    fun `遮罩越大背景越暗`() {
        val base = NtpWallpaperIds.MIST
        val none = NtpCapsuleInk.luminance(
            NtpWallpaperPalette.backgroundArgb(base, 0f, 0xFF000000.toInt()),
        )
        val heavy = NtpCapsuleInk.luminance(
            NtpWallpaperPalette.backgroundArgb(base, 0.4f, 0xFF000000.toInt()),
        )
        assertTrue(heavy < none)
    }

    @Test
    fun `无壁纸时直接用页面底色`() {
        val pageBg = 0xFF101418.toInt()
        assertEquals(
            pageBg,
            NtpWallpaperPalette.backgroundArgb(
                wallpaperId = NtpWallpaperIds.CYBER,
                scrimAlpha = 0.3f,
                pageBg = pageBg,
                noWallpaper = true,
            ),
        )
    }
}
