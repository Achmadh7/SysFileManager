package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tes ImgOps (r151): deteksi jenis citra dari magic bytes, parse header
 * boot image v0/v1/v4, dan baca ramdisk cpio newc. Fixture dibangun
 * sintetis di tes — tulis byte per indeks (jangan slice-assign pada
 * ByteArray: nilai lebih pendek akan mengubah panjang array).
 */
class ImgOpsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val modeFile = 0x81A4L // 0100644
    private val modeDir = 0x41EDL // 0040755
    private val modeLink = 0xA1FFL // 0120777

    // --- helpers fixture ---

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putU64(b: ByteArray, off: Int, v: Long) {
        putU32(b, off, v)
        putU32(b, off + 4, v shr 32)
    }

    private fun alignUp(n: Long, a: Long) = (n + a - 1) / a * a

    private fun putAscii(b: ByteArray, off: Int, text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        for (i in bytes.indices) b[off + i] = bytes[i]
    }

    private fun writeFile(name: String, bytes: ByteArray): File {
        val f = File(tmp.root, name)
        FileOutputStream(f).use { it.write(bytes) }
        return f
    }

    /** Header boot v0–v2: magic, kernel/ramdisk, page_size, header_version. */
    private fun makeBootV0(pageSize: Int, kernelSize: Int, ramdisk: ByteArray, version: Long = 0): File {
        val hdr = ByteArray(pageSize)
        putAscii(hdr, 0, "ANDROID!")
        putU32(hdr, 8, kernelSize.toLong())
        putU32(hdr, 16, ramdisk.size.toLong())
        putU32(hdr, 36, pageSize.toLong())
        putU32(hdr, 40, version)
        putAscii(hdr, 48, "marble")
        putAscii(hdr, 64, "cmd=x")
        val kernel = ByteArray(kernelSize) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        out.write(hdr)
        out.write(kernel)
        // Pad kernel ke kelipatan page_size sebelum ramdisk.
        val pad = alignUp(kernelSize.toLong(), pageSize.toLong()) - kernelSize
        out.write(ByteArray(pad.toInt()))
        out.write(ramdisk)
        return writeFile("boot.img", out.toByteArray())
    }

    /** Header boot v4 (page_size tetap 4096; kernel/ramdisk di 8/12). */
    private fun makeBootV4(kernelSize: Int, ramdisk: ByteArray): File {
        val pageSize = 4096
        val hdr = ByteArray(pageSize)
        putAscii(hdr, 0, "ANDROID!")
        putU32(hdr, 8, kernelSize.toLong())
        putU32(hdr, 12, ramdisk.size.toLong())
        putU32(hdr, 20, 1584L) // header_size v4
        putU32(hdr, 40, 4L)
        putAscii(hdr, 44, "cmd=y")
        putU32(hdr, 1580, 0L) // signature_size
        val kernel = ByteArray(kernelSize) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        out.write(hdr)
        out.write(kernel)
        val pad = alignUp(kernelSize.toLong(), pageSize.toLong()) - kernelSize
        out.write(ByteArray(pad.toInt()))
        out.write(ramdisk)
        return writeFile("boot.img", out.toByteArray())
    }

    /** Satu entri cpio newc (man cpio(5)): header 110 byte + nama + data. */
    private fun cpioEntry(name: String, mode: Long, data: ByteArray): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val hdr = ByteArray(110)
        putAscii(hdr, 0, "070701")
        putAscii(hdr, 6, "%08x".format(1)) // ino
        putAscii(hdr, 14, "%08x".format(mode))
        putAscii(hdr, 22, "%08x".format(0)) // uid
        putAscii(hdr, 30, "%08x".format(0)) // gid
        putAscii(hdr, 38, "%08x".format(1)) // nlink
        putAscii(hdr, 46, "%08x".format(0)) // mtime
        putAscii(hdr, 54, "%08x".format(data.size))
        putAscii(hdr, 62, "%08x".format(0)) // devmajor
        putAscii(hdr, 70, "%08x".format(0)) // devminor
        putAscii(hdr, 78, "%08x".format(0)) // rdevmajor
        putAscii(hdr, 86, "%08x".format(0)) // rdevminor
        putAscii(hdr, 94, "%08x".format(nameBytes.size))
        putAscii(hdr, 102, "%08x".format(0)) // check
        val out = ByteArrayOutputStream()
        out.write(hdr)
        out.write(nameBytes)
        val pad1 = alignUp((110 + nameBytes.size).toLong(), 4) - (110 + nameBytes.size)
        out.write(ByteArray(pad1.toInt()))
        out.write(data)
        val pad2 = alignUp(data.size.toLong(), 4) - data.size
        out.write(ByteArray(pad2.toInt()))
        return out.toByteArray()
    }

    private fun makeCpio(vararg entries: Triple<String, Long, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, mode, data) in entries) out.write(cpioEntry(name, mode, data))
        out.write(cpioEntry("TRAILER!!!", 0L, ByteArray(0)))
        return out.toByteArray()
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun sampleCpio(): ByteArray {
        return makeCpio(
            Triple("sbin", modeDir, ByteArray(0)),
            Triple("init.rc", modeFile, "on boot\n".toByteArray(Charsets.UTF_8)),
            Triple("sbin/adbd", modeFile, ByteArray(16) { it.toByte() }),
            Triple("sbin/link", modeLink, "adbd".toByteArray(Charsets.UTF_8))
        )
    }

    // --- deteksi ---

    @Test
    fun detectImgType_bootMagicByContentNotName() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        // Nama berkas sengaja menyesatkan: deteksi dari isi, bukan nama.
        val renamed = File(tmp.root, "foto.png")
        assertTrue(f.renameTo(renamed))
        assertEquals(ImgType.BOOT, ImgOps.detectImgType(renamed))
    }

    @Test
    fun detectImgType_sparseMagic() {
        val b = ByteArray(64)
        putU32(b, 0, 0xED26FF3AL)
        putU32(b, 4, 1L) // major_version
        assertEquals(ImgType.SPARSE, ImgOps.detectImgType(writeFile("system.img", b)))
    }

    @Test
    fun detectImgType_ext4Magic() {
        val b = ByteArray(2048)
        b[1080] = 0x53.toByte()
        b[1081] = 0xEF.toByte()
        assertEquals(ImgType.EXT4, ImgOps.detectImgType(writeFile("system.img", b)))
    }

    @Test
    fun detectImgType_fat32AndFat16() {
        val fat32 = ByteArray(512)
        putAscii(fat32, 82, "FAT32   ")
        fat32[510] = 0x55.toByte()
        fat32[511] = 0xAA.toByte()
        assertEquals(ImgType.FAT, ImgOps.detectImgType(writeFile("fat32.img", fat32)))
        val fat16 = ByteArray(512)
        putAscii(fat16, 54, "FAT16   ")
        fat16[510] = 0x55.toByte()
        fat16[511] = 0xAA.toByte()
        assertEquals(ImgType.FAT, ImgOps.detectImgType(writeFile("fat16.img", fat16)))
    }

    @Test
    fun detectImgType_fatWithoutSignatureIsRaw() {
        // Teks FAT ada tapi 55 AA tidak ada -> bukan FAT.
        val b = ByteArray(512)
        putAscii(b, 82, "FAT32   ")
        assertEquals(ImgType.RAW, ImgOps.detectImgType(writeFile("x.img", b)))
        // 55 AA ada tapi teks FAT tidak ada -> bukan FAT.
        val c = ByteArray(512)
        c[510] = 0x55.toByte()
        c[511] = 0xAA.toByte()
        assertEquals(ImgType.RAW, ImgOps.detectImgType(writeFile("y.img", c)))
    }

    @Test
    fun detectImgType_rawAndUnknown() {
        assertEquals(ImgType.RAW, ImgOps.detectImgType(writeFile("raw.img", ByteArray(64) { 0x42 })))
        assertEquals(ImgType.RAW, ImgOps.detectImgType(writeFile("tiny.img", ByteArray(3) { 1 })))
        assertEquals(ImgType.UNKNOWN, ImgOps.detectImgType(writeFile("empty.img", ByteArray(0))))
        assertEquals(ImgType.UNKNOWN, ImgOps.detectImgType(File(tmp.root, "tidak-ada.img")))
        assertEquals(ImgType.UNKNOWN, ImgOps.detectImgType(tmp.root))
    }

    // --- parse header boot ---

    @Test
    fun parseBootImg_v0() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        val info = ImgOps.parseBootImg(f)
        assertNotNull(info)
        assertEquals(0, info!!.headerVersion)
        assertEquals(2048, info.pageSize)
        assertEquals(100L, info.kernelSize)
        assertEquals(gzip(sampleCpio()).size.toLong(), info.ramdiskSize)
        assertEquals(2048L, info.kernelOffset)
        assertEquals(4096L, info.ramdiskOffset)
        assertEquals("marble", info.name)
        assertEquals("cmd=x", info.cmdline)
    }

    @Test
    fun parseBootImg_v1UsesV0Layout() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()), version = 1)
        val info = ImgOps.parseBootImg(f)
        assertNotNull(info)
        assertEquals(1, info!!.headerVersion)
        assertEquals(2048, info.pageSize)
        assertEquals(2048L, info.kernelOffset)
        assertEquals(4096L, info.ramdiskOffset)
    }

    @Test
    fun parseBootImg_v4() {
        val ramdisk = gzip(sampleCpio())
        val f = makeBootV4(1000, ramdisk)
        val info = ImgOps.parseBootImg(f)
        assertNotNull(info)
        assertEquals(4, info!!.headerVersion)
        assertEquals(4096, info.pageSize)
        assertEquals(1000L, info.kernelSize)
        assertEquals(ramdisk.size.toLong(), info.ramdiskSize)
        assertEquals(4096L, info.kernelOffset)
        assertEquals(8192L, info.ramdiskOffset)
        assertEquals("", info.name)
        assertEquals("cmd=y", info.cmdline)
    }

    @Test
    fun parseBootImg_rejectsBadMagic() {
        val b = ByteArray(4096)
        putAscii(b, 0, "ANDROID?")
        assertNull(ImgOps.parseBootImg(writeFile("bad.img", b)))
    }

    @Test
    fun parseBootImg_rejectsTruncated() {
        val f = makeBootV0(2048, 1000, gzip(sampleCpio()))
        java.io.RandomAccessFile(f, "rw").use { it.setLength(2048) }
        assertNull(ImgOps.parseBootImg(f))
    }

    @Test
    fun parseBootImg_rejectsVersion9() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        val bytes = f.readBytes()
        putU32(bytes, 40, 9)
        f.writeBytes(bytes)
        assertNull(ImgOps.parseBootImg(f))
    }

    @Test
    fun parseBootImg_rejectsZeroRamdisk() {
        val f = makeBootV0(2048, 100, ByteArray(0))
        assertNull(ImgOps.parseBootImg(f))
    }

    // --- ramdisk ---

    @Test
    fun listRamdisk_gzipCpio() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        val result = ImgOps.listRamdisk(f)
        assertTrue(result is RamdiskResult.Ok)
        val entries = (result as RamdiskResult.Ok).entries
        assertEquals(listOf("sbin", "init.rc", "sbin/adbd", "sbin/link"), entries.map { it.name })
        assertEquals(listOf(true, false, false, false), entries.map { it.isDir })
        assertEquals(listOf(false, false, false, true), entries.map { it.isSymlink })
        assertEquals(listOf(0L, 8L, 16L, 4L), entries.map { it.size })
    }

    @Test
    fun listRamdisk_uncompressedCpio() {
        val f = makeBootV0(2048, 100, sampleCpio())
        val result = ImgOps.listRamdisk(f)
        assertTrue(result is RamdiskResult.Ok)
        assertEquals(4, (result as RamdiskResult.Ok).entries.size)
    }

    @Test
    fun listRamdisk_xzAndLz4Unsupported() {
        val xz = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00, 0x00, 0x01)
        val f = makeBootV0(2048, 100, xz)
        val r1 = ImgOps.listRamdisk(f)
        assertTrue(r1 is RamdiskResult.UnsupportedCompression)
        assertEquals("xz", (r1 as RamdiskResult.UnsupportedCompression).kind)
        val lz4 = byteArrayOf(0x04, 0x22, 0x4D, 0x18, 0x00, 0x00, 0x00, 0x00)
        val g = makeBootV0(2048, 100, lz4)
        val r2 = ImgOps.listRamdisk(g)
        assertTrue(r2 is RamdiskResult.UnsupportedCompression)
        assertEquals("lz4", (r2 as RamdiskResult.UnsupportedCompression).kind)
    }

    @Test
    fun listRamdisk_invalidCpio() {
        // Gzip berisi byte acak -> bukan cpio.
        val f = makeBootV0(2048, 100, gzip(ByteArray(64) { 0x42 }))
        assertTrue(ImgOps.listRamdisk(f) is RamdiskResult.Invalid)
        // Cpio tanpa TRAILER!!! -> terpotong.
        val raw = cpioEntry("a.txt", modeFile, "hi".toByteArray(Charsets.UTF_8))
        val g = makeBootV0(2048, 100, gzip(raw))
        assertTrue(ImgOps.listRamdisk(g) is RamdiskResult.Invalid)
    }

    @Test
    fun listRamdisk_entryCap() {
        val out = ByteArrayOutputStream()
        for (i in 0 until 10_001) {
            out.write(cpioEntry("f$i.txt", modeFile, ByteArray(0)))
        }
        out.write(cpioEntry("TRAILER!!!", 0L, ByteArray(0)))
        val f = makeBootV0(2048, 100, gzip(out.toByteArray()))
        assertTrue(ImgOps.listRamdisk(f) is RamdiskResult.Invalid)
    }

    @Test
    fun readRamdiskEntryText() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        assertEquals("on boot\n", ImgOps.readRamdiskEntryText(f, "init.rc"))
        assertNull(ImgOps.readRamdiskEntryText(f, "tidak-ada.rc"))
        assertNull(ImgOps.readRamdiskEntryText(f, "sbin")) // direktori
        assertNull(ImgOps.readRamdiskEntryText(f, "sbin/link")) // symlink
    }

    @Test
    fun extractRamdiskEntry() {
        val cpio = makeCpio(
            Triple("init.rc", modeFile, "on boot\n".toByteArray(Charsets.UTF_8)),
            Triple("sbin/adbd", modeFile, ByteArray(16) { it.toByte() }),
            Triple("../evil.txt", modeFile, "x".toByteArray(Charsets.UTF_8))
        )
        val f = makeBootV0(2048, 100, gzip(cpio))
        val dest = tmp.newFolder("imgone")
        val out = ImgOps.extractRamdiskEntry(f, "sbin/adbd", dest)
        assertNotNull(out)
        assertEquals(ByteArray(16) { it.toByte() }.toList(), out!!.readBytes().toList())
        assertNull(ImgOps.extractRamdiskEntry(f, "tidak-ada", dest))
        assertNull(ImgOps.extractRamdiskEntry(f, "../evil.txt", dest))
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test
    fun extractRamdiskAll() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        val dest = tmp.newFolder("imgall")
        assertTrue(ImgOps.extractRamdiskAll(f, dest))
        assertEquals("on boot\n", File(dest, "init.rc").readText())
        assertEquals(16, File(dest, "sbin/adbd").readBytes().size)
        assertTrue(File(dest, "sbin").isDirectory)
        assertFalse(File(dest, "sbin/link").exists()) // symlink tidak ditulis
    }

    @Test
    fun extractRamdiskAll_zipSlipRejected() {
        val cpio = makeCpio(
            Triple("ok.txt", modeFile, "ok".toByteArray(Charsets.UTF_8)),
            Triple("../evil.txt", modeFile, "x".toByteArray(Charsets.UTF_8))
        )
        val f = makeBootV0(2048, 100, gzip(cpio))
        val dest = tmp.newFolder("imgall2")
        assertFalse(ImgOps.extractRamdiskAll(f, dest))
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(dest, "ok.txt").exists()) // dibersihkan saat gagal
    }

    // --- ramdiskChildren + looksLikeText (r152) ---

    @Test
    fun ramdiskChildren_groupsFoldersAndFiles() {
        val entries = listOf(
            RamdiskEntry("sbin", true, false, 0L),
            RamdiskEntry("init.rc", false, false, 8L),
            RamdiskEntry("sbin/adbd", false, false, 16L),
            RamdiskEntry("sbin/link", false, true, 4L),
            RamdiskEntry("usr/bin/tool", false, false, 32L)
        )
        val root = ImgOps.ramdiskChildren(entries, "")
        // Folder dulu (dengan jumlah isi), lalu berkas.
        assertEquals(listOf("sbin/", "usr/", "init.rc"), root.map { it.first.name })
        assertEquals(listOf(2, 1, null), root.map { it.second })
        assertEquals(listOf(true, true, false), root.map { it.first.isDir })
        val sbin = ImgOps.ramdiskChildren(entries, "sbin/")
        assertEquals(listOf("sbin/adbd", "sbin/link"), sbin.map { it.first.name })
        val usr = ImgOps.ramdiskChildren(entries, "usr/")
        assertEquals(listOf("usr/bin/"), usr.map { it.first.name })
        assertEquals(listOf(1), usr.map { it.second })
        val bin = ImgOps.ramdiskChildren(entries, "usr/bin/")
        assertEquals(listOf("usr/bin/tool"), bin.map { it.first.name })
    }

    @Test
    fun ramdiskChildren_dotSlashPrefix() {
        val entries = listOf(
            RamdiskEntry("./init.rc", false, false, 8L),
            RamdiskEntry("./sbin/adbd", false, false, 16L)
        )
        val root = ImgOps.ramdiskChildren(entries, "")
        assertEquals(listOf("sbin/", "init.rc"), root.map { it.first.name })
        // Nama yang dinormalisasi tetap bisa dibuka/diekstrak.
        val cpio = makeCpio(
            Triple("./init.rc", modeFile, "on boot\n".toByteArray(Charsets.UTF_8))
        )
        val f = makeBootV0(2048, 100, gzip(cpio))
        assertEquals("on boot\n", ImgOps.readRamdiskEntryText(f, "init.rc"))
    }

    @Test
    fun looksLikeText() {
        assertTrue(ImgOps.looksLikeText("on boot\n".toByteArray(Charsets.UTF_8)))
        assertTrue(ImgOps.looksLikeText("café\n".toByteArray(Charsets.UTF_8)))
        assertTrue(ImgOps.looksLikeText(ByteArray(0)))
        assertFalse(ImgOps.looksLikeText(byteArrayOf(0, 1, 2, 3)))
        assertFalse(ImgOps.looksLikeText(ByteArray(16) { it.toByte() }))
        // NUL di luar sampel 8 KiB tidak dihitung.
        val big = ByteArray(9000) { 'a'.code.toByte() }
        big[8500] = 0
        assertTrue(ImgOps.looksLikeText(big))
    }

    @Test
    fun readRamdiskEntryText_binaryReturnsNull() {
        val f = makeBootV0(2048, 100, gzip(sampleCpio()))
        assertNull(ImgOps.readRamdiskEntryText(f, "sbin/adbd")) // byte 0..15, ada NUL
        assertEquals("on boot\n", ImgOps.readRamdiskEntryText(f, "init.rc"))
    }

    @Test
    fun readRamdiskEntryText_utf8Text() {
        val cpio = makeCpio(
            Triple("ueventd.rc", modeFile, "café — ünïcode\n".toByteArray(Charsets.UTF_8))
        )
        val f = makeBootV0(2048, 100, gzip(cpio))
        assertEquals("café — ünïcode\n", ImgOps.readRamdiskEntryText(f, "ueventd.rc"))
    }
}
