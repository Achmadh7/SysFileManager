package id.sysfile.manager

// r210: LiquidKit — gaya kedua "Liquid Glass" (iOS 26) untuk SysFile.
// Di-port dari file Liquid Glass yang ditulis Achmad sendiri
// (SysFile_0_s4cl.kt): warna, pegas ala SwiftUI, permukaan kaca,
// tab bar kapsul, kontrol segmen. Yang TIDAK ikut: layar/data
// aplikasi mandiri itu (MediaStore, ViewModel, MainActivity-nya) —
// SysFile memakai layar + datanya sendiri; yang dipakai di sini
// hanya gayanya. Efek kaca asli (blur + lensa + vibrancy) memakai
// library Backdrop 1.0.0 (lini androidx); kartu/ubin memakai
// GlassSurface murah (tint + tepi terang) seperti file aslinya.

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/* ───────────── Animasi: pegas ala SwiftUI ─────────────
 * spring(duration, bounce) → dampingRatio = 1 - bounce, stiffness = (2π / duration)²
 * .smooth = bounce 0, .snappy = 0.15, .bouncy = 0.3 (durasi bawaan 0.5 detik)
 */
fun <T> iosSpring(duration: Float = 0.5f, bounce: Float = 0f) = spring<T>(
    dampingRatio = 1f - bounce,
    stiffness = (2.0 * Math.PI / duration).let { (it * it).toFloat() }
)

/* ───────────── Warna (gelap + terang) ───────────── */

@Immutable
class StatusColors(val dot: Color, val text: Color)

@Immutable
class LiquidColors(
    val bg: Color, val text: Color, val textSecondary: Color,
    val glassTint: Color, val rimStart: Color, val rimEnd: Color,
    val barTint: Color, val barTintFallback: Color,
    val indicator: Color, val indicatorRim: Color, val accent: Color,
    val chipBg: Color, val chipFg: Color,
    val track: Color, val barFill: Color, val separator: Color,
    val dangerTile: Color, val dangerChip: Color,
    val success: StatusColors, val warning: StatusColors, val danger: StatusColors,
    val segmentColors: List<Color>,
    val washColors: List<Color>,
    val progressTrack: Color, val progressFill: Color,
    val pillDotOk: Color, val pillDotError: Color
)

val DarkLiquid = LiquidColors(
    bg = Color(0xFF0D1016), text = Color.White, textSecondary = Color.White.copy(alpha = 0.72f),
    glassTint = Color.White.copy(alpha = 0.10f),
    rimStart = Color.White.copy(alpha = 0.32f), rimEnd = Color.White.copy(alpha = 0.10f),
    barTint = Color.White.copy(alpha = 0.10f), barTintFallback = Color(0xEB1B1F27),
    indicator = Color.White.copy(alpha = 0.17f), indicatorRim = Color.White.copy(alpha = 0.24f),
    accent = Color(0xFF64B5FF), chipBg = Color(0xFF93C5FD), chipFg = Color(0xFF0C2A52),
    track = Color.White.copy(alpha = 0.18f), barFill = Color(0xFF7CC0FF),
    separator = Color.White.copy(alpha = 0.10f),
    dangerTile = Color(0xFFF0443F).copy(alpha = 0.22f), dangerChip = Color(0xFFF87171),
    success = StatusColors(Color(0xFF30D158), Color(0xFF9AF3B4)),
    warning = StatusColors(Color(0xFFFF9F0A), Color(0xFFFFD08A)),
    danger = StatusColors(Color(0xFFFF453A), Color(0xFFFFB3AD)),
    segmentColors = listOf(
        Color(0xFFBF5AF2), Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFFF9F0A),
        Color(0xFFFF375F), Color(0xFF64D2FF), Color(0xFF8E8E93), Color(0xFF636366)
    ),
    washColors = listOf(Color(0xFF101827), Color(0xFF161230), Color(0xFF0B2027)),
    progressTrack = Color.White.copy(alpha = 0.18f), progressFill = Color.White.copy(alpha = 0.85f),
    pillDotOk = Color.White.copy(alpha = 0.25f), pillDotError = Color(0xFFFF3B30).copy(alpha = 0.85f)
)

