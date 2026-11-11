package io.legado.app.ui.theme

import android.os.Build

/**
 * SPEC-056/061: global chrome surface style for browser / floating bars / reader menus.
 * Stored in ThemeSettings.chromeSurfaceStyle.
 *
 * SPEC-061: Translucent is no longer offered.
 * SPEC-258: default is always Filled (liquid glass opt-in), including API 35+.
 */
enum class ChromeSurfaceStyle(val storageValue: String) {
    Translucent("translucent"),
    Filled("filled"),
    LiquidGlass("liquid");

    companion object {
        fun fromStorage(value: String?): ChromeSurfaceStyle? =
            entries.firstOrNull { it.storageValue == value }
    }
}

object ChromeSurfaceStylePolicy {
    @Suppress("UNUSED_PARAMETER")
    fun defaultStyle(sdk: Int = Build.VERSION.SDK_INT): ChromeSurfaceStyle =
        ChromeSurfaceStyle.Filled

    fun availableStyles(sdk: Int = Build.VERSION.SDK_INT): List<ChromeSurfaceStyle> =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            listOf(ChromeSurfaceStyle.Filled, ChromeSurfaceStyle.LiquidGlass)
        } else {
            listOf(ChromeSurfaceStyle.Filled)
        }

    fun resolve(stored: String?, sdk: Int = Build.VERSION.SDK_INT): ChromeSurfaceStyle {
        val requested = ChromeSurfaceStyle.fromStorage(stored?.takeIf { it.isNotBlank() })
            ?: defaultStyle(sdk)
        val available = availableStyles(sdk)
        return if (requested in available) requested else defaultStyle(sdk)
    }

    /** SPEC-224: 不再受 GlassMotionGate 抑制；开启液态玻璃即保持激活。 */
    fun isLiquidGlassActive(stored: String?, sdk: Int = Build.VERSION.SDK_INT): Boolean =
        resolve(stored, sdk) == ChromeSurfaceStyle.LiquidGlass &&
            sdk >= Build.VERSION_CODES.TIRAMISU

    fun isFilledActive(stored: String?, sdk: Int = Build.VERSION.SDK_INT): Boolean =
        resolve(stored, sdk) == ChromeSurfaceStyle.Filled

    /** SPEC-061: top-bar button style is driven by chrome surface (no separate toggle). */
    fun effectiveTopBarButtonStyle(stored: String?, sdk: Int = Build.VERSION.SDK_INT): String =
        if (isLiquidGlassActive(stored, sdk)) "liquid" else "tonal"

    /**
     * SPEC-061: filled chrome forces opaque reader menu surfaces
     * (overrides ephemeral novel alpha=0).
     */
    fun effectiveReadMenuSurfaceAlpha(
        stored: String?,
        configuredAlpha: Int,
        sdk: Int = Build.VERSION.SDK_INT,
    ): Int =
        if (isFilledActive(stored, sdk)) 100 else configuredAlpha.coerceIn(0, 100)
}
