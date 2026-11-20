package id.sysfile.manager

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/** Jenis citra disk (.img), dikenali dari isi berkas — bukan dari namanya.
 *  BOOT = boot image Android ("ANDROID!"); SPARSE = citra sparse Android;
 *  EXT4 = sistem berkas ext2/3/4 mentah; FAT = sektor boot FAT12/16/32;
 *  SUPER = super.img partisi logis (liblp);
 *  RAW = berkas tak kosong yang tidak cocok dengan magic mana pun;
 *  UNKNOWN = kosong atau tidak bisa dibaca. */
enum class ImgType { BOOT, SPARSE, EXT4, FAT, SUPER, RAW, UNKNOWN }

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

/** Dibatalkan pengguna lewat tombol Batal dialog kerja (LD3 r159).
 *  Dilempar dari operasi panjang .img saat cancelFlag terpasang —
 *  pemanggil (MainActivity) menangkapnya untuk membedakan
 *  "dibatalkan" dari "gagal". */
internal class ImgCancelledException : Exception()

/** Hasil unsparse citra sparse Android: citra mentah, terlalu besar
 *  untuk batas, rusak, atau dibatalkan — tidak pernah throw. */
sealed interface UnsparseResult {
    data class Ok(val outFile: File, val totalBytes: Long) : UnsparseResult
    data object TooLarge : UnsparseResult
    data class Invalid(val reason: String) : UnsparseResult
    data object Cancelled : UnsparseResult
}

/** Info superblock ext4 (kernel.org ext4/super.html): dibaca dari
 *  citra ext4 mentah, atau dari hasil unsparse citra sparse. */
data class Ext4Info(
    val blockSize: Long,
    val totalBlocks: Long,
    val freeBlocks: Long,
    val totalBytes: Long,
    val freeBytes: Long,
    val label: String,
    val featureCompat: Long,
    val featureIncompat: Long,
    val featureRoCompat: Long
) {
    /** INCOMPAT_64BIT (0x80): jumlah blok 64-bit. */
    val has64bit: Boolean get() = featureIncompat and 0x80L != 0L
    /** INCOMPAT_EXTENTS (0x40): berkas memakai extent. */
    val hasExtents: Boolean get() = featureIncompat and 0x40L != 0L
    /** RO_COMPAT_METADATA_CSUM (0x400): checksum metadata. */
    val hasMetadataCsum: Boolean get() = featureRoCompat and 0x400L != 0L
}

/**
 * Pembaca citra disk (.img), read-only — menulis kembali / repack di
 * luar cakupan (IM3: deteksi + info + boot.img + sparse + ext4 +
 * FAT + partisi logis super.img).
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
 * - Citra sparse: sparse_format.h AOSP — header 28 byte (magic
 *   0xED26FF3A, blk_sz, total_blks/chunks, image_checksum CRC32
 *   802.3), chunk 12 byte: RAW 0xCAC1, FILL 0xCAC2, DONT_CARE
 *   0xCAC3, CRC32 0xCAC4 (isinya diabaikan pembaca nyata).
 * - Superblock ext4: kernel.org ext4/super.html — 1024 byte di
 *   offset 1024, magic 0xEF53 di 0x38, ukuran blok
 *   2^(10+s_log_block_size), jumlah blok 64-bit bila
 *   INCOMPAT_64BIT, label 16 byte di 0x78.
 * - Super.img: metadata_format.h + reader.cpp liblp AOSP —
 *   geometri @4096 (magic 0x616c4467), slot metadata @12288 +
 *   max_size*slot (cadangan setelah max_size*slot_count),
 *   partisi 52 byte, extent 24 byte (LINEAR/ZERO), checksum
 *   SHA-256 geometri/header/tabel.
 */
object ImgOps {
    /** Batas ramdisk hasil dekompresi (bom-dekompresi): 256 MiB. */
    const val MAX_RAMDISK_BYTES = 256L * 1024L * 1024L
    /** Batas ramdisk terkompresi yang dibaca: 128 MiB. */
    const val MAX_RAMDISK_COMPRESSED_BYTES = 128L * 1024L * 1024L
    /** Batas citra hasil unsparse (8 GiB): system image multi-GB
     *  ditulis ke berkas temp, bukan ke memori. */
    const val MAX_UNSPARSE_BYTES = 8L * 1024L * 1024L * 1024L

