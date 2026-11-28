package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the 3-theme state helpers (Nordic Cobalt, Nothing Matrix, iOS Liquid Glass) and dock math. */
class ThemeTest {

    @Test
    fun isLiquidGlassStyle_matchesOnlyThirdTheme() {
        assertTrue(isLiquidGlassStyle(UI_STYLE_LIQUID_GLASS))
        assertFalse(isLiquidGlassStyle(UI_STYLE_M3))
        assertFalse(isLiquidGlassStyle(UI_STYLE_NOTHING))
        assertFalse(isLiquidGlassStyle(""))
    }

    @Test
    fun sanitizeUiStyle_normalizesUnknownToM3AndKeepsThreeStyles() {
        assertEquals(UI_STYLE_M3, sanitizeUiStyle(UI_STYLE_M3))
        assertEquals(UI_STYLE_NOTHING, sanitizeUiStyle(UI_STYLE_NOTHING))
        assertEquals(UI_STYLE_LIQUID_GLASS, sanitizeUiStyle(UI_STYLE_LIQUID_GLASS))
        assertEquals(UI_STYLE_M3, sanitizeUiStyle(null))
        assertEquals(UI_STYLE_M3, sanitizeUiStyle("unknown"))
    }

    @Test
    fun resolveFallbackUiStyle_preservesBaseAndDefaultsToM3() {
        assertEquals(UI_STYLE_M3, resolveFallbackUiStyle(UI_STYLE_M3, UI_STYLE_NOTHING))
        assertEquals(UI_STYLE_NOTHING, resolveFallbackUiStyle(UI_STYLE_NOTHING, UI_STYLE_M3))
        assertEquals(UI_STYLE_NOTHING, resolveFallbackUiStyle(UI_STYLE_LIQUID_GLASS, UI_STYLE_NOTHING))
        assertEquals(UI_STYLE_M3, resolveFallbackUiStyle(UI_STYLE_LIQUID_GLASS, null))
    }

    @Test
    fun toggleLiquidGlassUiStyle_enablesThirdThemeAndRestoresSavedBase() {
        assertEquals(UI_STYLE_LIQUID_GLASS, toggleLiquidGlassUiStyle(UI_STYLE_M3, enable = true))
        assertEquals(UI_STYLE_LIQUID_GLASS, toggleLiquidGlassUiStyle(UI_STYLE_NOTHING, enable = true))
        assertEquals(UI_STYLE_M3, toggleLiquidGlassUiStyle(UI_STYLE_M3, enable = false))
        assertEquals(UI_STYLE_NOTHING, toggleLiquidGlassUiStyle(UI_STYLE_NOTHING, enable = false))
    }

    @Test
    fun rubberbandClamp_passesInsideAndResistsPastBounds() {
        assertEquals(50f, rubberbandClamp(50f, 0f, 100f, 100f), 0.001f)
        val over = rubberbandClamp(150f, 0f, 100f, 100f)
        assertTrue(over > 100f && over < 150f)
        val under = rubberbandClamp(-50f, 0f, 100f, 100f)
        assertTrue(under < 0f && under > -50f)
    }

    @Test
    fun nearestTabByCenter_clampsToValidRange() {
        assertEquals(0, nearestTabByCenter(-10f, 100f, 3))
        assertEquals(0, nearestTabByCenter(40f, 100f, 3))
        assertEquals(1, nearestTabByCenter(150f, 100f, 3))
        assertEquals(2, nearestTabByCenter(250f, 100f, 3))
        assertEquals(2, nearestTabByCenter(400f, 100f, 3))
    }

    @Test
    fun liquidGlassProgressFromPill_normalizesBetweenZeroAndOne() {
        assertEquals(0f, liquidGlassProgressFromPill(-10f, 200f), 0.001f)
        assertEquals(0.5f, liquidGlassProgressFromPill(100f, 200f), 0.001f)
        assertEquals(1f, liquidGlassProgressFromPill(250f, 200f), 0.001f)
        assertEquals(0f, liquidGlassProgressFromPill(10f, 0f), 0.001f)
    }
}
