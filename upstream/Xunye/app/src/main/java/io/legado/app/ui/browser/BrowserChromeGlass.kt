package io.legado.app.ui.browser

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import io.legado.app.ui.animation.InteractiveHighlight
import io.legado.app.ui.theme.ChromeSurfaceStyle
import io.legado.app.ui.theme.ChromeSurfaceStylePolicy
import io.legado.app.ui.theme.GlassReadiness
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalTopBarBackdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * SPEC-024/026/056: phone floating chrome — style from global chromeSurfaceStyle.
 * Never samples AwContents (uses scaffold backdrop or a solid local layer only).
 */
internal object BrowserChromeGlassStyle {
    fun barSurfaceAlpha(isDark: Boolean): Float = if (isDark) 0.42f else 0.16f
    /** SPEC-221: 净读 FAB 跟 AppTheme primary，玻璃态仍用较高着色以免洗成灰。 */
    fun fabSurfaceAlpha(isDark: Boolean): Float = if (isDark) 0.78f else 0.72f
    fun barFallbackAlpha(isDark: Boolean): Float = if (isDark) 0.55f else 0.38f
    fun fabFallbackAlpha(isDark: Boolean): Float = if (isDark) 0.92f else 0.88f
    fun shadowAlpha(isDark: Boolean): Float = if (isDark) 0.20f else 0.10f

}

@Composable
internal fun currentChromeSurfaceStyle(): ChromeSurfaceStyle =
    ChromeSurfaceStylePolicy.resolve(LocalAppUiConfiguration.current.theme.chromeSurfaceStyle)

/**
 * SPEC-071: alpha for solid tints drawn on NTP liquid-glass controls.
 * Liquid glass → light 0.4 / dark 0.35 (SPEC-072); filled → opaque 1.
 * SPEC-224: 不再因滑动门闸改 alpha。
 */
@Composable
fun ntpGlassOverlayAlpha(forceDark: Boolean? = null): Float {
    val style = currentChromeSurfaceStyle()
    if (style != ChromeSurfaceStyle.LiquidGlass) return 1f
    val dark = forceDark ?: LegadoTheme.isDark
    return if (dark) BrowserUiTokens.NtpGlassOverlayDark else BrowserUiTokens.NtpGlassOverlayLight
}

@Composable
fun Modifier.browserFloatingChromeGlass(
    shape: Shape = ContinuousCapsule,
    tint: Color = LegadoTheme.colorScheme.surfaceContainer,
): Modifier {
    val isDark = LegadoTheme.isDark
    val style = currentChromeSurfaceStyle()
    val scaffoldBackdrop = LocalTopBarBackdrop.current
    val localBackdrop = rememberLayerBackdrop {
        drawRect(tint.copy(alpha = 1f))
    }
    return when (style) {
        ChromeSurfaceStyle.LiquidGlass -> {
            // SPEC-148 R3: 就绪门闸；SPEC-224: 废除运动抑制。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                GlassReadiness.ready
            ) {
                drawBackdrop(
                    backdrop = scaffoldBackdrop ?: localBackdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(14.dp.toPx())
                        lens(20.dp.toPx(), 20.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(alpha = BrowserChromeGlassStyle.shadowAlpha(isDark)),
                        )
                    },
                    onDrawSurface = {
                        drawRect(tint.copy(alpha = BrowserChromeGlassStyle.barSurfaceAlpha(isDark)))
                    },
                )
            } else {
                clip(shape).background(
                    tint.copy(alpha = BrowserChromeGlassStyle.barFallbackAlpha(isDark)),
                    shape,
                )
            }
        }
        ChromeSurfaceStyle.Translucent -> clip(shape).background(
            tint.copy(alpha = BrowserChromeGlassStyle.barFallbackAlpha(isDark)),
            shape,
        )
        ChromeSurfaceStyle.Filled -> clip(shape).background(tint.copy(alpha = 1f), shape)
    }
}

@Composable
fun Modifier.browserReaderFabGlass(
    accent: Color,
): Modifier {
    val isDark = LegadoTheme.isDark
    val style = currentChromeSurfaceStyle()
    val scaffoldBackdrop = LocalTopBarBackdrop.current
    val localBackdrop = rememberLayerBackdrop {
        drawRect(accent.copy(alpha = 1f))
    }
    return when (style) {
        ChromeSurfaceStyle.LiquidGlass -> {
            // SPEC-148 R3 / SPEC-224: readiness only（无运动抑制）。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                GlassReadiness.ready
            ) {
                drawBackdrop(
                    backdrop = scaffoldBackdrop ?: localBackdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(14.dp.toPx())
                        lens(20.dp.toPx(), 20.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(alpha = BrowserChromeGlassStyle.shadowAlpha(isDark)),
                        )
                    },
                    onDrawSurface = {
                        drawRect(accent.copy(alpha = BrowserChromeGlassStyle.fabSurfaceAlpha(isDark)))
                    },
                )
            } else {
                clip(CircleShape).background(
                    accent.copy(alpha = BrowserChromeGlassStyle.fabFallbackAlpha(isDark)),
                    CircleShape,
                )
            }
        }
        ChromeSurfaceStyle.Translucent -> clip(CircleShape).background(
            accent.copy(alpha = BrowserChromeGlassStyle.fabFallbackAlpha(isDark)),
            CircleShape,
        )
        ChromeSurfaceStyle.Filled -> clip(CircleShape).background(accent.copy(alpha = 1f), CircleShape)
    }
}

