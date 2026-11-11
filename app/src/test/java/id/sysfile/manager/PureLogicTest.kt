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
    fun kindOf_compoundTarNamesWinOverSingleStreams() {
        assertEquals("tar.gz", ArchiveOps.kindOf("backup.tar.gz"))
        assertEquals("tar.bz2", ArchiveOps.kindOf("backup.tar.bz2"))
        assertEquals("tar.xz", ArchiveOps.kindOf("backup.tar.xz"))
    }

    @Test
    fun kindOf_shortAliases() {
        // Regression: .tgz/.tbz2/.txz once fell through to a plain stream.
        assertEquals("tar.gz", ArchiveOps.kindOf("a.tgz"))
        assertEquals("tar.bz2", ArchiveOps.kindOf("a.tbz2"))
        assertEquals("tar.xz", ArchiveOps.kindOf("a.txz"))
    }

    @Test
    fun kindOf_plainFormats() {
        assertEquals("tar", ArchiveOps.kindOf("a.tar"))
        assertEquals("zip", ArchiveOps.kindOf("a.zip"))
        assertEquals("7z", ArchiveOps.kindOf("a.7z"))
        assertEquals("rar", ArchiveOps.kindOf("a.rar"))
        assertEquals("gz", ArchiveOps.kindOf("a.gz"))
        assertEquals("bz2", ArchiveOps.kindOf("a.bz2"))
        assertEquals("xz", ArchiveOps.kindOf("a.xz"))
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
        assertEquals("tar.gz", ArchiveOps.kindOf("BACKUP.TGZ"))
        assertEquals("zip", ArchiveOps.kindOf("A.ZIP"))
        assertNull(ArchiveOps.kindOf("photo.jpg"))
        assertNull(ArchiveOps.kindOf("archive.tar.gz.bak"))
        assertNull(ArchiveOps.kindOf("noextension"))
    }

    // --- FileOps.categoryOf / extOf: category routing ----------------------

    @Test
    fun categoryOf_archiveAliasesAreArchives() {
        // Regression: .tgz/.tbz2/.txz used to be categorised as OTHER.
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.tgz"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.tbz2"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.txz"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.zip"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("a.tar"))
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
    fun uniqueName_noExtension() {
        val dir = tmp.newFolder("noext")
        File(dir, "README").createNewFile()
        assertEquals("README (1)", FileOps.uniqueName(dir, "README"))
    }
}
