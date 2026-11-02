package id.sysfile.manager

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    secondary = Color(0xFFCDB9DD),
    onSecondary = Color(0xFF372847),
    secondaryContainer = Color(0xFF4F3D63),
    onSecondaryContainer = Color(0xFFEDDCFF),
    tertiary = Color(0xFFC6CC94),
    onTertiary = Color(0xFF2E330F),
    tertiaryContainer = Color(0xFF44481F),
    onTertiaryContainer = Color(0xFFE2E6AE),
    background = Color(0xFF141118),
    onBackground = Color(0xFFEDE6F2),
    surface = Color(0xFF141118),
    onSurface = Color(0xFFEDE6F2),
    surfaceVariant = Color(0xFF2C2733),
    onSurfaceVariant = Color(0xFFCFC6D9),
    surfaceTint = Color(0xFFFFB77D),
    inverseSurface = Color(0xFFEDE6F2),
    inverseOnSurface = Color(0xFF332C3D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF988DA1),
    outlineVariant = Color(0xFF4A4356),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3544),
    surfaceDim = Color(0xFF141118),
    surfaceContainerLowest = Color(0xFF0E0B11),
    surfaceContainerLow = Color(0xFF1C1822),
    surfaceContainer = Color(0xFF221D2A),
    surfaceContainerHigh = Color(0xFF2C2635),
    surfaceContainerHighest = Color(0xFF373040)
)

/** Tema aplikasi: mode "light" / "dark" / "system" dari preferensi. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(themeMode: String, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkScheme else LightScheme
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
