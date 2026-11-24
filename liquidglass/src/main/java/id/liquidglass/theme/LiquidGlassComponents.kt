package id.liquidglass.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.sin

/** Glass card that automatically renders with Liquid Glass optics when [LocalLiquidGlassEnabled] is true. */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    floating: Boolean = false,
    selected: Boolean = false,
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = liquidGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerHighest)
        ),
        modifier = modifier.liquidGlass(
            shape = shape,
            floating = floating,
            selected = selected,
            backdrop = backdrop
        ),
        content = content
    )
}

/** Floating iOS 26-style glass capsule for TopAppBar action groups, pills, or interactive buttons. */
@Composable
fun LiquidGlassCapsule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.liquidGlassInteractive(interactionSource) else Modifier)
            .liquidGlass(shape = shape, floating = true, selected = selected, backdrop = backdrop)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

/** Tab model for [LiquidGlassNavBar]. */
@Immutable
data class LiquidGlassTabItem(
    val label: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector = icon
)

/**
 * Floating iOS 26-style capsule navigation bar with a gel-morphing refractive active tab lens.
 * Pass your scrolling content's [LayerBackdrop] into [backdrop] for live refraction over scrolled items.
 */
@Composable
fun LiquidGlassNavBar(
    items: List<LiquidGlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = LocalLiquidBackdrop.current
) {
    if (items.isEmpty()) return
    val isGlass = LocalLiquidGlassEnabled.current
    val dark = LocalLiquidGlassDark.current
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val pillProgress by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
        label = "liquidNavProgress"
    )
    val flightArc = sin((pillProgress - pillProgress.toInt()).coerceIn(0f, 1f) * Math.PI.toFloat())
    val navShape = RoundedCornerShape(50)
    val activeFg = MaterialTheme.colorScheme.primary
    val inactiveFg = if (dark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(navShape)
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(navShape, floating = true, backdrop = backdrop)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, navShape)
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val gap = 6.dp
        val slotWidth = (maxWidth - gap * (items.size - 1)) / items.size.toFloat()
        val stepWidth = slotWidth + gap
        val pillX = stepWidth * pillProgress
        val pillLift = (-2).dp * flightArc

        Box(
            Modifier
                .offset(x = pillX, y = pillLift)
                .width(slotWidth)
                .height(52.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.12f * flightArc
                    scaleY = 1f - 0.04f * flightArc
                }
                .then(
                    if (isGlass) {
                        Modifier.liquidGlass(navShape, floating = true, selected = true, backdrop = backdrop)
                    } else {
                        Modifier
                            .clip(navShape)
                            .background(activeFg.copy(alpha = 0.16f))
                    }
                )
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = safeIndex == index
                val fg by animateColorAsState(
                    targetValue = if (selected) activeFg else inactiveFg,
                    label = "liquidNavFg"
                )
                val lift by animateDpAsState(
                    targetValue = if (selected) (-2).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "liquidNavLift"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(navShape)
                        .clickable { onSelect(index) }
                        .offset(y = lift),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = if (selected) item.icon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = fg,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
