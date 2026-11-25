package id.sysfile.manager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

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

    // --- deleteRecursively (r175 H1: symlink ke direktori jangan diikuti) ---

    @Test
    fun deleteRecursively_symlinkToDirDoesNotDeleteTarget() {
        val victim = tmp.newFolder("victim")
        File(victim, "important.txt").writeText("jangan hapus")
        val dir = tmp.newFolder("del2")
        Files.createSymbolicLink(File(dir, "link").toPath(), victim.toPath())
        assertTrue(FileOps.deleteRecursively(dir))
        assertFalse(dir.exists())
        assertTrue(File(victim, "important.txt").exists()) // target selamat
    }

    @Test
    fun deleteRecursively_symlinkItselfIsDeleted() {
        val victim = tmp.newFolder("victim2")
        File(victim, "f.txt").writeText("x")
        val link = File(tmp.root, "linkfile")
        Files.createSymbolicLink(link.toPath(), victim.toPath())
        assertTrue(FileOps.deleteRecursively(link))
        assertFalse(link.exists())
        assertTrue(File(victim, "f.txt").exists())
    }

    @Test
    fun deleteRecursively_brokenSymlink() {
        val dir = tmp.newFolder("del3")
        Files.createSymbolicLink(
            File(dir, "broken").toPath(),
            File(tmp.root, "tidak-ada-target").toPath()
        )
        assertTrue(FileOps.deleteRecursively(dir))
        assertFalse(dir.exists())
    }

    // --- isLockedPath / canonicalizeLocked / isLockedFolder (r175 H4) ---

    @Test
    fun isLockedPath_aliasViaSymlink() {
        val real = tmp.newFolder("Rahasia")
        val aliasDir = tmp.newFolder("alias")
        val alias = File(aliasDir, "Rahasia")
        Files.createSymbolicLink(alias.toPath(), real.toPath())
        val locked = setOf(real.absolutePath)
        assertTrue(FileOps.isLockedPath(alias.absolutePath, locked))
        assertTrue(FileOps.isLockedPath(File(alias, "foto.jpg").absolutePath, locked))
        assertFalse(FileOps.isLockedPath(File(tmp.root, "Lain").absolutePath, locked))
        // Sepupu dengan awalan sama bukan anaknya.
        assertFalse(FileOps.isLockedPath(real.absolutePath + "2", locked))
    }

    @Test
    fun canonicalizeLocked_keepsRawAndAddsCanonical() {
        val real = tmp.newFolder("Priv")
        val alias = File(tmp.root, "PrivAlias")
        Files.createSymbolicLink(alias.toPath(), real.toPath())
        val out = FileOps.canonicalizeLocked(setOf(alias.absolutePath))
        assertTrue(out.contains(alias.absolutePath))
        assertTrue(out.contains(real.canonicalPath))
    }

    @Test
    fun isLockedFolder_identityOnly() {
        val real = tmp.newFolder("Kunci")
        val alias = File(tmp.root, "KunciAlias")
        Files.createSymbolicLink(alias.toPath(), real.toPath())
        val locked = FileOps.canonicalizeLocked(setOf(real.absolutePath))
        assertTrue(FileOps.isLockedFolder(real.absolutePath, locked))
        assertTrue(FileOps.isLockedFolder(alias.absolutePath, locked))
        // Anak folder terkunci BUKAN folder terkunci itu sendiri.
        assertFalse(FileOps.isLockedFolder(File(real, "sub").absolutePath, locked))
        assertFalse(FileOps.isLockedFolder(File(tmp.root, "Lain").absolutePath, locked))
    }

    @Test
    fun isLockedPath_childOfLocked() {
        val real = tmp.newFolder("Induk")
        val locked = setOf(real.absolutePath)
        assertTrue(FileOps.isLockedPath(File(real, "sub/f.txt").absolutePath, locked))
        assertTrue(FileOps.isLockedPath(real.absolutePath, locked))
        assertFalse(FileOps.isLockedPath(File(tmp.root, "Lain").absolutePath, locked))
        assertFalse(FileOps.isLockedPath(File(real, "sub").absolutePath, emptySet()))
    }

    @Test
    fun isUnlockedPath_ancestorAware() {
        val real = tmp.newFolder("Buka")
        val unlocked = setOf(real.absolutePath)
        assertTrue(FileOps.isUnlockedPath(File(real, "sub/f.txt").absolutePath, unlocked))
        assertTrue(FileOps.isUnlockedPath(real.absolutePath, unlocked))
        assertFalse(FileOps.isUnlockedPath(File(tmp.root, "Lain/f.txt").absolutePath, unlocked))
        assertFalse(FileOps.isUnlockedPath(File(real, "sub").absolutePath, emptySet()))
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

    // --- r179: L14 / L23 / L7 / L1 / L2 ---

    @Test
    fun isUsbVolume_multiSpaceAndUnresolvableFirstMatchContinues() {
        // L14: baris mounts ber-spasi ganda + match pertama yang sysfs-nya
        // tak bisa di-resolve tidak menghentikan scan.
        val mounts = "8,33  /mnt/media_rw/ABCD-1234   vfat rw,relatime 0 0\n" +
            "8,34  /storage/ABCD-1234  vfat rw,relatime 0 0\n"
        val resolved = mapOf<Pair<Int, Int>, String?>(
            (8 to 33) to null,
            (8 to 34) to "/sys/devices/pci0000:00/0000:00:14.0/usb1/8:0:0:0/block/sda"
        )
        assertTrue(FileOps.isUsbVolume("/storage/ABCD-1234", mounts) { major, minor ->
            resolved[major to minor]
        })
    }

    @Test
    fun isUsbVolume_noUsbSegmentReturnsFalse() {
        val mounts = "179,33  /mnt/media_rw/ABCD-1234  vfat rw 0 0\n"
        assertFalse(FileOps.isUsbVolume("/storage/ABCD-1234", mounts) { _, _ ->
            "/sys/devices/platform/soc/mmc_host/mmc0/block/mmcblk0"
        })
    }

    @Test
    fun folderStats_symlinkCycleVisitedOnce() {
        // L23: siklus symlink (sub/loop -> dir) tidak lagi dikunjungi
        // berulang kali — berkas dihitung sekali; direktori loop-nya
        // sendiri tidak dihitung (onEnter false = entri dilewati walk).
        val dir = tmp.newFolder("cyc")
        val sub = File(dir, "sub").apply { mkdirs() }
        File(sub, "f.txt").writeText("hello")
        Files.createSymbolicLink(File(sub, "loop").toPath(), dir.toPath())
        val stats = FileOps.folderStats(dir)
        assertEquals(1, stats.fileCount)
        assertEquals(5L, stats.totalSize)
        assertEquals(1, stats.dirCount)
    }

    @Test
    fun search_symlinkCycleReturnsOnce() {
        val dir = tmp.newFolder("cyc2")
        val sub = File(dir, "sub").apply { mkdirs() }
        File(sub, "target.txt").writeText("hello")
        Files.createSymbolicLink(File(sub, "loop").toPath(), dir.toPath())
        val results = FileOps.search(dir, "target", showHidden = false)
        assertEquals(1, results.size)
        assertEquals("target.txt", results[0].name)
    }

    @Test
    fun unzip_nestedDirsCleanedOnSlip() {
        // L7: direktori yang dibuat selama ekstraksi ikut dihapus bila
        // ekstraksi gagal (dulu hanya berkas yang dibersihkan).
        val zip = File(tmp.root, "nested.zip")
        makeZip(zip, listOf("a/b/c.txt" to "deep", "../evil.txt" to "x"))
        val dest = tmp.newFolder("nestedout")
        assertFalse(FileOps.unzip(zip, dest))
        assertFalse(File(dest, "a").exists())
        assertTrue(dest.listFiles().orEmpty().isEmpty())
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test
    fun noteCreatedDir_recordsNewChainOnly() {
        // L1: hanya rantai direktori yang belum ada yang dicatat.
        val dest = tmp.newFolder("d1")
        val created = mutableListOf<File>()
        FileOps.noteCreatedDir(File(dest, "a/b"), dest.canonicalPath, created)
        assertEquals(listOf(File(dest, "a/b"), File(dest, "a")), created)
        val created2 = mutableListOf<File>()
        FileOps.noteCreatedDir(dest, dest.canonicalPath, created2)
        assertTrue(created2.isEmpty())
    }

    @Test
    fun deleteCreatedDirs_removesDeepestFirst() {
        val dest = tmp.newFolder("d2")
        val deep = File(dest, "a/b/c").apply { mkdirs() }
        val created = mutableListOf(deep, File(dest, "a/b"), File(dest, "a"))
        FileOps.deleteCreatedDirs(created)
        assertFalse(File(dest, "a").exists())
        assertTrue(dest.exists())
    }

    @Test
    fun extractSingle_tarScanCap() {
        // L2: target di posisi 10.001 (di luar cap) -> null; target awal ketemu.
        val tar = File(tmp.root, "many.tar")
        TarArchiveOutputStream(FileOutputStream(tar)).use { tos ->
            for (i in 0..FileOps.MAX_ARCHIVE_ENTRIES) {
                val data = "x".toByteArray()
                val e = TarArchiveEntry("f$i.txt")
                e.size = data.size.toLong()
                tos.putArchiveEntry(e)
                tos.write(data)
                tos.closeArchiveEntry()
            }
        }
        val dest = tmp.newFolder("singleout")
        assertNull(ArchiveOps.extractSingle(tar, "f${FileOps.MAX_ARCHIVE_ENTRIES}.txt", dest))
        val found = ArchiveOps.extractSingle(tar, "f5.txt", dest)
        assertNotNull(found)
        assertEquals("x", found!!.readText())
    }

    // --- extract (LD3 r160: progres + batal) ---

    @Test
    fun extract_progressReportsMonotonicToTotal() {
        val zip = File(tmp.root, "prog.zip")
        makeZip(zip, listOf("a.txt" to "hello", "sub/b.txt" to "world", "c.txt" to "!", "d.txt" to "?"))
        val dest = tmp.newFolder("progout")
        val seen = mutableListOf<Pair<Long, Long>>()
        assertTrue(ArchiveOps.extract(zip, dest, onProgress = { done, total -> seen += done to total }))
        assertEquals(4, seen.size)
        assertTrue(seen.map { it.first }.zipWithNext().all { (a, b) -> b == a + 1 })
        assertEquals(4L to 4L, seen.last())
        assertEquals("hello", File(dest, "a.txt").readText())
    }

    @Test
    fun extract_cancelMidwayDeletesWrittenAndThrows() {
        val zip = File(tmp.root, "cancel.zip")
        makeZip(zip, listOf("a.txt" to "hello", "b.txt" to "world", "c.txt" to "!"))
        val dest = tmp.newFolder("cancelout")
        val flag = AtomicBoolean(false)
        assertThrows(ImgCancelledException::class.java) {
            ArchiveOps.extract(
                zip, dest,
                onProgress = { done, _ -> if (done >= 1L) flag.set(true) },
                cancelFlag = flag
            )
        }
        assertFalse(File(dest, "a.txt").exists())
        assertFalse(File(dest, "b.txt").exists())
    }

    // --- r182: extractToFolder meneruskan progres + batal ke extract ---

    @Test
    fun extractToFolder_reportsEntryProgress() {
        val zip = File(tmp.root, "prog2.zip")
        makeZip(zip, listOf("a.txt" to "hello", "b.txt" to "world"))
        val dest = tmp.newFolder("progout2")
        val seen = mutableListOf<Pair<Long, Long>>()
        assertTrue(ArchiveOps.extractToFolder(zip, dest, onProgress = { done, total -> seen += done to total }))
        assertEquals(listOf(1L to 2L, 2L to 2L), seen)
        assertEquals("hello", File(dest, "prog2/a.txt").readText())
    }

    @Test
    fun extractToFolder_cancelMidwayDeletesWrittenAndThrows() {
        val zip = File(tmp.root, "cancel2.zip")
        makeZip(zip, listOf("a.txt" to "hello", "b.txt" to "world", "c.txt" to "!"))
        val dest = tmp.newFolder("cancelout2")
        val flag = AtomicBoolean(false)
        assertThrows(ImgCancelledException::class.java) {
            ArchiveOps.extractToFolder(
                zip, dest,
                onProgress = { done, _ -> if (done >= 1L) flag.set(true) },
                cancelFlag = flag
            )
        }
        assertFalse(File(dest, "cancel2/a.txt").exists())
        assertFalse(File(dest, "cancel2/b.txt").exists())
    }

    // --- r190: "Ekstrak di sini" selalu ke folder bernama seperti arsip ---

    @Test
    fun extractToFolder_createsFolderNamedAfterArchive() {
        val zip = File(tmp.root, "data.zip")
        makeZip(zip, listOf("a.txt" to "hello", "sub/b.txt" to "world"))
        val dest = tmp.newFolder("hereout")
        assertTrue(ArchiveOps.extractToFolder(zip, dest))
        assertTrue(File(dest, "data").isDirectory)
        assertEquals("hello", File(dest, "data/a.txt").readText())
        assertEquals("world", File(dest, "data/sub/b.txt").readText())
        // tidak ada isi yang bocor langsung ke folder induk
        assertFalse(File(dest, "a.txt").exists())
        assertFalse(File(dest, "sub").exists())
    }

    @Test
    fun extractToFolder_uniqueNameWhenFolderExists() {
        val zip = File(tmp.root, "data.zip")
        makeZip(zip, listOf("a.txt" to "hello"))
        val dest = tmp.newFolder("hereout2")
        val existing = File(dest, "data")
        assertTrue(existing.mkdirs())
        File(existing, "keep.txt").writeText("keep")
        assertTrue(ArchiveOps.extractToFolder(zip, dest))
        assertEquals("keep", File(dest, "data/keep.txt").readText())
        assertEquals("hello", File(dest, "data (1)/a.txt").readText())
    }

    @Test
    fun archiveBaseName_stripsKnownSuffixes() {
        assertEquals("data", ArchiveOps.archiveBaseName("data.zip"))
        assertEquals("data", ArchiveOps.archiveBaseName("data.7z"))
        assertEquals("data", ArchiveOps.archiveBaseName("data.rar"))
        assertEquals("data", ArchiveOps.archiveBaseName("data.tar"))
        assertEquals("game", ArchiveOps.archiveBaseName("game.apk"))
        assertEquals("main.1.com.game", ArchiveOps.archiveBaseName("main.1.com.game.obb"))
        assertEquals("plain", ArchiveOps.archiveBaseName("plain"))
        assertEquals(".zip", ArchiveOps.archiveBaseName(".zip"))
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

    // --- replaceZipEntry (AT1: edit teks di dalam ZIP, simpan kembali) ---

    @Test
    fun replaceZipEntry_replacesTargetKeepsOthers() {
        val zip = File(tmp.root, "mod.zip")
        // Satu entri STORED (agar jalur size/crc teruji) + dua DEFLATED + satu direktori.
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val stored = ZipEntry("data.bin")
            stored.method = ZipEntry.STORED
            stored.size = bytes.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(bytes)
            stored.crc = crc.value
            zos.putNextEntry(stored)
            zos.write(bytes)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("module.prop"))
            zos.write("version=1.0".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("folder/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("folder/customize.sh"))
            zos.write("echo hi".toByteArray())
            zos.closeEntry()
        }
        assertTrue(ArchiveOps.canReplaceEntry(zip))
        assertTrue(ArchiveOps.replaceZipEntry(zip, "module.prop", "version=2.0"))
        java.util.zip.ZipFile(zip).use { z ->
            assertEquals("version=2.0", z.getInputStream(z.getEntry("module.prop")).readBytes().toString(Charsets.UTF_8))
            assertEquals("echo hi", z.getInputStream(z.getEntry("folder/customize.sh")).readBytes().toString(Charsets.UTF_8))
            val bin = z.getEntry("data.bin")
            assertEquals(ZipEntry.STORED, bin.method)
            assertEquals(listOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 5.toByte()),
                z.getInputStream(bin).readBytes().toList())
            assertEquals(4, z.size())
        }
    }

    @Test
    fun replaceZipEntry_missingEntryReturnsFalseAndKeepsArchive() {
        val zip = File(tmp.root, "mod2.zip")
        makeZip(zip, listOf("a.txt" to "hello", "b.txt" to "world"))
        val before = zip.readBytes()
        assertFalse(ArchiveOps.replaceZipEntry(zip, "tidak-ada.txt", "x"))
        assertEquals(before.toList(), zip.readBytes().toList())
        // Tidak ada berkas temp/cadangan yang tertinggal.
        assertTrue(tmp.root.listFiles().orEmpty().none { it.name.startsWith("mod2.zip.tmp-") || it.name.startsWith("mod2.zip.bak-") })
    }

    @Test
    fun replaceZipEntry_nonZipAndEncryptedRejected() {
        val txt = File(tmp.root, "bukan.zip.txt")
        txt.writeText("bukan arsip")
        assertFalse(ArchiveOps.canReplaceEntry(txt))
        assertFalse(ArchiveOps.replaceZipEntry(txt, "a.txt", "x"))
        // APK (kontainer ZIP) sengaja ditolak — menulis ulang merusak tanda tangan.
        val apk = File(tmp.root, "app.apk")
        makeZip(apk, listOf("a.txt" to "hello"))
        assertFalse(ArchiveOps.canReplaceEntry(apk))
        assertFalse(ArchiveOps.replaceZipEntry(apk, "a.txt", "x"))
        // ZIP terenkripsi ditolak.
        val src = tmp.newFolder("encsrc")
        File(src, "a.txt").writeText("rahasia")
        val enc = checkNotNull(ArchiveOps.create(listOf(File(src, "a.txt")), tmp.root, "enc", "zip", "sandi123"))
        assertFalse(ArchiveOps.canReplaceEntry(enc))
        assertFalse(ArchiveOps.replaceZipEntry(enc, "a.txt", "x"))
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
    fun isTextFile_log_returnsTrueAndIsDocument() {
        assertTrue(FileOps.isTextFile(File("app.log")))
        assertTrue(FileOps.isTextFile(File("DEBUG.LOG")))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("app.log"))
        assertFalse(FileOps.isTextFile(File("foto.png")))
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
    fun isTextFile_practicalBatch_returnsTrue() {
        // r148: 5 ekstensi baru + 3 dotfile (dicocokkan dari nama
        // karena extOf mengembalikan "" untuk dotfile).
        assertTrue(FileOps.isTextFile(File("data.tsv")))
        assertTrue(FileOps.isTextFile(File("data.jsonl")))
        assertTrue(FileOps.isTextFile(File("query.sql")))
        assertTrue(FileOps.isTextFile(File("QUERY.SQL")))
        assertTrue(FileOps.isTextFile(File("settings.jsonc")))
        assertTrue(FileOps.isTextFile(File("fix.reg")))
        assertTrue(FileOps.isTextFile(File(".env")))
        assertTrue(FileOps.isTextFile(File(".env.local")))
        assertTrue(FileOps.isTextFile(File(".editorconfig")))
        assertTrue(FileOps.isTextFile(File(".gitignore")))
        assertFalse(FileOps.isTextFile(File("env")))
        assertFalse(FileOps.isTextFile(File("foto.png")))
    }

    @Test
    fun isRestrictedDir_androidDataObb() {
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/data/com.x"))
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/obb/com.x"))
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/data"))
        // M16: sandbox ikut restricted; alias volume ikut dikenali.
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/sandbox"))
        assertTrue(FileOps.isRestrictedDir("/storage/emulated/0/Android/sandbox/com.x"))
        assertTrue(FileOps.isRestrictedDir("/sdcard/Android/obb/com.x"))
        assertTrue(FileOps.isRestrictedDir("/data/media/0/Android/data/com.x"))
        assertTrue(FileOps.isRestrictedDir("/storage/ABCD-1234/Android/sandbox/com.x"))
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Download"))
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Android"))
        // M16: folder pengguna yang kebetulan mengandung
        // "Android/data" BUKAN restricted lagi.
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Backup/Android/data/com.x"))
        assertFalse(FileOps.isRestrictedDir("/storage/emulated/0/Backup/Android/obb/com.x"))
        assertFalse(FileOps.isRestrictedDir("/storage/ABCD-1234/Backup/Android/data"))
    }

    @Test
    fun volumeRelativePath_anchors() {
        assertEquals("Android/data/com.x", FileOps.volumeRelativePath("/storage/emulated/0/Android/data/com.x"))
        assertEquals("Android/obb", FileOps.volumeRelativePath("/sdcard/Android/obb"))
        assertEquals("Download", FileOps.volumeRelativePath("/storage/ABCD-1234/Download"))
        assertEquals("", FileOps.volumeRelativePath("/storage/emulated/0"))
        assertNull(FileOps.volumeRelativePath("/data/data/id.sysfile.manager"))
        // Fallback /storage/<vol>/... memperlakukan "emulated" sebagai
        // nama volume — hasilnya tetap bukan restricted (tidak diawali
        // "Android/"), jadi aman.
        assertEquals("0x/Android/data", FileOps.volumeRelativePath("/storage/emulated/0x/Android/data"))
    }

    @Test
    fun iconBitmapSize_capsLongestSide() {
        assertEquals(100 to 100, FileOps.iconBitmapSize(100, 100))
        assertEquals(512 to 512, FileOps.iconBitmapSize(8192, 8192))
        assertEquals(512 to 256, FileOps.iconBitmapSize(1024, 512))
        assertEquals(25 to 512, FileOps.iconBitmapSize(100, 2000))
        assertEquals(1 to 1, FileOps.iconBitmapSize(0, 0))
        assertEquals(1 to 100, FileOps.iconBitmapSize(-5, 100))
    }

    @Test
    fun tempFileIn_uniqueAndInsideDir() {
        val dir = tmp.newFolder("tmpdir")
        val a = FileOps.tempFileIn(dir)
        val b = FileOps.tempFileIn(dir)
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(a!!.name, b!!.name)
        assertEquals(dir, a.parentFile)
        assertTrue(a.name.startsWith(".sysfile-tmp-"))
        assertFalse(a.exists())
    }

    @Test
    fun evictStaleCache_removesOldAndExcess() {
        val dir = tmp.newFolder("cache")
        val now = System.currentTimeMillis()
        val old = File(dir, "old.txt").also { it.writeText("o"); it.setLastModified(now - 25L * 60L * 60L * 1000L) }
        val fresh = File(dir, "fresh.txt").also { it.writeText("f"); it.setLastModified(now) }
        FileOps.evictStaleCache(dir)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        // 25 entri segar -> hanya 20 terbaru yang dipertahankan.
        for (i in 0 until 25) {
            File(dir, "n$i.txt").also { it.writeText("x"); it.setLastModified(now - i * 1000L) }
        }
        FileOps.evictStaleCache(dir)
        val remaining = dir.listFiles()!!.map { it.name }
        assertEquals(20, remaining.size)
        assertTrue("fresh.txt" in remaining)
        assertTrue("n0.txt" in remaining)
        assertFalse("n24.txt" in remaining)
    }
    // r172: klasifikasi volume USB dari /proc/mounts + sysfs.
    private val usbMounts = "/dev/block/vold/public:8,1 /mnt/media_rw/1234-ABCD vfat rw,dirsync,nosuid,nodev,noexec,noatime 0 0\n" +
        "/dev/fuse /storage/1234-ABCD fuse rw,nosuid,nodev,noexec,noatime 0 0\n" +
        "/dev/block/vold/public:179,1 /mnt/media_rw/9C33-1A2B vfat rw,dirsync,nosuid,nodev,noexec,noatime 0 0\n"
    private val usbSysPath: (Int, Int) -> String? = { major, minor ->
        when (major to minor) {
            8 to 1 -> "/sys/devices/pci0000:00/0000:00:14.0/usb1/1-1/1-1:1.0/host0/target0:0:0/0:0:0:0/block/sda/sda1"
            179 to 1 -> "/sys/devices/platform/soc/1c40000.mmc/mmc_host/mmc0/mmc0:0001/block/mmcblk0/mmcblk0p1"
            else -> null
        }
    }

    @Test
    fun isUsbVolume_usbStick_returnsTrue() {
        assertTrue(FileOps.isUsbVolume("/storage/1234-ABCD", usbMounts, usbSysPath))
    }

    @Test
    fun isUsbVolume_sdCard_returnsFalse() {
        assertFalse(FileOps.isUsbVolume("/storage/9C33-1A2B", usbMounts, usbSysPath))
    }

    @Test
    fun isUsbVolume_noMediaRwLine_returnsFalse() {
        assertFalse(FileOps.isUsbVolume("/storage/FFFF-0000", usbMounts, usbSysPath))
    }

    @Test
    fun isUsbVolume_unresolvableSysPath_returnsFalse() {
        assertFalse(FileOps.isUsbVolume("/storage/1234-ABCD", usbMounts) { _, _ -> null })
    }

    @Test
    fun isUsbVolume_usbSubstringNotSegment_returnsFalse() {
        assertFalse(FileOps.isUsbVolume("/storage/1234-ABCD", usbMounts) { _, _ -> "/sys/devices/platform/soc/usb-hub.0/block/sda/sda1" })
    }


    // --- r177 H7/M1: ukuran deklarasi yang dipalsukan ---

    /** Timpa ukuran uncompressed deklarasi [entryName] di dalam [file]:
     *  header lokal (+22) bila [patchLocal], direktori pusat (+24) bila
     *  [patchCentral]. Dipakai untuk meniru arsip yang berbohong tentang
     *  ukuran entrinya. */
    private fun patchDeclaredSize(file: File, entryName: String, declaredSize: Long, patchLocal: Boolean = true, patchCentral: Boolean = true) {
        val bytes = file.readBytes()
        fun le16At(pos: Int) = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        fun putLe32(pos: Int, v: Long) {
            for (i in 0..3) bytes[pos + i] = ((v ushr (8 * i)) and 0xFF).toByte()
        }
        var i = 0
        while (i + 46 < bytes.size) {
            val isLocal = bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 0x03.toByte() && bytes[i + 3] == 0x04.toByte()
            val isCentral = bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 0x01.toByte() && bytes[i + 3] == 0x02.toByte()
            if ((isLocal && patchLocal) || (isCentral && patchCentral)) {
                val nameOff = if (isLocal) 30 else 46
                val nameLenOff = if (isLocal) 26 else 28
                val nameLen = le16At(i + nameLenOff)
                if (i + nameOff + nameLen <= bytes.size) {
                    val name = String(bytes, i + nameOff, nameLen, Charsets.UTF_8)
                    if (name == entryName) {
                        putLe32(i + if (isLocal) 22 else 24, declaredSize)
                    }
                }
            }
            i++
        }
        file.writeBytes(bytes)
    }

    /** Tulis ZIP dengan entri STORED (ukuran ada di header lokal), lalu
     *  timpa ukuran deklarasi [lyingEntry] menjadi [declaredSize] di
     *  header lokal DAN direktori pusat. */
    private fun makeStoredZipWithLyingSize(file: File, entries: List<Pair<String, String>>, lyingEntry: String, declaredSize: Long) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                val bytes = content.toByteArray()
                val e = ZipEntry(name)
                e.method = ZipEntry.STORED
                e.size = bytes.size.toLong()
                val crc = java.util.zip.CRC32()
                crc.update(bytes)
                e.crc = crc.value
                zos.putNextEntry(e)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        patchDeclaredSize(file, lyingEntry, declaredSize)
    }

    @Test
    fun countingOutputStream_limitThrowsMidWrite() {
        val out = java.io.ByteArrayOutputStream()
        val counting = CountingOutputStream(out, limit = 100L)
        counting.write(ByteArray(60))
        assertEquals(60L, counting.count)
        assertThrows(BudgetExceededException::class.java) { counting.write(ByteArray(60)) }
        assertEquals(60L, counting.count)
        assertEquals(60, out.size())
    }

    @Test
    fun unzip_declaredSizeOverBudgetRejectedAndCleaned() {
        // M1: entri kedua mengklaim 3 GiB — ditolak sebelum dibuka;
        // entri pertama yang sudah tertulis ikut dibersihkan.
        val zip = File(tmp.root, "lying.zip")
        makeStoredZipWithLyingSize(
            zip,
            listOf("good.txt" to "good", "big.txt" to "x"),
            "big.txt", 3L * 1024L * 1024L * 1024L
        )
        val dest = tmp.newFolder("lyingout")
        assertFalse(FileOps.unzip(zip, dest))
        assertFalse(File(dest, "good.txt").exists())
        assertFalse(File(dest, "big.txt").exists())
    }

    @Test
    fun extractSingle_declaredSizeOverCapReturnsNull() {
        val zip = File(tmp.root, "lying1.zip")
        makeStoredZipWithLyingSize(
            zip,
            listOf("good.txt" to "good", "big.txt" to "x"),
            "big.txt", 3L * 1024L * 1024L * 1024L
        )
        val dest = tmp.newFolder("singleout")
        assertNull(ArchiveOps.extractSingle(zip, "big.txt", dest))
        assertTrue(dest.listFiles().orEmpty().isEmpty())
        assertEquals("good", ArchiveOps.extractSingle(zip, "good.txt", dest)?.readText())
    }

    @Test
    fun extractSingle_midCopyAbortDeletesPartial() {
        // H7: ukuran deklarasi dipalsukan jadi 50 padahal isi 10.000 byte —
        // pre-check lolos, tapi copy berhenti di tengah begitu cap
        // (100 byte) terlampaui; berkas sebagian dihapus.
        val zip = File(tmp.root, "ten.zip")
        makeZip(zip, listOf("ten.txt" to "x".repeat(10_000)))
        patchDeclaredSize(zip, "ten.txt", 50L)
        val dest = tmp.newFolder("singleout2")
        assertNull(ArchiveOps.extractSingle(zip, "ten.txt", dest, maxBytes = 100L))
        assertTrue(dest.listFiles().orEmpty().isEmpty())
        // Tanpa cap yang diperkecil, entri yang sama terekstrak utuh.
        assertEquals("x".repeat(10_000), ArchiveOps.extractSingle(zip, "ten.txt", dest)?.readText())
    }

    @Test
    fun extractEncryptedZip_failureDeletesWritten() {
        // M2: entri kedua mengklaim 3 GiB (direktori pusat dipalsukan) —
        // ekstraksi gagal dan entri pertama yang sudah tertulis dibersihkan.
        val src = tmp.newFolder("encsrc2")
        File(src, "a.txt").writeText("aaa")
        File(src, "b.txt").writeText("bbb")
        val enc = checkNotNull(ArchiveOps.create(listOf(File(src, "a.txt"), File(src, "b.txt")), tmp.root, "enc2", "zip", "sandi123"))
        patchDeclaredSize(enc, "b.txt", 3L * 1024L * 1024L * 1024L, patchLocal = false, patchCentral = true)
        val dest = tmp.newFolder("encout")
        assertFalse(ArchiveOps.extractEncryptedZip(enc, dest, "sandi123"))
        assertTrue(dest.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun extractEncryptedZip_cancelDeletesWrittenAndThrows() {
        // M2: batal sebelum entri pertama — tidak ada berkas yang tertinggal.
        val src = tmp.newFolder("encsrc3")
        File(src, "a.txt").writeText("aaa")
        val enc = checkNotNull(ArchiveOps.create(listOf(File(src, "a.txt")), tmp.root, "enc3", "zip", "sandi123"))
        val dest = tmp.newFolder("encout3")
        val flag = AtomicBoolean(true)
        assertThrows(ImgCancelledException::class.java) {
            ArchiveOps.extractEncryptedZip(enc, dest, "sandi123", cancelFlag = flag)
        }
        assertTrue(dest.listFiles().orEmpty().isEmpty())
    }

    // --- r183: kecepatan ekstrak + buat zip ---

    @Test
    fun isIncompressibleEntry_matchesKnownExtensions() {
        assertTrue(FileOps.isIncompressibleEntry("foto.JPG"))
        assertTrue(FileOps.isIncompressibleEntry("video.mp4"))
        assertTrue(FileOps.isIncompressibleEntry("arsip.zip"))
        assertTrue(FileOps.isIncompressibleEntry("app.apk"))
        assertTrue(FileOps.isIncompressibleEntry("lagu.flac"))
        assertFalse(FileOps.isIncompressibleEntry("dokumen.txt"))
        assertFalse(FileOps.isIncompressibleEntry("laporan.pdf"))
        assertFalse(FileOps.isIncompressibleEntry("tanpa-ekstensi"))
        assertFalse(FileOps.isIncompressibleEntry(".gitignore"))
    }

    @Test
    fun archiveBufferConstants_useMeasuredSizes() {
        assertEquals(256 * 1024, ArchiveOps.ARCHIVE_COPY_BUFFER_SIZE)
        assertEquals(64 * 1024, ArchiveOps.SEVENZ_READ_BUFFER_SIZE)
    }

    @Test
    fun zipFiles_storedEntriesRoundTrip() {
        // r183: jpg (STORED) + txt (DEFLATED, level 1) harus kembali
        // utuh lewat unzip; metode entri jpg harus STORED.
        val src = tmp.newFolder("zsrc")
        File(src, "a.txt").writeText("hello world\n".repeat(200))
        val jpgBytes = ByteArray(50_000) { (it * 31 % 251).toByte() }
        File(src, "b.jpg").writeBytes(jpgBytes)
        File(src, "empty.jpg").writeBytes(ByteArray(0))
        val zip = checkNotNull(FileOps.zipFiles(listOf(src), tmp.root, "campur"))
        ZipFile(zip).use { zf ->
            assertEquals(ZipEntry.STORED, zf.getEntry("zsrc/b.jpg").method)
            assertEquals(ZipEntry.DEFLATED, zf.getEntry("zsrc/a.txt").method)
            assertEquals(ZipEntry.STORED, zf.getEntry("zsrc/empty.jpg").method)
        }
        val dest = tmp.newFolder("zdest")
        assertTrue(FileOps.unzip(zip, dest))
        assertEquals("hello world\n".repeat(200), File(dest, "zsrc/a.txt").readText())
        assertArrayEquals(jpgBytes, File(dest, "zsrc/b.jpg").readBytes())
        assertEquals(0L, File(dest, "zsrc/empty.jpg").length())
    }

    @Test
    fun createEncryptedZip_storedEntriesRoundTrip() {
        // r183: zip terenkripsi dengan folder bersarang + folder kosong:
        // struktur utuh, isi byte-identik, folder kosong tetap ada.
        val src = tmp.newFolder("encsrc")
        File(src, "doc.txt").writeText("rahasia\n".repeat(100))
        val sub = File(src, "sub").apply { mkdirs() }
        val jpgBytes = ByteArray(30_000) { (it * 17 % 251).toByte() }
        File(sub, "foto.jpg").writeBytes(jpgBytes)
        File(src, "kosong").mkdirs()
        val enc = checkNotNull(ArchiveOps.create(listOf(src), tmp.root, "rahasia", "zip", "sandi123"))
        val zf = net.lingala.zip4j.ZipFile(enc, "sandi123".toCharArray())
        val names = zf.fileHeaders.map { it.fileName }.toSet()
        assertTrue(names.contains("encsrc/"))
        assertTrue(names.contains("encsrc/sub/"))
        assertTrue(names.contains("encsrc/kosong/"))
        assertTrue(names.contains("encsrc/doc.txt"))
        assertTrue(names.contains("encsrc/sub/foto.jpg"))
        val dest = tmp.newFolder("encdest")
        assertTrue(ArchiveOps.extractEncryptedZip(enc, dest, "sandi123"))
        assertEquals("rahasia\n".repeat(100), File(dest, "encsrc/doc.txt").readText())
        assertArrayEquals(jpgBytes, File(dest, "encsrc/sub/foto.jpg").readBytes())
        assertTrue(File(dest, "encsrc/kosong").isDirectory)
    }

    @Test
    fun extract7z_largeEntryUsesBiggerBuffer() {
        // r183: entri 200 KB (> 3x buffer 64 KiB) diekstrak utuh lewat
        // buffer baru — regresi bila buffer salah ukuran.
        val archive = File(tmp.root, "big.7z")
        val content = "x".repeat(200 * 1024).toByteArray()
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive).use { seven ->
            val entry = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
            entry.name = "big.txt"
            seven.putArchiveEntry(entry)
            seven.write(content, 0, content.size)
            seven.closeArchiveEntry()
        }
        val dest = tmp.newFolder("big7zout")
        assertTrue(ArchiveOps.extract(archive, dest))
        assertArrayEquals(content, File(dest, "big.txt").readBytes())
    }

    @Test
    fun copyAndMove_blockSelfAndDescendantDir() {
        val src = tmp.newFolder("selfdir")
        val sub = File(src, "nested").apply { mkdirs() }
        File(src, "keep.txt").writeText("data")

        org.junit.Assert.assertEquals(0, FileOps.copyInto(listOf(src), src))
        org.junit.Assert.assertEquals(0, FileOps.copyInto(listOf(src), sub))
        org.junit.Assert.assertEquals(0, FileOps.moveInto(listOf(src), src))
        org.junit.Assert.assertEquals(0, FileOps.moveInto(listOf(src), sub))
        assertTrue(File(src, "keep.txt").exists())
    }

    @Test
    fun blockingLockedInside_detectsLockedDescendantUntilUnlocked() {
        val docs = tmp.newFolder("Docs")
        val secret = File(docs, "Secret").apply { mkdirs() }
        val other = tmp.newFolder("DocsOther")
        val locked = setOf(secret.canonicalPath)
        org.junit.Assert.assertEquals(
            secret.canonicalPath,
            FileOps.blockingLockedInside(docs.canonicalPath, locked, emptySet())
        )
        org.junit.Assert.assertNull(
            FileOps.blockingLockedInside(docs.canonicalPath, locked, locked)
        )
        org.junit.Assert.assertNull(
            FileOps.blockingLockedInside(other.canonicalPath, locked, emptySet())
        )
    }

    @Test
    fun extractTar_rejectsSymbolicLinkEntry() {
        val archive = tmp.newFile("sym.tar")
        TarArchiveOutputStream(java.io.FileOutputStream(archive)).use { tos ->
            val entry = TarArchiveEntry(
                "evil",
                org.apache.commons.compress.archivers.tar.TarConstants.LF_SYMLINK
            )
            entry.linkName = "../outside"
            tos.putArchiveEntry(entry)
            tos.closeArchiveEntry()
        }
        val dest = tmp.newFolder("symout")
        org.junit.Assert.assertFalse(ArchiveOps.extract(archive, dest))
        org.junit.Assert.assertFalse(File(dest, "evil").exists())
    }

    @Test
    fun extractEncryptedZip_extractsValidEntryAndRejectsUnsafePaths() {
        assertTrue(FileOps.isUnsafeEntryName("/abs.txt"))
        assertTrue(FileOps.isUnsafeEntryName("\\abs.txt"))
        assertTrue(FileOps.isUnsafeEntryName("C:drive.txt"))
        assertTrue(FileOps.isUnsafeEntryName("   "))
        org.junit.Assert.assertFalse(FileOps.isUnsafeEntryName("ok/file.txt"))

        val archive = File(tmp.newFolder("enczipsrc"), "secret.zip")
        net.lingala.zip4j.ZipFile(archive, "pw123".toCharArray()).use { zip ->
            val params = net.lingala.zip4j.model.ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                fileNameInZip = "inside/note.txt"
            }
            zip.addStream(java.io.ByteArrayInputStream("hello".toByteArray()), params)
        }
        val dest = tmp.newFolder("enczipout")
        assertTrue(ArchiveOps.extractEncryptedZip(archive, dest, "pw123"))
        org.junit.Assert.assertEquals("hello", File(dest, "inside/note.txt").readText())
    }

    @Test
    fun uniqueNameWith_appendsCounterOnCollisions() {
        val existing = setOf("note.txt", "note (1).txt")
        org.junit.Assert.assertEquals(
            "note (2).txt",
            FileOps.uniqueNameWith("note.txt") { it in existing }
        )
        org.junit.Assert.assertEquals(
            "fresh.txt",
            FileOps.uniqueNameWith("fresh.txt") { it in existing }
        )
    }

    @Test
    fun scanCleanup_findsDuplicatesAndEmptyDirsRespectingExclusions() {
        val root = tmp.newFolder("cleanuproot")
        val sub = File(root, "sub").apply { mkdirs() }
        val nonEmpty = File(root, "nonempty").apply { mkdirs() }
        val nestedEmpty = File(nonEmpty, "nested_empty").apply { mkdirs() }
        val emptyDir = File(root, "empty_top").apply { mkdirs() }
        val lockedDir = File(root, "locked").apply { mkdirs() }
        val lockedEmpty = File(lockedDir, "locked_empty").apply { mkdirs() }
        val trashDir = File(root, FileOps.TRASH_DIR_NAME).apply { mkdirs() }

        File(root, "dup1.bin").writeText("same-content") // 12 bytes
        File(sub, "dup2.bin").writeText("same-content")  // 12 bytes
        File(root, "diff12.bin").writeText("123456789012") // same size, different hash
        File(lockedDir, "dup3.bin").writeText("same-content")
        File(trashDir, "dup4.bin").writeText("same-content")

        val res = FileOps.scanCleanup(root, locked = setOf(lockedDir.canonicalPath))
        org.junit.Assert.assertEquals(1, res.duplicateGroups.size)
        org.junit.Assert.assertEquals(2, res.duplicateGroups[0].files.size)
        org.junit.Assert.assertEquals(12L, res.reclaimableBytes)
        org.junit.Assert.assertEquals(
            listOf(emptyDir.canonicalPath, nestedEmpty.canonicalPath).sorted(),
            res.emptyDirs.map { File(it.path).canonicalPath }.sorted()
        )
        org.junit.Assert.assertFalse(res.emptyDirs.any { it.name == lockedEmpty.name })
    }
}
