package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/**
 * Uji parser payload.bin terhadap payload SINTETIS yang dibangun di tempat:
 * header "CrAU" + manifest protobuf tulisan tangan + blob data. Mencakup
 * kode operasi update_engine yang sebenarnya (REPLACE_BZ=1, REPLACE_XZ=8,
 * ZERO=9), verifikasi hash SHA-256 partisi, penolakan payload delta,
 * berkas bukan payload, dan pembatalan ekstraksi.
 */
class PayloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun varint(v: Long): ByteArray {
        var x = v
        val out = ByteArrayOutputStream()
        while (true) {
            val b = (x and 0x7F).toInt()
            x = x ushr 7
            if (x == 0L) {
                out.write(b)
                break
            }
            out.write(b or 0x80)
        }
        return out.toByteArray()
    }

    private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

    private fun varField(field: Int, v: Long) = tag(field, 0) + varint(v)

    private fun bytesField(field: Int, data: ByteArray) =
        tag(field, 2) + varint(data.size.toLong()) + data

    private fun op(
        type: Long,
        dataOffset: Long,
        data: ByteArray?,
        extents: List<Pair<Long, Long>>
    ): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(varField(1, type))
        if (data != null) {
            b.write(varField(2, dataOffset))
            b.write(varField(3, data.size.toLong()))
        }
        for ((start, num) in extents) {
            val e = ByteArrayOutputStream()
            e.write(varField(1, start))
            e.write(varField(2, num))
            b.write(bytesField(6, e.toByteArray()))
        }
        return b.toByteArray()
    }

    private fun partition(
        name: String,
        size: Long,
        hash: ByteArray?,
        ops: List<ByteArray>
    ): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(bytesField(1, name.toByteArray(Charsets.UTF_8)))
        if (hash != null) {
            val pi = ByteArrayOutputStream()
            pi.write(varField(1, size))
            pi.write(bytesField(2, hash))
            b.write(bytesField(7, pi.toByteArray()))
        }
        for (o in ops) b.write(bytesField(8, o))
        return b.toByteArray()
    }

    private fun payloadFile(name: String, partitions: List<ByteArray>, blob: ByteArray): File {
        val m = ByteArrayOutputStream()
        m.write(varField(3, 4096))
        for (p in partitions) m.write(bytesField(13, p))
        val manifest = m.toByteArray()
        val f = File(tmp.root, name)
        DataOutputStream(FileOutputStream(f)).use { out ->
            out.write("CrAU".toByteArray(Charsets.US_ASCII))
            out.writeLong(2L)
            out.writeLong(manifest.size.toLong())
            out.writeInt(0)
            out.write(manifest)
            out.write(blob)
        }
        return f
    }

    private fun pattern(seed: Int, size: Int) =
        ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    @Test
    fun fullPayloadExtractsAndVerifies() {
        val block = 4096
        val bootData = pattern(7, block)
        val bz = ByteArrayOutputStream().also { out ->
            BZip2CompressorOutputStream(out).use { it.write(bootData) }
        }.toByteArray()
        val bootExpected = bootData + ByteArray(block)
        val vendorData = pattern(13, block)
        val xz = ByteArrayOutputStream().also { out ->
            XZOutputStream(out, LZMA2Options()).use { it.write(vendorData) }
        }.toByteArray()
        val blob = bz + xz
        val boot = partition(
            "boot", bootExpected.size.toLong(), sha(bootExpected),
            listOf(
                op(1, 0, bz, listOf(0L to 1L)),
                op(9, 0, null, listOf(1L to 1L))
            )
        )
        val vendor = partition(
            "vendor", vendorData.size.toLong(), sha(vendorData),
            listOf(op(8, bz.size.toLong(), xz, listOf(0L to 1L)))
        )
        val f = payloadFile("payload.bin", listOf(boot, vendor), blob)

        val listing = PayloadOps.listPartitions(f)
        assertNull(listing.error)
        assertEquals(listOf("boot", "vendor"), listing.partitions.map { it.name })
        assertEquals(8192L, listing.partitions[0].size)
        assertEquals(4096L, listing.partitions[1].size)

        val one = PayloadOps.extractOne(f, "boot", tmp.root)
        assertNull(one.error)
        assertArrayEquals(bootExpected, one.file!!.readBytes())

        val all = PayloadOps.extract(f, tmp.root)
        assertNull(all.error)
        assertEquals(2, all.partitions)
        val dir = File(tmp.root, "payload")
        assertArrayEquals(bootExpected, File(dir, "boot.img").readBytes())
        assertArrayEquals(vendorData, File(dir, "vendor.img").readBytes())
    }

    @Test
    fun hashMismatchIsReportedAndOutputRemoved() {
        val data = pattern(3, 4096)
        val bad = partition(
            "boot", data.size.toLong(), sha(pattern(99, 4096)),
            listOf(op(0, 0, data, listOf(0L to 1L)))
        )
        val f = payloadFile("payload.bin", listOf(bad), data)
        val out = PayloadOps.extractOne(f, "boot", tmp.root)
        assertEquals("hash", out.error)
        assertNull(out.file)
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".img") })
    }

    @Test
    fun deltaOpsAreUnsupported() {
        val p = partition(
            "boot", 4096, null,
            listOf(op(4, 0, null, listOf(0L to 1L)))
        )
        val f = payloadFile("payload.bin", listOf(p), ByteArray(0))
        assertEquals("unsupported", PayloadOps.listPartitions(f).error)
    }

    @Test
    fun notAPayload() {
        val f = File(tmp.root, "x.bin")
        f.writeBytes(pattern(1, 128))
        assertEquals("notpayload", PayloadOps.listPartitions(f).error)
    }

    @Test
    fun cancelledExtractionLeavesNoFile() {
        val data = pattern(11, 4096)
        val p = partition(
            "boot", 4096, sha(data),
            listOf(op(0, 0, data, listOf(0L to 1L)))
        )
        val f = payloadFile("payload.bin", listOf(p), data)
        val out = PayloadOps.extractOne(f, "boot", tmp.root, cancelFlag = AtomicBoolean(true))
        assertEquals("cancelled", out.error)
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".img") })
    }
}
