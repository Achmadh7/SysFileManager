package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tes SparseSource (LD2 r157): baca langsung dari citra sparse tanpa
 *  unsparse. Fixture dibangun sintetis di sini; hasil baca
 *  dibandingkan byte-per-byte dengan [ImgOps.unsparse] pada citra
 *  yang sama. */
class SparseReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- helpers fixture ---

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putAscii(b: ByteArray, off: Int, text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        for (i in bytes.indices) b[off + i] = bytes[i]
    }

    private fun writeFile(name: String, bytes: ByteArray): File {
        val f = File(tmp.root, name)
        FileOutputStream(f).use { it.write(bytes) }
        return f
    }

    /** Citra sparse dari chunk-chunk mentah (header 12 byte + data). */
    private fun sparseBytes(
        blkSz: Int,
        totalBlks: Long,
        chunks: List<ByteArray>,
        imageChecksum: Long = 0,
        major: Int = 1,
        magic: Long = 0xED26FF3AL
    ): ByteArray {
        val hdr = ByteArray(28)
        putU32(hdr, 0, magic)
        putU16(hdr, 4, major)
        putU16(hdr, 6, 0)
        putU16(hdr, 8, 28)
        putU16(hdr, 10, 12)
        putU32(hdr, 12, blkSz.toLong())
        putU32(hdr, 16, totalBlks)
        putU32(hdr, 20, chunks.size.toLong())
        putU32(hdr, 24, imageChecksum)
        val out = ByteArrayOutputStream()
        out.write(hdr)
        for (c in chunks) out.write(c)
        return out.toByteArray()
    }

    /** Chunk RAW: data harus kelipatan blk_sz. */
    private fun rawChunk(data: ByteArray, blkSz: Int): ByteArray {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0xCAC1)
        putU32(hdr, 4, (data.size / blkSz).toLong())
        putU32(hdr, 8, (12 + data.size).toLong())
        return hdr + data
    }

    private fun fillChunk(fill: Long, blocks: Long): ByteArray {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0xCAC2)
        putU32(hdr, 4, blocks)
        putU32(hdr, 8, 16L)
        val out = ByteArrayOutputStream()
        out.write(hdr)
        val v = ByteArray(4)
        putU32(v, 0, fill)
        out.write(v)
        return out.toByteArray()
    }

    private fun dontCareChunk(blocks: Long): ByteArray {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0xCAC3)
        putU32(hdr, 4, blocks)
        putU32(hdr, 8, 12L)
        return hdr
    }

    private fun crcChunk(value: Long): ByteArray {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0xCAC4)
        putU32(hdr, 4, 0L)
        putU32(hdr, 8, 16L)
        val out = ByteArrayOutputStream()
        out.write(hdr)
        val v = ByteArray(4)
        putU32(v, 0, value)
        out.write(v)
        return out.toByteArray()
    }

    /** Isi logis yang diharapkan dari chunk FILL: pola 4 byte berulang. */
    private fun fillBytes(fill: Long, size: Int): ByteArray {
        val v = ByteArray(4)
        putU32(v, 0, fill)
        return ByteArray(size) { v[it % 4] }
    }

    private fun unsparseBytes(file: File): ByteArray {
        val out = File(tmp.root, "out-${System.nanoTime()}.img")
        val result = ImgOps.unsparse(file, out)
        assertTrue(result is UnsparseResult.Ok)
        return out.readBytes()
    }

    private fun openSparse(bytes: ByteArray, name: String = "sparse.img"): SparseSource? {
        return SparseSource.open(writeFile(name, bytes))
    }

    // --- tes baca langsung ---

    @Test
    fun directReadMatchesUnsparseRawOnly() {
        val blkSz = 1024
        val d1 = ByteArray(blkSz) { (it % 251).toByte() }
        val d2 = ByteArray(blkSz) { ((it * 7) % 251).toByte() }
        val d3 = ByteArray(blkSz) { ((255 - it) % 251).toByte() }
        val bytes = sparseBytes(blkSz, 3, listOf(rawChunk(d1, blkSz), rawChunk(d2, blkSz), rawChunk(d3, blkSz)))
        val file = writeFile("raw-only.img", bytes)
        val expected = unsparseBytes(file)
        SparseSource.open(file)!!.use { src ->
            assertEquals(3L * blkSz, src.length())
            assertEquals(3, src.chunkCount)
            assertArrayEquals(expected, src.readAt(0, expected.size))
            // Baca sebagian di tengah chunk kedua.
            assertArrayEquals(expected.copyOfRange(1500, 2500), src.readAt(1500, 1000))
        }
    }

    @Test
    fun directReadFillAndDontCare() {
        val blkSz = 1024
        val fill = 0x11223344L
        val bytes = sparseBytes(blkSz, 4, listOf(
            fillChunk(fill, 2),
            dontCareChunk(2)
        ))
        openSparse(bytes, "fill.img")!!.use { src ->
            assertEquals(4L * blkSz, src.length())
            assertArrayEquals(fillBytes(fill, 2 * blkSz), src.readAt(0, 2 * blkSz))
            assertArrayEquals(ByteArray(2 * blkSz), src.readAt(2L * blkSz, 2 * blkSz))
            // Baca tidak rata 4 byte: fase pola ikut awal chunk, bukan awal baca.
            val slice = src.readAt(3, 10)!!
            val expected = fillBytes(fill, 13).copyOfRange(3, 13)
            assertArrayEquals(expected, slice)
        }
    }

    @Test
    fun directReadSpansChunkBoundaries() {
        val blkSz = 1024
        val d1 = ByteArray(blkSz) { 0x41 }
        val d2 = ByteArray(blkSz) { 0x42 }
        val bytes = sparseBytes(blkSz, 5, listOf(
            rawChunk(d1, blkSz),
            fillChunk(0x01020304L, 1),
            dontCareChunk(2),
            rawChunk(d2, blkSz)
        ))
        val file = writeFile("span.img", bytes)
        val expected = unsparseBytes(file)
        SparseSource.open(file)!!.use { src ->
            // Satu baca melewati RAW -> FILL -> DONT_CARE -> RAW.
            assertArrayEquals(expected.copyOfRange(1000, 5000), src.readAt(1000, 4000))
            assertArrayEquals(expected, src.readAt(0, expected.size))
        }
    }

    @Test
    fun directReadCrc32ChunkSkipped() {
        val blkSz = 1024
        val d1 = ByteArray(blkSz) { 0x11 }
        val d2 = ByteArray(blkSz) { 0x22 }
        val bytes = sparseBytes(blkSz, 2, listOf(
            rawChunk(d1, blkSz), crcChunk(0xDEADBEEFL), rawChunk(d2, blkSz)
        ))
        val file = writeFile("crc.img", bytes)
        val expected = unsparseBytes(file)
        SparseSource.open(file)!!.use { src ->
            assertArrayEquals(expected, src.readAt(0, expected.size))
        }
    }

    @Test
    fun directReadMatchesUnsparseMixed() {
        val blkSz = 512
        val d1 = ByteArray(2 * blkSz) { (it % 251).toByte() }
        val d2 = ByteArray(blkSz) { ((it * 3) % 251).toByte() }
        // Total blok: 2 + 1 + 2 + 0 + 1 + 1 + 1 = 8.
        val bytes = sparseBytes(blkSz, 8, listOf(
            rawChunk(d1, blkSz),
            fillChunk(0xA5A55A5AL, 1),
            dontCareChunk(2),
            crcChunk(0x12345678L),
            rawChunk(d2, blkSz),
            fillChunk(0x00000000L, 1),
            dontCareChunk(1)
        ))
        val file = writeFile("mixed.img", bytes)
        val expected = unsparseBytes(file)
        SparseSource.open(file)!!.use { src ->
            assertEquals(8L * blkSz, src.length())
            assertArrayEquals(expected, src.readAt(0, expected.size))
            // Sampel baca parsial di berbagai offset (termasuk melewati batas chunk).
            for ((off, len) in listOf(0 to 100, 500 to 1500, 1023 to 3, 2048 to 2048, 3000 to 1096)) {
                assertArrayEquals(expected.copyOfRange(off, off + len), src.readAt(off.toLong(), len))
            }
        }
    }

    // --- tes penolakan ---

    @Test
    fun directReadRejectsBadMagic() {
        val bytes = sparseBytes(1024, 1, listOf(rawChunk(ByteArray(1024) { 1 }, 1024)), magic = 0x12345678L)
        assertNull(openSparse(bytes, "bad-magic.img"))
    }

    @Test
    fun directReadRejectsTruncatedChunk() {
        val bytes = sparseBytes(1024, 2, listOf(rawChunk(ByteArray(1024) { 1 }, 1024), rawChunk(ByteArray(1024) { 2 }, 1024)))
        // Potong di tengah data chunk pertama.
        assertNull(openSparse(bytes.copyOf(28 + 12 + 100), "truncated.img"))
    }

    @Test
    fun directReadRejectsChunkOverTotal() {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0xCAC1)
        putU32(hdr, 4, 3L) // klaim 3 blok, total cuma 2
        putU32(hdr, 8, (12 + 3 * 1024).toLong())
        val bytes = sparseBytes(1024, 2, listOf(hdr + ByteArray(3 * 1024) { 1 }))
        assertNull(openSparse(bytes, "over-total.img"))
    }

    @Test
    fun directReadRejectsBadTotalSz() {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0xCAC1)
        putU32(hdr, 4, 1L)
        putU32(hdr, 8, (12 + 1024 + 8).toLong()) // total_sz kelebihan 8
        val bytes = sparseBytes(1024, 1, listOf(hdr + ByteArray(1024) { 1 }))
        assertNull(openSparse(bytes, "bad-totalsz.img"))
    }

    @Test
    fun directReadRejectsUnknownChunkType() {
        val hdr = ByteArray(12)
        putU16(hdr, 0, 0x9999)
        putU32(hdr, 4, 1L)
        putU32(hdr, 8, 12L)
        val bytes = sparseBytes(1024, 1, listOf(hdr))
        assertNull(openSparse(bytes, "bad-type.img"))
    }

    @Test
    fun directReadRejectsBlocksMismatch() {
        val bytes = sparseBytes(1024, 4, listOf(rawChunk(ByteArray(1024) { 1 }, 1024)))
        assertNull(openSparse(bytes, "mismatch.img"))
    }

    @Test
    fun directReadOutOfRangeReturnsNull() {
        val bytes = sparseBytes(1024, 2, listOf(rawChunk(ByteArray(1024) { 1 }, 1024), rawChunk(ByteArray(1024) { 2 }, 1024)))
        openSparse(bytes, "range.img")!!.use { src ->
            assertNull(src.readAt(2048, 1))
            assertNull(src.readAt(-1, 1))
            assertNull(src.readAt(0, 2049))
            assertEquals(0, src.readAt(0, 0)!!.size)
            assertEquals(0, src.readAt(2048, 0)!!.size)
        }
    }

    // --- tes info ext4 langsung dari sparse ---

    @Test
    fun readExt4InfoSparseDirect() {
        // Superblock ext4 minimal (bukan fs lengkap): cukup untuk parse.
        val raw = ByteArray(8192)
        putU16(raw, 1024 + 0x38, 0xEF53)
        putU32(raw, 1024 + 0x18, 2) // blok 4 KiB
        putU32(raw, 1024 + 0x4, 4) // total blok
        putU32(raw, 1024 + 0xC, 1) // blok bebas
        putAscii(raw, 1024 + 0x78, "TESTVOL")
        val rawFile = writeFile("raw.img", raw)
        val sparse = sparseBytes(4096, 2, listOf(
            rawChunk(raw.copyOfRange(0, 4096), 4096),
            rawChunk(raw.copyOfRange(4096, 8192), 4096)
        ))
        val sparseFile = writeFile("sparse.img", sparse)
        val fromRaw = ImgOps.readExt4Info(rawFile)
        val fromSparse = ImgOps.readExt4Info(sparseFile)
        assertNotNull(fromRaw)
        assertEquals(fromRaw, fromSparse)
        assertEquals(16384L, fromSparse!!.totalBytes)
        assertEquals("TESTVOL", fromSparse.label)
        // readExt4Info tidak lagi membuat temp di mana pun.
        assertEquals(listOf("raw.img", "sparse.img"), tmp.root.listFiles()!!.map { it.name }.sorted())
    }
}
