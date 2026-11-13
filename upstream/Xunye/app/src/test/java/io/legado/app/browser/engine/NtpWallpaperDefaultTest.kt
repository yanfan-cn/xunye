package io.legado.app.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC-266: new-user NTP wallpaper seed by theme. */
class NtpWallpaperDefaultTest {

    @Test
    fun darkTheme_defaultsToNeon() {
        assertEquals(NtpWallpaperIds.NEON, NtpWallpaperIds.defaultForTheme(isDark = true))
    }

    @Test
    fun lightTheme_defaultsToMist() {
        assertEquals(NtpWallpaperIds.MIST, NtpWallpaperIds.defaultForTheme(isDark = false))
    }
}
