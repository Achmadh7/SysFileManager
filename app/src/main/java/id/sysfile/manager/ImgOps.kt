package id.sysfile.manager

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream

/** Jenis citra disk (.img), dikenali dari isi berkas — bukan dari namanya.
 *  BOOT = boot image Android ("ANDROID!"); SPARSE = citra sparse Android;
 *  EXT4 = sistem berkas ext2/3/4 mentah; FAT = sektor boot FAT12/16/32;
 *  RAW = berkas tak kosong yang tidak cocok dengan magic mana pun;
 *  UNKNOWN = kosong atau tidak bisa dibaca. */
enum class ImgType { BOOT, SPARSE, EXT4, FAT, RAW, UNKNOWN }

/** Satu entri ramdisk (arsip cpio newc). */
data class RamdiskEntry(
    val name: String,
    val isDir: Boolean,
    val isSymlink: Boolean,
    val size: Long
)

/** Info header boot image Android (bootimg.h AOSP), versi 0–4. */
data class BootImgInfo(
    val headerVersion: Int,
    val pageSize: Int,
    val kernelSize: Long,
    val ramdiskSize: Long,
    val kernelOffset: Long,
    val ramdiskOffset: Long,
    val name: String,
    val cmdline: String
)

/** Hasil pembacaan ramdisk: daftar entri, kompresi tak didukung, atau rusak. */
sealed interface RamdiskResult {
    data class Ok(val entries: List<RamdiskEntry>) : RamdiskResult
    data class UnsupportedCompression(val kind: String) : RamdiskResult
    data object Invalid : RamdiskResult
}

/**
 * Pembaca citra disk (.img), read-only — menulis kembali / repack di
 * luar cakupan (IM3 fase 1: deteksi + info + baca boot.img).
 *
 * Format yang diverifikasi dari sumbernya, bukan dari ingatan:
 * - Boot image: bootimg.h AOSP (system/tools/mkbootimg) — magic
 *   "ANDROID!", header v0–v4, bagian-bagian diratakan ke page_size
 *   (v0–v2) atau 4096 (v3–v4).
 * - Ramdisk: arsip cpio newc (man cpio(5)) — header 110 byte, field
 *   hex 8 karakter, nama + data dipad ke kelipatan 4, diakhiri entri
 *   "TRAILER!!!". Ramdisk biasanya gzip; kompresi lain dilaporkan,
 *   bukan gagal diam-diam.
 * - Deteksi: sparse 0xED26FF3A (sparse_format.h), ext4 0xEF53 di
 *   offset 1080 (kernel.org ext4 globals), FAT "FAT12/16" di 54 /
 *   "FAT32" di 82 + 55 AA di 510 (spesifikasi FAT Microsoft).
 */
object ImgOps {
    /** Batas ramdisk hasil dekompresi (bom-dekompresi): 256 MiB. */
    const val MAX_RAMDISK_BYTES = 256L * 1024L * 1024L
    /** Batas ramdisk terkompresi yang dibaca: 128 MiB. */
    const val MAX_RAMDISK_COMPRESSED_BYTES = 128L * 1024L * 1024L

    private val BOOT_MAGIC = "ANDROID!".toByteArray(Charsets.US_ASCII)
    private const val SPARSE_MAGIC = 0xED26FF3AL
    private const val EXT4_MAGIC_OFFSET = 1080L
    private const val CPIO_HEADER_SIZE = 110

