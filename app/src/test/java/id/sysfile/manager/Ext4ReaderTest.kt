package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tes Ext4Reader dengan citra ext4 minimal yang dibangun tangan di sini
 *  (superblock + GDT + tabel inode + blok direktori + data), jadi tidak
 *  butuh mkfs.ext4 dan tidak ada biner yang di-commit.
 *
 *  Tata letak citra 4K (19 blok): 0 superblock, 1 GDT (tabel inode di
 *  blok 4), 2 bitmap blok, 3 bitmap inode, 4-5 tabel inode (32 inode
 *  x 256 byte), 6 root, 7 hello.txt, 8 sub, 9-11 big.bin, 12 deep,
 *  13 f.txt, 14 target slowlink, 15 daun extent, 16 deepfile, 17 plaindir,
 *  18 plainfile + gap.bin.
 *  Inode: 2 root, 12 hello.txt, 13 big.bin (2 extent), 14 sub,
 *  15 link.txt (fast symlink), 16 slowlink, 17 deepfile (pohon extent
 *  kedalaman 1), 18 deep, 19 f.txt, 20 hole.bin (extent uninitialized),
 *  21 plainfile, 22 plaindir (direktori kosong), 23 gap.bin (celah
 *  extent: hanya blok 0 terpetakan, blok 1 lubang — M10).
 *
 *  Citra 1K (16 blok): 0 boot, 1 superblock, 2 GDT (tabel inode di
 *  blok 5), 3-4 bitmap, 5-12 tabel inode, 13 root, 14 hello.txt.
 */
