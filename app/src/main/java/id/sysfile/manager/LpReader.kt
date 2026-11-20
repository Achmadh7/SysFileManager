package id.sysfile.manager

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Satu partisi logis di dalam super.img (liblp). [available] false
 *  bila partisi dinonaktifkan (LP_PARTITION_ATTR_DISABLED), atau
 *  extent-nya menunjuk ke perangkat blok lain (target_source != 0),
 *  atau jangkauan fisiknya keluar dari berkas — partisi seperti itu
 *  tetap ditampilkan di daftar, tapi tidak bisa dibuka/diekstrak.
 *  [name] sudah termasuk akhiran slot ("_a"/"_b") bila partisi
 *  bertanda SLOT_SUFFIXED (seperti liblp AdjustMetadataForSlot).
 *  [firstExtentIndex]/[numExtents] adalah indeks ke tabel extent
 *  metadata (dipakai [LpReader.materialize]). */
data class LpPartition(
    val name: String,
    val sizeBytes: Long,
    val attributes: Int,
    val available: Boolean,
    val firstExtentIndex: Int,
    val numExtents: Int
)

/** Sesi baca super.img: citra mentah yang dibaca + temp hasil unsparse
 *  (null bila citra memang mentah) + reader yang memegang file itu.
 *  [close] menutup reader dan menghapus temp bila ada. */
class LpSession(
    val rawFile: File,
    val tempFile: File?,
    val reader: LpReader
) : Closeable {
    override fun close() {
        try { reader.close() } catch (_: Exception) { }
        tempFile?.delete()
    }
}

/** Pembaca partisi logis (liblp) read-only untuk super.img mentah.
 *
 *  Dibaca lewat [RandomSource] (berkas mentah, atau citra sparse
 *  langsung sejak LD2 r158) — citra multi-GB TIDAK PERNAH dimuat
 *  utuh ke memori; yang dibaca hanya geometri, header + tabel metadata,
 *  dan sektor-sektor partisi yang sedang dimaterialisasi.
 *
 *  Tata letak (metadata_format.h + reader.cpp + utility.cpp AOSP):
 *  4096 byte pertama dicadangkan; geometri primer @4096, cadangan
 *  @8192 (52 byte: magic 0x616c4467, struct_size 52, checksum SHA-256,
 *  metadata_max_size, metadata_slot_count, logical_block_size);
 *  slot metadata primer @12288 + max_size*slot, cadangan setelah
 *  max_size*slot_count (header magic 0x414C5030, major 10, minor <= 2,
 *  header 128 byte v1.0 / 256 byte v1.2, checksum SHA-256 header +
 *  tabel); tabel partisi 52 byte (nama[36], atribut, first_extent,
 *  num_extents, grup), extent 24 byte (num_sectors u64, target_type
 *  0=LINEAR / 1=ZERO, target_data u64, target_source u32), grup
 *  48 byte, perangkat blok 64 byte (first_logical_sector u64).
 *  Region metadata total 4096 + (4096 + max_size*slot_count)*2 tidak
 *  boleh melewati first_logical_sector*512. Sektor selalu 512 byte.
 *
 *  Batasan yang disengaja (mengikuti liblp bila memungkinkan):
 *  - Checksum SHA-256 geometri, header, dan tabel DIVERIFIKASI seperti
 *    liblp (ParseGeometry / ReadMetadataHeader / ParseMetadata) —
 *    citra dengan checksum rusak ditolak bersih, bukan dibuka setengah.
 *  - Slot dicoba berurutan: slot 0 primer, slot 0 cadangan, slot 1
 *    primer, slot 1 cadangan (liblp hanya mencoba satu slot; pembaca
 *    ini mencoba slot 1 juga supaya super.img yang slot A-nya rusak
 *    tetap bisa dibuka lewat slot B). Nama SLOT_SUFFIXED mendapat
 *    akhiran "_a"/"_b" sesuai slot yang dipakai.
 *  - Metadata rusak (magic, versi, batas tabel, indeks extent/grup,
 *    atribut tak dikenal, nama duplikat/panjang) membuat seluruh
 *    metadata ditolak — bukan daftar partisi setengah. Sebaliknya,
 *    extent yang jangkauannya keluar berkas / menunjuk perangkat lain
 *    hanya membuat PARTISI itu tidak tersedia (available=false),
 *    metadata lain tetap terbaca.
 *  - Materialisasi partisi mengalir ke berkas temp (streaming, cap
 *    8 GiB seperti unsparse); berkas setengah ditulis dihapus.
 *  - Semua akses sumber dikunci satu lock: layar dan MainActivity
 *    berbagi satu reader yang sama.
 */
