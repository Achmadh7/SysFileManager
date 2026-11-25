package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** r180 EP2: teks pill ekstrak — tanpa jumlah file, tanpa titik. */
class StringsTest {

    @Test
    fun extractPillText() {
        assertEquals("Berhasil diekstrak", TxtId.extractPill)
        assertEquals("Extracted successfully", TxtEn.extractPill)
        assertFalse(TxtId.extractPill.contains("\u2022"))
        assertFalse(TxtEn.extractPill.contains("\u2022"))
        assertFalse(TxtId.extractPill.endsWith("."))
        assertFalse(TxtEn.extractPill.endsWith("."))
    }

    @Test
    fun extractDoneUnchanged() {
        assertEquals("Berhasil diekstrak.", TxtId.extractDone)
        assertEquals("Extracted successfully.", TxtEn.extractDone)
    }

    @Test
    fun v140SettingsAndBiometricStringsPresent() {
        listOf(TxtId, TxtEn).forEach { t ->
            assertFalse(t.tapToSwitch.isBlank())
            assertFalse(t.dotfilesHint.isBlank())
            assertFalse(t.manageApksSubtitle.isBlank())
            assertFalse(t.infoEngineSubtitle.isBlank())
            assertFalse(t.biometricUnavailable.isBlank())
            assertFalse(t.cleanupTitle.isBlank())
            assertFalse(t.cleanupScan.isBlank())
            assertFalse(t.cleanupScanning.isBlank())
            assertFalse(t.cleanupRescan.isBlank())
            assertFalse(t.cleanupCleanAll.isBlank())
            assertFalse(t.cleanupNone.isBlank())
            assertFalse(t.cleanupDuplicates.isBlank())
            assertFalse(t.cleanupEmptyDirs.isBlank())
            assertFalse(t.cleanupSummary(2, 1, "1 MB").isBlank())
            assertFalse(t.cleanupConfirm(3, "2 MB").isBlank())
        }
    }
}
