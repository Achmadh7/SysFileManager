package id.liquidglass.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/** Whether Liquid Glass theme rendering is active in the current composition. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** Whether dark mode is active for Liquid Glass shading. */
val LocalLiquidGlassDark = staticCompositionLocalOf { false }

/** Default visual variant for [Modifier.liquidGlass]. */
val LocalLiquidGlassVariant = staticCompositionLocalOf { LiquidGlassVariant.Clear }

/** Active [LayerBackdrop] capturing the background or scrolling content behind glass surfaces. */
val LocalLiquidBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** Returns [Color.Transparent] when Liquid Glass is active so M3 containers do not cover refraction. */
@Composable
fun liquidGlassContainerColor(fallback: Color): Color =
    if (LocalLiquidGlassEnabled.current) Color.Transparent else fallback

/**
 * Applies iOS 26-style Liquid Glass optics to a composable:
 * vibrancy + blur + AGSL SDF lens refraction + chromatic aberration + specular highlight + top sheen.
 *
 * Gracefully degrades to blurred glass on Android 12 (API 31-32) and translucent tint on API 26-30.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    floating: Boolean = false,
    selected: Boolean = false,
    variant: LiquidGlassVariant = LocalLiquidGlassVariant.current,
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val dark = LocalLiquidGlassDark.current
    val spec = liquidGlassSpecFor(variant, floating)
    val primary = MaterialTheme.colorScheme.primary
    val surfaceTint = when {
        selected -> primary.copy(alpha = if (dark) 0.24f else 0.18f)
        dark -> Color.White.copy(alpha = spec.darkTintAlpha)
        else -> Color.White.copy(alpha = spec.lightTintAlpha)
    }
    if (backdrop == null) {
        return this.clip(shape).background(surfaceTint)
    }
    val topSheen = Color.White.copy(alpha = if (dark) spec.darkSheenAlpha else spec.lightSheenAlpha)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(spec.blurRadius.toPx())
            lens(
                refractionHeight = spec.refractionHeight.toPx(),
                refractionAmount = spec.refractionAmount.toPx(),
                depthEffect = spec.depthEffect,
                chromaticAberration = spec.chromaticAberration
            )
        },
        shadow = if (floating) ({ Shadow.Default }) else null,
        onDrawSurface = {
            drawRect(surfaceTint)
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(topSheen, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.48f
                )
            )
        }
    )
}

/**
 * Adds iOS 26 `.interactive()` spring scale response when a glass control is pressed.
 */
@Composable
fun Modifier.liquidGlassInteractive(
    interactionSource: InteractionSource,
    pressedScale: Float = 1.04f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidGlassPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Draws a window-aligned ambient multi-orb wallpaper so Liquid Glass refraction
 * stays seamless across screens and reveals optical bending at curved edges.
 */
@Composable
fun Modifier.liquidGlassWallpaper(
    dark: Boolean = LocalLiquidGlassDark.current
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val win = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = if (dark) Color(0xFF060B18) else Color(0xFFE6F0FF)
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            drawRect(base)
            val alpha = if (dark) 0.68f else 0.42f
            val w = win.width.toFloat().takeIf { it > 0f } ?: size.width
            val h = win.height.toFloat().takeIf { it > 0f } ?: size.height
            fun orb(color: Color, x: Float, y: Float, r: Float) {
                val center = Offset(w * x, h * y) - origin
                val radius = minOf(w, h) * r
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
            orb(Color(0xFF2563EB), 0.12f, 0.10f, 0.85f)
            orb(Color(0xFFD946EF), 0.92f, 0.38f, 0.78f)
            orb(Color(0xFF06B6D4), 0.18f, 0.86f, 0.82f)
            orb(Color(0xFF6366F1), 0.82f, 0.92f, 0.65f)
        }
}
