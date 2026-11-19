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
    fun parseRational_fractionsAndPlain() {
        assertEquals(1.8, ExifOps.parseRational("18/10")!!, 0.0001)
        assertEquals(4.7, ExifOps.parseRational("47/10")!!, 0.0001)
        assertEquals(0.008, ExifOps.parseRational("1/125")!!, 0.000001)
        assertEquals(100.0, ExifOps.parseRational("100")!!, 0.0001)
    }

    @Test
    fun parseRational_invalidIsNull() {
        assertNull(ExifOps.parseRational(null))
        assertNull(ExifOps.parseRational(""))
        assertNull(ExifOps.parseRational("abc"))
        assertNull(ExifOps.parseRational("0/0"))
        assertNull(ExifOps.parseRational("1/2/3"))
    }

    @Test
    fun formatFocalLength_localeDecimal() {
        assertEquals("4,7 mm", ExifOps.formatFocalLength(4.7, id))
        assertEquals("50 mm", ExifOps.formatFocalLength(50.0, us))
        assertNull(ExifOps.formatFocalLength(null, id))
    }

    @Test
    fun formatExposure_fractionsAndSeconds() {
        assertEquals("1/125 s", ExifOps.formatExposure(0.008, id))
        assertEquals("1/60 s", ExifOps.formatExposure(1.0 / 60.0, us))
        assertEquals("0,5 s", ExifOps.formatExposure(0.5, id))
        assertEquals("2 s", ExifOps.formatExposure(2.0, us))
        assertNull(ExifOps.formatExposure(null, id))
        assertNull(ExifOps.formatExposure(0.0, id))
    }

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
            ExifOps.ExifInfo(null, null, null, "3,2 MB", null, null, null)
                .hasExif()
        )
        assertTrue(
            ExifOps.ExifInfo("POCO F5", null, null, "3,2 MB", null, null, null)
                .hasExif()
        )
        assertTrue(
            ExifOps.ExifInfo(null, null, "4032 \u00d7 3024", null, null, null, null)
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