val LightLiquid = LiquidColors(
    bg = Color(0xFFF2F2F7), text = Color.Black, textSecondary = Color(0xFF3C3C43).copy(alpha = 0.78f),
    glassTint = Color.White.copy(alpha = 0.62f),
    rimStart = Color.White, rimEnd = Color.Black.copy(alpha = 0.07f),
    barTint = Color.White.copy(alpha = 0.55f), barTintFallback = Color(0xF0FFFFFF),
    indicator = Color(0xFF787880).copy(alpha = 0.18f), indicatorRim = Color.White.copy(alpha = 0.7f),
    accent = Color(0xFF0062CC), chipBg = Color(0xFFCFE3FF), chipFg = Color(0xFF0A4A9E),
    track = Color(0xFF787880).copy(alpha = 0.2f), barFill = Color(0xFF007AFF),
    separator = Color(0xFF3C3C43).copy(alpha = 0.14f),
    dangerTile = Color(0xFFFF3B30).copy(alpha = 0.14f), dangerChip = Color(0xFFFF3B30),
    success = StatusColors(Color(0xFF34C759), Color(0xFF14702C)),
    warning = StatusColors(Color(0xFFFF9500), Color(0xFF8A4B00)),
    danger = StatusColors(Color(0xFFFF3B30), Color(0xFFB3261E)),
    segmentColors = listOf(
        Color(0xFFAF52DE), Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500),
        Color(0xFFFF2D55), Color(0xFF5AC8FA), Color(0xFF8E8E93), Color(0xFFC7C7CC)
    ),
    washColors = listOf(Color(0xFFE7EDF6), Color(0xFFEEE7F6), Color(0xFFE3F1EE)),
    progressTrack = Color.Black.copy(alpha = 0.12f), progressFill = Color.Black.copy(alpha = 0.70f),
    pillDotOk = Color.Black.copy(alpha = 0.40f), pillDotError = Color(0xFFFF3B30).copy(alpha = 0.85f)
)

val LocalLiquid = staticCompositionLocalOf { DarkLiquid }

/** true saat gaya Liquid aktif — dibaca layar/dialog yang tidak
 *  menerima param isLiquid (Pengaturan pakai themeStyle langsung). */
val LocalIsLiquid = staticCompositionLocalOf { false }

/** Backdrop kaca (r219 LG2): layer berisi konten di balik elemen kaca,
 *  disediakan LiquidTheme; null saat gaya Material (tanpa blur). */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Skema Material 3 yang diturunkan dari warna Liquid — supaya semua
 *  layar SysFile yang membaca peran skema ikut berubah warna tanpa
 *  diedit satu-satu saat gaya Liquid aktif. Peran wadah memakai
 *  turunan opak dari latar (kaca tembus pandang baru dipakai saat
 *  kartu diganti GlassSurface di revisi layar). */