    private val BOOT_MAGIC = "ANDROID!".toByteArray(Charsets.US_ASCII)
    private const val SPARSE_MAGIC = 0xED26FF3AL
    private const val SPARSE_HEADER_SIZE = 28
    private const val SPARSE_CHUNK_HEADER_SIZE = 12
    private const val SPARSE_CHUNK_RAW = 0xCAC1
    private const val SPARSE_CHUNK_FILL = 0xCAC2
    private const val SPARSE_CHUNK_DONT_CARE = 0xCAC3
    private const val SPARSE_CHUNK_CRC32 = 0xCAC4
    private const val EXT4_MAGIC_OFFSET = 1080L
    private const val EXT4_SUPERBLOCK_OFFSET = 1024L
    private const val EXT4_SUPERBLOCK_SIZE = 1024
    private const val CPIO_HEADER_SIZE = 110
    /** Buffer streaming unsparse (64 KiB). */
    private const val IO_BUFFER = 65536

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
                // r156: super.img partisi logis — geometri liblp
                // di offset 4096 (magic 0x616c4467, struct 52
                // byte). Dicek terakhir: 4096 byte pertama citra
                // lain sudah ditangkap di atas (ext4/FAT butuh
                // magic di area itu), jadi tidak ada tabrakan.
                if (len >= LpReader.GEOMETRY_OFFSET + LpReader.GEOMETRY_STRUCT_SIZE) {
                    val geo = readAt(raf, LpReader.GEOMETRY_OFFSET, LpReader.GEOMETRY_STRUCT_SIZE)
                    if (geo != null && u32le(geo, 0) == LpReader.GEOMETRY_MAGIC &&
                        u32le(geo, 4) == LpReader.GEOMETRY_STRUCT_SIZE.toLong()
                    ) {
                        val maxSize = u32le(geo, 40)
                        val slots = u32le(geo, 44)
                        val lbs = u32le(geo, 48)
                        if (maxSize > 0 && maxSize % 512L == 0L &&
                            slots >= 1 && slots <= 8 &&
                            lbs % 512L == 0L && lbs in 512L..1048576L
                        ) {
                            return ImgType.SUPER
                        }
                    }
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
    /** LD3 r160: [onProgress] menerima byte ramdisk yang sudah
     *  didekompresi (total tak diketahui — gzip tak menyimpan ukuran
     *  aslinya di header); [cancelFlag] yang terpasang membatalkan
     *  lewat ImgCancelledException. */
    fun listRamdisk(
        file: File,
        onProgress: (Long) -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): RamdiskResult {
        return when (val bytes = readAndDecompressRamdisk(file, onProgress, cancelFlag)) {
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

    /** Isi satu entri ramdisk sebagai teks UTF-8; null bila tak ada,
     *  direktori/symlink, terlalu besar, atau isinya biner. Ramdisk
     *  tidak punya ekstensi teks (init.rc, *.prop) — kenali teks
     *  dari isinya lewat [looksLikeText], bukan dari nama. */
    fun readRamdiskEntryText(file: File, entryName: String, maxBytes: Int = 1_000_000): String? {
        val bytes = readRamdiskEntry(file, entryName, maxBytes) ?: return null
        if (!looksLikeText(bytes)) return null
        return String(bytes, Charsets.UTF_8)
    }

    /** Heuristik teks: tanpa byte NUL di sampel awal (8 KiB). Berkas
     *  biner (ELF, kernel, arsip terkompresi) hampir selalu mengandung
     *  NUL di awal; berkas teks ramdisk (init.rc, *.prop, skrip)
     *  tidak pernah. */
    fun looksLikeText(bytes: ByteArray): Boolean {
        val sample = minOf(bytes.size, 8192)
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) return false
        }
        return true
    }

    /** Ekstrak satu entri ramdisk biasa ke [destDir]; null bila gagal / ditolak. */
    fun extractRamdiskEntry(file: File, entryName: String, destDir: File): File? {
        val (data, entries) = readRamdiskEntries(file) ?: return null
        val target = entries.firstOrNull { it.name == entryName } ?: return null
        if ((target.mode and 0xF000L) != 0x8000L) return null
        val rawOut = File(destDir, target.name)
        return try {
            val destCanonical = destDir.canonicalPath
            if (isUnsafeName(target.name) || !FileOps.isPathInside(destCanonical, rawOut.canonicalPath)) return null
            // M4: tabrakan nama memakai nama unik; tulis ke temp unik
            // lalu rename bila sukses — berkas yang sudah ada tidak
            // terpotong, dan tidak ada berkas sebagian yang tertinggal.
            val out = File(destDir, FileOps.uniqueName(destDir, target.name))
            out.parentFile?.mkdirs()
            val tmp = FileOps.tempFileIn(out.parentFile ?: destDir) ?: return null
            try {
                FileOutputStream(tmp).use { it.write(data, target.dataStart, target.dataEnd - target.dataStart) }
                if (tmp.renameTo(out)) out else null
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Ekstrak seluruh isi ramdisk ke [destDir] (seperti "Ekstrak Di Sini"
     *  untuk arsip); false bila ada entri yang gagal — berkas yang sudah
     *  tertulis dibersihkan. LD3 r160: [onProgress] menerima (entri
     *  selesai, total entri) — daftar entri sudah di memori; [cancelFlag]
     *  yang terpasang membatalkan lewat ImgCancelledException. */
    fun extractRamdiskAll(
        file: File,
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val (data, entries) = readRamdiskEntries(file) ?: return false
        val total = entries.size.toLong()
        var done = 0L
        val written = mutableListOf<File>()
        var ok = true
        try {
            val destCanonical = destDir.canonicalPath
            val budget = ExtractBudget()
            for (e in entries) {
                if (cancelFlag?.get() == true) throw ImgCancelledException()
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
                    // M1: anggaran diperiksa SEBELUM menulis — data sudah
                    // di memori dan ukurannya diketahui, jadi tidak ada
                    // alasan menulis dulu baru memeriksa.
                    if (!budget.addBytes(count)) { ok = false; break }
                    FileOutputStream(out).use { it.write(data, e.dataStart, e.dataEnd - e.dataStart) }
                    if (!existed) written += out
                }
                // Symlink dan tipe lain dilewati (tidak ditulis sebagai berkas).
                done++
                onProgress(done, total)
            }
        } catch (e: ImgCancelledException) {
            written.forEach { it.delete() }
            throw e
        } catch (e: Exception) {
            ok = false
        }
        if (!ok) written.forEach { it.delete() }
        return ok
    }

    /** Anak-anak langsung dari [prefix] di dalam ramdisk — cerminan
     *  archiveChildren untuk arsip: folder (dengan jumlah isi) dulu,
     *  lalu berkas. Folder disintesis dari segmen path, jadi entri
     *  "usr/bin/tool" memunculkan folder "usr/" walau tanpa entri
     *  direktori eksplisit. Nama dinormalisasi seperti di
     *  [readRamdiskEntries] (awalan "./" dibuang). */
    fun ramdiskChildren(entries: List<RamdiskEntry>, prefix: String): List<Pair<RamdiskEntry, Int?>> {
        val folderPaths = linkedSetOf<String>()
        val files = mutableListOf<RamdiskEntry>()
        for (entry in entries) {
            val name = entry.name.removePrefix("./")
            if (!name.startsWith(prefix)) continue
            val rest = name.removePrefix(prefix)
            if (rest.isBlank()) continue
            val slash = rest.indexOf('/')
            if (slash >= 0) {
                folderPaths += prefix + rest.substring(0, slash + 1)
            } else if (entry.isDir) {
                folderPaths += if (name.endsWith("/")) name else name + "/"
            } else {
                files += entry.copy(name = name)
            }
        }
        val folders = folderPaths.map { path ->
            val count = entries.count {
                val n = it.name.removePrefix("./")
                n.length > path.length && n.startsWith(path)
            }
            RamdiskEntry(path, true, false, 0L) to count
        }
        return folders.sortedBy { it.first.name.lowercase() } +
            files.sortedBy { it.name.lowercase() }.map { it to null }
    }

    /** Ubah citra sparse Android menjadi citra mentah di [out] (streaming,
     *  bukan ke memori — system image bisa multi-GB). [out] dimiliki
     *  unsparse: bila gagal (terlalu besar / rusak), berkas output
     *  parsial dihapus — kecuali berkas yang sudah ada sebelumnya.
     *  Batas keluaran [maxBytes] (default 8 GiB).
 *  LD3 r159: [onProgress] dipanggil per 64 KiB yang tertulis
 *  (done, total = totalBlks * blk_sz); [cancelFlag] yang terpasang
 *  menghentikan kerja dan mengembalikan [UnsparseResult.Cancelled].
     *
     *  Format (sparse_format.h AOSP): header 28 byte (magic 0xED26FF3A,
     *  major 1, blk_sz kelipatan 4, total_blks, total_chunks,
     *  image_checksum CRC32 802.3 atas data asli dengan don't-care
     *  dihitung 0); chunk 12 byte (tipe, chunk_sz dalam blok, total_sz
     *  termasuk header): RAW 0xCAC1, FILL 0xCAC2, DONT_CARE 0xCAC3,
     *  CRC32 0xCAC4. Isi chunk CRC32 dilewati — pembaca nyata
     *  (u-boot / libsparse) mengabaikannya; yang diverifikasi adalah
     *  image_checksum di header, bila bukan nol. */
    fun unsparse(
        source: File,
        out: File,
        maxBytes: Long = MAX_UNSPARSE_BYTES,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): UnsparseResult {
        if (!source.isFile) return UnsparseResult.Invalid("bukan berkas")
        if (maxBytes < 0) return UnsparseResult.Invalid("batas negatif")
        val existed = out.exists()
        val result = unsparseRun(source, out, maxBytes, onProgress, cancelFlag)
        // Gagal -> berkas output parsial tidak boleh tertinggal; berkas
        // yang sudah ada sebelumnya tidak disentuh.
        if (result !is UnsparseResult.Ok && !existed) out.delete()
        return result
    }

    /** Parse superblock ext4 (1024 byte di offset 1024); null bila bukan
     *  ext4, terpotong, atau field-nya tidak masuk akal. Superblock
     *  selalu di offset 1024 berapa pun ukuran bloknya. */
    fun parseExt4Superblock(file: File): Ext4Info? {
        if (!file.isFile) return null
        return try {
            FileSource(RandomAccessFile(file, "r")).use { src -> parseExt4Superblock(src) }
        } catch (e: Exception) {
            null
        }
    }

    /** Sama seperti [parseExt4Superblock] tapi dari sumber akses-acak
     *  apa pun — dipakai untuk membaca superblock langsung dari citra
     *  sparse tanpa unsparse (LD2 r157). */
    fun parseExt4Superblock(src: RandomSource): Ext4Info? {
        return try {
            val len = src.length()
            if (len < EXT4_SUPERBLOCK_OFFSET + EXT4_SUPERBLOCK_SIZE) return null
            val sb = src.readAt(EXT4_SUPERBLOCK_OFFSET, EXT4_SUPERBLOCK_SIZE) ?: return null
            // Magic 0xEF53 little-endian di 0x38 dari awal superblock.
            if (u16le(sb, 0x38) != 0xEF53) return null
            val logBlockSize = u32le(sb, 0x18)
            if (logBlockSize > 6) return null // blok 1 KiB..64 KiB
            val blockSize = 1024L shl logBlockSize.toInt()
            val compat = u32le(sb, 0x5C)
            val incompat = u32le(sb, 0x60)
            val roCompat = u32le(sb, 0x64)
            val is64bit = incompat and 0x80L != 0L
            val blocksLo = u32le(sb, 0x4)
            val blocksHi = if (is64bit) u32le(sb, 0x150) else 0L
            val totalBlocks = blocksLo or (blocksHi shl 32)
            val freeLo = u32le(sb, 0xC)
            val freeHi = if (is64bit) u32le(sb, 0x158) else 0L
            val freeBlocks = freeLo or (freeHi shl 32)
            if (totalBlocks <= 0 || freeBlocks > totalBlocks) return null
            if (totalBlocks > Long.MAX_VALUE / blockSize) return null
            val totalBytes = totalBlocks * blockSize
            val freeBytes = freeBlocks * blockSize
            val labelBytes = sb.copyOfRange(0x78, 0x78 + 16)
            val nul = labelBytes.indexOf(0.toByte())
            val label = String(labelBytes, 0, if (nul >= 0) nul else 16, Charsets.UTF_8).trim()
            Ext4Info(blockSize, totalBlocks, freeBlocks, totalBytes, freeBytes, label, compat, incompat, roCompat)
        } catch (e: Exception) {
            null
        }
    }

    /** Info ext4 untuk berkas .img apa pun yang memang ext4: mentah
     *  langsung dibaca, sparse dibaca langsung dari chunk-nya tanpa
     *  unsparse (LD2 r157 — dulu seluruh citra di-unsparse ke temp
     *  cuma untuk membaca 1 KB superblock). null bila bukan
     *  ext4/sparse atau rusak. */
    fun readExt4Info(file: File): Ext4Info? {
        return when (detectImgType(file)) {
            ImgType.EXT4 -> parseExt4Superblock(file)
            ImgType.SPARSE -> SparseSource.open(file)?.use { src -> parseExt4Superblock(src) }
            else -> null
        }
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
    private fun readAndDecompressRamdisk(
        file: File,
        onProgress: (Long) -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): RamdiskBytes {
        val info = parseBootImg(file) ?: return RamdiskBytes.Invalid
        if (info.ramdiskSize > MAX_RAMDISK_COMPRESSED_BYTES) return RamdiskBytes.Invalid
        val raw = try {
            RandomAccessFile(file, "r").use { raf ->
                readAt(raf, info.ramdiskOffset, info.ramdiskSize.toInt()) ?: return RamdiskBytes.Invalid
            }
        } catch (e: Exception) {
            return RamdiskBytes.Invalid
        }
        return decompressRamdisk(raw, onProgress, cancelFlag)
    }

    /** Dekompresi ramdisk: cpio mentah atau gzip. Kompresi lain dikenali
     *  dari magic-nya dan dilaporkan — tidak pernah gagal diam-diam. */
    private fun decompressRamdisk(
        raw: ByteArray,
        onProgress: (Long) -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): RamdiskBytes {
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
                        if (cancelFlag?.get() == true) throw ImgCancelledException()
                        val n = gz.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_RAMDISK_BYTES) return RamdiskBytes.Invalid
                        out.write(buf, 0, n)
                        onProgress(total)
                    }
                }
                RamdiskBytes.Data(out.toByteArray())
            } catch (e: ImgCancelledException) {
                throw e
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

    /** Baca ramdisk + parse cpio; null bila boot image / ramdisk rusak.
     *  Nama dinormalisasi (awalan "./" dibuang — arsip cpio dari
     *  `find .` memakai "./init.rc") supaya layar isi dan pencarian
     *  entri memakai nama yang sama. */
    private fun readRamdiskEntries(file: File): Pair<ByteArray, List<CpioEntry>>? {
        val data = when (val r = readAndDecompressRamdisk(file)) {
            is RamdiskBytes.Data -> r.bytes
            else -> return null
        }
        val entries = parseCpio(data) ?: return null
        return data to entries.map { it.copy(name = it.name.removePrefix("./")) }
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
    /** Kerja [unsparse]; pemanggil yang menghapus output bila gagal.
     *  Semua jalan gagal mengembalikan Invalid/TooLarge — tidak ada
     *  yang throw ke pemanggil (exception IO ditangkap di bawah). */
    private fun unsparseRun(
        source: File,
        out: File,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit,
        cancelFlag: AtomicBoolean?
    ): UnsparseResult {
        return try {
            BufferedInputStream(FileInputStream(source), IO_BUFFER).use { ins ->
                val header = ByteArray(SPARSE_HEADER_SIZE)
                if (!readExact(ins, header, 0, SPARSE_HEADER_SIZE)) {
                    return UnsparseResult.Invalid("header terpotong")
                }
                if (u32le(header, 0) != SPARSE_MAGIC) {
                    return UnsparseResult.Invalid("bukan sparse")
                }
                val major = u16le(header, 4)
                if (major != 1) return UnsparseResult.Invalid("major_version $major")
                val fileHdrSz = u16le(header, 8)
                val chunkHdrSz = u16le(header, 10)
                if (fileHdrSz < SPARSE_HEADER_SIZE || chunkHdrSz < SPARSE_CHUNK_HEADER_SIZE) {
                    return UnsparseResult.Invalid("ukuran header tidak valid")
                }
                val blkSz = u32le(header, 12)
                val totalBlks = u32le(header, 16)
                val totalChunks = u32le(header, 20)
                val imageChecksum = u32le(header, 24)
                if (blkSz == 0L || blkSz % 4L != 0L) {
                    return UnsparseResult.Invalid("blk_sz $blkSz")
                }
                // Total keluaran tanpa overflow: totalBlks * blkSz > maxBytes.
                if (totalBlks > maxBytes / blkSz) return UnsparseResult.TooLarge
                if (totalBlks > Long.MAX_VALUE / blkSz) {
                    return UnsparseResult.Invalid("total_blks * blk_sz meluap")
                }
                // LD3 r159: total untuk progres (done, total).
                val totalBytes = totalBlks * blkSz
                // Lewati sisa header bila file_hdr_sz > 28.
                if (!skipExact(ins, (fileHdrSz - SPARSE_HEADER_SIZE).toLong())) {
                    return UnsparseResult.Invalid("header terpotong")
                }
                BufferedOutputStream(FileOutputStream(out), IO_BUFFER).use { outs ->
                    val crc = CRC32()
                    val buf = ByteArray(IO_BUFFER)
                    var blocksWritten = 0L
                    var bytesWritten = 0L
                    for (chunkIndex in 0L until totalChunks) {
                        // LD3 r159: batal sebelum chunk berikutnya diproses.
                        if (cancelFlag?.get() == true) return UnsparseResult.Cancelled
                        val chunkHeader = ByteArray(SPARSE_CHUNK_HEADER_SIZE)
                        if (!readExact(ins, chunkHeader, 0, SPARSE_CHUNK_HEADER_SIZE)) {
                            return UnsparseResult.Invalid("chunk $chunkIndex terpotong")
                        }
                        val type = u16le(chunkHeader, 0)
                        val chunkSz = u32le(chunkHeader, 4)
                        val totalSz = u32le(chunkHeader, 8)
                        if (totalSz < chunkHdrSz) {
                            return UnsparseResult.Invalid("chunk $chunkIndex total_sz $totalSz")
                        }
                        // Lewati sisa header chunk bila chunk_hdr_sz > 12.
                        if (!skipExact(ins, (chunkHdrSz - SPARSE_CHUNK_HEADER_SIZE).toLong())) {
                            return UnsparseResult.Invalid("chunk $chunkIndex terpotong")
                        }
                        val dataSz = totalSz - chunkHdrSz
                        when (type) {
                            SPARSE_CHUNK_RAW -> {
                                if (chunkSz > Long.MAX_VALUE / blkSz) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex terlalu besar")
                                }
                                val bytes = chunkSz * blkSz
                                if (dataSz != bytes) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex total_sz tidak cocok")
                                }
                                if (chunkSz > totalBlks - blocksWritten) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex melebihi total_blks")
                                }
                                var remaining = bytes
                                while (remaining > 0) {
                                    val n = ins.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                    if (n < 0) return UnsparseResult.Invalid("chunk $chunkIndex data terpotong")
                                    outs.write(buf, 0, n)
                                    crc.update(buf, 0, n)
                                    remaining -= n
                                    bytesWritten += n
                                    if (bytesWritten > maxBytes) return UnsparseResult.TooLarge
                                    if (cancelFlag?.get() == true) return UnsparseResult.Cancelled
                                    onProgress(bytesWritten, totalBytes)
                                }
                                blocksWritten += chunkSz
                            }
                            SPARSE_CHUNK_FILL -> {
                                if (dataSz != 4L) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex fill bukan 4 byte")
                                }
                                if (chunkSz > totalBlks - blocksWritten) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex melebihi total_blks")
                                }
                                val fill = ByteArray(4)
                                if (!readExact(ins, fill, 0, 4)) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex data terpotong")
                                }
                                if (chunkSz > Long.MAX_VALUE / blkSz) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex terlalu besar")
                                }
                                val bytes = chunkSz * blkSz
                                // Isi buffer dengan pola 4 byte berulang.
                                for (i in buf.indices) buf[i] = fill[i % 4]
                                var remaining = bytes
                                while (remaining > 0) {
                                    val n = minOf(buf.size.toLong(), remaining).toInt()
                                    outs.write(buf, 0, n)
                                    crc.update(buf, 0, n)
                                    remaining -= n
                                    bytesWritten += n
                                    if (bytesWritten > maxBytes) return UnsparseResult.TooLarge
                                    if (cancelFlag?.get() == true) return UnsparseResult.Cancelled
                                    onProgress(bytesWritten, totalBytes)
                                }
                                blocksWritten += chunkSz
                            }
                            SPARSE_CHUNK_DONT_CARE -> {
                                if (dataSz != 0L) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex don't-care membawa data")
                                }
                                if (chunkSz > totalBlks - blocksWritten) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex melebihi total_blks")
                                }
                                if (chunkSz > Long.MAX_VALUE / blkSz) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex terlalu besar")
                                }
                                val bytes = chunkSz * blkSz
                                buf.fill(0)
                                var remaining = bytes
                                while (remaining > 0) {
                                    val n = minOf(buf.size.toLong(), remaining).toInt()
                                    outs.write(buf, 0, n)
                                    crc.update(buf, 0, n)
                                    remaining -= n
                                    bytesWritten += n
                                    if (bytesWritten > maxBytes) return UnsparseResult.TooLarge
                                    if (cancelFlag?.get() == true) return UnsparseResult.Cancelled
                                    onProgress(bytesWritten, totalBytes)
                                }
                                blocksWritten += chunkSz
                            }
                            SPARSE_CHUNK_CRC32 -> {
                                // Dilewati seperti pembaca nyata (u-boot /
                                // libsparse mengabaikan chunk CRC32); yang
                                // diverifikasi adalah image_checksum header.
                                if (dataSz != 4L) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex crc bukan 4 byte")
                                }
                                if (!skipExact(ins, 4)) {
                                    return UnsparseResult.Invalid("chunk $chunkIndex data terpotong")
                                }
                            }
                            else -> return UnsparseResult.Invalid("chunk $chunkIndex tipe 0x${type.toString(16)}")
                        }
                    }
                    if (blocksWritten != totalBlks) {
                        return UnsparseResult.Invalid("blok tertulis $blocksWritten, mau $totalBlks")
                    }
                    if (imageChecksum != 0L && crc.value != imageChecksum) {
                        return UnsparseResult.Invalid("image_checksum tidak cocok")
                    }
                    outs.flush()
                    UnsparseResult.Ok(out, bytesWritten)
                }
            }
        } catch (e: Exception) {
            UnsparseResult.Invalid(e.message ?: "gagal baca")
        }
    }

    private fun u16le(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    /** Baca tepat [len] byte dari stream; false bila EOF sebelum penuh. */
    private fun readExact(ins: InputStream, buf: ByteArray, off: Int, len: Int): Boolean {
        var read = 0
        while (read < len) {
            val n = ins.read(buf, off + read, len - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    /** Lewati tepat [count] byte dari stream; false bila EOF sebelum penuh. */
    private fun skipExact(ins: InputStream, count: Long): Boolean {
        var remaining = count
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val n = ins.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) return false
            remaining -= n
        }
        return true
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
