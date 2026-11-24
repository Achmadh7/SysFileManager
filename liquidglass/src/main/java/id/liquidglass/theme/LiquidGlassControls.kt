package id.liquidglass.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.sin

/**
 * Interactive iOS 26 Liquid Glass button with AGSL lens refraction and finger-tracking glow.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (tint != Color.Unspecified) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .liquidGlassTouchGlow(enabled = enabled)
            .liquidGlass(
                shape = shape,
                floating = true,
                selected = tint != Color.Unspecified,
                backdrop = backdrop
            )
            .clip(shape)
            .then(if (tint != Color.Unspecified) Modifier.background(tint.copy(alpha = 0.28f)) else Modifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * iOS 26 Liquid Glass switch with gel-stretching refractive thumb and tap/drag interaction.
 */
@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color(0xFF34C759),
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current
) {
    val shape = RoundedCornerShape(50)
    val dark = LocalLiquidGlassDark.current
    val isGlass = LocalLiquidGlassEnabled.current
    var dragging by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "liquidToggleProgress"
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> activeColor.copy(alpha = if (isGlass) 0.78f else 1f)
            dark -> Color.White.copy(alpha = 0.16f)
            else -> Color.Black.copy(alpha = 0.12f)
        },
        label = "liquidToggleTrack"
    )
    val arc = sin(progress.coerceIn(0f, 1f) * Math.PI.toFloat())
    val stretchX = 1f + 0.18f * arc + (if (dragging) 0.10f else 0f)
    val stretchY = 1f - 0.06f * arc + (if (dragging) 0.04f else 0f)

    Box(
        modifier = modifier
            .size(width = 60.dp, height = 34.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(trackColor)
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.24f else 0.55f), shape)
            .pointerInput(enabled, checked) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 8f && !checked) onCheckedChange(true)
                        else if (dragAmount < -8f && checked) onCheckedChange(false)
                    }
                )
            }
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbOffset = 26.dp * progress
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .graphicsLayer {
                    scaleX = stretchX
                    scaleY = stretchY
                }
                .then(
                    if (isGlass) {
                        Modifier
                            .liquidGlass(shape, floating = true, backdrop = backdrop)
                            .clip(shape)
                            .background(Color.White.copy(alpha = 0.72f))
                    } else {
                        Modifier
                            .clip(shape)
                            .background(Color.White)
                    }
                )
        )
    }
}

/**
 * iOS 26 Liquid Glass slider with a magnifying capsule lens thumb that stretches on drag.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val dark = LocalLiquidGlassDark.current
    val isGlass = LocalLiquidGlassEnabled.current
    val trackShape = RoundedCornerShape(50)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }
    if (!dragging) dragFraction = fraction

    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidSliderThumbScale"
    )

    val thumbWidth = 38.dp
    val thumbHeight = 24.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.CenterStart
    ) {
        val travelDp = (maxWidth - thumbWidth).coerceAtLeast(1.dp)
        val travelPx = with(density) { travelDp.toPx() }
        val thumbHalfPx = with(density) { (thumbWidth / 2).toPx() }

        fun emitFraction(f: Float) {
            val clamped = f.coerceIn(0f, 1f)
            dragFraction = clamped
            onValueChange(valueRange.start + clamped * span)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled, travelPx, valueRange) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        emitFraction((offset.x - thumbHalfPx) / travelPx)
                    }
                }
                .pointerInput(enabled, travelPx, valueRange) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            emitFraction(dragFraction + dragAmount / travelPx)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(trackShape)
                    .background(if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(dragFraction)
                        .fillMaxHeight()
                        .clip(trackShape)
                        .background(activeColor)
                )
            }

            // Refractive glass lens thumb
            Box(
                Modifier
                    .offset(x = travelDp * dragFraction)
                    .size(width = thumbWidth, height = thumbHeight)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .then(
                        if (isGlass) {
                            Modifier
                                .liquidGlass(trackShape, floating = true, backdrop = backdrop)
                                .clip(trackShape)
                                .background(Color.White.copy(alpha = if (dragging) 0.22f else 0.72f))
                        } else {
                            Modifier
                                .clip(trackShape)
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, trackShape)
                        }
                    )
            )
        }
    }
}