fun liquidColorScheme(dark: Boolean): ColorScheme {
    val c = if (dark) DarkLiquid else LightLiquid
    return ColorScheme(
        primary = c.accent,
        onPrimary = c.chipFg,
        primaryContainer = c.chipBg,
        onPrimaryContainer = c.chipFg,
        inversePrimary = if (dark) LightLiquid.accent else DarkLiquid.accent,
        secondary = c.textSecondary,
        onSecondary = c.bg,
        secondaryContainer = c.track,
        onSecondaryContainer = c.text,
        tertiary = c.segmentColors[0],
        onTertiary = Color.White,
        tertiaryContainer = c.chipBg,
        onTertiaryContainer = c.chipFg,
        background = c.bg,
        onBackground = c.text,
        surface = c.bg,
        onSurface = c.text,
        surfaceVariant = c.track,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.accent,
        inverseSurface = if (dark) Color(0xFFE0E2E8) else Color(0xFF2D3135),
        inverseOnSurface = c.bg,
        error = c.danger.dot,
        onError = Color.White,
        errorContainer = c.dangerTile,
        onErrorContainer = c.danger.text,
        outline = c.separator,
        outlineVariant = c.separator,
        scrim = Color.Black,
        surfaceBright = c.barTintFallback,
        surfaceDim = if (dark) Color(0xFF0B0F12) else Color(0xFFD8DAE0),
        surfaceContainer = c.barTintFallback,
        surfaceContainerHigh = if (dark) Color(0xFF22272F) else Color(0xFFE6E8EE),
        surfaceContainerHighest = if (dark) Color(0xFF2A2F38) else Color(0xFFE0E2E8),
        surfaceContainerLow = if (dark) Color(0xFF10141A) else Color(0xFFF1F4F9),
        surfaceContainerLowest = if (dark) Color(0xFF0B0F12) else Color.White,
        primaryFixed = c.chipBg,
        primaryFixedDim = c.accent,
        onPrimaryFixed = c.chipFg,
        onPrimaryFixedVariant = c.chipFg,
        secondaryFixed = c.chipBg,
        secondaryFixedDim = c.accent,
        onSecondaryFixed = c.chipFg,
        onSecondaryFixedVariant = c.chipFg,
        tertiaryFixed = c.segmentColors[0].copy(alpha = 0.35f),
        tertiaryFixedDim = c.segmentColors[0],
        onTertiaryFixed = c.chipFg,
        onTertiaryFixedVariant = c.chipFg,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiquidTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkLiquid else LightLiquid
    // r219 LG2: perekam layer untuk blur kaca — hanya dibuat saat liquid.
    val backdrop = rememberLayerBackdrop()
    MaterialExpressiveTheme(
        colorScheme = liquidColorScheme(dark),
        motionScheme = MotionScheme.expressive()
    ) {
        CompositionLocalProvider(
            LocalLiquid provides colors,
            LocalIsLiquid provides true,
            LocalGlassBackdrop provides backdrop,
            LocalContentColor provides colors.text,
            content = content
        )
    }
}

/** Warna wadah dialog: kaca saat gaya Liquid, bawaan M3 bila bukan.
 *  Dipakai semua AlertDialog di Dialogs.kt — satu baris per dialog. */
@Composable
fun liquidDialogContainerColor(): Color =
    if (LocalIsLiquid.current) LocalLiquid.current.glassTint else AlertDialogDefaults.containerColor

/** Kaca blur asli (r219 LG2): bila ada backdrop (gaya Liquid), gambar
 *  latar yang nge-blur + tint + tepi terang; bila tidak ada (gaya
 *  Material / fallback), tint + tepi seperti kaca murah sekarang. */
@Composable
fun Modifier.glassBlur(shape: Shape, tint: Color): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val c = LocalLiquid.current
    val rim = Brush.linearGradient(listOf(c.rimStart, c.rimEnd))
    return if (backdrop != null) {
        this.liquidGlass(backdrop, shape, tint).border(1.dp, rim, shape)
    } else {
        this.clip(shape).background(tint).border(1.dp, rim, shape)
    }
}

/** Latar wash gradasi halus (r220 A2): bila ada backdrop (gaya Liquid),
 *  rekam wadah ke layer backdrop + gambar gradasi wash di atasnya,
 *  supaya kaca punya sesuatu untuk dibiaskan; bila tidak ada (gaya
 *  Material), kembalikan modifier apa adanya. */
@Composable
fun Modifier.liquidGlassBackground(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val c = LocalLiquid.current
    return if (backdrop != null) this.layerBackdrop(backdrop).background(Brush.linearGradient(c.washColors)) else this
}

/** Bar progres datar ala iOS (r221 P2), hanya untuk gaya Liquid.
 *  progress null = indeterminate (pil geser 40% lebar track). */
@Composable
fun LiquidProgressBar(progress: (() -> Float)?, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    Box(modifier.height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(c.progressTrack)) {
        if (progress == null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pillW = maxWidth * 0.4f
                val frac by rememberInfiniteTransition(label = "liquidProgress").animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween<Float>(1200, easing = LinearEasing), RepeatMode.Restart),
                    label = "liquidProgressFrac"
                )
                val off = (maxWidth + pillW) * frac - pillW
                Box(Modifier.offset(x = off).width(pillW).fillMaxHeight().clip(RoundedCornerShape(2.5.dp)).background(c.progressFill))
            }
        } else {
            val p = progress().coerceIn(0f, 1f)
            Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(RoundedCornerShape(2.5.dp)).background(c.progressFill))
        }
    }
}

