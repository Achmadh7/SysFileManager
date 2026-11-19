package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for the file operations in FileOps / ArchiveOps (copy/move/delete/
 * extract). JVM-only: TemporaryFolder + real files, no Android APIs. These cover
 * the audit findings B1 (data loss on failed copy), C1 (zip-slip) and C2 (limits).
 */
class FileOpsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun folderWithFiles(dir: File) {
        File(dir, "a.txt").writeText("hello")
        val sub = File(dir, "sub").apply { mkdirs() }
        File(sub, "b.txt").writeText("world")
    }

    private fun makeZip(file: File, entries: List<Pair<String, String>>) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
    }

    // --- copyInto / moveInto (B1: kegagalan anak harus menggagalkan semuanya) ---

    @Test
    fun copyInto_folderCopiesAll() {
        val src = tmp.newFolder("src")
        folderWithFiles(src)
        val dest = tmp.newFolder("dest")
        assertEquals(1, FileOps.copyInto(listOf(src), dest))
        assertEquals("hello", File(dest, "src/a.txt").readText())
        assertEquals("world", File(dest, "src/sub/b.txt").readText())
        assertTrue(File(src, "a.txt").exists()) // sumber tetap
    }

    @Test
    fun copyInto_failingChildReturnsZeroAndCleansDest() {
        val src = tmp.newFolder("srcbad")
        File(src, "ok.txt").writeText("ok")
        // Tautan simbolik rusak: dibuka sebagai berkas -> gagal. Deterministik dan
        // tidak bergantung pada izin pengguna (seperti chmod 0 yang lolos bila root).
        Files.createSymbolicLink(File(src, "badlink").toPath(), File(src, "tidak-ada").toPath())
        val dest = tmp.newFolder("destbad")
        assertEquals(0, FileOps.copyInto(listOf(src), dest))
        assertTrue(File(src, "ok.txt").exists())
        assertFalse(File(dest, "srcbad").exists()) // dest parsial dibersihkan
    }

    @Test
    fun moveInto_folderMovesAndDeletesSource() {
        val src = tmp.newFolder("mvsrc")
        folderWithFiles(src)
        val dest = tmp.newFolder("mvdest")
        assertEquals(1, FileOps.moveInto(listOf(src), dest))
        assertFalse(src.exists())
        assertEquals("hello", File(dest, "mvsrc/a.txt").readText())
    }

    @Test
    fun moveInto_brokenSymlinkMovesViaRename() {
        // renameTo (jalur cepat moveInto) memindahkan folder utuh termasuk
        // tautan rusak — rename tidak membaca target tautan, jadi tidak gagal.
        val src = tmp.newFolder("mvbad")
        File(src, "ok.txt").writeText("ok")
        Files.createSymbolicLink(File(src, "badlink").toPath(), File(src, "tidak-ada").toPath())
        val dest = tmp.newFolder("mvbdest")
        assertEquals(1, FileOps.moveInto(listOf(src), dest))
        assertFalse(src.exists())
        val moved = File(dest, "mvbad")
        assertTrue(moved.exists())
        assertEquals("ok", File(moved, "ok.txt").readText())
        assertTrue(Files.isSymbolicLink(File(moved, "badlink").toPath()))
    }

    @Test
    fun moveInto_copyFailureKeepsSource() {
        // destDir yang tidak ada membuat renameTo gagal -> jalur salin
        // berjalan -> tautan rusak menggagalkan salinan (B1 untuk moveInto).
        val src = tmp.newFolder("mvbad2")
        File(src, "ok.txt").writeText("ok")
        Files.createSymbolicLink(File(src, "badlink").toPath(), File(src, "tidak-ada").toPath())
        val dest = File(tmp.root, "tidak-ada-dest") // sengaja tidak dibuat
        assertEquals(0, FileOps.moveInto(listOf(src), dest))
        assertTrue(src.exists())
        assertTrue(File(src, "ok.txt").exists()) // sumber TIDAK dihapus (B1)
        assertFalse(File(dest, "mvbad2").exists()) // dest parsial dibersihkan
    }

    @Test
    fun deleteRecursively_removesTree() {
        val dir = tmp.newFolder("del")
        folderWithFiles(dir)
        assertTrue(FileOps.deleteRecursively(dir))
        assertFalse(dir.exists())
    }

    // --- isPathInside (C1: penjaga zip-slip) ---

    @Test
    fun isPathInside_cases() {
        val dest = "/storage/Download/out"
        assertTrue(FileOps.isPathInside(dest, "/storage/Download/out/a.txt"))
        assertTrue(FileOps.isPathInside(dest, "/storage/Download/out"))
        assertTrue(FileOps.isPathInside(dest, "/storage/Download/out/sub/b.txt"))
        // Kasus audit: sepupu dengan awalan sama harus DITOLAK.
        assertFalse(FileOps.isPathInside(dest, "/storage/Download/out2/evil.sh"))
        assertFalse(FileOps.isPathInside(dest, "/storage/Download/outside.txt"))
        assertFalse(FileOps.isPathInside(dest, "/etc/passwd"))
        assertFalse(FileOps.isPathInside(dest, "/storage/Download/other"))
        // Akar: semua di bawah "/" berada di dalam.
        assertTrue(FileOps.isPathInside("/", "/etc/passwd"))
    }

    // --- ExtractBudget (C2: batas entri + byte) ---

    @Test
    fun extractBudget_entryCap() {
        val b = ExtractBudget()
        repeat(FileOps.MAX_ARCHIVE_ENTRIES) { assertTrue(b.allowEntry()) }
        assertFalse(b.allowEntry())
        assertEquals(FileOps.MAX_ARCHIVE_ENTRIES + 1, b.entries)
    }

    @Test
    fun extractBudget_byteCap() {
        val b = ExtractBudget()
        assertTrue(b.addBytes(100))
        assertTrue(b.addBytes(200))
        assertEquals(300L, b.bytes)
        assertTrue(b.addBytes(FileOps.MAX_EXTRACT_BYTES - 300L))
        assertEquals(FileOps.MAX_EXTRACT_BYTES, b.bytes)
        assertFalse(b.addBytes(1))
    }

    // --- unzip (C1 + C2 + E3) ---

    @Test
    fun unzip_normalExtracts() {
        val zip = File(tmp.root, "ok.zip")
        makeZip(zip, listOf("a.txt" to "hello", "sub/b.txt" to "world"))
        val dest = tmp.newFolder("zout")
        assertTrue(FileOps.unzip(zip, dest))
        assertEquals("hello", File(dest, "a.txt").readText())
        assertEquals("world", File(dest, "sub/b.txt").readText())
    }

    @Test
    fun unzip_zipSlipSiblingRejectedAndCleaned() {
        val zip = File(tmp.root, "evil.zip")
        // Entri bagus dulu (tertulis), lalu entri jahat -> abort + cleanup (E3).
        makeZip(zip, listOf("good.txt" to "good", "../out2/evil.txt" to "x"))
        val dest = tmp.newFolder("out")
        assertFalse(FileOps.unzip(zip, dest))
        assertFalse(File(tmp.root, "out2/evil.txt").exists())
        assertFalse(File(dest, "good.txt").exists()) // tertulis lalu dibersihkan
    }

    @Test
    fun unzip_zipSlipDotDotRejected() {
        val zip = File(tmp.root, "evil2.zip")
        makeZip(zip, listOf("sub/../../out2/evil.txt" to "x"))
        val dest = tmp.newFolder("out2")
        assertFalse(FileOps.unzip(zip, dest))
        assertFalse(File(tmp.root, "out2/evil.txt").exists())
    }

    @Test
    fun unzip_zipSlipAbsoluteRejected() {
        val zip = File(tmp.root, "evil3.zip")
        val outside = File(tmp.root, "evil-abs.txt").absolutePath
        makeZip(zip, listOf(outside to "x"))
        val dest = tmp.newFolder("out3")
        assertFalse(FileOps.unzip(zip, dest))
        assertFalse(File(tmp.root, "evil-abs.txt").exists())
    }

    @Test
    fun unzip_entryCapRejected() {
        val zip = File(tmp.root, "many.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            for (i in 0..FileOps.MAX_ARCHIVE_ENTRIES) {
                zos.putNextEntry(ZipEntry("f$i.txt"))
                zos.write("x".toByteArray())
                zos.closeEntry()
            }
        }
        val dest = tmp.newFolder("manyout")
        assertFalse(FileOps.unzip(zip, dest))
    }

    @Test
    fun listZipContents_entryCapReturnsNull() {
        val zip = File(tmp.root, "many2.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            for (i in 0..FileOps.MAX_ARCHIVE_ENTRIES) {
                zos.putNextEntry(ZipEntry("f$i.txt"))
                zos.closeEntry()
            }
        }
        assertNull(ArchiveOps.listZipContents(zip))
    }

    // --- tar / 7z (C1: penjaga yang sama, jalur kode berbeda) ---

    @Test
    fun extractTar_zipSlipRejected() {
        val archive = File(tmp.root, "evil.tar")
        org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(FileOutputStream(archive)).use { tos ->
            val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("../evil-tar.txt")
            entry.size = 3
            tos.putArchiveEntry(entry)
            tos.write("xxx".toByteArray())
            tos.closeArchiveEntry()
        }
        val dest = tmp.newFolder("tarout")
        assertFalse(ArchiveOps.extract(archive, dest))
        assertFalse(File(tmp.root, "evil-tar.txt").exists())
    }

    @Test
    fun extractTar_normalExtracts() {
        val archive = File(tmp.root, "ok.tar")
        org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(FileOutputStream(archive)).use { tos ->
            val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("a.txt")
            entry.size = 5
            tos.putArchiveEntry(entry)
            tos.write("hello".toByteArray())
            tos.closeArchiveEntry()
        }
        val dest = tmp.newFolder("tarok")
        assertTrue(ArchiveOps.extract(archive, dest))
        assertEquals("hello", File(dest, "a.txt").readText())
    }

    @Test
    fun extract7z_zipSlipRejected() {
        val archive = File(tmp.root, "evil.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive).use { seven ->
            val entry = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
            entry.name = "../evil-7z.txt"
            seven.putArchiveEntry(entry)
            seven.write("xxx".toByteArray())
            seven.closeArchiveEntry()
        }
        val dest = tmp.newFolder("sevenout")
        assertFalse(ArchiveOps.extract(archive, dest))
        assertFalse(File(tmp.root, "evil-7z.txt").exists())
    }

    @Test
    fun extract7z_normalExtracts() {
        val archive = File(tmp.root, "ok.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive).use { seven ->
            val entry = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
            entry.name = "a.txt"
            seven.putArchiveEntry(entry)
            seven.write("hello".toByteArray())
            seven.closeArchiveEntry()
        }
        val dest = tmp.newFolder("sevenok")
        assertTrue(ArchiveOps.extract(archive, dest))
        assertEquals("hello", File(dest, "a.txt").readText())
    }

    // --- .obb: kontainer ZIP (r102) ---

    @Test
    fun obb_kindIsZipAndCategoryIsArchive() {
        assertEquals("zip", ArchiveOps.kindOf("main.123.com.game.obb"))
        assertEquals("zip", ArchiveOps.kindOf("game.OBB"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("main.123.com.game.obb"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("game.OBB"))
    }

    @Test
    fun obb_listsContentsLikeZip() {
        val obb = File(tmp.root, "main.1.com.game.obb")
        makeZip(obb, listOf("assets/a.txt" to "hello", "b.txt" to "world"))
        val items = ArchiveOps.listContents(obb)
        assertEquals(listOf("assets/a.txt", "b.txt"), items?.map { it.name })
    }

    @Test
    fun jar_kindIsZipAndCategoryIsArchive() {
        assertEquals("zip", ArchiveOps.kindOf("app.jar"))
        assertEquals("zip", ArchiveOps.kindOf("lib.JAR"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("app.jar"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("lib.JAR"))
        assertEquals("app", ArchiveOps.archiveBaseName("app.jar"))
    }

    @Test
    fun jar_listsContentsLikeZip() {
        val jar = File(tmp.root, "app.jar")
        makeZip(jar, listOf("META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n", "com/example/Main.class" to "cafebabe"))
        val items = ArchiveOps.listContents(jar)
        assertEquals(listOf("META-INF/MANIFEST.MF", "com/example/Main.class"), items?.map { it.name })
    }

    @Test
    fun archiveKinds_unchanged() {
        assertEquals("zip", ArchiveOps.kindOf("game.zip"))
        assertEquals("7z", ArchiveOps.kindOf("game.7z"))
        assertEquals("rar", ArchiveOps.kindOf("game.rar"))
        assertEquals("tar", ArchiveOps.kindOf("game.tar"))
        assertNull(ArchiveOps.kindOf("game.txt"))
        assertEquals(Category.ARCHIVE, FileOps.categoryOf("game.zip"))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("game.txt"))
    }

    // --- readEntryText: baca teks dari ZIP ke memori (r103) ---

    @Test
    fun readEntryText_zipHtmlReturnsContent() {
        val zip = File(tmp.root, "web.zip")
        makeZip(zip, listOf("index.html" to "<html><body>halo</body></html>", "a.txt" to "hello"))
        assertEquals("<html><body>halo</body></html>", ArchiveOps.readEntryText(zip, "index.html"))
        assertEquals("hello", ArchiveOps.readEntryText(zip, "a.txt"))
    }

    @Test
    fun readEntryText_missingAndDirectoryReturnNull() {
        val zip = File(tmp.root, "web2.zip")
        makeZip(zip, listOf("dir/" to "", "a.txt" to "hello"))
        assertNull(ArchiveOps.readEntryText(zip, "tidak-ada.html"))
        assertNull(ArchiveOps.readEntryText(zip, "dir/"))
    }

    @Test
    fun readEntryText_tooBigReturnsNull() {
        val zip = File(tmp.root, "web3.zip")
        makeZip(zip, listOf("big.html" to "x".repeat(100)))
        assertNull(ArchiveOps.readEntryText(zip, "big.html", maxBytes = 10))
        assertEquals("x".repeat(100), ArchiveOps.readEntryText(zip, "big.html", maxBytes = 100))
    }

    @Test
    fun readEntryText_nonZipReturnsNull() {
        val txt = File(tmp.root, "plain.txt")
        txt.writeText("hello")
        assertNull(ArchiveOps.readEntryText(txt, "plain.txt"))
    }

    @Test
    fun isTextFile_toml_returnsTrueAndIsDocument() {
        assertTrue(FileOps.isTextFile(File("cargo.toml")))
        assertTrue(FileOps.isTextFile(File("CONFIG.TOML")))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("cargo.toml"))
        assertFalse(FileOps.isTextFile(File("foto.png")))
    }

    @Test
    fun isTextFile_propConfigConf_returnsTrue() {
        assertTrue(FileOps.isTextFile(File("build.prop")))
        assertTrue(FileOps.isTextFile(File("app.config")))
        assertTrue(FileOps.isTextFile(File("server.conf")))
        assertTrue(FileOps.isTextFile(File("APP.CONF")))
        assertFalse(FileOps.isTextFile(File("foto.png")))
    }

    @Test
    fun isRestrictedDir_androidDataObb() {
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/data"))
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/data/com.example"))
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/obb"))
        assertTrue(FileOps.isRestrictedDir("/storage/ABCD-1234/Android/data/com.example/files"))
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Download"))
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Android"))
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Android/media"))
    }
}
