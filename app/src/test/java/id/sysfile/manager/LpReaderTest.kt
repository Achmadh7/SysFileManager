package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
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

/** Tes LpReader terhadap fixture super.img yang dibangun tangan di
 *  sini (geometri + metadata + extent ditulis byte per byte, checksum
 *  SHA-256 dihitung seperti liblp) — geometri fixture (max_size
 *  16384, slot_count 2, logical_block_size 4096) BERBEDA dari citra
 *  cross-check Python (~/workspace/jvtest/r156/pylp.py). */
class LpReaderTest {

    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("lp-test").toFile()
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // --- perilaku utama ---

    @Test
    fun openListsPartitions() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        assertEquals(0, reader.slotNumber)
        val parts = reader.partitions
        assertEquals(
            listOf("system", "vendor", "product_a", "odm", "misc"),
            parts.map { it.name }
        )
        assertEquals(listOf(6144L, 2560L, 1024L, 512L, 512L), parts.map { it.sizeBytes })
        assertEquals(listOf(true, true, true, false, false), parts.map { it.available })
        assertEquals(listOf(0x1, 0x0, 0x2, 0x8, 0x0), parts.map { it.attributes })
        reader.close()
    }

    @Test
    fun materializeLinearAndZero() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        val system = reader.partitions.first { it.name == "system" }
        val out = File(tmpDir, "system.img")
        assertTrue(reader.materialize(system, out))
        val expected = ByteArrayOutputStream()
        for (s in 2048L..2055L) expected.write(sectorText("SYS-$s"))
        expected.write(ByteArray(4 * 512))
        assertArrayEquals(expected.toByteArray(), out.readBytes())
        assertEquals(6144L, out.length())
        reader.close()
    }

    @Test
    fun materializeFragmented() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        val vendor = reader.partitions.first { it.name == "vendor" }
        val out = File(tmpDir, "vendor.img")
        assertTrue(reader.materialize(vendor, out))
        val expected = ByteArrayOutputStream()
        for (s in 2056L..2058L) expected.write(sectorFill((s % 251).toInt()))
        for (s in 2300L..2301L) expected.write(sectorText("VND-$s"))
        assertArrayEquals(expected.toByteArray(), out.readBytes())
        reader.close()
    }

    @Test
    fun materializeUnavailableReturnsFalse() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        for (name in listOf("odm", "misc")) {
            val part = reader.partitions.first { it.name == name }
            val out = File(tmpDir, "$name.img")
            assertFalse(reader.materialize(part, out))
            assertFalse(out.exists())
        }
        reader.close()
    }

    @Test
    fun materializeOverCapReturnsFalse() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        val system = reader.partitions.first { it.name == "system" }
        val out = File(tmpDir, "system.img")
        assertFalse(reader.materialize(system, out, maxBytes = 1024))
        assertFalse(out.exists())
        reader.close()
    }

    @Test
    fun materializeProgressReportsMonotonicToTotal() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        val system = reader.partitions.first { it.name == "system" }
        val out = File(tmpDir, "system.img")
        val reports = mutableListOf<Pair<Long, Long>>()
        assertTrue(reader.materialize(system, out, onProgress = { done, total -> reports += done to total }))
        assertTrue(reports.isNotEmpty())
        assertTrue(reports.all { it.second == 6144L })
        val dones = reports.map { it.first }
        assertEquals(dones.sorted(), dones)
        assertEquals(6144L, dones.last())
        reader.close()
    }

    @Test
    fun materializeCancelBeforeStartThrowsAndDeletesPartial() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        val system = reader.partitions.first { it.name == "system" }
        val out = File(tmpDir, "system.img")
        val flag = AtomicBoolean(true)
        assertThrows(ImgCancelledException::class.java) {
            reader.materialize(system, out, cancelFlag = flag)
        }
        assertFalse(out.exists())
        reader.close()
    }

    @Test
    fun materializeCancelMidwayThrowsAndDeletesPartial() {
        val reader = LpReader.open(writeTmp(buildSuper()))!!
        val system = reader.partitions.first { it.name == "system" }
        val out = File(tmpDir, "system.img")
        val flag = AtomicBoolean(false)
        val reports = mutableListOf<Pair<Long, Long>>()
        assertThrows(ImgCancelledException::class.java) {
            reader.materialize(system, out,
                onProgress = { done, total ->
                    reports += done to total
                    if (done >= 4096L) flag.set(true)
                },
                cancelFlag = flag)
        }
        assertFalse(out.exists())
        assertTrue(reports.isNotEmpty())
        reader.close()
    }

    // --- metadata rusak -> null ---

    @Test
    fun openRejectsBadGeometryMagic() {
        val img = buildSuper()
        img[4096] = (img[4096].toInt() xor 0xFF).toByte()
        img[8192] = (img[8192].toInt() xor 0xFF).toByte()
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsBadGeometryChecksum() {
        val img = buildSuper()
        img[4096 + 48] = (img[4096 + 48].toInt() xor 0x01).toByte()
        img[8192 + 48] = (img[8192 + 48].toInt() xor 0x01).toByte()
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsBadHeaderMagic() {
        val img = buildSuper()
        for (off in slotOffsets()) img[off] = (img[off].toInt() xor 0xFF).toByte()
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsBadHeaderChecksum() {
        val img = buildSuper()
        for (off in slotOffsets()) img[off + 128] = (img[off + 128].toInt() xor 0x01).toByte()
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsBadTablesChecksum() {
        val img = buildSuper()
        for (off in slotOffsets()) img[off + 256] = 'x'.code.toByte()
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsBadTableBounds() {
        val img = buildSuper()
        for (off in slotOffsets()) {
            putU32(img, off + 80, 99999L)
            fixChecksums(img, off)
        }
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsPartitionExtentOverflow() {
        val img = buildSuper()
        for (off in slotOffsets()) {
            putU32(img, off + 256 + 44, 99L)
            fixChecksums(img, off)
        }
        assertNull(LpReader.open(writeTmp(img)))
    }

    @Test
    fun openRejectsBadExtentSource() {
        val parts = defaultParts().toMutableList()
        parts[4] = FixPart("misc", 0x0, listOf(FixExtent(1, 0, 2062, 5)))
        assertNull(LpReader.open(writeTmp(buildSuper(parts = parts))))
    }

    @Test
    fun openRejectsDuplicateName() {
        val parts = defaultParts().toMutableList()
        parts += FixPart("system", 0x0, listOf(FixExtent(1, 0, 2063, 0)))
        assertNull(LpReader.open(writeTmp(buildSuper(parts = parts))))
    }

    @Test
    fun openRejectsZeroExtents() {
        val parts = defaultParts().toMutableList()
        parts += FixPart("empty", 0x0, emptyList())
        assertNull(LpReader.open(writeTmp(buildSuper(parts = parts))))
    }

    @Test
    fun openRejectsLongSuffixedName() {
        val longName = "a".repeat(35)
        val parts = listOf(FixPart(longName, 0x2, listOf(FixExtent(1, 0, 2048, 0))))
        assertNull(LpReader.open(writeTmp(buildSuper(parts = parts))))
    }

    // --- slot cadangan ---

    @Test
    fun backupSlotFallback() {
        val img = buildSuper()
        img[12288] = (img[12288].toInt() xor 0xFF).toByte()
        val reader = LpReader.open(writeTmp(img))!!
        assertEquals(0, reader.slotNumber)
        assertTrue(reader.partitions.any { it.name == "product_a" })
        reader.close()
    }

    @Test
    fun slot1FallbackWhenSlot0Dead() {
        val img = buildSuper()
        img[12288] = (img[12288].toInt() xor 0xFF).toByte()
        img[45056] = (img[45056].toInt() xor 0xFF).toByte()
        val reader = LpReader.open(writeTmp(img))!!
        assertEquals(1, reader.slotNumber)
        assertTrue(reader.partitions.any { it.name == "product_b" })
        reader.close()
    }

    // --- sparse + deteksi + sesi ---

    @Test
    fun sparseSuperOpens() {
        val raw = buildSuper()
        val sparseFile = writeTmp(sparseWrap(raw), "super-sparse.img")
        assertEquals(ImgType.SPARSE, ImgOps.detectImgType(sparseFile))
        val tmp = File(tmpDir, "unsparsed.img")
        assertTrue(ImgOps.unsparse(sparseFile, tmp) is UnsparseResult.Ok)
        val reader = LpReader.open(tmp)
        assertNotNull(reader)
        assertEquals(
            listOf("system", "vendor", "product_a", "odm", "misc"),
            reader!!.partitions.map { it.name }
        )
        reader.close()
    }

    @Test
    fun detectImgTypeSuper() {
        assertEquals(ImgType.SUPER, ImgOps.detectImgType(writeTmp(buildSuper())))
        val plain = File(tmpDir, "plain.bin")
        plain.writeBytes(ByteArray(8192) { (it % 251).toByte() })
        assertEquals(ImgType.RAW, ImgOps.detectImgType(plain))
    }

    @Test
    fun lpSessionCloseDeletesTemp() {
        val img = writeTmp(buildSuper())
        val reader = LpReader.open(img)!!
        val tmp = File(tmpDir, "temp.img").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val session = LpSession(img, tmp, reader)
        session.close()
        assertFalse(tmp.exists())
    }

    @Test
    fun extentBeyondFileUnavailable() {
        val parts = defaultParts().toMutableList()
        parts += FixPart("far", 0x0, listOf(FixExtent(1, 0, 5000, 0)))
        val reader = LpReader.open(writeTmp(buildSuper(parts = parts)))!!
        val far = reader.partitions.first { it.name == "far" }
        assertFalse(far.available)
        assertEquals(512L, far.sizeBytes)
        assertFalse(reader.materialize(far, File(tmpDir, "far.img")))
        reader.close()
    }

    // --- sparse langsung (LD2 r158) ---

    @Test
    fun sparseSuperDirectOpens() {
        val raw = buildSuper()
        val sparseFile = writeTmp(sparseWrap(raw), "super-sparse.img")
        assertEquals(ImgType.SPARSE, ImgOps.detectImgType(sparseFile))
        val reader = LpReader.openSparse(sparseFile)
        assertNotNull(reader)
        reader!!.use {
            assertEquals(0, it.slotNumber)
            assertEquals(
                listOf("system", "vendor", "product_a", "odm", "misc"),
                it.partitions.map { p -> p.name }
            )
            assertEquals(
                listOf(true, true, true, false, false),
                it.partitions.map { p -> p.available }
            )
        }
    }

    @Test
    fun sparseSuperMaterializeMatchesRaw() {
        val raw = buildSuper()
        val sparseFile = writeTmp(sparseWrap(raw), "super-sparse.img")
        // Jalur lama: unsparse dulu, buka mentah.
        val tmp = File(tmpDir, "unsparsed.img")
        assertTrue(ImgOps.unsparse(sparseFile, tmp) is UnsparseResult.Ok)
        val rawReader = LpReader.open(tmp)!!
        val system = rawReader.partitions.first { it.name == "system" }
        val outRaw = File(tmpDir, "system-raw.img")
        assertTrue(rawReader.materialize(system, outRaw))
        rawReader.close()
        // Jalur baru: baca langsung dari sparse.
        val sparseReader = LpReader.openSparse(sparseFile)!!
        val outSparse = File(tmpDir, "system-sparse.img")
        assertTrue(sparseReader.materialize(system, outSparse))
        sparseReader.close()
        assertArrayEquals(outRaw.readBytes(), outSparse.readBytes())
    }

    @Test
    fun sparseSuperCorruptFailsCleanly() {
        val raw = buildSuper()
        // Magic sparse rusak.
        val badMagic = sparseWrap(raw)
        badMagic[0] = 0
        assertNull(LpReader.openSparse(writeTmp(badMagic, "bad-magic.img")))
        // Geometri primer + cadangan rusak (magic dicek keduanya).
        val badGeo = raw.copyOf()
        badGeo[4096] = (badGeo[4096].toInt() xor 0xFF).toByte()
        badGeo[8192] = (badGeo[8192].toInt() xor 0xFF).toByte()
        assertNull(LpReader.openSparse(writeTmp(sparseWrap(badGeo), "bad-geo.img")))
        // Sparse terpotong di tengah data chunk.
        val cut = sparseWrap(raw).copyOf(28 + 12 + 100)
        assertNull(LpReader.openSparse(writeTmp(cut, "cut.img")))
    }

    // --- fixture ---

    private data class FixPart(
        val name: String,
        val attrs: Int,
        val extents: List<FixExtent>,
        val groupIndex: Int = 0
    )

    private data class FixExtent(
        val numSectors: Long,
        val type: Int = 0,
        val targetData: Long = 0,
        val targetSource: Int = 0
    )

    private data class FixDevice(
        val firstLogicalSector: Long,
        val alignment: Int,
        val alignmentOffset: Int,
        val size: Long,
        val name: String,
        val flags: Int
    )

    private fun defaultParts() = listOf(
        FixPart("system", 0x1, listOf(FixExtent(8, 0, 2048, 0), FixExtent(4, 1))),
        FixPart("vendor", 0x0, listOf(FixExtent(3, 0, 2056, 0), FixExtent(2, 0, 2300, 0))),
        FixPart("product", 0x2, listOf(FixExtent(2, 0, 2059, 0))),
        FixPart("odm", 0x8, listOf(FixExtent(1, 0, 2061, 0))),
        FixPart("misc", 0x0, listOf(FixExtent(1, 0, 2062, 1)))
    )

    private fun defaultData(): Map<Long, ByteArray> {
        val m = mutableMapOf<Long, ByteArray>()
        for (s in 2048L..2055L) m[s] = sectorText("SYS-$s")
        for (s in 2056L..2058L) m[s] = sectorFill((s % 251).toInt())
        for (s in 2059L..2060L) m[s] = sectorFill(0xAB)
        m[2061L] = sectorText("ODM-HIDDEN")
        m[2062L] = sectorText("MISC-OTHER")
        for (s in 2300L..2301L) m[s] = sectorText("VND-$s")
        return m
    }

    /** Offset 4 slot metadata fixture default (max_size 16384,
     *  slot_count 2): primer slot 0/1 lalu cadangan slot 0/1. */
    private fun slotOffsets(): List<Int> = listOf(12288, 28672, 45056, 61440)

    private fun buildSuper(
        maxSize: Int = 16384,
        slotCount: Int = 2,
        lbs: Int = 4096,
        firstLogicalSector: Long = 2048,
        totalSectors: Long = 4096,
        parts: List<FixPart> = defaultParts(),
        devices: List<FixDevice>? = null,
        data: Map<Long, ByteArray> = defaultData(),
        minor: Int = 2,
        patch: ((ByteArray) -> Unit)? = null
    ): ByteArray {
        val devs = devices ?: listOf(
            FixDevice(firstLogicalSector, 1048576, 0, totalSectors * 512, "super", 0),
            FixDevice(firstLogicalSector, 1048576, 0, 1048576, "super2", 0)
        )
        val meta = buildMetadata(parts, devs, minor)
        require(meta.size <= maxSize) { "metadata ${meta.size} > maxSize $maxSize" }
        val geo = buildGeometry(maxSize, slotCount, lbs)
        val img = ByteArray((totalSectors * 512).toInt())
        geo.copyInto(img, 4096)
        geo.copyInto(img, 8192)
        val base = 12288
        for (slot in 0 until slotCount) {
            meta.copyInto(img, base + maxSize * slot)
        }
        val backupBase = base + maxSize * slotCount
        for (slot in 0 until slotCount) {
            meta.copyInto(img, backupBase + maxSize * slot)
        }
        for ((sector, content) in data) {
            require(content.size == 512)
            content.copyInto(img, (sector * 512).toInt())
        }
        patch?.invoke(img)
        return img
    }

    private fun buildGeometry(maxSize: Int, slotCount: Int, lbs: Int): ByteArray {
        val g = ByteArray(52)
        putU32(g, 0, 0x616c4467L)
        putU32(g, 4, 52L)
        putU32(g, 40, maxSize.toLong())
        putU32(g, 44, slotCount.toLong())
        putU32(g, 48, lbs.toLong())
        sha256(g).copyInto(g, 8)
        return g
    }

    private fun buildMetadata(
        parts: List<FixPart>,
        devices: List<FixDevice>,
        minor: Int = 2,
        groups: List<Triple<String, Int, Long>> = listOf(Triple("default", 0, 0L))
    ): ByteArray {
        val flatExtents = parts.flatMap { it.extents }
        val pt = ByteArray(parts.size * 52)
        var extIdx = 0
        for ((i, p) in parts.withIndex()) {
            val o = i * 52
            putName(pt, o, p.name)
            putU32(pt, o + 36, p.attrs.toLong())
            putU32(pt, o + 40, extIdx.toLong())
            putU32(pt, o + 44, p.extents.size.toLong())
            putU32(pt, o + 48, p.groupIndex.toLong())
            extIdx += p.extents.size
        }
        val et = ByteArray(flatExtents.size * 24)
        for ((i, e) in flatExtents.withIndex()) {
            val o = i * 24
            putU64(et, o, e.numSectors)
            putU32(et, o + 8, e.type.toLong())
            putU64(et, o + 12, e.targetData)
            putU32(et, o + 20, e.targetSource.toLong())
        }
        val gt = ByteArray(groups.size * 48)
        for ((i, g) in groups.withIndex()) {
            val o = i * 48
            putName(gt, o, g.first)
            putU32(gt, o + 36, g.second.toLong())
            putU64(gt, o + 40, g.third)
        }
        val dt = ByteArray(devices.size * 64)
        for ((i, d) in devices.withIndex()) {
            val o = i * 64
            putU64(dt, o, d.firstLogicalSector)
            putU32(dt, o + 8, d.alignment.toLong())
            putU32(dt, o + 12, d.alignmentOffset.toLong())
            putU64(dt, o + 16, d.size)
            putName(dt, o + 24, d.name)
            putU32(dt, o + 60, d.flags.toLong())
        }
        val tables = pt + et + gt + dt
        val headerSize = if (minor >= 2) 256 else 128
        val h = ByteArray(headerSize)
        putU32(h, 0, 0x414C5030L)
        putU16(h, 4, 10)
        putU16(h, 6, minor)
        putU32(h, 8, headerSize.toLong())
        putU32(h, 44, tables.size.toLong())
        sha256(tables).copyInto(h, 48)
        var off = 0
        val descs = listOf(
            parts.size to 52, flatExtents.size to 24,
            groups.size to 48, devices.size to 64
        )
        for ((idx, desc) in descs.withIndex()) {
            val base = 80 + idx * 12
            putU32(h, base, off.toLong())
            putU32(h, base + 4, desc.first.toLong())
            putU32(h, base + 8, desc.second.toLong())
            off += desc.first * desc.second
        }
        // Byte 12..44 masih nol — checksum header dihitung di atasnya.
        sha256(h).copyInto(h, 12)
        return h + tables
    }

    /** Hitung ulang checksum tabel + header satu slot setelah patch
     *  tabel (dipakai tes batas tabel / overflow extent). */
    private fun fixChecksums(img: ByteArray, slotOff: Int, headerSize: Int = 256) {
        val tablesSize = u32le(img, slotOff + 44).toInt()
        val tables = img.copyOfRange(slotOff + headerSize, slotOff + headerSize + tablesSize)
        sha256(tables).copyInto(img, slotOff + 48)
        val header = img.copyOfRange(slotOff, slotOff + headerSize)
        header.fill(0, 12, 44)
        sha256(header).copyInto(img, slotOff + 12)
    }

    /** Bungkus citra mentah jadi sparse (satu chunk RAW, checksum 0). */
    private fun sparseWrap(raw: ByteArray, blkSz: Int = 4096): ByteArray {
        val totalBlks = raw.size / blkSz
        val out = ByteArrayOutputStream()
        val hdr = ByteArray(28)
        putU32(hdr, 0, 0xED26FF3AL)
        putU16(hdr, 4, 1)
        putU16(hdr, 6, 0)
        putU16(hdr, 8, 28)
        putU16(hdr, 10, 12)
        putU32(hdr, 12, blkSz.toLong())
        putU32(hdr, 16, totalBlks.toLong())
        putU32(hdr, 20, 1L)
        putU32(hdr, 24, 0L)
        out.write(hdr)
        val chunk = ByteArray(12)
        putU16(chunk, 0, 0xCAC1)
        putU16(chunk, 2, 0)
        putU32(chunk, 4, totalBlks.toLong())
        putU32(chunk, 8, (12 + raw.size).toLong())
        out.write(chunk)
        out.write(raw)
        return out.toByteArray()
    }

    private fun writeTmp(bytes: ByteArray, name: String = "super.img"): File {
        val f = File(tmpDir, name)
        f.writeBytes(bytes)
        return f
    }

    private fun sectorFill(byte: Int): ByteArray = ByteArray(512) { byte.toByte() }

    private fun sectorText(text: String): ByteArray {
        val b = text.toByteArray(Charsets.US_ASCII)
        require(b.size <= 512)
        return b + ByteArray(512 - b.size)
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        for (i in 0 until 4) b[off + i] = ((v ushr (8 * i)) and 0xFF).toByte()
    }

    private fun putU64(b: ByteArray, off: Int, v: Long) {
        for (i in 0 until 8) b[off + i] = ((v ushr (8 * i)) and 0xFF).toByte()
    }

    private fun putName(b: ByteArray, off: Int, s: String) {
        val bytes = s.toByteArray(Charsets.US_ASCII)
        require(bytes.size <= 36)
        bytes.copyInto(b, off)
    }

    private fun u32le(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
