package id.sysfile.manager

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests untuk optimasi load kategori (r128: L1+L2+L3).
 * Bagian MediaStore yang butuh ContentResolver tidak bisa dites di JVM —
 * yang dites di sini adalah orkestrasinya (paralel walk, cabang media,
 * fallback, file tersembunyi) lewat mediaQuery palsu, plus pemetaan
 * baris MediaStore yang murni.
 */
class CategoryLoadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun root(name: String, kind: String = "internal"): RootInfo {
        val dir = File(tmp.root, name).apply { mkdirs() }
        return RootInfo(dir.absolutePath, kind, 0L, 0L)
    }

    private fun put(dir: File, name: String, modified: Long): File {
        val f = File(dir, name).apply { writeText("x") }
        f.setLastModified(modified)
        return f
    }

    @Test
    fun capStopsWalk_returnsFirstMatchesOnly() {
        val dir = File(tmp.root, "a").apply { mkdirs() }
        repeat(10) { i -> put(dir, "img%02d.jpg".format(i), 1000L + i) }
        val found = FileOps.categoryFiles(listOf(dir), Category.IMAGE, showHidden = false, cap = 3)
        assertEquals(3, found.size)
    }

    @Test
    fun parallelWalk_mergesRootsSortedDesc() = runBlocking {
        val r1 = root("r1")
        val r2 = root("r2")
        put(File(r1.path), "a.jpg", 1000L)
        put(File(r1.path), "b.jpg", 3000L)
        put(File(r2.path), "c.jpg", 2000L)
        val out = loadCategoryFiles(listOf(r1, r2), Category.IMAGE, showHidden = false, locked = emptySet())
        assertEquals(listOf("b.jpg", "c.jpg", "a.jpg"), out.map { it.name })
    }

    @Test
    fun mediaQuery_usedForMedia_skipsInternalWalkButWalksUsb() = runBlocking {
        val internal = root("internal")
        val usb = root("usb", kind = "usb")
        put(File(internal.path), "dalam.jpg", 1000L)
        put(File(usb.path), "otg.jpg", 2000L)
        val fake = FEntry("/sdcard/DCIM/foto.jpg", "foto.jpg", false, 10L, 5000L, Category.IMAGE)
        val out = loadCategoryFiles(
            listOf(internal, usb), Category.IMAGE, showHidden = false, locked = emptySet(),
            mediaQuery = { listOf(fake) }
        )
        assertEquals(listOf("foto.jpg", "otg.jpg"), out.map { it.name })
    }

    @Test
    fun mediaQueryNull_fallsBackToFullWalk() = runBlocking {
        val r1 = root("r1")
        put(File(r1.path), "a.jpg", 1000L)
        val out = loadCategoryFiles(
            listOf(r1), Category.IMAGE, showHidden = false, locked = emptySet(),
            mediaQuery = { null }
        )
        assertEquals(listOf("a.jpg"), out.map { it.name })
    }

    @Test
    fun showHiddenTrue_ignoresMediaQuery() = runBlocking {
        val r1 = root("r1")
        put(File(r1.path), "a.jpg", 1000L)
        var called = false
        val out = loadCategoryFiles(
            listOf(r1), Category.IMAGE, showHidden = true, locked = emptySet(),
            mediaQuery = {
                called = true
                listOf(FEntry("/x/y.jpg", "y.jpg", false, 1L, 9L, Category.IMAGE))
            }
        )
        assertEquals(listOf("a.jpg"), out.map { it.name })
        assertFalse(called)
    }

    @Test
    fun mediaOps_entryFromRow_mapsFields() {
        val e = MediaStoreOps.entryFromMediaRow("/sdcard/DCIM/a.jpg", 1234L, 1700000000L, Category.IMAGE)
        assertEquals("/sdcard/DCIM/a.jpg", e.path)
        assertEquals("a.jpg", e.name)
        assertEquals(1234L, e.size)
        assertEquals(1700000000L * 1000L, e.modified)
        assertEquals(Category.IMAGE, e.category)
        assertFalse(e.isDir)
    }

    @Test
    fun mediaOps_isMediaCategory() {
        assertTrue(MediaStoreOps.isMediaCategory(Category.IMAGE))
        assertTrue(MediaStoreOps.isMediaCategory(Category.VIDEO))
        assertTrue(MediaStoreOps.isMediaCategory(Category.AUDIO))
        assertFalse(MediaStoreOps.isMediaCategory(Category.DOCUMENT))
        assertFalse(MediaStoreOps.isMediaCategory(Category.ARCHIVE))
    }

    @Test
    fun mediaOps_isLockedPath() {
        assertTrue(MediaStoreOps.isLockedPath("/sdcard/Rahasia/foto.jpg", setOf("/sdcard/Rahasia")))
        assertFalse(MediaStoreOps.isLockedPath("/sdcard/Rahasia2/foto.jpg", setOf("/sdcard/Rahasia")))
        assertFalse(MediaStoreOps.isLockedPath("/sdcard/DCIM/foto.jpg", setOf("/sdcard/Rahasia")))
    }
}
