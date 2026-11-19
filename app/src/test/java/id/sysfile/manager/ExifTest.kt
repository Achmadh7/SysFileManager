package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/** Unit tests for EXIF detail (DF1). JVM-only: pure helpers + the
 *  null paths of readExif (android.media.ExifInterface is a stub on
 *  the JVM, so the positive read path is device-verified). */
class ExifTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val id = Locale("id", "ID")
    private val us = Locale.US

    @Test
    fun formatGps_fourDecimals() {
        assertEquals("-6,1751, 106,6345", ExifOps.formatGps(-6.1751, 106.6345, id))
        assertEquals("-6.1751, 106.6345", ExifOps.formatGps(-6.1751, 106.6345, us))
    }

    @Test
    fun formatExifDate_parsesExifTimestamp() {
        assertEquals(
            "20 Nov 2026, 14.15",
            ExifOps.formatExifDate("2026:11:20 14:15:30", id)
        )
        assertNull(ExifOps.formatExifDate(null, id))
        assertNull(ExifOps.formatExifDate("2026-11-20", id))
        assertNull(ExifOps.formatExifDate("bukan tanggal", id))
    }

    @Test
    fun hasExif_sizeAloneDoesNotCount() {
        assertFalse(
            ExifOps.ExifInfo(null, null, null, "3,2 MB", null)
                .hasExif()
        )
        assertTrue(
            ExifOps.ExifInfo("POCO F5", null, null, "3,2 MB", null)
                .hasExif()
        )
        assertTrue(
            ExifOps.ExifInfo(null, null, "4032 \u00d7 3024", null, null)
                .hasExif()
        )
    }

    @Test
    fun readExif_missingFileIsNull() {
        assertNull(ExifOps.readExif(File(tmp.root, "tidak-ada.jpg").absolutePath))
    }

    @Test
    fun readExif_textFileIsNull() {
        val file = File(tmp.root, "catatan.txt")
        file.writeText("bukan gambar")
        assertNull(ExifOps.readExif(file.absolutePath))
    }
}
