package id.liquidglass.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Finger-tracking specular highlight + spring scale bounce inspired by iOS 26 `.interactive()`
 * and Kyant0/AndroidLiquidGlass `InteractiveHighlight`.
 */
@Composable
fun Modifier.liquidGlassTouchGlow(
    enabled: Boolean = true,
    pressedScale: Float = 1.04f,
    glowColor: Color = Color.White
): Modifier {
    if (!enabled) return this
    var pressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidTouchScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.34f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "liquidTouchGlow"
    )

    return this
        .pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                touchOffset = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    touchOffset = change.position
                    if (!change.pressed) break
                }
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (glowAlpha > 0.01f) {
                val center = if (touchOffset != Offset.Unspecified) touchOffset else Offset(size.width / 2f, size.height / 2f)
                val radius = maxOf(size.width, size.height) * 0.85f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    blendMode = BlendMode.Plus
                )
            }
        }
}
