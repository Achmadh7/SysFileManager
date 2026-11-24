package id.liquidglass.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Top-level theme wrapper providing iOS 26-inspired Liquid Glass styling and backdrop capture.
 *
 * Set [enabled] to `false` to toggle back to standard Material 3 using [fallbackDarkScheme] / [fallbackLightScheme].
 */
@Composable
fun LiquidGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    enabled: Boolean = true,
    variant: LiquidGlassVariant = LiquidGlassVariant.Clear,
    showWallpaper: Boolean = true,
    fallbackDarkScheme: ColorScheme = darkColorScheme(),
    fallbackLightScheme: ColorScheme = lightColorScheme(),
    content: @Composable () -> Unit
) {
    val scheme = when {
        enabled && darkTheme -> LiquidGlassDarkColorScheme
        enabled -> LiquidGlassLightColorScheme
        darkTheme -> fallbackDarkScheme
        else -> fallbackLightScheme
    }
    MaterialTheme(colorScheme = scheme) {
        val bgBackdrop = if (enabled) rememberLayerBackdrop() else null
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalLiquidGlassEnabled provides enabled,
            LocalLiquidGlassDark provides darkTheme,
            LocalLiquidGlassVariant provides variant,
            LocalLiquidBackdrop provides bgBackdrop
        ) {
            if (enabled && bgBackdrop != null && showWallpaper) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .liquidGlassWallpaper(darkTheme)
                            .layerBackdrop(bgBackdrop)
                    )
                    content()
                }
            } else {
                content()
            }
        }
    }
}
