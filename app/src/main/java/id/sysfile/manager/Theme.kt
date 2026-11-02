package id.sysfile.manager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Skema warna oranye/amber SysFileManager, ditulis tangan mengikuti peran warna
// Material 3 (tanpa peran "shadow": peran itu membuat skema gagal
// dikompilasi di Material3 1.5.0-alpha08).
private val LightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF9A4A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC3),
    onPrimaryContainer = Color(0xFF6E3400),
    inversePrimary = Color(0xFFFFB77D),
    secondary = Color(0xFF755845),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC3),
    onSecondaryContainer = Color(0xFF5A4130),
    tertiary = Color(0xFF5F6238),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E6AE),
    onTertiaryContainer = Color(0xFF44481F),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF4DED1),
    onSurfaceVariant = Color(0xFF54443A),
    surfaceTint = Color(0xFF9A4A00),
    inverseSurface = Color(0xFF382E27),
    inverseOnSurface = Color(0xFFFFEDE3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF877367),
    outlineVariant = Color(0xFFD8C2B4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F4),
    surfaceDim = Color(0xFFEAD6C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFBEBDF),
    surfaceContainerHigh = Color(0xFFF5E5D9),
    surfaceContainerHighest = Color(0xFFEEDDD0)
)

private val DarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFFFB77D),
    onPrimary = Color(0xFF4D2600),
    primaryContainer = Color(0xFF6E3400),
    onPrimaryContainer = Color(0xFFFFDCC3),
    inversePrimary = Color(0xFF9A4A00),
    secondary = Color(0xFFE5C0A8),
    onSecondary = Color(0xFF422B1A),
    secondaryContainer = Color(0xFF5A4130),
    onSecondaryContainer = Color(0xFFFFDCC3),
    tertiary = Color(0xFFC6CC94),
    onTertiary = Color(0xFF2E330F),
    tertiaryContainer = Color(0xFF44481F),
    onTertiaryContainer = Color(0xFFE2E6AE),
    background = Color(0xFF1A120C),
    onBackground = Color(0xFFF0DFD3),
    surface = Color(0xFF1A120C),
    onSurface = Color(0xFFF0DFD3),
    surfaceVariant = Color(0xFF54443A),
    onSurfaceVariant = Color(0xFFD8C2B4),
    surfaceTint = Color(0xFFFFB77D),
    inverseSurface = Color(0xFFF0DFD3),
    inverseOnSurface = Color(0xFF382E27),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D81),
    outlineVariant = Color(0xFF54443A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413731),
    surfaceDim = Color(0xFF1A120C),
    surfaceContainerLowest = Color(0xFF140D08),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF261E17),
    surfaceContainerHigh = Color(0xFF31281F),
    surfaceContainerHighest = Color(0xFF3D332A)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Tema aplikasi: mode "light" / "dark" / "system" dari preferensi. */
@Composable
fun SysFileTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