/**
 * SPEC-031/056: Legado top-bar liquid glass (press/drag lens). Never samples AwContents.
 * [interactive]=false for the omnibox so text gestures stay on the field.
 * Respects global [ChromeSurfaceStyle].
 */
@Composable
fun Modifier.browserControlLiquidGlass(
    shape: Shape = RoundedCornerShape(50),
    interactive: Boolean = true,
    showShadow: Boolean = true,
    readableOverlay: Boolean = false,
    /** SPEC-075: lens on small circles squashes icons into ellipses — keep off for NTP bubbles. */
    enableLens: Boolean = true,
    /** SPEC-099: optional surface tint alpha (toolbox panel / denser cells). */
    overlayAlpha: Float? = null,
    /** SPEC-265: toolbox can disable edge Highlight stroke. */
    showHighlight: Boolean = true,
): Modifier {
    val isDark = LegadoTheme.isDark
    val surface = LegadoTheme.colorScheme.surface
    val style = currentChromeSurfaceStyle()
    val scaffoldBackdrop = LocalTopBarBackdrop.current
    val localBackdrop = rememberLayerBackdrop {
        drawRect(surface)
    }
    val backdrop = scaffoldBackdrop ?: localBackdrop
    val blurRadius = if (readableOverlay) BrowserUiTokens.NtpOverlayBlur else 12.dp
    val surfaceAlpha = overlayAlpha ?: when {
        readableOverlay && isDark -> BrowserUiTokens.NtpOverlaySurfaceDark
        readableOverlay -> BrowserUiTokens.NtpOverlaySurfaceLight
        isDark -> 0.50f
        else -> 0.42f
    }
    val fallbackAlpha = when {
        style == ChromeSurfaceStyle.Filled -> 1f
        style == ChromeSurfaceStyle.Translucent || style == ChromeSurfaceStyle.LiquidGlass -> {
            if (overlayAlpha != null) surfaceAlpha
            else if (readableOverlay) surfaceAlpha
            else BrowserChromeGlassStyle.barFallbackAlpha(isDark)
        }
        else -> 1f
    }
    val fallback = clip(shape).background(
        surface.copy(alpha = fallbackAlpha),
        shape,
    )
    // SPEC-224: 废除 GlassMotionGate；仅就绪门闸。
    val liquidActive = style == ChromeSurfaceStyle.LiquidGlass &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        GlassReadiness.ready
    if (!liquidActive) return fallback
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val glass = drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (enableLens) {
                lens(24.dp.toPx(), 24.dp.toPx())
            }
        },
        highlight = if (showHighlight) {
            { Highlight.Default }
        } else {
            null
        },
        shadow = {
            if (showShadow) {
                Shadow(
                    radius = 12.dp,
                    color = Color.Black.copy(alpha = BrowserChromeGlassStyle.shadowAlpha(isDark)),
                )
            } else {
                null
            }
        },
        layerBlock = {
            if (interactive) {
                val width = size.width
                val height = size.height
                if (width > 0f && height > 0f) {
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 4.dp.toPx() / height * progress
                    val maxOffset = size.minDimension
                    val dragOffset = interactiveHighlight.dragOffset
                    translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset) * progress
                    translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset) * progress
                    val maxDragScale = 4.dp.toPx() / height
                    val offsetAngle = atan2(dragOffset.y, dragOffset.x)
                    scaleX = scale + maxDragScale *
                        abs(cos(offsetAngle) * dragOffset.x / size.maxDimension) *
                        (width / height).coerceAtMost(1f) * progress
                    scaleY = scale + maxDragScale *
                        abs(sin(offsetAngle) * dragOffset.y / size.maxDimension) *
                        (height / width).coerceAtMost(1f) * progress
                }
            }
        },
        onDrawSurface = {
            drawRect(surface.copy(alpha = surfaceAlpha))
        },
    )
    return if (interactive) {
        glass.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier)
    } else {
        glass
    }
}
