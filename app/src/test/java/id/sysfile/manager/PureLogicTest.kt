package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for the pure (Android-free) logic in FileOps / ArchiveOps.
 * These run on the JVM in CI before the APK is built, so routing and
 * formatting regressions are caught before they reach a device.
 */
class PureLogicTest {

    // --- FileOps property formatting (Properties dialog, reference style) --

    @Test
    fun formatPerms_fileLikeReferenceScreenshot() {
        // 0100770 = regular file, rwxrwx--- -> the BiTGApps zip in the reference.
        assertEquals("-rwxrwx---(770)", FileOps.formatPerms(0x81F8))
        assertEquals("drwxr-xr-x(755)", FileOps.formatPerms(0x41ED))
        assertEquals("lrwxrwxrwx(777)", FileOps.formatPerms(0xA1FF))
        assertEquals("-rw-r--r--(644)", FileOps.formatPerms(0x81A4))
    }

    @Test
    fun ownerAndGroupNames_androidConventions() {
        assertEquals("u0_a277", FileOps.ownerName(10277))
        assertEquals("root", FileOps.ownerName(0))
        assertEquals("system", FileOps.ownerName(1000))
        assertEquals("media_rw", FileOps.groupName(1023))
        assertEquals("sdcard_rw", FileOps.groupName(1015))
        assertEquals("9999", FileOps.groupName(9999))
    }

    @Test
    fun formatSizeExact_referenceStyle() {
        assertEquals("301.92M (316591162)", FileOps.formatSizeExact(316591162L))
        assertEquals("512 B (512)", FileOps.formatSizeExact(512L))
        assertEquals("1.50K (1536)", FileOps.formatSizeExact(1536L))
        assertEquals("2.00G (2147483648)", FileOps.formatSizeExact(2147483648L))
    }


    @get:Rule
    val tmp = TemporaryFolder()

    // --- ArchiveOps.kindOf: archive routing -------------------------------



    @Test
    fun kindOf_plainFormats() {
        assertEquals("tar", ArchiveOps.kindOf("a.tar"))
        assertEquals("zip", ArchiveOps.kindOf("a.zip"))
        assertEquals("7z", ArchiveOps.kindOf("a.7z"))
        assertEquals("rar", ArchiveOps.kindOf("a.rar"))
        // Tarball terkompresi + aliran tunggal dihapus: bukan lagi jenis yang didukung.
        assertNull(ArchiveOps.kindOf("backup.tar.gz"))
        assertNull(ArchiveOps.kindOf("a.tgz"))
        assertNull(ArchiveOps.kindOf("a.tbz2"))
        assertNull(ArchiveOps.kindOf("a.txz"))
        assertNull(ArchiveOps.kindOf("a.gz"))
        assertNull(ArchiveOps.kindOf("a.bz2"))
        assertNull(ArchiveOps.kindOf("a.xz"))
    }

    @Test
    fun kindOf_apkFamilyIsZip() {
        assertEquals("zip", ArchiveOps.kindOf("app.apk"))
        assertEquals("zip", ArchiveOps.kindOf("app.apks"))
        assertEquals("zip", ArchiveOps.kindOf("app.xapk"))
        assertEquals("zip", ArchiveOps.kindOf("app.aab"))
    }

    @Test
    fun kindOf_caseInsensitiveAndRejectsOthers() {
        assertNull(ArchiveOps.kindOf("BACKUP.TGZ"))
        assertEquals("zip", ArchiveOps.kindOf("A.ZIP"))
        assertNull(ArchiveOps.kindOf("photo.jpg"))
        assertNull(ArchiveOps.kindOf("archive.tar.gz.bak"))
        assertNull(ArchiveOps.kindOf("noextension"))
    }

    // --- FileOps.categoryOf / extOf: category routing ----------------------

