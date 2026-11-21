package io.legado.app.browser.engine

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * SPEC-270: 主页搜索胶囊内墨色由**胶囊实际可见底色**决定，不再由壁纸预设 Id 决定。
 *
 * 背景：SPEC-265 见「深色预置壁纸 + 浅玻璃」白字不可读 → 强制白字；SPEC-269 反过来
 * 「深色预置壁纸 ⇒ 强制深字」，于是在**深色主题 + 赛博/霓虹**下形成黑字压近黑胶囊
 * （对比 ≈ 1:1，真机全黑不可见）。
 *
 * 现在的唯一规则：
 * 1. 先按「填色 × 玻璃透明度 × 背景」合成胶囊真实可见底色；
 * 2. 深墨 / 浅墨取对比度更高者；
 * 3. 若最高对比仍 < [MIN_CONTRAST]，给胶囊自动补一层极薄垫色（深墨补白、浅墨补黑），
 *    把底色推到可读区间 —— 任何主题/壁纸/表面样式组合都不会再出现不可读文字。
 */
object NtpCapsuleInk {

    /** WCAG AA 正文对比度下限。 */
    const val MIN_CONTRAST = 4.5f

    /** 垫色上限，避免把胶囊洗成纯黑/纯白。 */
    const val MAX_SCRIM = 0.60f

    /** SPEC-203/239 深墨（预置浅底上的 logo/书签同一支颜色）。 */
    const val DARK_INK = 0xFF1C1B19.toInt()
    const val LIGHT_INK = 0xFFFFFFFF.toInt()
    private const val BLACK_SCRIM = 0xFF000000.toInt()
    private const val WHITE_SCRIM = 0xFFFFFFFF.toInt()

    const val SECONDARY_ALPHA_DARK_INK = 0.62f
    const val SECONDARY_ALPHA_LIGHT_INK = 0.88f

    /**
     * @param useDarkInk true = 深墨（`DARK_INK`）
     * @param scrimAlpha 胶囊底色的补充垫色透明度，0 = 不需要
     * @param scrimWhite 垫色方向：true = 白色（深墨时用），false = 黑色（浅墨时用）
     * @param secondaryAlpha 次要文字透明度：按可达 4.5:1 的下限自适应，不低于设计基线
     */
    data class Decision(
        val useDarkInk: Boolean,
        val scrimAlpha: Float = 0f,
        val scrimWhite: Boolean = false,
        val secondaryAlpha: Float? = null,
    ) {
        val inkArgb: Int get() = if (useDarkInk) DARK_INK else LIGHT_INK
        val effectiveSecondaryAlpha: Float
            get() = secondaryAlpha
                ?: if (useDarkInk) SECONDARY_ALPHA_DARK_INK else SECONDARY_ALPHA_LIGHT_INK
        /** 兼容旧调用点（SPEC-269 命名）。 */
        val secondaryAlphaValue: Float get() = effectiveSecondaryAlpha
        val scrimArgb: Int get() = if (scrimWhite) WHITE_SCRIM else BLACK_SCRIM
    }

    /**
     * @param capsuleFill 胶囊填色（ARGB，通常为 `surfaceContainerLow`）
     * @param fillAlpha 填色透明度：填充表面 1.0，液态玻璃取 [io.legado.app.ui.browser.ntpGlassOverlayAlpha]
     * @param background 胶囊下方的可见背景（壁纸/页面底色，ARGB）
     *
     * 判定顺序：
     * 1. 主文字按底色亮度选深/浅墨；
     * 2. 次要文字（占位、AI 助手等）把 alpha 提到刚好满足 4.5:1 的下限（不低于设计值）；
     * 3. 仅当**满不透明的主文字**仍不足 4.5:1 时，才给胶囊补垫色。
     */
    fun decide(capsuleFill: Int, fillAlpha: Float, background: Int): Decision {
        val visible = composite(background, capsuleFill, fillAlpha.coerceIn(0f, 1f))
        val darkContrast = contrastRatio(DARK_INK, visible)
        val lightContrast = contrastRatio(LIGHT_INK, visible)
        val useDark = darkContrast >= lightContrast
        val ink = if (useDark) DARK_INK else LIGHT_INK
        val designAlpha = if (useDark) SECONDARY_ALPHA_DARK_INK else SECONDARY_ALPHA_LIGHT_INK
        val scrim = if (useDark) WHITE_SCRIM else BLACK_SCRIM

        fun secondaryAlphaFor(surface: Int): Float {
            var alpha = designAlpha
            while (alpha < 1f) {
                if (contrastRatio(composite(surface, ink, alpha), surface) >= MIN_CONTRAST) break
                alpha = (alpha + 0.02f).coerceAtMost(1f)
            }
            return alpha
        }

        fun primaryPasses(surface: Int) = contrastRatio(ink, surface) >= MIN_CONTRAST

        if (primaryPasses(visible)) {
            return Decision(
                useDarkInk = useDark,
                secondaryAlpha = secondaryAlphaFor(visible),
            )
        }

        val steps = (MAX_SCRIM * 100f).toInt()
        for (i in 1..steps) {
            val alpha = i / 100f
            val adjusted = composite(visible, scrim, alpha)
            if (primaryPasses(adjusted)) {
                return Decision(
                    useDarkInk = useDark,
                    scrimAlpha = alpha,
                    scrimWhite = useDark,
                    secondaryAlpha = secondaryAlphaFor(adjusted),
                )
            }
        }
        return Decision(
            useDarkInk = useDark,
            scrimAlpha = MAX_SCRIM,
            scrimWhite = useDark,
            secondaryAlpha = 1f,
        )
    }

    /** 逐通道 alpha 混合：`overlay` 以 [alpha] 覆盖在 [base] 之上（四舍五入避免整体偏暗）。 */
    fun composite(base: Int, overlay: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val b = (base shr shift) and 0xFF
            val o = (overlay shr shift) and 0xFF
            return (b + (o - b) * a).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** WCAG 相对亮度（sRGB 线性化）。 */
    fun luminance(argb: Int): Float {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return (0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)).toFloat()
    }

    /** WCAG 对比度，返回 1..21。 */
    fun contrastRatio(first: Int, second: Int): Float {
        val a = luminance(first)
        val b = luminance(second)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
