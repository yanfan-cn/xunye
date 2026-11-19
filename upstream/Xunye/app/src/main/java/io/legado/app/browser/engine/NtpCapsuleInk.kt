package io.legado.app.browser.engine

/**
 * SPEC-269: NTP search-capsule ink follows the **capsule surface**, not wallpaper.
 * Cyber/neon presets use frosted light glass — forcing white ink (SPEC-265) made
 * placeholder / AI row unreadable.
 */
object NtpCapsuleInk {
    const val LUMINANCE_THRESHOLD = 0.45f

    /**
     * @param fieldBgLuminance Compose [androidx.compose.ui.graphics.Color.luminance] of capsule fill
     * @param darkPresetWallpaper true for 赛博/霓虹 built-in wallpapers
     */
    fun useDarkInk(fieldBgLuminance: Float, darkPresetWallpaper: Boolean): Boolean =
        darkPresetWallpaper || fieldBgLuminance >= LUMINANCE_THRESHOLD
}
