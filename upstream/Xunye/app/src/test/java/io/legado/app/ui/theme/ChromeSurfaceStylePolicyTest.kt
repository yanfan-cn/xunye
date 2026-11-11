package io.legado.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeSurfaceStylePolicyTest {

    @Test
    fun defaultIsFilledOnAllApis() {
        // SPEC-258: liquid glass is opt-in; never the install default.
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.defaultStyle(sdk = 35),
        )
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.defaultStyle(sdk = 34),
        )
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.defaultStyle(sdk = 26),
        )
    }

    @Test
    fun translucentNeverAvailable() {
        assertFalse(
            ChromeSurfaceStylePolicy.availableStyles(sdk = 35)
                .contains(ChromeSurfaceStyle.Translucent),
        )
        assertFalse(
            ChromeSurfaceStylePolicy.availableStyles(sdk = 34)
                .contains(ChromeSurfaceStyle.Translucent),
        )
        assertFalse(
            ChromeSurfaceStylePolicy.availableStyles(sdk = 32)
                .contains(ChromeSurfaceStyle.Translucent),
        )
    }

    @Test
    fun liquidUnavailableBelowApi33() {
        val available = ChromeSurfaceStylePolicy.availableStyles(sdk = 32)
        assertFalse(available.contains(ChromeSurfaceStyle.LiquidGlass))
        assertEquals(
            listOf(ChromeSurfaceStyle.Filled),
            available,
        )
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.resolve("liquid", sdk = 32),
        )
    }

    @Test
    fun legacyTranslucentMapsToFilledDefault() {
        // SPEC-258: unavailable translucent remaps to defaultStyle (= Filled).
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.resolve("translucent", sdk = 35),
        )
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.resolve("translucent", sdk = 34),
        )
    }

    @Test
    fun liquidActiveOnlyWhenSelectedAndApi33Plus() {
        assertTrue(ChromeSurfaceStylePolicy.isLiquidGlassActive("liquid", sdk = 33))
        assertTrue(ChromeSurfaceStylePolicy.isLiquidGlassActive("liquid", sdk = 35))
        assertFalse(ChromeSurfaceStylePolicy.isLiquidGlassActive("liquid", sdk = 32))
        // SPEC-258: legacy translucent remaps to Filled default → not liquid.
        assertFalse(ChromeSurfaceStylePolicy.isLiquidGlassActive("translucent", sdk = 35))
        assertFalse(ChromeSurfaceStylePolicy.isLiquidGlassActive("translucent", sdk = 34))
        assertFalse(ChromeSurfaceStylePolicy.isLiquidGlassActive("filled", sdk = 35))
    }

    @Test
    fun blankUsesFilledDefault() {
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.resolve("", sdk = 35),
        )
        assertEquals(
            ChromeSurfaceStyle.Filled,
            ChromeSurfaceStylePolicy.resolve(null, sdk = 34),
        )
    }

    @Test
    fun topBarButtonStyleFollowsSurface() {
        assertEquals(
            "liquid",
            ChromeSurfaceStylePolicy.effectiveTopBarButtonStyle("liquid", sdk = 35),
        )
        assertEquals(
            "tonal",
            ChromeSurfaceStylePolicy.effectiveTopBarButtonStyle("filled", sdk = 35),
        )
        assertEquals(
            "tonal",
            ChromeSurfaceStylePolicy.effectiveTopBarButtonStyle("liquid", sdk = 32),
        )
        assertEquals(
            "tonal",
            ChromeSurfaceStylePolicy.effectiveTopBarButtonStyle("translucent", sdk = 34),
        )
    }

    @Test
    fun filledForcesOpaqueReadMenuAlpha() {
        assertEquals(
            100,
            ChromeSurfaceStylePolicy.effectiveReadMenuSurfaceAlpha("filled", 0, sdk = 34),
        )
        assertEquals(
            0,
            ChromeSurfaceStylePolicy.effectiveReadMenuSurfaceAlpha("liquid", 0, sdk = 35),
        )
        assertTrue(ChromeSurfaceStylePolicy.isFilledActive("filled", sdk = 34))
        assertFalse(ChromeSurfaceStylePolicy.isFilledActive("liquid", sdk = 35))
    }

    @Test
    fun storedLiquidIsPreservedOnApi35() {
        // SPEC-258: no migration of users who already chose liquid.
        assertEquals(
            ChromeSurfaceStyle.LiquidGlass,
            ChromeSurfaceStylePolicy.resolve("liquid", sdk = 35),
        )
    }
}
