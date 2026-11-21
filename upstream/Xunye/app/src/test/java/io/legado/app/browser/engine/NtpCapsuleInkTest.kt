package io.legado.app.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC-270：胶囊墨色必须由「胶囊实际可见底色」决定，且任何组合下对比 ≥ 4.5:1。
 *
 * 回归背景（原 SPEC-269 缺陷）：`useDarkInk` 里 `darkPresetWallpaper` 是绝对分支，
 * 深色主题 + 赛博/霓虹 = 黑字压近黑胶囊，真机全黑不可读。
 */
class NtpCapsuleInkTest {

    private val surfaceContainerLowDark = 0xFF181C1F.toInt()
    private val surfaceContainerLowLight = 0xFFF2F0EC.toInt()

    /** 断言决策后的实际渲染对比度达标（含垫色与次要文字透明度调整）。 */
    private fun assertReadable(
        capsuleFill: Int,
        fillAlpha: Float,
        background: Int,
    ): NtpCapsuleInk.Decision {
        val decision = NtpCapsuleInk.decide(capsuleFill, fillAlpha, background)
        val visible = NtpCapsuleInk.composite(background, capsuleFill, fillAlpha)
        val surface = if (decision.scrimAlpha > 0f) {
            NtpCapsuleInk.composite(visible, decision.scrimArgb, decision.scrimAlpha)
        } else {
            visible
        }
        val primary = NtpCapsuleInk.contrastRatio(decision.inkArgb, surface)
        val secondaryColor =
            NtpCapsuleInk.composite(surface, decision.inkArgb, decision.effectiveSecondaryAlpha)
        val secondary = NtpCapsuleInk.contrastRatio(secondaryColor, surface)
        assertTrue("primary=$primary decision=$decision", primary >= NtpCapsuleInk.MIN_CONTRAST - 0.01f)
        assertTrue("secondary=$secondary decision=$decision", secondary >= NtpCapsuleInk.MIN_CONTRAST - 0.01f)
        return decision
    }

    @Test
    fun `深色主题 加 赛博壁纸 用浅墨`() {
        // 真机复现组合：填充表面（alpha=1）、深色 surfaceContainerLow、赛博壁纸。
        val decision = assertReadable(
            capsuleFill = surfaceContainerLowDark,
            fillAlpha = 1f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.CYBER),
        )
        assertFalse("深底必须浅墨", decision.useDarkInk)
        assertEquals(NtpCapsuleInk.LIGHT_INK, decision.inkArgb)
    }

    @Test
    fun `深色主题 加 霓虹壁纸 液态玻璃 用浅墨`() {
        val decision = assertReadable(
            capsuleFill = surfaceContainerLowDark,
            fillAlpha = 0.35f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.NEON),
        )
        assertFalse(decision.useDarkInk)
    }

    @Test
    fun `浅色主题 加 赛博壁纸 用深墨`() {
        // SPEC-269 的原始目标不能被这次修复推翻。
        val decision = assertReadable(
            capsuleFill = surfaceContainerLowLight,
            fillAlpha = 1f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.CYBER),
        )
        assertTrue("浅底必须深墨", decision.useDarkInk)
        assertEquals(NtpCapsuleInk.DARK_INK, decision.inkArgb)
    }

    @Test
    fun `深色主题 加 浅色壁纸 用深墨而不是白压白`() {
        val decision = assertReadable(
            capsuleFill = surfaceContainerLowDark,
            fillAlpha = 0.35f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.MIST),
        )
        assertTrue(decision.useDarkInk)
    }

    @Test
    fun `全部预置壁纸乘两种主题乘三种表面样式 均可读`() {
        val presets = listOf(
            NtpWallpaperIds.MIST,
            NtpWallpaperIds.MORNING,
            NtpWallpaperIds.LINEN,
            NtpWallpaperIds.GRAD_BLUE,
            NtpWallpaperIds.GRAD_GREEN,
            NtpWallpaperIds.GRAD_PINK,
            NtpWallpaperIds.CYBER,
            NtpWallpaperIds.NEON,
        )
        for (id in presets) {
            for (fill in listOf(surfaceContainerLowDark, surfaceContainerLowLight)) {
                for (alpha in listOf(1f, 0.4f, 0.35f)) {
                    assertReadable(fill, alpha, NtpWallpaperPalette.averageArgb(id))
                }
            }
        }
    }

    @Test
    fun `深色预置壁纸不再强制深墨`() {
        // 同一壁纸，两种主题必须给出不同墨色——证明决策跟底色，不跟壁纸 Id。
        val dark = NtpCapsuleInk.decide(
            capsuleFill = surfaceContainerLowDark,
            fillAlpha = 1f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.CYBER),
        )
        val light = NtpCapsuleInk.decide(
            capsuleFill = surfaceContainerLowLight,
            fillAlpha = 1f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.CYBER),
        )
        assertFalse(dark.useDarkInk)
        assertTrue(light.useDarkInk)
    }

    @Test
    fun `中亮度底色触发垫色兜底`() {
        // 0x7B（123）落在「深墨与浅墨都不足 4.5:1」的死区，必须靠垫色达标。
        val grey = 0xFF7B7B7B.toInt()
        val decision = assertReadable(capsuleFill = grey, fillAlpha = 1f, background = grey)
        assertTrue("应触发垫色", decision.scrimAlpha > 0f)
        assertTrue(decision.scrimAlpha <= NtpCapsuleInk.MAX_SCRIM)
    }

    @Test
    fun `深色胶囊无需垫色`() {
        val decision = NtpCapsuleInk.decide(
            capsuleFill = surfaceContainerLowDark,
            fillAlpha = 1f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.CYBER),
        )
        assertEquals(0f, decision.scrimAlpha, 0.0001f)
    }

    @Test
    fun `次要文字透明度不低于设计基线且能补偿`() {
        val dark = NtpCapsuleInk.Decision(useDarkInk = true)
        val light = NtpCapsuleInk.Decision(useDarkInk = false)
        assertEquals(NtpCapsuleInk.SECONDARY_ALPHA_DARK_INK, dark.effectiveSecondaryAlpha, 0.0001f)
        assertEquals(NtpCapsuleInk.SECONDARY_ALPHA_LIGHT_INK, light.effectiveSecondaryAlpha, 0.0001f)
        // 实际决策里，次要文字在浅底上会自动加深，不会低于基线。
        val decided = NtpCapsuleInk.decide(
            capsuleFill = surfaceContainerLowLight,
            fillAlpha = 1f,
            background = NtpWallpaperPalette.averageArgb(NtpWallpaperIds.MIST),
        )
        assertTrue(decided.effectiveSecondaryAlpha >= NtpCapsuleInk.SECONDARY_ALPHA_DARK_INK)
    }

    @Test
    fun `composite 逐通道混合`() {
        assertEquals(
            0xFF808080.toInt(),
            NtpCapsuleInk.composite(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f),
        )
        assertEquals(
            0xFF000000.toInt(),
            NtpCapsuleInk.composite(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f),
        )
        assertEquals(
            0xFFFFFFFF.toInt(),
            NtpCapsuleInk.composite(0xFF123456.toInt(), 0xFFFFFFFF.toInt(), 1f),
        )
    }

    @Test
    fun `对比度公式符合 WCAG 端点`() {
        assertEquals(21f, NtpCapsuleInk.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01f)
        assertEquals(1f, NtpCapsuleInk.contrastRatio(0xFF3366CC.toInt(), 0xFF3366CC.toInt()), 0.01f)
    }
}
