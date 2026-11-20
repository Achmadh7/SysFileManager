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

/** Tes FatReader dengan citra FAT minimal yang dibangun tangan di sini
 *  (sektor boot + FAT + direktori root + klaster data), jadi tidak
 *  butuh mkfs.vfat dan tidak ada biner yang di-commit.
 *
 *  FAT12 (floppy 1,44 MB, 2880 sektor): FAT1 @sektor 1 (9 sektor),
 *  FAT2 @10, root @19 (224 entri), data @33, klaster 1 sektor.
 *  Isi root: label volume "NO NAME" (dilewati), entri terhapus
 *  (dilewati), HELLO.TXT (klaster 2, "Hello FAT12\n"), SUB (dir,
 *  klaster 3), LONGFILENAME.TXT via LFN (alias LONGFI~1.TXT, klaster
 *  4->5->6, 1500 byte), EMPTY.BIN (kosong, klaster 0). SUB berisi
 *  INNER.TXT (klaster 7, "inner") plus "." dan "..".
 *
 *  FAT16 (20000 sektor): FAT1 @1 (32 sektor), FAT2 @33, root @65
 *  (512 entri), data @97, klaster 4 sektor. Isi root: DATA.BIN
 *  (klaster 2->4->3, 3000 byte, rantai terfragmentasi), DOCS (dir,
 *  klaster 5), "My Document.txt" via LFN (alias MYDOCU~1.TXT,
 *  klaster 6). DOCS berisi NOTE.TXT (klaster 7).
 *
 *  FAT32 (BPB mengklaim 600000 sektor, berkas fisik hanya 1 MiB —
 *  klaster yang dipakai bernomor kecil sehingga muat; pola yang sama
 *  seperti r153: totalSectors sengaja tidak dicek terhadap panjang
 *  berkas): FAT1 @32 (256 sektor), FAT2 @288, data @544, klaster 8
 *  sektor, root = klaster 2. Isi root: KERNEL.BIN (klaster 3->5->4,
 *  5000 byte), SYSTEM (dir, klaster 6), "Readme File.txt" via LFN
 *  (alias README~1.TXT, klaster 7). SYSTEM berisi BUILD.PROP
 *  (klaster 8, "ro.build=1\n").
 */
class FatReaderTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("fat-test").toFile()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    // ---------- builder ----------

    private fun u16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun u32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putStr(b: ByteArray, off: Int, s: String) {
        val bytes = s.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(b, off)
    }

    private fun setFat12(img: ByteArray, fatOff: Int, cluster: Int, value: Int) {
        val off = fatOff + cluster + cluster / 2
        val cur = (img[off].toInt() and 0xFF) or ((img[off + 1].toInt() and 0xFF) shl 8)
        val next = if (cluster % 2 == 1) {
            (cur and 0x000F) or ((value and 0xFFF) shl 4)
        } else {
            (cur and 0xF000) or (value and 0xFFF)
        }
        u16(img, off, next)
    }

    private fun setFat16(img: ByteArray, fatOff: Int, cluster: Int, value: Int) {
        u16(img, fatOff + cluster * 2, value)
    }

    private fun setFat32(img: ByteArray, fatOff: Int, cluster: Int, value: Long) {
        u32(img, fatOff + cluster * 4, value)
    }

    /** Nama 8.3 (11 byte, huruf besar, spasi sebagai padding).
     *  Nama maksimal 8 huruf, ekstensi maksimal 3 huruf — lebih dari
     *  itu harus lewat LFN. */
    private fun name11(name: String, ext: String): ByteArray {
        require(name.length <= 8) { "nama 8.3 terlalu panjang: $name" }
        require(ext.length <= 3) { "ekstensi 8.3 terlalu panjang: $ext" }
        val out = ByteArray(11) { ' '.code.toByte() }
        name.toByteArray(Charsets.US_ASCII).copyInto(out, 0)
        ext.toByteArray(Charsets.US_ASCII).copyInto(out, 8)
        return out
    }

    private fun dirEntry(
        img: ByteArray,
        off: Int,
        name: ByteArray,
        attr: Int,
        firstCluster: Int,
        size: Long,
        hiCluster: Int = 0
    ) {
        name.copyInto(img, off)
        img[off + 11] = attr.toByte()
        u16(img, off + 20, hiCluster)
        u16(img, off + 26, firstCluster)
        u32(img, off + 28, size)
    }

    /** Checksum nama pendek (algoritma yang sama seperti pembaca). */
    private fun lfnChecksum(name: ByteArray): Int {
        var sum = 0
        for (i in 0 until 11) {
            sum = (((sum and 1) shl 7) or ((sum and 0xFE) shr 1)) +
                (name[i].toInt() and 0xFF)
            sum = sum and 0xFF
        }
        return sum
    }

    /** Tulis slot LFN untuk [longName] di depan aliasnya; kembalikan
     *  offset sesudah slot terakhir (tempat alias ditulis). Slot
     *  tertinggi (dengan flag 0x40) ditulis dulu. */
    private fun lfnSlots(
        img: ByteArray,
        off: Int,
        longName: String,
        alias: ByteArray
    ): Int {
        val chars = longName.map { it.code }
        val slots = (chars.size + 12) / 13
        val checksum = lfnChecksum(alias)
        val offs = intArrayOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)
        var pos = off
        for (slotIdx in slots downTo 1) {
            img[pos] = (slotIdx or (if (slotIdx == slots) 0x40 else 0)).toByte()
            for (k in 0 until 13) {
                val idx = (slotIdx - 1) * 13 + k
                val c = when {
                    idx < chars.size -> chars[idx]
                    idx == chars.size -> 0x0000
                    else -> 0xFFFF
                }
                u16(img, pos + offs[k], c)
            }
            img[pos + 11] = 0x0F
            img[pos + 12] = 0
            img[pos + 13] = checksum.toByte()
            u16(img, pos + 26, 0)
            pos += 32
        }
        return pos
    }

    private fun bootSector(
        img: ByteArray,
        bytesPerSector: Int,
        sectorsPerCluster: Int,
        reserved: Int,
        numFats: Int,
        rootEntries: Int,
        totalSectors16: Int,
        totalSectors32: Long,
        fatSize16: Int,
        fatSize32: Long,
        rootCluster: Long,
        sysIdOff: Int,
        sysId: String
    ) {
        img[0] = 0xEB.toByte()
        img[1] = 0x3C
        img[2] = 0x90.toByte()
        putStr(img, 3, "MSWIN4.1")
        u16(img, 11, bytesPerSector)
        img[13] = sectorsPerCluster.toByte()
        u16(img, 14, reserved)
        img[16] = numFats.toByte()
        u16(img, 17, rootEntries)
        u16(img, 19, totalSectors16)
        img[21] = 0xF8.toByte()
        u16(img, 22, fatSize16)
        u32(img, 32, totalSectors32)
        u32(img, 36, fatSize32)
        u32(img, 44, rootCluster)
        u16(img, 48, 1)
        u16(img, 50, 6)
        putStr(img, sysIdOff, sysId)
        img[bytesPerSector - 2] = 0x55
        img[bytesPerSector - 1] = 0xAA.toByte()
    }

    private fun buildFat12Image(
        corruptLfn: Boolean = false,
        breakChain: Int = 0
    ): File {
        val img = ByteArray(2880 * 512)
        bootSector(
            img, 512, 1, 1, 2, 224, 2880, 0, 9, 0, 2, 54, "FAT12   "
        )
        val fat1 = 512
        val fat2 = 10 * 512
        img[fat1] = 0xF0.toByte()
        img[fat1 + 1] = 0xFF.toByte()
        img[fat1 + 2] = 0xFF.toByte()
        img[fat2] = 0xF0.toByte()
        img[fat2 + 1] = 0xFF.toByte()
        img[fat2 + 2] = 0xFF.toByte()
        for (fat in intArrayOf(fat1, fat2)) {
            setFat12(img, fat, 2, 0xFFF)
            setFat12(img, fat, 3, 0xFFF)
            when (breakChain) {
                1 -> {
                    setFat12(img, fat, 4, 5)
                    setFat12(img, fat, 5, 4)
                }
                2 -> setFat12(img, fat, 4, 0)
                else -> {
                    setFat12(img, fat, 4, 5)
                    setFat12(img, fat, 5, 6)
                    setFat12(img, fat, 6, 0xFFF)
                }
            }
            setFat12(img, fat, 7, 0xFFF)
        }
        val root = 19 * 512
        val data = 33 * 512
        fun clusterOff(n: Int) = data + (n - 2) * 512
        var pos = root
        dirEntry(img, pos, name11("NO NAME", ""), 0x08, 0, 0)
        pos += 32
        dirEntry(img, pos, name11("DELETED", "TXT"), 0x20, 0, 0)
        img[pos] = 0xE5.toByte()
        pos += 32
        dirEntry(img, pos, name11("HELLO", "TXT"), 0x20, 2, 12)
        "Hello FAT12\n".toByteArray(Charsets.US_ASCII)
            .copyInto(img, clusterOff(2))
        pos += 32
        dirEntry(img, pos, name11("SUB", ""), 0x10, 3, 0)
        pos += 32
        val alias = name11("LONGFI~1", "TXT")
        var lfnPos = pos
        if (corruptLfn) {
            // Slot ditulis dengan checksum yang salah (+1).
            val chars = "LONGFILENAME.TXT".map { it.code }
            val slots = (chars.size + 12) / 13
            val offs = intArrayOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)
            for (slotIdx in slots downTo 1) {
                img[lfnPos] = (slotIdx or (if (slotIdx == slots) 0x40 else 0)).toByte()
                for (k in 0 until 13) {
                    val idx = (slotIdx - 1) * 13 + k
                    val c = when {
                        idx < chars.size -> chars[idx]
                        idx == chars.size -> 0x0000
                        else -> 0xFFFF
                    }
                    u16(img, lfnPos + offs[k], c)
                }
                img[lfnPos + 11] = 0x0F
                img[lfnPos + 12] = 0
                img[lfnPos + 13] = (lfnChecksum(alias) + 1).toByte()
                u16(img, lfnPos + 26, 0)
                lfnPos += 32
            }
        } else {
            lfnPos = lfnSlots(img, pos, "LONGFILENAME.TXT", alias)
        }
        dirEntry(img, lfnPos, alias, 0x20, 4, 1500)
        img.fill('A'.code.toByte(), clusterOff(4), clusterOff(4) + 512)
        img.fill('B'.code.toByte(), clusterOff(5), clusterOff(5) + 512)
        img.fill('C'.code.toByte(), clusterOff(6), clusterOff(6) + 476)
        pos = lfnPos + 32
        dirEntry(img, pos, name11("EMPTY", "BIN"), 0x20, 0, 0)
        // SUB (klaster 3): "." , ".." , INNER.TXT.
        val sub = clusterOff(3)
        dirEntry(img, sub, name11(".", ""), 0x10, 3, 0)
        dirEntry(img, sub + 32, name11("..", ""), 0x10, 0, 0)
        dirEntry(img, sub + 64, name11("INNER", "TXT"), 0x20, 7, 5)
        "inner".toByteArray(Charsets.US_ASCII).copyInto(img, clusterOff(7))
        val file = File(tmp, "fat12.img")
        file.writeBytes(img)
        return file
    }

    private fun buildFat16Image(): File {
        val img = ByteArray(20000 * 512)
        bootSector(
            img, 512, 4, 1, 2, 512, 20000, 0, 32, 0, 2, 54, "FAT16   "
        )
        val fat1 = 512
        val fat2 = 33 * 512
        for (fat in intArrayOf(fat1, fat2)) {
            setFat16(img, fat, 2, 4)
            setFat16(img, fat, 4, 3)
            setFat16(img, fat, 3, 0xFFFF)
            setFat16(img, fat, 5, 0xFFFF)
            setFat16(img, fat, 6, 0xFFFF)
            setFat16(img, fat, 7, 0xFFFF)
        }
        val root = 65 * 512
        val data = 97 * 512
        fun clusterOff(n: Int) = data + (n - 2) * 2048
        var pos = root
        dirEntry(img, pos, name11("DATA", "BIN"), 0x20, 2, 3000)
        val dataBytes = ByteArray(3000) { i -> (i % 251).toByte() }
        dataBytes.copyInto(img, clusterOff(2), 0, 2048)
        dataBytes.copyInto(img, clusterOff(4), 2048, 3000)
        pos += 32
        dirEntry(img, pos, name11("DOCS", ""), 0x10, 5, 0)
        pos += 32
        val alias = name11("MYDOCU~1", "TXT")
        val lfnEnd = lfnSlots(img, pos, "My Document.txt", alias)
        dirEntry(img, lfnEnd, alias, 0x20, 6, 9)
        "document\n".toByteArray(Charsets.US_ASCII).copyInto(img, clusterOff(6))
        val docs = clusterOff(5)
        dirEntry(img, docs, name11(".", ""), 0x10, 5, 0)
        dirEntry(img, docs + 32, name11("..", ""), 0x10, 0, 0)
        dirEntry(img, docs + 64, name11("NOTE", "TXT"), 0x20, 7, 4)
        "note".toByteArray(Charsets.US_ASCII).copyInto(img, clusterOff(7))
        val file = File(tmp, "fat16.img")
        file.writeBytes(img)
        return file
    }

    private fun buildFat32Image(): File {
        // Berkas fisik 1 MiB; BPB mengklaim 600000 sektor (~293 MiB)
        // supaya jumlah klaster >= 65525 (FAT32). Klaster yang dipakai
        // bernomor kecil, jadi semuanya muat di berkas fisik.
        val img = ByteArray(1024 * 1024)
        bootSector(
            img, 512, 8, 32, 2, 0, 0, 600000, 0, 256, 2, 82, "FAT32   "
        )
        val fat1 = 32 * 512
        val fat2 = 288 * 512
        for (fat in intArrayOf(fat1, fat2)) {
            setFat32(img, fat, 2, 0x0FFFFFFFL)
            setFat32(img, fat, 3, 5)
            setFat32(img, fat, 5, 4)
            setFat32(img, fat, 4, 0x0FFFFFFFL)
            setFat32(img, fat, 6, 0x0FFFFFFFL)
            setFat32(img, fat, 7, 0x0FFFFFFFL)
            setFat32(img, fat, 8, 0x0FFFFFFFL)
        }
        val data = 544 * 512
        fun clusterOff(n: Int) = data + (n - 2) * 4096
        var pos = clusterOff(2)
        dirEntry(img, pos, name11("KERNEL", "BIN"), 0x20, 3, 5000)
        val kernel = ByteArray(5000) { i -> (i % 251).toByte() }
        kernel.copyInto(img, clusterOff(3), 0, 4096)
        kernel.copyInto(img, clusterOff(5), 4096, 5000)
        pos += 32
        dirEntry(img, pos, name11("SYSTEM", ""), 0x10, 6, 0)
        pos += 32
        val alias = name11("README~1", "TXT")
        val lfnEnd = lfnSlots(img, pos, "Readme File.txt", alias)
        dirEntry(img, lfnEnd, alias, 0x20, 7, 6)
        "readme".toByteArray(Charsets.US_ASCII).copyInto(img, clusterOff(7))
        val sys = clusterOff(6)
        dirEntry(img, sys, name11(".", ""), 0x10, 6, 0)
        dirEntry(img, sys + 32, name11("..", ""), 0x10, 2, 0)
        // Ekstensi 8.3 maksimal 3 huruf — BUILD.PROP (4 huruf) hanya
        // bisa lewat LFN; aliasnya BUILD.PRP.
        val buildAlias = name11("BUILD", "PRP")
        val buildLfnEnd = lfnSlots(img, sys + 64, "BUILD.PROP", buildAlias)
        dirEntry(img, buildLfnEnd, buildAlias, 0x20, 8, 11)
        "ro.build=1\n".toByteArray(Charsets.US_ASCII).copyInto(img, clusterOff(8))
        val file = File(tmp, "fat32.img")
        file.writeBytes(img)
        return file
    }

    // ---------- tes ----------

    @Test
    fun fat12RootListing() {
        val img = buildFat12Image()
        val reader = FatReader.open(img)
        assertNotNull(reader)
        reader!!.use {
            assertEquals(FatType.FAT12, it.type)
            val root = it.listDir("")
            assertNotNull(root)
            assertEquals(
                listOf("SUB", "EMPTY.BIN", "HELLO.TXT", "LONGFILENAME.TXT"),
                root!!.map { e -> e.name }
            )
            val sub = root.first { e -> e.name == "SUB" }
            assertTrue(sub.isDir)
            assertEquals(0L, sub.size)
            val empty = root.first { e -> e.name == "EMPTY.BIN" }
            assertFalse(empty.isDir)
            assertEquals(0L, empty.size)
            assertEquals(0L, empty.firstCluster)
            val hello = root.first { e -> e.name == "HELLO.TXT" }
            assertEquals(12L, hello.size)
        }
    }

    @Test
    fun fat12SubdirNavigation() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val sub = r.listDir("SUB")
            assertNotNull(sub)
            assertEquals(listOf("INNER.TXT"), sub!!.map { e -> e.name })
            val inner = r.resolveEntry("SUB/INNER.TXT")
            assertNotNull(inner)
            assertEquals(5L, inner!!.size)
            assertEquals("inner", r.readFileText("SUB/INNER.TXT"))
            assertNotNull(r.resolvePath("SUB"))
            assertNull(r.resolvePath("HELLO.TXT"))
            assertNull(r.resolvePath("NOPE"))
        }
    }

    @Test
    fun fat12MultiClusterFileRead() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val entry = r.resolveEntry("LONGFILENAME.TXT")
            assertNotNull(entry)
            assertEquals(1500L, entry!!.size)
            val bytes = r.readFileBytes(entry, 1_000_000L)
            assertNotNull(bytes)
            assertEquals(1500, bytes!!.size)
            assertEquals('A'.code.toByte(), bytes[0])
            assertEquals('A'.code.toByte(), bytes[511])
            assertEquals('B'.code.toByte(), bytes[512])
            assertEquals('B'.code.toByte(), bytes[1023])
            assertEquals('C'.code.toByte(), bytes[1024])
            assertEquals('C'.code.toByte(), bytes[1499])
            assertEquals("Hello FAT12\n", r.readFileText("HELLO.TXT"))
        }
    }

    @Test
    fun fat12LfnNames() {
        val good = buildFat12Image()
        FatReader.open(good)!!.use { r ->
            assertNotNull(r.listDir("")!!.firstOrNull { e -> e.name == "LONGFILENAME.TXT" })
        }
        // Checksum LFN salah -> jatuh kembali ke nama 8.3.
        val bad = buildFat12Image(corruptLfn = true)
        FatReader.open(bad)!!.use { r ->
            val names = r.listDir("")!!.map { e -> e.name }
            assertTrue(names.contains("LONGFI~1.TXT"))
            assertFalse(names.contains("LONGFILENAME.TXT"))
        }
    }

    @Test
    fun fat16RootAndSubdir() {
        val img = buildFat16Image()
        val reader = FatReader.open(img)
        assertNotNull(reader)
        reader!!.use {
            assertEquals(FatType.FAT16, it.type)
            assertEquals(
                listOf("DOCS", "DATA.BIN", "My Document.txt"),
                it.listDir("")!!.map { e -> e.name }
            )
            val data = it.resolveEntry("DATA.BIN")
            assertNotNull(data)
            assertEquals(3000L, data!!.size)
            val bytes = it.readFileBytes(data, 1_000_000L)
            assertNotNull(bytes)
            assertEquals(3000, bytes!!.size)
            for (i in 0 until 3000) {
                assertEquals((i % 251).toByte(), bytes[i])
            }
            assertEquals(listOf("NOTE.TXT"), it.listDir("DOCS")!!.map { e -> e.name })
            assertEquals("note", it.readFileText("DOCS/NOTE.TXT"))
        }
    }

    @Test
    fun fat16Lfn() {
        val img = buildFat16Image()
        FatReader.open(img)!!.use { r ->
            assertEquals("document\n", r.readFileText("My Document.txt"))
        }
    }

    @Test
    fun fat32RootViaClusterChain() {
        val img = buildFat32Image()
        val reader = FatReader.open(img)
        assertNotNull(reader)
        reader!!.use {
            assertEquals(FatType.FAT32, it.type)
            assertEquals(
                listOf("SYSTEM", "KERNEL.BIN", "Readme File.txt"),
                it.listDir("")!!.map { e -> e.name }
            )
            val kernel = it.resolveEntry("KERNEL.BIN")
            assertNotNull(kernel)
            assertEquals(5000L, kernel!!.size)
            val bytes = it.readFileBytes(kernel, 1_000_000L)
            assertNotNull(bytes)
            assertEquals(5000, bytes!!.size)
            for (i in 0 until 5000) {
                assertEquals((i % 251).toByte(), bytes[i])
            }
        }
    }

    @Test
    fun fat32SubdirAndText() {
        val img = buildFat32Image()
        FatReader.open(img)!!.use { r ->
            assertEquals(listOf("BUILD.PROP"), r.listDir("SYSTEM")!!.map { e -> e.name })
            assertEquals("ro.build=1\n", r.readFileText("SYSTEM/BUILD.PROP"))
            assertEquals("readme", r.readFileText("Readme File.txt"))
        }
    }

    @Test
    fun fat32Lfn() {
        val img = buildFat32Image()
        FatReader.open(img)!!.use { r ->
            assertNotNull(r.listDir("")!!.firstOrNull { e -> e.name == "Readme File.txt" })
        }
    }

    @Test
    fun emptyFileReadsEmpty() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            assertEquals("", r.readFileText("EMPTY.BIN"))
            val out = r.extractEntry("EMPTY.BIN", tmp)
            assertNotNull(out)
            assertEquals(0L, out!!.length())
        }
    }

    @Test
    fun openRejectsCorruptBpb() {
        // Bytes/sector bukan pangkat dua.
        val badBps = buildFat12Image().readBytes()
        u16(badBps, 11, 1000)
        val f1 = File(tmp, "bad-bps.img")
        f1.writeBytes(badBps)
        assertNull(FatReader.open(f1))
        // Total sektor nol.
        val badTot = buildFat12Image().readBytes()
        u16(badTot, 19, 0)
        u32(badTot, 32, 0)
        val f2 = File(tmp, "bad-tot.img")
        f2.writeBytes(badTot)
        assertNull(FatReader.open(f2))
        // FAT32 dengan entri root bukan nol (harus 0 untuk FAT32).
        val bad32 = buildFat32Image().readBytes()
        u16(bad32, 17, 512)
        val f3 = File(tmp, "bad-32.img")
        f3.writeBytes(bad32)
        assertNull(FatReader.open(f3))
        // Tanda boot 55 AA hilang.
        val badSig = buildFat12Image().readBytes()
        badSig[510] = 0
        badSig[511] = 0
        val f4 = File(tmp, "bad-sig.img")
        f4.writeBytes(badSig)
        assertNull(FatReader.open(f4))
        // Berkas terlalu pendek.
        val tiny = File(tmp, "tiny.img")
        tiny.writeBytes(ByteArray(100))
        assertNull(FatReader.open(tiny))
    }

    @Test
    fun brokenChainFailsCleanly() {
        // Rantai berputar (4->5, 5->4): baca gagal, tidak menggantung.
        val looped = buildFat12Image(breakChain = 1)
        FatReader.open(looped)!!.use { r ->
            val entry = r.resolveEntry("LONGFILENAME.TXT")
            assertNotNull(entry)
            assertNull(r.readFileBytes(entry!!, 1_000_000L))
            assertNull(r.readFileText("LONGFILENAME.TXT"))
        }
        // Rantai putus di klaster bebas (4->0).
        val freed = buildFat12Image(breakChain = 2)
        FatReader.open(freed)!!.use { r ->
            val entry = r.resolveEntry("LONGFILENAME.TXT")
            assertNotNull(entry)
            assertNull(r.readFileBytes(entry!!, 1_000_000L))
        }
    }

    @Test
    fun readFileTextBinaryReturnsNull() {
        val img = buildFat16Image()
        FatReader.open(img)!!.use { r ->
            // DATA.BIN mengandung byte 0x00 (pola i % 251).
            assertNull(r.readFileText("DATA.BIN"))
        }
    }

    @Test
    fun extractEntryAndExtractAll() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val dest = File(tmp, "out").apply { mkdirs() }
            val one = r.extractEntry("SUB/INNER.TXT", dest)
            assertNotNull(one)
            assertEquals("inner", one!!.readText())
            val all = File(tmp, "all").apply { mkdirs() }
            assertTrue(r.extractAll(all))
            assertEquals("Hello FAT12\n", File(all, "HELLO.TXT").readText())
            assertEquals("inner", File(all, "SUB/INNER.TXT").readText())
            assertEquals(1500, File(all, "LONGFILENAME.TXT").length())
            assertEquals(0L, File(all, "EMPTY.BIN").length())
            assertTrue(File(all, "SUB").isDirectory)
        }
    }

    // --- extractAll (LD3 r160: progres + batal) ---
    // Total entri fixture FAT12: root 4 + SUB 1 (INNER.TXT) = 5.

    @Test
    fun extractAll_progressReportsMonotonicToTotal() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val all = File(tmp, "allprog").apply { mkdirs() }
            val seen = mutableListOf<Pair<Long, Long>>()
            assertTrue(r.extractAll(all, onProgress = { done, total -> seen += done to total }))
            assertEquals(5, seen.size)
            assertTrue(seen.map { it.first }.zipWithNext().all { (a, b) -> b == a + 1 })
            assertEquals(5L to 5L, seen.last())
            assertEquals("Hello FAT12\n", File(all, "HELLO.TXT").readText())
        }
    }

    @Test
    fun extractAllPreservesPreExistingFile() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val all = File(tmp, "allkeep").apply { mkdirs() }
            File(all, "HELLO.TXT").writeText("ORIGINAL")
            assertTrue(r.extractAll(all))
            assertEquals("ORIGINAL", File(all, "HELLO.TXT").readText())
            assertEquals("Hello FAT12\n", File(all, "HELLO (1).TXT").readText())
        }
    }

    @Test
    fun extractEntryCollisionUsesUniqueName() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val dest = File(tmp, "collide").apply { mkdirs() }
            File(dest, "INNER.TXT").writeText("ORIGINAL")
            val out = r.extractEntry("SUB/INNER.TXT", dest)
            assertNotNull(out)
            assertEquals("INNER (1).TXT", out!!.name)
            assertEquals("inner", out.readText())
            assertEquals("ORIGINAL", File(dest, "INNER.TXT").readText())
        }
    }

    @Test
    fun extractAllCancelKeepsPreExistingFile() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val all = File(tmp, "allkeepcancel").apply { mkdirs() }
            File(all, "HELLO.TXT").writeText("ORIGINAL")
            val flag = AtomicBoolean(false)
            assertThrows(ImgCancelledException::class.java) {
                r.extractAll(
                    all,
                    onProgress = { done, _ -> if (done >= 1L) flag.set(true) },
                    cancelFlag = flag
                )
            }
            assertEquals("ORIGINAL", File(all, "HELLO.TXT").readText())
            assertEquals(listOf("HELLO.TXT"), all.walkTopDown().filter { it.isFile }.map { it.name }.toList())
        }
    }

    @Test
    fun extractAll_cancelMidwayDeletesWrittenAndThrows() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            val all = File(tmp, "allcancel").apply { mkdirs() }
            val flag = AtomicBoolean(false)
            // Batal setelah 1 entri: SUB sudah menulis INNER.TXT —
            // INNER.TXT harus ikut dibersihkan.
            assertThrows(ImgCancelledException::class.java) {
                r.extractAll(
                    all,
                    onProgress = { done, _ -> if (done >= 1L) flag.set(true) },
                    cancelFlag = flag
                )
            }
            assertTrue(all.walkTopDown().filter { it.isFile }.toList().isEmpty())
        }
    }

    @Test
    fun resolvePathEdges() {
        val img = buildFat12Image()
        FatReader.open(img)!!.use { r ->
            assertEquals(FatReader.ROOT_CLUSTER, r.resolvePath(""))
            assertNull(r.resolveEntry("NOPE"))
            assertNull(r.listDir("HELLO.TXT"))
            // ".." tidak pernah keluar dari root (normalisasi leksikal).
            val hello = r.resolveEntry("SUB/../HELLO.TXT")
            assertNotNull(hello)
            assertEquals(12L, hello!!.size)
            // FAT tidak peka huruf besar-kecil.
            val inner = r.resolveEntry("sub/inner.txt")
            assertNotNull(inner)
            assertEquals(5L, inner!!.size)
        }
    }

    @Test
    fun detectImgTypeFat() {
        assertEquals(ImgType.FAT, ImgOps.detectImgType(buildFat12Image()))
        assertEquals(ImgType.FAT, ImgOps.detectImgType(buildFat16Image()))
        assertEquals(ImgType.FAT, ImgOps.detectImgType(buildFat32Image()))
    }

    @Test
    fun openRejectsTruncated() {
        // Berkas terpotong sebelum wilayah FAT berakhir.
        val full = buildFat12Image().readBytes()
        val cut = File(tmp, "cut.img")
        cut.writeBytes(full.copyOf(600))
        assertNull(FatReader.open(cut))
    }

    @Test
    fun fat12SparseDirectMatchesRaw() {
        val rawFile = buildFat12Image()
        val sparse = sparseFile(rawFile.readBytes(), "fat12-sparse.img")
        assertEquals(ImgType.SPARSE, ImgOps.detectImgType(sparse))
        FatReader.open(rawFile)!!.use { r ->
            FatReader.openSparse(sparse)!!.use { s ->
                assertEquals(r.type, s.type)
                assertEquals(
                    r.listDir("")!!.map { it.name },
                    s.listDir("")!!.map { it.name }
                )
                assertEquals(r.readFileText("HELLO.TXT"), s.readFileText("HELLO.TXT"))
                val rawBytes = r.readFileBytes(r.resolveEntry("LONGFILENAME.TXT")!!, 1_000_000L)
                val sparseBytes = s.readFileBytes(s.resolveEntry("LONGFILENAME.TXT")!!, 1_000_000L)
                assertArrayEquals(rawBytes, sparseBytes)
                assertEquals(listOf("INNER.TXT"), s.listDir("SUB")!!.map { it.name })
                assertEquals("inner", s.readFileText("SUB/INNER.TXT"))
            }
        }
    }

    @Test
    fun fat16SparseDirectMatchesRaw() {
        val rawFile = buildFat16Image()
        val sparse = sparseFile(rawFile.readBytes(), "fat16-sparse.img")
        assertEquals(ImgType.SPARSE, ImgOps.detectImgType(sparse))
        FatReader.open(rawFile)!!.use { r ->
            FatReader.openSparse(sparse)!!.use { s ->
                assertEquals(r.type, s.type)
                assertEquals(
                    r.listDir("")!!.map { it.name },
                    s.listDir("")!!.map { it.name }
                )
                val rawBytes = r.readFileBytes(r.resolveEntry("DATA.BIN")!!, 1_000_000L)
                val sparseBytes = s.readFileBytes(s.resolveEntry("DATA.BIN")!!, 1_000_000L)
                assertArrayEquals(rawBytes, sparseBytes)
                assertEquals(r.listDir("DOCS")!!.map { it.name }, s.listDir("DOCS")!!.map { it.name })
                assertEquals(r.readFileText("My Document.txt"), s.readFileText("My Document.txt"))
                assertEquals(r.readFileText("DOCS/NOTE.TXT"), s.readFileText("DOCS/NOTE.TXT"))
            }
        }
    }

    @Test
    fun fat32SparseDirectMatchesRaw() {
        val rawFile = buildFat32Image()
        val sparse = sparseFile(rawFile.readBytes(), "fat32-sparse.img")
        assertEquals(ImgType.SPARSE, ImgOps.detectImgType(sparse))
        FatReader.open(rawFile)!!.use { r ->
            FatReader.openSparse(sparse)!!.use { s ->
                assertEquals(r.type, s.type)
                assertEquals(
                    r.listDir("")!!.map { it.name },
                    s.listDir("")!!.map { it.name }
                )
                val rawBytes = r.readFileBytes(r.resolveEntry("KERNEL.BIN")!!, 1_000_000L)
                val sparseBytes = s.readFileBytes(s.resolveEntry("KERNEL.BIN")!!, 1_000_000L)
                assertArrayEquals(rawBytes, sparseBytes)
                assertEquals(r.listDir("SYSTEM")!!.map { it.name }, s.listDir("SYSTEM")!!.map { it.name })
                assertEquals(r.readFileText("SYSTEM/BUILD.PROP"), s.readFileText("SYSTEM/BUILD.PROP"))
                assertEquals(r.readFileText("Readme File.txt"), s.readFileText("Readme File.txt"))
            }
        }
    }

    @Test
    fun fat16SparseMixedChunksMatchesRaw() {
        val raw = buildFat16Image().readBytes()
        // 32 blok pertama (128 KiB) mencakup boot + FAT + root +
        // semua klaster terpakai; sisanya nol semua.
        assertTrue(raw.copyOfRange(32 * 4096, raw.size).all { it == 0.toByte() })
        val f = File(tmp, "fat16-mixed.img")
        f.writeBytes(sparseWrapMixed(raw, 32))
        FatReader.openSparse(f)!!.use { s ->
            assertEquals(
                listOf("DOCS", "DATA.BIN", "My Document.txt"),
                s.listDir("")!!.map { it.name }
            )
            val data = s.resolveEntry("DATA.BIN")!!
            val bytes = s.readFileBytes(data, 1_000_000L)!!
            assertEquals(3000, bytes.size)
            for (i in 0 until 3000) assertEquals((i % 251).toByte(), bytes[i])
        }
    }

    @Test
    fun fatSparseCorruptFailsCleanly() {
        val raw = buildFat16Image().readBytes()
        // Magic sparse rusak.
        val badMagic = sparseWrap(raw)
        badMagic[0] = 0
        val f1 = File(tmp, "bad-magic.img")
        f1.writeBytes(badMagic)
        assertNull(FatReader.openSparse(f1))
        // BPB rusak di dalam sparse yang valid.
        val badBpb = raw.copyOf()
        badBpb[510] = 0
        badBpb[511] = 0
        val f2 = File(tmp, "bad-bpb.img")
        f2.writeBytes(sparseWrap(badBpb))
        assertNull(FatReader.openSparse(f2))
        // Sparse terpotong di tengah data chunk.
        val f3 = File(tmp, "cut.img")
        f3.writeBytes(sparseWrap(raw).copyOf(28 + 12 + 100))
        assertNull(FatReader.openSparse(f3))
    }

    // ---------- sparse langsung (LD2 r158) ----------

    /** Bungkus citra mentah dalam satu chunk RAW sparse (blk 4 KiB). */
    private fun sparseWrap(raw: ByteArray, blkSz: Int = 4096): ByteArray {
        val totalBlks = raw.size / blkSz
        val out = ByteArrayOutputStream()
        val hdr = ByteArray(28)
        u32(hdr, 0, 0xED26FF3AL)
        u16(hdr, 4, 1)
        u16(hdr, 6, 0)
        u16(hdr, 8, 28)
        u16(hdr, 10, 12)
        u32(hdr, 12, blkSz.toLong())
        u32(hdr, 16, totalBlks.toLong())
        u32(hdr, 20, 1L)
        u32(hdr, 24, 0L)
        out.write(hdr)
        val chunk = ByteArray(12)
        u16(chunk, 0, 0xCAC1)
        u16(chunk, 2, 0)
        u32(chunk, 4, totalBlks.toLong())
        u32(chunk, 8, (12 + raw.size).toLong())
        out.write(chunk)
        out.write(raw)
        return out.toByteArray()
    }

    /** Bungkus citra mentah: RAW untuk [rawBlocks] blok pertama,
     *  DONT_CARE untuk sisanya (sisanya harus nol semua). */
    private fun sparseWrapMixed(raw: ByteArray, rawBlocks: Int, blkSz: Int = 4096): ByteArray {
        val totalBlks = raw.size / blkSz
        val out = ByteArrayOutputStream()
        val hdr = ByteArray(28)
        u32(hdr, 0, 0xED26FF3AL)
        u16(hdr, 4, 1)
        u16(hdr, 6, 0)
        u16(hdr, 8, 28)
        u16(hdr, 10, 12)
        u32(hdr, 12, blkSz.toLong())
        u32(hdr, 16, totalBlks.toLong())
        u32(hdr, 20, 2L)
        u32(hdr, 24, 0L)
        out.write(hdr)
        val rawBytes = rawBlocks * blkSz
        val rawHdr = ByteArray(12)
        u16(rawHdr, 0, 0xCAC1)
        u16(rawHdr, 2, 0)
        u32(rawHdr, 4, rawBlocks.toLong())
        u32(rawHdr, 8, (12 + rawBytes).toLong())
        out.write(rawHdr)
        out.write(raw, 0, rawBytes)
        val dcHdr = ByteArray(12)
        u16(dcHdr, 0, 0xCAC3)
        u16(dcHdr, 2, 0)
        u32(dcHdr, 4, (totalBlks - rawBlocks).toLong())
        u32(dcHdr, 8, 12L)
        out.write(dcHdr)
        return out.toByteArray()
    }

    private fun sparseFile(raw: ByteArray, name: String): File {
        val f = File(tmp, name)
        f.writeBytes(sparseWrap(raw))
        return f
    }
}