    /** Kenali jenis citra dari magic bytes di awal berkas. */
    fun detectImgType(file: File): ImgType {
        if (!file.isFile) return ImgType.UNKNOWN
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len == 0L) return ImgType.UNKNOWN
                if (len < 8) return ImgType.RAW // terlalu pendek untuk magic apa pun
                val head = readAt(raf, 0, 8) ?: return ImgType.UNKNOWN
                if (head.contentEquals(BOOT_MAGIC)) return ImgType.BOOT
                if (u32le(head, 0) == SPARSE_MAGIC) return ImgType.SPARSE
                if (len >= EXT4_MAGIC_OFFSET + 2) {
                    val magic = readAt(raf, EXT4_MAGIC_OFFSET, 2)
                    if (magic != null && magic[0] == 0x53.toByte() && magic[1] == 0xEF.toByte()) {
                        return ImgType.EXT4
                    }
                }
                if (len >= 512) {
                    val boot = readAt(raf, 0, 512) ?: return ImgType.RAW
                    val hasSig = boot[510] == 0x55.toByte() && boot[511] == 0xAA.toByte()
                    val fat32 = ascii(boot, 82, 8) == "FAT32   "
                    val fat1216 = ascii(boot, 54, 8).startsWith("FAT")
                    if (hasSig && (fat32 || fat1216)) return ImgType.FAT
                }
                ImgType.RAW
            }
        } catch (e: Exception) {
            ImgType.UNKNOWN
        }
    }

    /** Parse header boot image (v0–v4); null bila bukan boot image atau rusak. */
    fun parseBootImg(file: File): BootImgInfo? {
        if (!file.isFile) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val head = readAt(raf, 0, 1660) ?: return null
                if (!head.copyOfRange(0, 8).contentEquals(BOOT_MAGIC)) return null
                val version = u32le(head, 40).toInt()
                when (version) {
                    in 0..2 -> {
                        val pageSize = u32le(head, 36).toInt()
                        if (!isValidPageSize(pageSize)) return null
                        val kernelSize = u32le(head, 8)
                        val ramdiskSize = u32le(head, 16)
                        if (kernelSize <= 0 || ramdiskSize <= 0) return null
                        val kernelOffset = pageSize.toLong()
                        val ramdiskOffset = kernelOffset + alignUp(kernelSize, pageSize.toLong())
                        if (ramdiskOffset + ramdiskSize > len) return null
                        BootImgInfo(
                            headerVersion = version,
                            pageSize = pageSize,
                            kernelSize = kernelSize,
                            ramdiskSize = ramdiskSize,
                            kernelOffset = kernelOffset,
                            ramdiskOffset = ramdiskOffset,
                            name = asciiZ(head, 48, 16),
                            cmdline = asciiZ(head, 64, 512) + asciiZ(head, 608, 1024)
                        )
                    }
                    in 3..4 -> {
                        // v3/v4: page_size tetap 4096; nama pindah ke vendor_boot.
                        val pageSize = 4096
                        val kernelSize = u32le(head, 8)
                        val ramdiskSize = u32le(head, 12)
                        if (kernelSize <= 0 || ramdiskSize <= 0) return null
                        val kernelOffset = pageSize.toLong()
                        val ramdiskOffset = kernelOffset + alignUp(kernelSize, pageSize.toLong())
                        if (ramdiskOffset + ramdiskSize > len) return null
                        BootImgInfo(
                            headerVersion = version,
                            pageSize = pageSize,
                            kernelSize = kernelSize,
                            ramdiskSize = ramdiskSize,
                            kernelOffset = kernelOffset,
                            ramdiskOffset = ramdiskOffset,
                            name = "",
                            cmdline = asciiZ(head, 44, 1536)
                        )
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Daftar isi ramdisk boot.img (cpio newc, biasanya gzip). */
    fun listRamdisk(file: File): RamdiskResult {
        return when (val bytes = readAndDecompressRamdisk(file)) {
            is RamdiskBytes.Data -> {
                val entries = parseCpio(bytes.bytes) ?: return RamdiskResult.Invalid
                RamdiskResult.Ok(entries.map { it.toRamdiskEntry() })
            }
            is RamdiskBytes.UnsupportedCompression -> RamdiskResult.UnsupportedCompression(bytes.kind)
            RamdiskBytes.Invalid -> RamdiskResult.Invalid
        }
    }

    /** Isi satu entri ramdisk biasa (bukan direktori/symlink); null bila tak ada. */
    fun readRamdiskEntry(file: File, entryName: String, maxBytes: Int = 1_000_000): ByteArray? {
        val (data, entries) = readRamdiskEntries(file) ?: return null
        val target = entries.firstOrNull { it.name == entryName } ?: return null
        if ((target.mode and 0xF000L) != 0x8000L) return null
        if (target.size > maxBytes) return null
        return data.copyOfRange(target.dataStart, target.dataEnd)
    }

    /** Isi satu entri ramdisk sebagai teks UTF-8; null bila tak ada. */
    fun readRamdiskEntryText(file: File, entryName: String, maxBytes: Int = 1_000_000): String? {
        val bytes = readRamdiskEntry(file, entryName, maxBytes) ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    /** Ekstrak satu entri ramdisk biasa ke [destDir]; null bila gagal / ditolak. */
    fun extractRamdiskEntry(file: File, entryName: String, destDir: File): File? {
        val (data, entries) = readRamdiskEntries(file) ?: return null
        val target = entries.firstOrNull { it.name == entryName } ?: return null
        if ((target.mode and 0xF000L) != 0x8000L) return null
        val out = File(destDir, target.name)
        return try {
            val destCanonical = destDir.canonicalPath
            if (isUnsafeName(target.name) || !FileOps.isPathInside(destCanonical, out.canonicalPath)) return null
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { it.write(data, target.dataStart, target.dataEnd - target.dataStart) }
            out
        } catch (e: Exception) {
            null
        }
    }

    /** Ekstrak seluruh isi ramdisk ke [destDir] (seperti "Ekstrak Di Sini"
     *  untuk arsip); false bila ada entri yang gagal — berkas yang sudah
     *  tertulis dibersihkan. */
    fun extractRamdiskAll(file: File, destDir: File): Boolean {
        val (data, entries) = readRamdiskEntries(file) ?: return false
        val written = mutableListOf<File>()
        var ok = true
        try {
            val destCanonical = destDir.canonicalPath
            val budget = ExtractBudget()
            for (e in entries) {
                if (!budget.allowEntry()) { ok = false; break }
                if (isUnsafeName(e.name)) { ok = false; break }
                val out = File(destDir, e.name)
                if (!FileOps.isPathInside(destCanonical, out.canonicalPath)) { ok = false; break }
                val type = e.mode and 0xF000L
                if (type == 0x4000L) {
                    out.mkdirs()
                } else if (type == 0x8000L) {
                    val existed = out.exists()
                    out.parentFile?.mkdirs()
                    val count = (e.dataEnd - e.dataStart).toLong()
                    FileOutputStream(out).use { it.write(data, e.dataStart, e.dataEnd - e.dataStart) }
                    if (!existed) written += out
                    if (!budget.addBytes(count)) { ok = false; break }
                }
                // Symlink dan tipe lain dilewati (tidak ditulis sebagai berkas).
            }
        } catch (e: Exception) {
            ok = false
        }
        if (!ok) written.forEach { it.delete() }
        return ok
    }

    // --- internal ---

    private sealed interface RamdiskBytes {
        data class Data(val bytes: ByteArray) : RamdiskBytes
        data class UnsupportedCompression(val kind: String) : RamdiskBytes
        data object Invalid : RamdiskBytes
    }

    /** Satu entri cpio mentah (dengan rentang byte datanya). */
    private data class CpioEntry(
        val name: String,
        val mode: Long,
        val size: Long,
        val dataStart: Int,
        val dataEnd: Int
    ) {
        fun toRamdiskEntry(): RamdiskEntry {
            val type = mode and 0xF000L
            return RamdiskEntry(name, type == 0x4000L, type == 0xA000L, size)
        }
    }

    /** Parse header + baca + dekompresi ramdisk; data cpio mentah. */
    private fun readAndDecompressRamdisk(file: File): RamdiskBytes {
        val info = parseBootImg(file) ?: return RamdiskBytes.Invalid
        if (info.ramdiskSize > MAX_RAMDISK_COMPRESSED_BYTES) return RamdiskBytes.Invalid
        val raw = try {
            RandomAccessFile(file, "r").use { raf ->
                readAt(raf, info.ramdiskOffset, info.ramdiskSize.toInt()) ?: return RamdiskBytes.Invalid
            }
        } catch (e: Exception) {
            return RamdiskBytes.Invalid
        }
        return decompressRamdisk(raw)
    }

    /** Dekompresi ramdisk: cpio mentah atau gzip. Kompresi lain dikenali
     *  dari magic-nya dan dilaporkan — tidak pernah gagal diam-diam. */
    private fun decompressRamdisk(raw: ByteArray): RamdiskBytes {
        if (startsWithAscii(raw, "070701") || startsWithAscii(raw, "070702")) {
            return RamdiskBytes.Data(raw)
        }
        if (raw.size >= 2 && raw[0] == 0x1F.toByte() && raw[1] == 0x8B.toByte()) {
            return try {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                GZIPInputStream(ByteArrayInputStream(raw)).use { gz ->
                    while (true) {
                        val n = gz.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_RAMDISK_BYTES) return RamdiskBytes.Invalid
                        out.write(buf, 0, n)
                    }
                }
                RamdiskBytes.Data(out.toByteArray())
            } catch (e: Exception) {
                RamdiskBytes.Invalid
            }
        }
        val kind = when {
            startsWith(raw, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> "xz"
            startsWith(raw, 0x04, 0x22, 0x4D, 0x18) -> "lz4"
            startsWith(raw, 0x42, 0x5A, 0x68) -> "bzip2"
            startsWith(raw, 0x28, 0xB5, 0x2F, 0xFD) -> "zstd"
            else -> "unknown"
        }
        return RamdiskBytes.UnsupportedCompression(kind)
    }

    /** Baca ramdisk + parse cpio; null bila boot image / ramdisk rusak. */
    private fun readRamdiskEntries(file: File): Pair<ByteArray, List<CpioEntry>>? {
        val data = when (val r = readAndDecompressRamdisk(file)) {
            is RamdiskBytes.Data -> r.bytes
            else -> return null
        }
        val entries = parseCpio(data) ?: return null
        return data to entries
    }

    /** Parse arsip cpio newc (man cpio(5)); null bila rusak / tanpa trailer. */
    private fun parseCpio(data: ByteArray): List<CpioEntry>? {
        val entries = mutableListOf<CpioEntry>()
        var pos = 0
        while (pos + CPIO_HEADER_SIZE <= data.size) {
            val magic = ascii(data, pos, 6)
            if (magic != "070701" && magic != "070702") return null
            val mode = hex8(data, pos + 14) ?: return null
            val filesize = hex8(data, pos + 54) ?: return null
            val namesize = hex8(data, pos + 94) ?: return null
            if (namesize <= 0 || namesize > 4096) return null
            val nameEndLong = pos + CPIO_HEADER_SIZE + namesize
            if (nameEndLong > data.size) return null
            val nameEnd = nameEndLong.toInt()
            val nameStart = pos + CPIO_HEADER_SIZE
            val nameBytes = data.copyOfRange(nameStart, nameEnd)
            val nul = nameBytes.indexOf(0.toByte())
            val name = String(nameBytes, 0, if (nul >= 0) nul else nameBytes.size, Charsets.UTF_8)
            if (name.isEmpty()) return null
            if (name == "TRAILER!!!") return entries
            val dataStart = alignUp(nameEnd.toLong(), 4).toInt()
            val dataEndLong = dataStart.toLong() + filesize
            if (dataEndLong > data.size) return null
            val dataEnd = dataEndLong.toInt()
            entries += CpioEntry(name, mode, filesize, dataStart, dataEnd)
            if (entries.size > FileOps.MAX_ARCHIVE_ENTRIES) return null
            pos = alignUp(dataEnd.toLong(), 4).toInt()
        }
        return null // TRAILER!!! tidak ditemukan -> arsip terpotong
    }

    /** Nama entri yang tidak boleh diekstrak: absolut atau lolos dari dest. */
    private fun isUnsafeName(name: String): Boolean {
        return name.startsWith("/") || name.startsWith("\\") ||
            (name.length >= 2 && name[1] == ':')
    }

    private fun isValidPageSize(page: Int): Boolean {
        return page in 512..65536 && (page and (page - 1)) == 0
    }

    private fun alignUp(value: Long, alignment: Long): Long {
        return (value + alignment - 1) / alignment * alignment
    }

    private fun u32le(b: ByteArray, off: Int): Long {
        return (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)
    }

    private fun hex8(data: ByteArray, off: Int): Long? {
        if (off + 8 > data.size) return null
        var value = 0L
        for (i in 0 until 8) {
            val c = data[off + i].toInt() and 0xFF
            val d = when (c) {
                in '0'.code..'9'.code -> c - '0'.code
                in 'a'.code..'f'.code -> c - 'a'.code + 10
                in 'A'.code..'F'.code -> c - 'A'.code + 10
                else -> return null
            }
            value = (value shl 4) or d.toLong()
        }
        return value
    }

    private fun ascii(b: ByteArray, off: Int, len: Int): String {
        return String(b, off, len, Charsets.US_ASCII)
    }

    private fun asciiZ(b: ByteArray, off: Int, len: Int): String {
        val end = (off until off + len).firstOrNull { b[it] == 0.toByte() } ?: (off + len)
        return String(b, off, end - off, Charsets.US_ASCII).trim()
    }

    private fun startsWithAscii(bytes: ByteArray, text: String): Boolean {
        val magic = text.toByteArray(Charsets.US_ASCII)
        if (bytes.size < magic.size) return false
        return bytes.copyOfRange(0, magic.size).contentEquals(magic)
    }

    private fun startsWith(bytes: ByteArray, vararg prefix: Int): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i].toByte()) return false
        }
        return true
    }

    /** Baca tepat [size] byte di [offset]; null bila di luar berkas / gagal. */
    private fun readAt(raf: RandomAccessFile, offset: Long, size: Int): ByteArray? {
        if (offset < 0 || offset + size > raf.length()) return null
        val out = ByteArray(size)
        raf.seek(offset)
        var read = 0
        while (read < size) {
            val n = raf.read(out, read, size - read)
            if (n < 0) return null
            read += n
        }
        return out
    }
}