/** Centang seleksi lingkaran ala iOS (r222 X5), hanya untuk gaya Liquid. */
@Composable
fun IosCheckCircle(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).clip(CircleShape)
            .then(
                if (checked) Modifier.background(MaterialTheme.colorScheme.primary)
                else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** Spinner ala iOS (r222 X6d): lingkaran tipis, hanya untuk gaya Liquid. */
@Composable
fun LiquidSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        strokeWidth = 3.dp,
        modifier = modifier
    )
}

/* ───────────── Komponen kaca ───────────── */

/** Permukaan kaca: blur asli (r219) bila ada backdrop, tint + tepi terang bila tidak. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = LocalLiquid.current.glassTint,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.glassBlur(shape, tint),
        content = content
    )
}

@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    background: Color = LocalLiquid.current.chipBg,
    tint: Color = LocalLiquid.current.chipFg
) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.57f))
    }
}

/** Efek tekan iOS: membesar sedikit saat disentuh, memantul kembali saat dilepas. */
fun Modifier.iosPressable(onClick: () -> Unit, pressedScale: Float = 1.04f): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) iosSpring(0.3f, 0f) else iosSpring(0.45f, 0.35f),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
}

/** Efek Liquid Glass (vibrancy + blur + lensa) dengan degradasi per versi Android. Dipakai tab bar, bar mengambang, dan pill (lewat glassBlur). */
fun Modifier.liquidGlass(backdrop: Backdrop, shape: Shape, surface: Color): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (Build.VERSION.SDK_INT >= 33) vibrancy()
        if (Build.VERSION.SDK_INT >= 31) blur(8f.dp.toPx())
        if (Build.VERSION.SDK_INT >= 33) lens(12f.dp.toPx(), 24f.dp.toPx())
    },
    onDrawSurface = { drawRect(surface) }
)

/* ───────────── Tab bar kapsul (iOS 26) ───────────── */

/**
 * Tab bar kapsul kaca untuk gaya Liquid — pengganti SysFileNavBar
 * saat themeStyle == "liquid". r219 LG2: blur asli via glassBlur;
 * bar mengambang di overlay (MainActivity) supaya konten bergulir
 * di bawahnya dan blur-nya terlihat.
 */
@Composable
fun LiquidGlassTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalLiquid.current
    val t = txt()
    val tabs = listOf(
        Triple(0, Icons.Default.Home, t.home),
        Triple(1, Icons.Default.History, t.recentShort),
        Triple(2, Icons.Default.Analytics, t.analysisShort)
    )
    val barShape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, bottom = 16.dp)
            .height(64.dp)
            .glassBlur(barShape, c.barTint)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * selected,
            animationSpec = iosSpring<Dp>(0.45f, 0.3f),
            label = "indicator"
        )
        val pill = RoundedCornerShape(50)
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(pill)
                .background(c.indicator)
                .border(1.dp, c.indicatorRim, pill)
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { (index, icon, label) ->
                LiquidTabItem(icon, label, index == selected) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun RowScope.LiquidTabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalLiquid.current
    val color by animateColorAsState(
        targetValue = if (selected) c.accent else c.textSecondary,
        animationSpec = tween(200),
        label = "tabColor"
    )
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Kontrol segmen ala iOS dengan indikator yang bergeser memakai pegas. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = LocalLiquid.current
    val pill = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth().height(40.dp).clip(pill).background(c.track).padding(3.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, iosSpring<Dp>(0.4f, 0.25f), label = "segment")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().clip(pill)
                .background(c.indicator).border(1.dp, c.indicatorRim, pill)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(pill).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(i) }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (i == selected) c.text else c.textSecondary
                    )
                }
            }
        }
    }
}