class Ext4ReaderTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("ext4-test").toFile()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun openAndRootListing() {
        val img = buildExt4Fixture(tmp)
        val reader = Ext4Reader.open(img)
        assertNotNull(reader)
        reader!!.use {
            assertEquals(4096L, it.blockSize)
            val root = it.listDir(2L)
            assertNotNull(root)
            assertEquals(
                listOf(
                    "plaindir", "sub", "big.bin", "deepfile", "gap.bin", "hello.txt",
                    "hole.bin", "link.txt", "plainfile", "slowlink"
                ),
                root!!.map { e -> e.name }
            )
            val hello = root.first { e -> e.name == "hello.txt" }
            assertFalse(hello.isDir)
            assertFalse(hello.isSymlink)
            assertEquals(11L, hello.size)
            val link = root.first { e -> e.name == "link.txt" }
            assertTrue(link.isSymlink)
            assertEquals("hello.txt", link.symlinkTarget)
            val slow = root.first { e -> e.name == "slowlink" }
            assertEquals("sub/deep/f.txt", slow.symlinkTarget)
            val sub = root.first { e -> e.name == "sub" }
            assertTrue(sub.isDir)
        }
    }

    @Test
    fun subdirNavigationAndResolveEntry() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            assertEquals(14L, r.resolvePath("sub"))
            assertEquals(18L, r.resolvePath("sub/deep"))
            assertEquals(2L, r.resolvePath(""))
            assertEquals(2L, r.resolvePath("/"))
            val deep = r.listDir(18L)
            assertNotNull(deep)
            assertEquals(listOf("f.txt"), deep!!.map { it.name })
            val f = r.resolveEntry("sub/deep/f.txt")
            assertNotNull(f)
            assertEquals(19L, f!!.inode)
            assertEquals(10L, f.size)
            assertNull(r.resolveEntry("nope"))
            assertNull(r.resolveEntry("hello.txt/sub"))
            assertNull(r.resolvePath("hello.txt"))
        }
    }

    @Test
    fun readFileSmallAndText() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            assertEquals(
                "hello ext4\n",
                String(r.readFile(12L, 1_000_000L)!!, Charsets.UTF_8)
            )
            assertEquals("hello ext4\n", r.readFileText(12L))
            assertNull(r.readFileText(13L))
            assertNull(r.readFileText(15L))
            assertNull(r.readFile(12L, 5L))
        }
    }

    @Test
    fun readFileMultiBlock() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val bytes = r.readFile(13L, 1_000_000L)
            assertNotNull(bytes)
            assertEquals(9000, bytes!!.size)
            val expected = ByteArray(9000) { i -> ((i * 7) % 256).toByte() }
            assertArrayEquals(expected, bytes)
        }
    }

    @Test
    fun readFileDepthOneExtent() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            assertEquals(
                "nested extent\n",
                String(r.readFile(17L, 1_000_000L)!!, Charsets.UTF_8)
            )
        }
    }

    @Test
    fun readFileUninitializedExtentZeros() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val bytes = r.readFile(20L, 1_000_000L)
            assertNotNull(bytes)
            assertEquals(4096, bytes!!.size)
            assertTrue(bytes!!.all { it == 0.toByte() })
        }
    }

    @Test
    fun symlinkResolution() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val hello = r.resolveEntry("link.txt")
            assertNotNull(hello)
            assertEquals(12L, hello!!.inode)
            val f = r.resolveEntry("slowlink")
            assertNotNull(f)
            assertEquals(19L, f!!.inode)
            val viaDir = r.resolveEntry("sub/../hello.txt")
            assertNotNull(viaDir)
            assertEquals(12L, viaDir!!.inode)
            assertEquals(2L, r.resolvePath("sub/../.."))
            assertEquals(2L, r.resolvePath("../../.."))
        }
    }

    @Test
    fun nonExtentInodesFailCleanly() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            assertNull(r.readFile(999L, 1_000_000L))
            assertNull(r.listDir(999L))
            assertNull(r.listDir(12L))
        }
        val raw = img.readBytes()
        // Berkas tanpa flag EXTENTS (flag inode "plainfile" di-nol-kan):
        // bacanya gagal bersih.
        val offFile = 4 * 4096 + 20 * 256 + 0x20
        val badFile = raw.copyOf().also { putU32(it, offFile, 0) }
        val f1 = File(tmp, "no-extents-file.img").also { it.writeBytes(badFile) }
        Ext4Reader.open(f1)!!.use { r ->
            assertNull(r.readFile(21L, 1_000_000L))
        }
        // Direktori tanpa flag EXTENTS (flag inode "sub" di-nol-kan):
        // listing-nya gagal bersih.
        val offDir = 4 * 4096 + 13 * 256 + 0x20
        val badDir = raw.copyOf().also { putU32(it, offDir, 0) }
        val f2 = File(tmp, "no-extents-dir.img").also { it.writeBytes(badDir) }
        Ext4Reader.open(f2)!!.use { r ->
            assertNull(r.listDir(14L))
        }
    }

    @Test
    fun openRejectsCorrupt() {
        val img = buildExt4Fixture(tmp)
        val raw = img.readBytes()
        val badMagic = raw.copyOf().also { putU16(it, 1024 + 0x38, 0x1234) }
        val f1 = File(tmp, "bad-magic.img").also { it.writeBytes(badMagic) }
        assertNull(Ext4Reader.open(f1))
        val f2 = File(tmp, "tiny.img").also { it.writeBytes(ByteArray(1024)) }
        assertNull(Ext4Reader.open(f2))
        val badLog = raw.copyOf().also { putU32(it, 1024 + 0x18, 9) }
        val f3 = File(tmp, "bad-log.img").also { it.writeBytes(badLog) }
        assertNull(Ext4Reader.open(f3))
        val badHi = raw.copyOf().also { putU32(it, 1024 + 0x150, 1) }
        val f4 = File(tmp, "bad-hi.img").also { it.writeBytes(badHi) }
        assertNull(Ext4Reader.open(f4))
        val badTable = raw.copyOf().also { putU32(it, 4096 + 0x8, 9999) }
        val f5 = File(tmp, "bad-table.img").also { it.writeBytes(badTable) }
        assertNull(Ext4Reader.open(f5))
        assertNull(Ext4Reader.open(File(tmp, "tidak-ada.img")))
    }

    @Test
    fun corruptExtentMagicFailsFileOnly() {
        val img = buildExt4Fixture(tmp)
        val raw = img.readBytes()
        val off = 4 * 4096 + 11 * 256 + 0x28
        val bad = raw.copyOf().also { putU16(it, off, 0x1234) }
        val f = File(tmp, "bad-extent.img").also { it.writeBytes(bad) }
        Ext4Reader.open(f)!!.use { r ->
            assertNull(r.readFile(12L, 1_000_000L))
            assertNotNull(r.listDir(2L))
        }
    }

    @Test
    fun corruptDirRecLenFailsListing() {
        val img = buildExt4Fixture(tmp)
        val raw = img.readBytes()
        val off = 6 * 4096 + 4
        val bad = raw.copyOf().also { putU16(it, off, 6) }
        val f = File(tmp, "bad-dir.img").also { it.writeBytes(bad) }
        Ext4Reader.open(f)!!.use { r ->
            assertNull(r.listDir(2L))
        }
    }

    @Test
    fun open1kBlocks() {
        val img = buildExt4Fixture1k(tmp)
        Ext4Reader.open(img)!!.use { r ->
            assertEquals(1024L, r.blockSize)
            val root = r.listDir(2L)
            assertNotNull(root)
            assertEquals(listOf("hello.txt"), root!!.map { it.name })
            assertEquals(
                "hello ext4\n",
                String(r.readFile(12L, 1_000_000L)!!, Charsets.UTF_8)
            )
        }
    }

    @Test
    fun sessionRawAndSparse() {
        val img = buildExt4Fixture(tmp)
        val rawSession = Ext4Reader.openSession(img)
        assertNotNull(rawSession)
        assertNull(rawSession!!.tempFile)
        assertEquals(10, rawSession.reader.listDir(2L)!!.size)
        rawSession.close()
        assertTrue(img.exists())

        val sparse = File(tmp, "system-sparse.img")
            .also { it.writeBytes(makeSparse(img.readBytes())) }
        val session = Ext4Reader.openSession(sparse)
        assertNotNull(session)
        session!!.use {
            // LD2 r157: sparse dibaca langsung — tanpa temp.
            assertNull(it.tempFile)
            val root = it.reader.listDir(2L)
            assertNotNull(root)
            assertEquals(10, root!!.size)
        }
        // Menutup sesi tidak menghapus berkas sumber.
        assertTrue(sparse.exists())
    }

    @Test
    fun extractEntryAndExtractAll() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val dest = File(tmp, "out").also { it.mkdirs() }
            val hello = r.extractEntry(12L, "hello.txt", dest)
            assertNotNull(hello)
            assertEquals("hello ext4\n", hello!!.readText())
            val big = r.extractEntry(13L, "big.bin", dest)
            assertNotNull(big)
            assertEquals(9000L, big!!.length())
            assertNull(r.extractEntry(12L, "../evil.txt", dest))
            assertNull(r.extractEntry(15L, "link.txt", dest))
            assertFalse(File(tmp, "evil.txt").exists())

            val all = File(tmp, "all").also { it.mkdirs() }
            assertTrue(r.extractAll(all))
            assertEquals("hello ext4\n", File(all, "hello.txt").readText())
            assertEquals("deep file\n", File(all, "sub/deep/f.txt").readText())
            assertEquals(9000L, File(all, "big.bin").length())
            assertFalse(File(all, "link.txt").exists())
            assertFalse(File(all, "slowlink").exists())
            assertTrue(File(all, "sub").isDirectory)
            assertTrue(File(all, "plaindir").isDirectory)
        }
    }

    // --- extractAll (LD3 r160: progres + batal) ---
    // Total entri fixture: root 10 + sub 1 (deep) + deep 1 (f.txt) + plaindir 0 = 12.

    @Test
    fun extractAll_progressReportsMonotonicToTotal() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val all = File(tmp, "allprog").also { it.mkdirs() }
            val seen = mutableListOf<Pair<Long, Long>>()
            assertTrue(r.extractAll(all, onProgress = { done, total -> seen += done to total }))
            assertEquals(12, seen.size)
            assertEquals(12L to 12L, seen.last())
            assertEquals("hello ext4\n", File(all, "hello.txt").readText())
        }
    }

    @Test
    fun readFileTrueGapReturnsZeros() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            // gap.bin: ukuran 8192, hanya blok 0 terpetakan (isi
            // "plain"), blok 1 celah — readData sudah benar (nol).
            val bytes = r.readFile(23L, 1_000_000L)
            assertNotNull(bytes)
            assertEquals(8192, bytes!!.size)
            assertEquals("plain", String(bytes.copyOfRange(0, 5), Charsets.UTF_8))
            assertTrue(bytes.copyOfRange(5, 8192).all { it == 0.toByte() })
        }
    }

    @Test
    fun extractEntryTrueGapWritesZeros() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val dest = File(tmp, "gapout").also { it.mkdirs() }
            val out = r.extractEntry(23L, "gap.bin", dest)
            assertNotNull(out)
            assertEquals(8192L, out!!.length())
            assertArrayEquals(r.readFile(23L, 1_000_000L)!!, out.readBytes())
        }
    }

    @Test
    fun extractEntryCollisionUsesUniqueName() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val dest = File(tmp, "collide").also { it.mkdirs() }
            File(dest, "hello.txt").writeText("ORIGINAL")
            val out = r.extractEntry(12L, "hello.txt", dest)
            assertNotNull(out)
            assertEquals("hello (1).txt", out!!.name)
            assertEquals("hello ext4\n", out.readText())
            assertEquals("ORIGINAL", File(dest, "hello.txt").readText())
        }
    }

    @Test
    fun extractEntryFailureLeavesNoPartialAndKeepsExisting() {
        val img = buildExt4Fixture(tmp)
        val raw = img.readBytes()
        // Rusak extent hello.txt: phys di luar total blok (offset
        // iBlock inode 12 + 12 header extent + 8 = physLo).
        val off = 4 * 4096 + 11 * 256 + 0x28 + 12 + 8
        val bad = raw.copyOf().also { putU32(it, off, 9999) }
        val f = File(tmp, "badext.img").also { it.writeBytes(bad) }
        Ext4Reader.open(f)!!.use { r ->
            val dest = File(tmp, "badout").also { it.mkdirs() }
            File(dest, "hello.txt").writeText("ORIGINAL")
            assertNull(r.extractEntry(12L, "hello.txt", dest))
            assertEquals("ORIGINAL", File(dest, "hello.txt").readText())
            assertEquals(listOf("hello.txt"), dest.listFiles()!!.map { it.name })
        }
    }

    @Test
    fun extractAllPreservesPreExistingFile() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val all = File(tmp, "allkeep").also { it.mkdirs() }
            File(all, "hello.txt").writeText("ORIGINAL")
            assertTrue(r.extractAll(all))
            assertEquals("ORIGINAL", File(all, "hello.txt").readText())
            assertEquals("hello ext4\n", File(all, "hello (1).txt").readText())
        }
    }

    @Test
    fun extractAllCancelKeepsPreExistingFile() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val all = File(tmp, "allkeepcancel").also { it.mkdirs() }
            File(all, "hello.txt").writeText("ORIGINAL")
            val flag = AtomicBoolean(false)
            assertThrows(ImgCancelledException::class.java) {
                r.extractAll(
                    all,
                    onProgress = { done, _ -> if (done >= 3L) flag.set(true) },
                    cancelFlag = flag
                )
            }
            assertEquals("ORIGINAL", File(all, "hello.txt").readText())
            assertEquals(listOf("hello.txt"), all.walkTopDown().filter { it.isFile }.map { it.name }.toList())
        }
    }

    @Test
    fun extractAll_cancelMidwayDeletesWrittenAndThrows() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            val all = File(tmp, "allcancel").also { it.mkdirs() }
            val flag = AtomicBoolean(false)
            // Batal setelah 3 entri: plaindir (dir kosong), lalu sub/deep
            // menulis f.txt — f.txt harus ikut dibersihkan.
            assertThrows(ImgCancelledException::class.java) {
                r.extractAll(
                    all,
                    onProgress = { done, _ -> if (done >= 3L) flag.set(true) },
                    cancelFlag = flag
                )
            }
            assertTrue(all.walkTopDown().filter { it.isFile }.toList().isEmpty())
        }
    }

    @Test
    fun resolvePathEdges() {
        val img = buildExt4Fixture(tmp)
        Ext4Reader.open(img)!!.use { r ->
            assertNull(r.resolveEntry(""))
            assertNull(r.resolveEntry("/"))
            assertNull(r.resolvePath("sub/deep/f.txt"))
            assertEquals(18L, r.resolvePath("sub/deep/"))
            assertEquals(14L, r.resolvePath("sub//"))
        }
    }

    @Test
    fun normalizePathNeverEscapesRoot() {
        assertEquals("", Ext4Reader.normalizePath(""))
        assertEquals("", Ext4Reader.normalizePath("/"))
        assertEquals("a/b", Ext4Reader.normalizePath("a/./b"))
        assertEquals("b", Ext4Reader.normalizePath("a/../b"))
        assertEquals("", Ext4Reader.normalizePath("../../.."))
        assertEquals("a", Ext4Reader.normalizePath("/a/b/.."))
    }

    private data class ExtentSpec(
        val fileBlock: Long,
        val phys: Long,
        val len: Long,
        val uninit: Boolean = false
    )

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun extentRoot(vararg extents: ExtentSpec): ByteArray {
        val b = ByteArray(60)
        putU16(b, 0, 0xF30A)
        putU16(b, 2, extents.size)
        putU16(b, 4, 4)
        putU16(b, 6, 0)
        extents.forEachIndexed { i, e ->
            val o = 12 + i * 12
            putU32(b, o, e.fileBlock)
            putU16(b, o + 4, if (e.uninit) (e.len + 32768).toInt() else e.len.toInt())
            putU16(b, o + 6, (e.phys ushr 32).toInt())
            putU32(b, o + 8, e.phys)
        }
        return b
    }

    private fun extentIdxRoot(leafBlock: Long): ByteArray {
        val b = ByteArray(60)
        putU16(b, 0, 0xF30A)
        putU16(b, 2, 1)
        putU16(b, 4, 4)
        putU16(b, 6, 1)
        putU32(b, 12, 0)
        putU32(b, 16, leafBlock)
        putU16(b, 20, 0)
        return b
    }

    private fun extentLeaf(blockSize: Int, vararg extents: ExtentSpec): ByteArray {
        val b = ByteArray(blockSize)
        putU16(b, 0, 0xF30A)
        putU16(b, 2, extents.size)
        putU16(b, 4, (blockSize - 12) / 12)
        putU16(b, 6, 0)
        extents.forEachIndexed { i, e ->
            val o = 12 + i * 12
            putU32(b, o, e.fileBlock)
            putU16(b, o + 4, if (e.uninit) (e.len + 32768).toInt() else e.len.toInt())
            putU16(b, o + 6, (e.phys ushr 32).toInt())
            putU32(b, o + 8, e.phys)
        }
        return b
    }

    private fun dirBlock(
        blockSize: Int,
        entries: List<Triple<Int, String, Int>>
    ): ByteArray {
        val b = ByteArray(blockSize)
        var pos = 0
        entries.forEachIndexed { i, (ino, name, ftype) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val last = i == entries.lastIndex
            val recLen = if (last) blockSize - 12 - pos
            else (8 + nameBytes.size + 3) / 4 * 4
            putU32(b, pos, ino.toLong())
            putU16(b, pos + 4, recLen)
            b[pos + 6] = nameBytes.size.toByte()
            b[pos + 7] = ftype.toByte()
            nameBytes.copyInto(b, pos + 8)
            pos += recLen
        }
        val t = blockSize - 12
        putU32(b, t, 0)
        putU16(b, t + 4, 12)
        b[t + 6] = 0
        b[t + 7] = 0xDE.toByte()
        return b
    }

    private fun putInode(
        img: ByteArray, blockSize: Int, tableBlock: Int, ino: Int,
        mode: Int, size: Long, flags: Int, blocksLo: Long, iBlock: ByteArray
    ) {
        val off = tableBlock * blockSize + (ino - 1) * 256
        putU16(img, off, mode)
        putU32(img, off + 4, size)
        putU32(img, off + 0x1C, blocksLo)
        putU32(img, off + 0x20, flags.toLong())
        iBlock.copyInto(img, off + 0x28)
    }

    private fun putSuperblock(
        img: ByteArray, totalBlocks: Long, freeBlocks: Long,
        logBlockSize: Int, firstDataBlock: Long, blocksPerGroup: Long,
        blocksHi: Long = 0
    ) {
        val sb = 1024
        putU32(img, sb + 0x0, 32)
        putU32(img, sb + 0x4, totalBlocks)
        putU32(img, sb + 0xC, freeBlocks)
        putU32(img, sb + 0x14, firstDataBlock)
        putU32(img, sb + 0x18, logBlockSize.toLong())
        putU32(img, sb + 0x20, blocksPerGroup)
        putU32(img, sb + 0x28, 32)
        putU16(img, sb + 0x38, 0xEF53)
        putU16(img, sb + 0x54, 11)
        putU16(img, sb + 0x58, 256)
        putU32(img, sb + 0x5C, 0x4)
        putU32(img, sb + 0x60, 0xC0)
        putU32(img, sb + 0x64, 0x400)
        "system".toByteArray(Charsets.UTF_8).copyInto(img, sb + 0x78)
        putU16(img, sb + 0xFE, 64)
        putU32(img, sb + 0x150, blocksHi)
        putU32(img, sb + 0x158, 0)
    }

    private fun putGdt(
        img: ByteArray, blockSize: Int, gdtBlock: Int, inodeTableBlock: Long
    ) {
        val off = gdtBlock * blockSize
        putU32(img, off + 0x8, inodeTableBlock)
        putU32(img, off + 0x28, 0)
    }

    private fun buildExt4Fixture(dir: File): File {
        val img = ByteArray(19 * 4096)
        putSuperblock(img, 19, 0, 2, 0, 32768)
        putGdt(img, 4096, 1, 4)
        putInode(img, 4096, 4, 2, 0x41ED, 4096, 0x80000, 1,
            extentRoot(ExtentSpec(0, 6, 1)))
        putInode(img, 4096, 4, 12, 0x81A4, 11, 0x80000, 1,
            extentRoot(ExtentSpec(0, 7, 1)))
        putInode(img, 4096, 4, 13, 0x81A4, 9000, 0x80000, 3,
            extentRoot(ExtentSpec(0, 9, 2), ExtentSpec(2, 11, 1)))
        putInode(img, 4096, 4, 14, 0x41ED, 4096, 0x80000, 1,
            extentRoot(ExtentSpec(0, 8, 1)))
        val fastTarget = "hello.txt".toByteArray(Charsets.UTF_8)
        val fastBlock = ByteArray(60).also { fastTarget.copyInto(it) }
        putInode(img, 4096, 4, 15, 0xA1FF, 9, 0x80000, 0, fastBlock)
        putInode(img, 4096, 4, 16, 0xA1FF, 14, 0x80000, 1,
            extentRoot(ExtentSpec(0, 14, 1)))
        putInode(img, 4096, 4, 17, 0x81A4, 14, 0x80000, 1,
            extentIdxRoot(15))
        putInode(img, 4096, 4, 18, 0x41ED, 4096, 0x80000, 1,
            extentRoot(ExtentSpec(0, 12, 1)))
        putInode(img, 4096, 4, 19, 0x81A4, 10, 0x80000, 1,
            extentRoot(ExtentSpec(0, 13, 1)))
        putInode(img, 4096, 4, 20, 0x81A4, 4096, 0x80000, 1,
            extentRoot(ExtentSpec(0, 7, 1, true)))
        putInode(img, 4096, 4, 21, 0x81A4, 5, 0x80000, 1,
            extentRoot(ExtentSpec(0, 18, 1)))
        putInode(img, 4096, 4, 22, 0x41ED, 4096, 0x80000, 1,
            extentRoot(ExtentSpec(0, 17, 1)))
        // gap.bin: ukuran 8192 (2 blok), hanya blok 0 terpetakan ke
        // blok fisik 18 (isi "plain"); blok 1 celah (M10).
        putInode(img, 4096, 4, 23, 0x81A4, 8192, 0x80000, 1, extentRoot(ExtentSpec(0, 18, 1)))
        dirBlock(
            4096,
            listOf(
                Triple(2, ".", 2),
                Triple(2, "..", 2),
                Triple(12, "hello.txt", 1),
                Triple(13, "big.bin", 1),
                Triple(14, "sub", 2),
                Triple(15, "link.txt", 7),
                Triple(16, "slowlink", 7),
                Triple(17, "deepfile", 1),
                Triple(20, "hole.bin", 1),
                Triple(21, "plainfile", 1),
                Triple(22, "plaindir", 2),
                Triple(23, "gap.bin", 1)
            )
        ).copyInto(img, 6 * 4096)
        dirBlock(
            4096,
            listOf(
                Triple(2, ".", 2),
                Triple(2, "..", 2),
                Triple(18, "deep", 2)
            )
        ).copyInto(img, 8 * 4096)
        dirBlock(
            4096,
            listOf(
                Triple(2, ".", 2),
                Triple(2, "..", 2),
                Triple(19, "f.txt", 1)
            )
        ).copyInto(img, 12 * 4096)
        "hello ext4\n".toByteArray(Charsets.UTF_8).copyInto(img, 7 * 4096)
        val big = ByteArray(9000) { i -> ((i * 7) % 256).toByte() }
        big.copyInto(img, 9 * 4096)
        "deep file\n".toByteArray(Charsets.UTF_8).copyInto(img, 13 * 4096)
        "sub/deep/f.txt".toByteArray(Charsets.UTF_8).copyInto(img, 14 * 4096)
        extentLeaf(4096, ExtentSpec(0, 16, 1)).copyInto(img, 15 * 4096)
        "nested extent\n".toByteArray(Charsets.UTF_8).copyInto(img, 16 * 4096)
        dirBlock(
            4096,
            listOf(
                Triple(2, ".", 2),
                Triple(2, "..", 2)
            )
        ).copyInto(img, 17 * 4096)
        "plain".toByteArray(Charsets.UTF_8).copyInto(img, 18 * 4096)
        return File(dir, "system.img").also { it.writeBytes(img) }
    }

    private fun buildExt4Fixture1k(dir: File): File {
        val img = ByteArray(16 * 1024)
        putSuperblock(img, 16, 1, 0, 1, 8192)
        putGdt(img, 1024, 2, 5)
        putInode(img, 1024, 5, 2, 0x41ED, 1024, 0x80000, 1,
            extentRoot(ExtentSpec(0, 13, 1)))
        putInode(img, 1024, 5, 12, 0x81A4, 11, 0x80000, 1,
            extentRoot(ExtentSpec(0, 14, 1)))
        dirBlock(
            1024,
            listOf(
                Triple(2, ".", 2),
                Triple(2, "..", 2),
                Triple(12, "hello.txt", 1)
            )
        ).copyInto(img, 13 * 1024)
        "hello ext4\n".toByteArray(Charsets.UTF_8).copyInto(img, 14 * 1024)
        return File(dir, "small1k.img").also { it.writeBytes(img) }
    }

    private fun makeSparse(raw: ByteArray, chunkSize: Int = 4096): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }
        fun u32(v: Long) {
            repeat(4) { i -> out.write(((v ushr (8 * i)) and 0xFF).toInt()) }
        }
        val chunks = (raw.size + chunkSize - 1) / chunkSize
        u32(0xED26FF3AL)
        u16(1)
        u16(0)
        u16(28)
        u16(12)
        u32(chunkSize.toLong())
        u32(chunks.toLong())
        u32(chunks.toLong())
        u32(0)
        for (i in 0 until chunks) {
            val start = i * chunkSize
            val len = minOf(chunkSize, raw.size - start)
            u16(0xCAC1)
            u16(0)
            u32((len / chunkSize).toLong())
            u32((len + 12).toLong())
            out.write(raw, start, len)
        }
        return out.toByteArray()
    }
}
