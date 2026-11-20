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
}