class LpReader private constructor(
    private val src: RandomSource,
    val slotNumber: Int,
    val partitions: List<LpPartition>,
    private val extents: List<LpExtent>
) : Closeable {

    companion object {
        /** Magic geometri liblp ("gDla" little-endian). */
        const val GEOMETRY_MAGIC = 0x616c4467L
        /** Geometri primer ada di offset ini (LP_PARTITION_RESERVED_BYTES). */
        const val GEOMETRY_OFFSET = 4096L
        /** Ukuran struct LpMetadataGeometry (52 byte). */
        const val GEOMETRY_STRUCT_SIZE = 52
        private const val GEOMETRY_SIZE = 4096L
        private const val RESERVED_BYTES = 4096L
        private const val HEADER_MAGIC = 0x414C5030L
        private const val MAJOR_VERSION = 10
        private const val MINOR_VERSION_MAX = 2
        private const val VERSION_FOR_UPDATED_ATTR = 1
        private const val VERSION_FOR_EXPANDED_HEADER = 2
        private const val HEADER_V10_SIZE = 128
        private const val HEADER_V12_SIZE = 256
        private const val SECTOR_SIZE = 512L
        private const val PARTITION_ENTRY_SIZE = 52
        private const val EXTENT_ENTRY_SIZE = 24
        private const val GROUP_ENTRY_SIZE = 48
        private const val BLOCK_DEVICE_ENTRY_SIZE = 64
        private const val TARGET_LINEAR = 0
        private const val TARGET_ZERO = 1
        private const val ATTR_READONLY = 0x1
        private const val ATTR_SLOT_SUFFIXED = 0x2
        private const val ATTR_UPDATED = 0x4
        private const val ATTR_DISABLED = 0x8
        private const val ATTR_MASK_V0 = ATTR_READONLY or ATTR_SLOT_SUFFIXED
        private const val ATTR_MASK_V1 = ATTR_UPDATED or ATTR_DISABLED
        /** Batas longgar anti-OOM untuk metadata jahat (aslinya 64 KiB). */
        private const val MAX_METADATA_SIZE = 16L * 1024L * 1024L
        private const val MAX_PARTITIONS = 10_000
        private const val MAX_EXTENTS = 1_000_000
        private const val MAX_GROUPS = 10_000
        private const val MAX_BLOCK_DEVICES = 64
        private const val IO_BUFFER = 65536

        /** Buka super.img mentah. Null bila bukan super.img / rusak /
         *  terpotong. Slot 0 dicoba dulu (primer lalu cadangan), lalu
         *  slot 1 bila ada. */
        fun open(file: File): LpReader? {
            if (!file.isFile) return null
            val src = try { FileSource(RandomAccessFile(file, "r")) } catch (_: Exception) { return null }
            return try {
                val reader = openOn(src)
                if (reader == null) {
                    try { src.close() } catch (_: Exception) { }
                    null
                } else reader
            } catch (_: Exception) {
                try { src.close() } catch (_: Exception) { }
                null
            }
        }

        /** Buka super.img sparse langsung tanpa unsparse (LD2 r158):
         *  geometri + metadata dibaca dari chunk sparse lewat
         *  SparseSource — tidak ada temp multi-GB. Null bila bukan
         *  sparse/super.img atau rusak. Materialisasi partisi tetap
         *  mengalir ke berkas temp (itu isi partisi, bukan unsparse). */
        fun openSparse(file: File): LpReader? {
            val src = SparseSource.open(file) ?: return null
            return try {
                val reader = openOn(src)
                if (reader == null) {
                    try { src.close() } catch (_: Exception) { }
                    null
                } else reader
            } catch (_: Exception) {
                try { src.close() } catch (_: Exception) { }
                null
            }
        }

        private fun openOn(src: RandomSource): LpReader? {
            val fileLen = try { src.length() } catch (_: Exception) { return null }
            val geometry = parseGeometry(src, fileLen, RESERVED_BYTES)
                ?: parseGeometry(src, fileLen, RESERVED_BYTES + GEOMETRY_SIZE)
                ?: return null
            val slots = minOf(geometry.slotCount, 2)
            for (slot in 0 until slots) {
                for (off in listOf(
                    primaryMetadataOffset(geometry, slot),
                    backupMetadataOffset(geometry, slot)
                )) {
                    val parsed = parseMetadata(src, fileLen, geometry, off, slot) ?: continue
                    return LpReader(src, slot, parsed.partitions, parsed.extents)
                }
            }
            return null
        }

        /** Offset slot metadata primer (utility.cpp liblp). */
        private fun primaryMetadataOffset(geometry: LpGeometry, slot: Int): Long {
            return RESERVED_BYTES + GEOMETRY_SIZE * 2 + geometry.maxSize * slot
        }

        /** Offset slot metadata cadangan (utility.cpp liblp). */
        private fun backupMetadataOffset(geometry: LpGeometry, slot: Int): Long {
            return primaryMetadataOffset(geometry, slot) + geometry.maxSize * geometry.slotCount
        }

        /** Geometri di [offset] (primer @4096, cadangan @8192). Null
         *  bila magic / struct_size / checksum / field-nya tidak
         *  masuk akal — checksum SHA-256 diverifikasi seperti liblp. */
        private fun parseGeometry(src: RandomSource, fileLen: Long, offset: Long): LpGeometry? {
            if (offset < 0 || offset + GEOMETRY_STRUCT_SIZE > fileLen) return null
            val g = readBytes(src, offset, GEOMETRY_STRUCT_SIZE) ?: return null
            if (u32le(g, 0) != GEOMETRY_MAGIC) return null
            if (u32le(g, 4) != GEOMETRY_STRUCT_SIZE.toLong()) return null
            val check = g.copyOf()
            check.fill(0, 8, 40)
            if (!sha256(check).contentEquals(g.copyOfRange(8, 40))) return null
            val maxSize = u32le(g, 40)
            val slotCount = u32le(g, 44)
            val lbs = u32le(g, 48)
            if (maxSize <= 0 || maxSize % SECTOR_SIZE != 0L || maxSize > MAX_METADATA_SIZE) return null
            if (slotCount < 1 || slotCount > 8) return null
            if (lbs < SECTOR_SIZE || lbs > 1048576L || lbs % SECTOR_SIZE != 0L) return null
            return LpGeometry(maxSize, slotCount.toInt(), lbs)
        }

        /** Metadata (header + tabel) di [slotOffset] untuk [slotNumber].
         *  Null bila rusak — semua aturan liblp (magic, versi, ukuran
         *  header, checksum header + tabel, batas tabel, ukuran entri,
         *  atribut, indeks extent/grup, nama) diperiksa. */
        private fun parseMetadata(
            src: RandomSource,
            fileLen: Long,
            geometry: LpGeometry,
            slotOffset: Long,
            slotNumber: Int
        ): ParsedMetadata? {
            if (slotOffset < 0 || slotOffset + HEADER_V10_SIZE > fileLen) return null
            val head128 = readBytes(src, slotOffset, HEADER_V10_SIZE) ?: return null
            if (u32le(head128, 0) != HEADER_MAGIC) return null
            val major = u16le(head128, 4)
            val minor = u16le(head128, 6)
            if (major != MAJOR_VERSION || minor > MINOR_VERSION_MAX) return null
            val headerSize = if (minor < VERSION_FOR_EXPANDED_HEADER) HEADER_V10_SIZE else HEADER_V12_SIZE
            if (u32le(head128, 8) != headerSize.toLong()) return null
            if (slotOffset + headerSize > fileLen) return null
            val header = readBytes(src, slotOffset, headerSize) ?: return null
            val headerCheck = header.copyOf()
            headerCheck.fill(0, 12, 44)
            if (!sha256(headerCheck).contentEquals(header.copyOfRange(12, 44))) return null
            val tablesSize = u32le(header, 44)
            if (tablesSize > geometry.maxSize) return null
            if (headerSize.toLong() + tablesSize > geometry.maxSize) return null
            if (slotOffset + headerSize + tablesSize > fileLen) return null
            if (tablesSize > Int.MAX_VALUE) return null
            val tables = readBytes(src, slotOffset + headerSize, tablesSize.toInt()) ?: return null
            if (!sha256(tables).contentEquals(header.copyOfRange(48, 80))) return null
            val validAttrs = ATTR_MASK_V0 or
                (if (minor >= VERSION_FOR_UPDATED_ATTR) ATTR_MASK_V1 else 0)
            val (partOff, partNum) = tableBounds(header, tablesSize, 0, PARTITION_ENTRY_SIZE)
                ?: return null
            val (extOff, extNum) = tableBounds(header, tablesSize, 1, EXTENT_ENTRY_SIZE)
                ?: return null
            val (_, groupNum) = tableBounds(header, tablesSize, 2, GROUP_ENTRY_SIZE)
                ?: return null
            val (devOff, devNum) = tableBounds(header, tablesSize, 3, BLOCK_DEVICE_ENTRY_SIZE)
                ?: return null
            if (partNum > MAX_PARTITIONS || extNum > MAX_EXTENTS ||
                groupNum > MAX_GROUPS || devNum > MAX_BLOCK_DEVICES
            ) return null
            if (devNum < 1) return null
            val extents = parseExtents(tables, extOff, extNum, devNum) ?: return null
            // Perangkat blok pertama = super itu sendiri; region
            // metadata tidak boleh menimpa isi partisi logis.
            val firstLogicalSector = u64le(tables, devOff)
            if (firstLogicalSector < 0 || firstLogicalSector > Long.MAX_VALUE / SECTOR_SIZE) return null
            val metadataRegion = RESERVED_BYTES +
                (GEOMETRY_SIZE + geometry.maxSize * geometry.slotCount) * 2
            if (metadataRegion > firstLogicalSector * SECTOR_SIZE) return null
            val partitions = parsePartitions(
                tables, partOff, partNum, extents, extNum, groupNum,
                validAttrs, slotNumber, fileLen
            ) ?: return null
            return ParsedMetadata(partitions, extents)
        }

        /** Batas satu tabel: (offset, jumlah) atau null bila
         *  entry_size salah / keluar dari tables_size. */
        private fun tableBounds(
            header: ByteArray,
            tablesSize: Long,
            tableIndex: Int,
            entrySize: Int
        ): Pair<Int, Int>? {
            val base = 80 + tableIndex * 12
            val offset = u32le(header, base)
            val num = u32le(header, base + 4)
            val esz = u32le(header, base + 8)
            if (esz != entrySize.toLong()) return null
            if (offset > tablesSize) return null
            if (num > (tablesSize - offset) / entrySize) return null
            return offset.toInt() to num.toInt()
        }

        private fun parseExtents(
            tables: ByteArray,
            extOff: Int,
            extNum: Int,
            devNum: Int
        ): List<LpExtent>? {
            val out = ArrayList<LpExtent>(extNum)
            for (i in 0 until extNum) {
                val o = extOff + i * EXTENT_ENTRY_SIZE
                val numSectors = u64le(tables, o)
                val type = u32le(tables, o + 8).toInt()
                val data = u64le(tables, o + 12)
                val source = u32le(tables, o + 20).toInt()
                if (numSectors < 0 || data < 0) return null
                if (type != TARGET_LINEAR && type != TARGET_ZERO) return null
                if (type == TARGET_LINEAR) {
                    if (source < 0 || source >= devNum) return null
                } else {
                    if (data != 0L || source != 0) return null
                }
                out += LpExtent(numSectors, type, data, source)
            }
            return out
        }

        private fun parsePartitions(
            tables: ByteArray,
            partOff: Int,
            partNum: Int,
            extents: List<LpExtent>,
            extNum: Int,
            groupNum: Int,
            validAttrs: Int,
            slotNumber: Int,
            fileLen: Long
        ): List<LpPartition>? {
            val out = ArrayList<LpPartition>(partNum)
            val seen = HashSet<String>(partNum)
            for (i in 0 until partNum) {
                val o = partOff + i * PARTITION_ENTRY_SIZE
                val rawName = partitionName(tables, o) ?: return null
                val attrs = u32le(tables, o + 36).toInt()
                if (attrs and validAttrs.inv() != 0) return null
                val first = u32le(tables, o + 40)
                val num = u32le(tables, o + 44)
                val group = u32le(tables, o + 48)
                if (num < 1) return null
                if (first > extNum || num > extNum - first) return null
                if (group >= groupNum) return null
                // Akhiran slot seperti liblp (nama + akhiran harus
                // muat 36 byte, kalau tidak seluruh metadata ditolak).
                val name = if (attrs and ATTR_SLOT_SUFFIXED != 0) {
                    val suffixed = rawName + if (slotNumber == 0) "_a" else "_b"
                    if (suffixed.length > 36) return null
                    suffixed
                } else rawName
                if (!seen.add(name)) return null
                var size = 0L
                var available = attrs and ATTR_DISABLED == 0
                for (j in 0 until num.toInt()) {
                    val e = extents[(first + j).toInt()]
                    if (e.numSectors > Long.MAX_VALUE / SECTOR_SIZE) return null
                    val bytes = e.numSectors * SECTOR_SIZE
                    if (size > Long.MAX_VALUE - bytes) return null
                    size += bytes
                    if (available && e.targetType == TARGET_LINEAR) {
                        if (e.targetSource != 0) {
                            available = false
                        } else if (e.targetData > Long.MAX_VALUE / SECTOR_SIZE) {
                            available = false
                        } else {
                            val physStart = e.targetData * SECTOR_SIZE
                            if (physStart > fileLen || bytes > fileLen - physStart) {
                                available = false
                            }
                        }
                    }
                }
                out += LpPartition(
                    name = name,
                    sizeBytes = size,
                    attributes = attrs,
                    available = available,
                    firstExtentIndex = first.toInt(),
                    numExtents = num.toInt()
                )
            }
            return out
        }

        /** Nama partisi dari 36 byte: hanya ASCII alfanumerik / '_',
         *  minimal satu karakter; null bila kosong / karakter liar. */
        private fun partitionName(tables: ByteArray, off: Int): String? {
            val end = (off until off + 36).firstOrNull { tables[it] == 0.toByte() } ?: (off + 36)
            if (end == off) return null
            val sb = StringBuilder()
            for (i in off until end) {
                val c = tables[i].toInt() and 0xFF
                if (!(c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code ||
                        c in '0'.code..'9'.code || c == '_'.code)
                ) return null
                sb.append(c.toChar())
            }
            return sb.toString()
        }

        private fun sha256(bytes: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(bytes)
        }

        private fun readBytes(src: RandomSource, offset: Long, size: Int): ByteArray? {
            if (offset < 0L || size < 0) return null
            val len = try { src.length() } catch (_: Exception) { return null }
            if (offset > len || size > len - offset) return null
            return try { src.readAt(offset, size) } catch (_: Exception) { null }
        }

        private fun u16le(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

        private fun u32le(b: ByteArray, off: Int): Long =
            (b[off].toLong() and 0xFF) or
                ((b[off + 1].toLong() and 0xFF) shl 8) or
                ((b[off + 2].toLong() and 0xFF) shl 16) or
                ((b[off + 3].toLong() and 0xFF) shl 24)

        private fun u64le(b: ByteArray, off: Int): Long =
            u32le(b, off) or (u32le(b, off + 4) shl 32)
    }

    private val lock = Any()

    override fun close() {
        try { src.close() } catch (_: Exception) { }
    }

    private fun readAt(offset: Long, size: Int): ByteArray? {
        synchronized(lock) { return readBytes(src, offset, size) }
    }

    /** Materialisasi satu partisi ke [out] (streaming per sektor —
     *  partisi multi-GB tidak pernah dimuat ke memori). False bila
     *  partisi tidak tersedia, melebihi [maxBytes], atau gagal di
     *  tengah jalan; berkas setengah ditulis dihapus (kecuali berkas
     *  yang sudah ada sebelumnya, seperti pola unsparse r153).
     *  Extent ZERO ditulis sebagai nol; extent LINEAR disalin dari
     *  sektor fisiknya. True hanya bila byte tertulis sama persis
     *  dengan ukuran partisi. */
    fun materialize(
        partition: LpPartition,
        out: File,
        maxBytes: Long = ImgOps.MAX_UNSPARSE_BYTES
    ): Boolean {
        if (!partition.available) return false
        if (partition.sizeBytes < 0 || partition.sizeBytes > maxBytes) return false
        val existed = out.exists()
        return try {
            val ok = materializeRun(partition, out, maxBytes)
            if (!ok && !existed) out.delete()
            ok
        } catch (_: Exception) {
            if (!existed) out.delete()
            false
        }
    }

    private fun materializeRun(partition: LpPartition, out: File, maxBytes: Long): Boolean {
        var written = 0L
        FileOutputStream(out).use { fos ->
            val buf = ByteArray(IO_BUFFER)
            for (i in partition.firstExtentIndex until partition.firstExtentIndex + partition.numExtents) {
                val e = extents[i]
                val bytes = e.numSectors * SECTOR_SIZE
                if (e.targetType == TARGET_ZERO) {
                    buf.fill(0)
                    var remaining = bytes
                    while (remaining > 0) {
                        val n = minOf(buf.size.toLong(), remaining).toInt()
                        fos.write(buf, 0, n)
                        remaining -= n
                        written += n
                        if (written > maxBytes) return false
                    }
                } else {
                    var pos = e.targetData * SECTOR_SIZE
                    var remaining = bytes
                    while (remaining > 0) {
                        val n = minOf(buf.size.toLong(), remaining).toInt()
                        val got = readAt(pos, n) ?: return false
                        fos.write(got)
                        pos += n
                        remaining -= n
                        written += n
                        if (written > maxBytes) return false
                    }
                }
            }
        }
        return written == partition.sizeBytes
    }
}

/** Geometri super.img (52 byte) setelah validasi. */
private data class LpGeometry(
    val maxSize: Long,
    val slotCount: Int,
    val logicalBlockSize: Long
)

/** Satu extent metadata (24 byte). */
private data class LpExtent(
    val numSectors: Long,
    val targetType: Int,
    val targetData: Long,
    val targetSource: Int
)

/** Hasil parse satu slot metadata. */
private data class ParsedMetadata(
    val partitions: List<LpPartition>,
    val extents: List<LpExtent>
)