    @Test
    fun categoryOf_keptArchivesAreArchives() {
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.zip"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.tar"))
        // Tarball terkompresi + aliran tunggal dihapus: bukan lagi arsip.
        assertEquals(Category.OTHER, FileOps.categoryOf("a.tgz"))
        assertEquals(Category.OTHER, FileOps.categoryOf("a.tbz2"))
        assertEquals(Category.OTHER, FileOps.categoryOf("a.txz"))
        assertEquals(Category.OTHER, FileOps.categoryOf("a.gz"))
        assertEquals(Category.OTHER, FileOps.categoryOf("a.bz2"))
        assertEquals(Category.OTHER, FileOps.categoryOf("a.xz"))
    }

    @Test
    fun categoryOf_mediaAndDocs() {
        assertEquals(Category.IMAGE, FileOps.categoryOf("IMG_0001.JPG"))
        assertEquals(Category.VIDEO, FileOps.categoryOf("clip.mp4"))
        assertEquals(Category.AUDIO, FileOps.categoryOf("song.flac"))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("report.pdf"))
        assertEquals(Category.APP, FileOps.categoryOf("app.apk"))
        assertEquals(Category.APP, FileOps.categoryOf("app.xapk"))
        assertEquals(Category.OTHER, FileOps.categoryOf("firmware.bin"))
        assertEquals(Category.OTHER, FileOps.categoryOf("noextension"))
    }

    @Test
    fun extOf_edgeCases() {
        assertEquals("gz", FileOps.extOf("A.Tar.GZ"))
        assertEquals("", FileOps.extOf(".hidden"))
        assertEquals("", FileOps.extOf("file."))
        assertEquals("", FileOps.extOf("noextension"))
    }

    // --- FileOps.formatSize -------------------------------------------------

    @Test
    fun formatSize_scales() {
        assertEquals("0 B", FileOps.formatSize(0))
        assertEquals("512 B", FileOps.formatSize(512))
        assertEquals("1.0 KB", FileOps.formatSize(1024))
        assertEquals("1.5 KB", FileOps.formatSize(1536))
        assertEquals("1.0 MB", FileOps.formatSize(1024L * 1024))
        assertEquals("1.0 GB", FileOps.formatSize(1024L * 1024 * 1024))
        assertEquals("1.0 TB", FileOps.formatSize(1024L * 1024 * 1024 * 1024))
    }

    // --- FileOps.uniqueName --------------------------------------------------

    @Test
    fun uniqueName_freeNamePassesThrough() {
        val dir = tmp.newFolder("free")
        assertEquals("a.txt", FileOps.uniqueName(dir, "a.txt"))
    }

    @Test
    fun uniqueName_collisionsGetNumbered() {
        val dir = tmp.newFolder("busy")
        File(dir, "a.txt").createNewFile()
        assertEquals("a (1).txt", FileOps.uniqueName(dir, "a.txt"))
        File(dir, "a (1).txt").createNewFile()
        assertEquals("a (2).txt", FileOps.uniqueName(dir, "a.txt"))
    }

    @Test
    fun permPreview_editorCases() {
        // Kasus tangkapan layar referensi: rw-rw---- (660).
        assertEquals("rw-rw---- (660)", FileOps.permPreview(0x1B0))
        assertEquals("rwsr-xr-x (4755)", FileOps.permPreview(0x9ED))
        assertEquals("rwxrwxrwt (1777)", FileOps.permPreview(0x3FF))
        assertEquals("--------- (0)", FileOps.permPreview(0))
    }

    @Test
    fun uniqueName_noExtension() {
        val dir = tmp.newFolder("noext")
        File(dir, "README").createNewFile()
        assertEquals("README (1)", FileOps.uniqueName(dir, "README"))
    }

    // --- mediaSiblings (geser penampil foto/video) ---

    private fun ent(path: String, cat: Category) =
        FEntry(path, File(path).name, false, 0L, 0L, cat)

    @Test
    fun mediaSiblings_hanyaSejenis_danIndeksBenar() {
        val list = listOf(
            ent("/a/foto1.jpg", Category.IMAGE),
            ent("/a/klip.mp4", Category.VIDEO),
            ent("/a/foto2.png", Category.IMAGE),
            ent("/a/foto3.jpg", Category.IMAGE)
        )
        val (paths, idx) = FileOps.mediaSiblings(list, list[2])
        assertEquals(listOf("/a/foto1.jpg", "/a/foto2.png", "/a/foto3.jpg"), paths)
        assertEquals(1, idx)
    }

    @Test
    fun mediaSiblings_tidakDiDaftar_kembalikanDirinya() {
        val list = listOf(ent("/a/foto1.jpg", Category.IMAGE))
        val (paths, idx) = FileOps.mediaSiblings(list, ent("/b/foto9.jpg", Category.IMAGE))
        assertEquals(listOf("/b/foto9.jpg"), paths)
        assertEquals(0, idx)
    }

    // --- BR4 (r161): angka kartu hero selalu dari TERPAKAI ---

    @Test
    fun heroCardData_internalUsed() {
        val d = heroCardData(RootInfo("/storage/emulated/0", "internal", 1000L, 400L))
        assertEquals(600L, d.usedBytes)
        assertEquals(400L, d.freeBytes)
        assertEquals(1000L, d.totalBytes)
        assertEquals(0.6f, d.usedFraction, 0.001f)
        assertEquals(60.0, d.usedPercent, 0.001)
    }

    @Test
    fun heroCardData_rootFreeBecomesUsed() {
        // Root dulu tampil "tersisa"; kini terpakai seperti Internal.
        val d = heroCardData(RootInfo("/", "root", 1900L, 816L))
        assertEquals(1084L, d.usedBytes)
        assertEquals(0.5705f, d.usedFraction, 0.001f)
        assertEquals(57.0526, d.usedPercent, 0.001)
    }

    @Test
    fun heroCardData_zeroTotal() {
        val d = heroCardData(RootInfo("/x", "usb", 0L, 0L))
        assertEquals(0L, d.usedBytes)
        assertEquals(0f, d.usedFraction, 0.0f)
        assertEquals(0.0, d.usedPercent, 0.0)
    }

    @Test
    fun heroCardData_freeExceedsTotalNeverNegative() {
        val d = heroCardData(RootInfo("/x", "sd", 100L, 150L))
        assertEquals(0L, d.usedBytes)
        assertEquals(100L, d.freeBytes)
        assertEquals(0f, d.usedFraction, 0.0f)
        assertEquals(0.0, d.usedPercent, 0.0)
    }

    @Test
    fun heroRoots_onlyKnownTotalsInOrder() {
        val roots = listOf(
            RootInfo("/a", "internal", 100L, 40L),
            RootInfo("/b", "usb", 0L, 0L),
            RootInfo("/c", "root", 200L, 50L)
        )
        assertEquals(listOf(roots[0], roots[2]), heroRoots(roots))
    }
}
