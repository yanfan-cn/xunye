package io.legado.app.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC-269 */
class NtpCapsuleInkTest {

    @Test
    fun darkPresetAlwaysDarkInk() {
        assertTrue(NtpCapsuleInk.useDarkInk(fieldBgLuminance = 0.1f, darkPresetWallpaper = true))
        assertTrue(NtpCapsuleInk.useDarkInk(fieldBgLuminance = 0.9f, darkPresetWallpaper = true))
    }

    @Test
    fun lightFieldUsesDarkInk() {
        assertTrue(NtpCapsuleInk.useDarkInk(fieldBgLuminance = 0.45f, darkPresetWallpaper = false))
        assertTrue(NtpCapsuleInk.useDarkInk(fieldBgLuminance = 0.8f, darkPresetWallpaper = false))
    }

    @Test
    fun darkFieldWithoutPresetUsesLightInk() {
        assertFalse(NtpCapsuleInk.useDarkInk(fieldBgLuminance = 0.2f, darkPresetWallpaper = false))
        assertFalse(NtpCapsuleInk.useDarkInk(fieldBgLuminance = 0.449f, darkPresetWallpaper = false))
    }
}
