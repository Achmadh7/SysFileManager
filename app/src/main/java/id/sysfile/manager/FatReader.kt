package id.sysfile.manager

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.BitSet

/** Jenis FAT, ditentukan dari jumlah klaster (Microsoft FAT spec:
 *  < 4085 klaster = FAT12, < 65525 = FAT16, sisanya FAT32). */
enum class FatType { FAT12, FAT16, FAT32 }

/** Satu entri direktori FAT. Tidak ada symlink di FAT — [isDir]
 *  false berarti berkas reguler. */
data class FatEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val firstCluster: Long
)

/** Pembaca FAT12/16/32 read-only untuk citra .img mentah (citra kartu
 *  SD, partisi ESP, citra recovery).
 *
 *  Dibaca lewat [RandomSource] (berkas mentah, atau citra sparse
 *  langsung sejak LD2 r158) — citra multi-GB TIDAK PERNAH dimuat
 *  utuh ke memori; yang dibaca hanya sektor boot, entri FAT yang sedang
 *  dituju, dan klaster direktori/berkas yang sedang dibaca.
 *
 *  Tata letak (Microsoft FAT spec + OSDev wiki "FAT"):
 *  BPB: bytes/sector @11, sector/cluster @13, reserved @14, jumlah FAT
 *  @16, entri root @17 (FAT12/16), total sector @19/@32, ukuran FAT
 *  @22 (FAT12/16) / @36 (FAT32), klaster root @44 (FAT32);
 *  entri direktori 32 byte (nama 8.3 @0, atribut @11, klaster hi @20,
 *  klaster lo @26, ukuran @28); entri LFN (atribut 0x0F, urutan @0
 *  dengan bit 0x40 di slot terakhir, checksum @13, karakter UTF-16LE
 *  di @1/@14/@28, terminator 0x0000, padding 0xFFFF).
 *  Entri FAT12 dipaket 12 bit (offset = klaster + klaster/2; klaster
 *  ganjil digeser >> 4, genap di-mask 0xFFF); FAT16 2 byte; FAT32 4 byte
 *  dengan 28 bit terpakai. EOC: >= 0xFF8 / 0xFFF8 / 0x0FFFFFF8;
 *  klaster rusak: 0xFF7 / 0xFFF7 / 0x0FFFFFF7.
 *
 *  Batasan yang disengaja:
 *  - Citra sparse yang isinya FAT dibaca langsung lewat SparseSource
 *    (LD2 r158): dulu ditolak — deteksi (r151) membedakan SPARSE dari
 *    FAT lewat magic, dan SPARSE dirutekan ke pembaca ext4 (r154).
 *  - Nama panjang (LFN) dipakai hanya bila seluruh slotnya valid
 *    (urutan 1..N berurutan, flag 0x40 di slot teratas, checksum
 *    cocok dengan nama pendek); bila tidak valid, jatuh kembali ke
 *    nama 8.3 — bukan crash, bukan nama setengah.
 *  - Rantai klaster tidak pernah diikuti tanpa batas: setiap klaster
 *    yang dikunjungi dicatat di BitSet (tumbuh malas, 1 bit per
 *    klaster yang disentuh) — rantai berputar terdeteksi dan gagal
 *    bersih; cap langkah tetap ada sebagai pengaman kedua. Jadi FAT
 *    rusak tidak bisa membuat loop tak berujung atau mengembalikan
 *    byte klaster yang terulang diam-diam.
 *  - Semua akses sumber dikunci satu lock: layar dan MainActivity
 *    berbagi satu reader yang sama.
 */
class FatReader private constructor(
    private val src: RandomSource,
    val type: FatType,
    private val bytesPerSector: Int,
    private val sectorsPerCluster: Int,
    private val clusterBytes: Long,
    private val reservedSectors: Long,
    private val numFats: Int,
    private val fatSizeSectors: Long,
    private val fatStart: Long,
    private val firstDataSector: Long,
    private val totalClusters: Long,
    private val rootDirStart: Long,
    private val rootDirBytes: Long,
    private val rootCluster: Long
) : Closeable {

    companion object {
        /** Penanda direktori root (bukan nomor klaster sungguhan). */
        const val ROOT_CLUSTER = -1L
        const val MAX_DIR_BYTES = 16L * 1024L * 1024L
        private const val EOC = -2L
        private const val BROKEN = -3L

        /** Buka citra FAT mentah. Null bila bukan FAT / rusak / terpotong. */
        fun open(file: File): FatReader? {
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

        /** Buka citra sparse yang isinya FAT, langsung tanpa unsparse
         *  (LD2 r158): sektor boot, FAT, dan klaster dibaca dari chunk
         *  sparse lewat SparseSource — tidak ada temp multi-GB. Null
         *  bila bukan sparse/FAT atau rusak. */
        fun openSparse(file: File): FatReader? {
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

        private fun openOn(src: RandomSource): FatReader? {
            val len = src.length()
            if (len < 512L) return null
            var boot = readBytes(src, 0L, 512) ?: return null
            val bytesPerSector = u16le(boot, 11)
            if (bytesPerSector < 512 || bytesPerSector > 4096) return null
            if (bytesPerSector and (bytesPerSector - 1) != 0) return null
            if (bytesPerSector > 512) {
                boot = readBytes(src, 0L, bytesPerSector) ?: return null
            }
            if (boot[bytesPerSector - 2] != 0x55.toByte() ||
                boot[bytesPerSector - 1] != 0xAA.toByte()
            ) return null
            val sectorsPerCluster = boot[13].toInt() and 0xFF
            if (sectorsPerCluster == 0 || sectorsPerCluster > 128) return null
            if (sectorsPerCluster and (sectorsPerCluster - 1) != 0) return null
            val reservedSectors = u16le(boot, 14).toLong()
            if (reservedSectors <= 0L) return null
            val numFats = boot[16].toInt() and 0xFF
            if (numFats < 1 || numFats > 2) return null
            val rootEntCnt = u16le(boot, 17).toLong()
            val totSec16 = u16le(boot, 19).toLong()
            val fatSize16 = u16le(boot, 22).toLong()
            val totSec32 = u32le(boot, 32)
            val fatSize32 = u32le(boot, 36)
            val totalSectors = if (totSec16 != 0L) totSec16 else totSec32
            if (totalSectors <= 0L) return null
            val fatSizeSectors = if (fatSize16 != 0L) fatSize16 else fatSize32
            if (fatSizeSectors <= 0L) return null
            val rootDirSectors =
                (rootEntCnt * 32L + bytesPerSector - 1) / bytesPerSector
            val firstDataSector =
                reservedSectors + numFats * fatSizeSectors + rootDirSectors
            if (firstDataSector >= totalSectors) return null
            val dataSectors = totalSectors - firstDataSector
            val totalClusters = dataSectors / sectorsPerCluster
            if (totalClusters < 2L) return null
            val type = when {
                totalClusters < 4085L -> FatType.FAT12
                totalClusters < 65525L -> FatType.FAT16
                else -> FatType.FAT32
            }
            var rootCluster = 2L
            if (type == FatType.FAT32) {
                // FAT32: tidak ada direktori root tetap (entri root 0,
                // ukuran FAT16 harus 0), root adalah rantai klaster.
                if (rootEntCnt != 0L || fatSize16 != 0L) return null
                rootCluster = u32le(boot, 44)
                if (rootCluster < 2L || rootCluster > totalClusters + 1) return null
            } else {
                // FAT12/16: direktori root tetap sesudah FAT.
                if (rootEntCnt <= 0L || fatSize16 <= 0L) return null
            }
            val clusterBytes = bytesPerSector.toLong() * sectorsPerCluster
            val fatStart = reservedSectors * bytesPerSector
            // Wilayah FAT dan awal data harus muat di dalam berkas.
            // totalSectors sengaja TIDAK dicek terhadap panjang berkas:
            // citra terpotong masih boleh dibuka, baca klaster di luar
            // berkas gagal bersih lewat readBytes (pola r153/r154).
            if (fatStart + fatSizeSectors * bytesPerSector > len) return null
            if (firstDataSector * bytesPerSector >= len) return null
            val rootDirStart =
                (reservedSectors + numFats * fatSizeSectors) * bytesPerSector
            val rootDirBytes = rootEntCnt * 32L
            if (type != FatType.FAT32 &&
                rootDirStart + rootDirBytes > len
            ) return null
            return FatReader(
                src, type, bytesPerSector, sectorsPerCluster, clusterBytes,
                reservedSectors, numFats, fatSizeSectors, fatStart,
                firstDataSector, totalClusters, rootDirStart, rootDirBytes,
                rootCluster
            )
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
    }

    private val lock = Any()

    private data class LfnSlot(
        val seq: Int,
        val checksum: Int,
        val chars: IntArray
    )

    override fun close() {
        try { src.close() } catch (_: Exception) { }
    }

    private fun readAt(offset: Long, size: Int): ByteArray? {
        synchronized(lock) { return readBytes(src, offset, size) }
    }

    private fun readCluster(n: Long): ByteArray? {
        if (n < 2L || n > totalClusters + 1) return null
        val offset = firstDataSector * bytesPerSector + (n - 2) * clusterBytes
        return readAt(offset, clusterBytes.toInt())
    }

    /** Nilai entri FAT untuk [cluster]; null bila di luar jangkauan
     *  atau tidak terbaca. */
    private fun fatValue(cluster: Long): Long? {
        if (cluster < 2L || cluster > totalClusters + 1) return null
        return when (type) {
            FatType.FAT12 -> {
                val off = fatStart + cluster + cluster / 2
                val b = readAt(off, 2) ?: return null
                val v = u16le(b, 0)
                if (cluster % 2L == 1L) (v shr 4).toLong() else (v and 0xFFF).toLong()
            }
            FatType.FAT16 -> {
                val b = readAt(fatStart + cluster * 2, 2) ?: return null
                u16le(b, 0).toLong()
            }
            FatType.FAT32 -> {
                val b = readAt(fatStart + cluster * 4, 4) ?: return null
                u32le(b, 0) and 0x0FFFFFFFL
            }
        }
    }

    private fun isEoc(v: Long): Boolean = when (type) {
        FatType.FAT12 -> v >= 0xFF8L
        FatType.FAT16 -> v >= 0xFFF8L
        FatType.FAT32 -> v >= 0x0FFFFFF8L
    }

    private fun isBad(v: Long): Boolean = when (type) {
        FatType.FAT12 -> v == 0xFF7L
        FatType.FAT16 -> v == 0xFFF7L
        FatType.FAT32 -> v == 0x0FFFFFF7L
    }

    /** Klaster berikut dalam rantai: nomor klaster, [EOC] bila rantai
     *  berakhir, [BROKEN] bila rantai rusak (bebas / rusak / di luar
     *  jangkauan / tidak terbaca). */
    private fun nextCluster(cur: Long): Long {
        val v = fatValue(cur) ?: return BROKEN
        if (isEoc(v)) return EOC
        if (isBad(v) || v < 2L || v > totalClusters + 1) return BROKEN
        return v
    }

    /** Baca isi rantai klaster mulai [start] (dipotong di [maxBytes]).
     *  Bila [allowLonger] false (direktori), rantai yang isinya
     *  melebihi cap ditolak; bila true (berkas), kelebihan rantai
     *  adalah slack klaster yang normal dan dipotong saja.
     *  Null bila rantai rusak. Rantai yang berakhir sebelum cap
     *  mengembalikan byte yang ada (normal untuk direktori yang
     *  rantainya pendek). */
    private fun readChainBytes(
        start: Long,
        maxBytes: Long,
        allowLonger: Boolean
    ): ByteArray? {
        if (start < 2L || start > totalClusters + 1) return null
        // Cap efektif: tidak pernah alokasi lebih dari MAX_DIR_BYTES.
        val cap = minOf(maxBytes, MAX_DIR_BYTES)
        val out = ByteArray(cap.toInt())
        var written = 0
        var cur = start
        // Klaster yang sudah dikunjungi dicatat — rantai berputar
        // (klaster yang sama muncul dua kali) gagal bersih, bukan
        // mengembalikan byte klaster yang terulang. BitSet tumbuh
        // malas: hanya sebesar klaster tertinggi yang disentuh.
        val visited = BitSet()
        // Cap langkah sebagai pengaman kedua.
        val maxSteps = cap / clusterBytes + 2
        var steps = 0L
        while (true) {
            if (cur < 2L || cur > totalClusters + 1) return null
            if (++steps > maxSteps) return null
            if (visited.get(cur.toInt())) return null
            visited.set(cur.toInt())
            val bytes = readCluster(cur) ?: return null
            val remaining = cap - written
            if (remaining <= 0L) {
                return if (allowLonger) out.copyOf(written) else null
            }
            val take = minOf(bytes.size.toLong(), remaining).toInt()
            bytes.copyInto(out, written, 0, take)
            written += take
            if (written >= cap) {
                if (allowLonger) return out.copyOf(written)
                // Cap tercapai: sah hanya bila rantai berakhir di sini.
                return if (nextCluster(cur) == EOC) out.copyOf(written) else null
            }
            val next = nextCluster(cur)
            if (next == EOC) return out.copyOf(written)
            if (next == BROKEN) return null
            cur = next
        }
    }

    /** Daftar isi direktori [cluster] ([ROOT_CLUSTER] = root).
     *  Null bila klaster tidak terbaca / rantai rusak. */
    private fun listDirCluster(cluster: Long): List<FatEntry>? {
        val raw: ByteArray = if (cluster == ROOT_CLUSTER) {
            if (type == FatType.FAT32) {
                readChainBytes(rootCluster, MAX_DIR_BYTES, false) ?: return null
            } else {
                readAt(rootDirStart, rootDirBytes.toInt()) ?: return null
            }
        } else {
            readChainBytes(cluster, MAX_DIR_BYTES, false) ?: return null
        }
        return parseDirEntries(raw)
    }

    /** Uraikan byte direktori mentah menjadi entri. Byte pertama 0x00
     *  mengakhiri direktori; 0xE5 = entri terhapus (dilewati, LFN yang
     *  tertunda dibuang); atribut 0x08 = label volume (dilewati);
     *  "." dan ".." dilewati. Entri yang klaster awalnya tidak valid
     *  dilewati saja — direktori lain tetap tampil. */
    private fun parseDirEntries(raw: ByteArray): List<FatEntry>? {
        val out = mutableListOf<FatEntry>()
        val pending = mutableListOf<LfnSlot>()
        var pos = 0
        while (pos + 32 <= raw.size) {
            val b0 = raw[pos].toInt() and 0xFF
            if (b0 == 0x00) break
            if (b0 == 0xE5) {
                pending.clear()
                pos += 32
                continue
            }
            val attr = raw[pos + 11].toInt() and 0xFF
            if (attr == 0x0F) {
                // Slot LFN: tipe @12 harus 0, klaster @26 harus 0.
                if (raw[pos + 12].toInt() == 0 && u16le(raw, pos + 26) == 0) {
                    pending += LfnSlot(
                        seq = b0,
                        checksum = raw[pos + 13].toInt() and 0xFF,
                        chars = lfnChars(raw, pos)
                    )
                } else {
                    pending.clear()
                }
                pos += 32
                continue
            }
            if (attr and 0x08 != 0) {
                pending.clear()
                pos += 32
                continue
            }
            val shortName = shortName(raw, pos)
            if (shortName == null || shortName == "." || shortName == "..") {
                pending.clear()
                pos += 32
                continue
            }
            val isDir = attr and 0x10 != 0
            val size = u32le(raw, pos + 28)
            val clusHi = u16le(raw, pos + 20)
            val clusLo = u16le(raw, pos + 26)
            if (type != FatType.FAT32 && clusHi != 0) {
                pending.clear()
                pos += 32
                continue
            }
            val firstCluster = (clusHi.toLong() shl 16) or clusLo.toLong()
            if (isDir) {
                if (firstCluster < 2L || firstCluster > totalClusters + 1) {
                    pending.clear()
                    pos += 32
                    continue
                }
            } else {
                if (size > 0L &&
                    (firstCluster < 2L || firstCluster > totalClusters + 1)
                ) {
                    pending.clear()
                    pos += 32
                    continue
                }
            }
            val name = resolveLfnName(pending, raw, pos) ?: shortName
            if (!isSafeEntryName(name)) {
                pending.clear()
                pos += 32
                continue
            }
            out += FatEntry(name, isDir, size, firstCluster)
            if (out.size > FileOps.MAX_ARCHIVE_ENTRIES) return null
            pending.clear()
            pos += 32
        }
        return out.sortedWith(
            compareByDescending<FatEntry> { it.isDir }
                .thenBy { it.name.lowercase() }
        )
    }

    /** 13 karakter UTF-16LE satu slot LFN (@1/@14/@28). */
    private fun lfnChars(raw: ByteArray, pos: Int): IntArray {
        val offs = intArrayOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)
        return IntArray(13) { i -> u16le(raw, pos + offs[i]) }
    }

    /** Checksum nama pendek (11 byte): putar-kanan 1 bit tiap langkah
     *  lalu tambah byte berikutnya (LFN spec). */
    private fun lfnChecksum(raw: ByteArray, pos: Int): Int {
        var sum = 0
        for (i in 0 until 11) {
            sum = (((sum and 1) shl 7) or ((sum and 0xFE) shr 1)) +
                (raw[pos + i].toInt() and 0xFF)
            sum = sum and 0xFF
        }
        return sum
    }

    /** Nama panjang dari slot LFN yang tertunda, atau null bila tidak
     *  valid (pemanggil jatuh kembali ke nama 8.3). Slot tersimpan
     *  dalam urutan direktori: slot teratas (seq tertinggi + flag
     *  0x40) dulu, slot seq 1 paling dekat dengan alias. */
    private fun resolveLfnName(
        pending: List<LfnSlot>,
        raw: ByteArray,
        pos: Int
    ): String? {
        if (pending.isEmpty()) return null
        val n = pending.size
        if (n > 20) return null // 20 slot x 13 = 260 > batas LFN 255
        val expected = lfnChecksum(raw, pos)
        for ((i, slot) in pending.withIndex()) {
            if (slot.seq and 0x80 != 0) return null
            val seq = slot.seq and 0x1F
            if (seq != n - i) return null
            if (i == 0) {
                if (slot.seq and 0x40 == 0) return null
            } else if (slot.seq and 0x40 != 0) return null
            if (slot.checksum != expected) return null
        }
        val chars = StringBuilder()
        var terminated = false
        for (slot in pending.reversed()) {
            for (c in slot.chars) {
                if (terminated) {
                    if (c != 0xFFFF) return null
                } else if (c == 0x0000) {
                    terminated = true
                } else {
                    if (c < 0x20 || c == 0xFFFF) return null
                    chars.append(c.toChar())
                }
            }
        }
        val name = chars.toString()
        if (name.isEmpty()) return null
        return name
    }

    /** Nama 8.3: 8 byte nama + 3 byte ekstensi, spasi di ujung dibuang,
     *  titik disisipkan bila ekstensi ada. Null bila byte nama tidak
     *  valid. Byte pertama 0x05 berarti karakter aslinya 0xE5. */
    private fun shortName(raw: ByteArray, pos: Int): String? {
        for (i in 0 until 11) {
            val b = raw[pos + i].toInt() and 0xFF
            if (b < 0x20 || b == 0x7F) return null
        }
        val nameBytes = raw.copyOfRange(pos, pos + 8)
        if (nameBytes[0] == 0x05.toByte()) nameBytes[0] = 0xE5.toByte()
        val name = String(nameBytes, Charsets.ISO_8859_1).trimEnd(' ')
        val ext = String(raw, pos + 8, 3, Charsets.ISO_8859_1).trimEnd(' ')
        if (name.isEmpty()) return null
        return if (ext.isEmpty()) name else "$name.$ext"
    }

    /** Uraikan path di dalam citra menjadi entrinya. Null bila path
     *  tidak ada / bukan direktori di tengah jalan. Pencocokan nama
     *  persis dulu, lalu abaikan besar-kecil huruf (FAT tidak peka
     *  huruf besar-kecil). */
    fun resolveEntry(path: String): FatEntry? {
        val segments = Ext4Reader.normalizePath(path)
            .split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        var cluster = ROOT_CLUSTER
        for ((i, seg) in segments.withIndex()) {
            val entries = listDirCluster(cluster) ?: return null
            val e = entries.firstOrNull { it.name == seg }
                ?: entries.firstOrNull { it.name.equals(seg, ignoreCase = true) }
                ?: return null
            if (i == segments.lastIndex) return e
            if (!e.isDir) return null
            cluster = e.firstCluster
        }
        return null
    }

    /** Klaster direktori untuk path ("" = root -> [ROOT_CLUSTER]).
     *  Null bila path tidak ada atau ujungnya bukan direktori. */
    fun resolvePath(path: String): Long? {
        if (Ext4Reader.normalizePath(path).isEmpty()) return ROOT_CLUSTER
        val e = resolveEntry(path) ?: return null
        return if (e.isDir) e.firstCluster else null
    }

    /** Daftar isi direktori [path] ("" = root). "." dan ".." tidak
     *  disertakan. Null bila direktori rusak / tidak ada. */
    fun listDir(path: String): List<FatEntry>? {
        val cluster = resolvePath(path) ?: return null
        return listDirCluster(cluster)
    }

    /** Baca isi berkas reguler (batas [maxBytes]); null bila ukuran
     *  melebihi cap atau rantai rusak / terpotong. */
    fun readFileBytes(entry: FatEntry, maxBytes: Long): ByteArray? {
        if (entry.isDir) return null
        if (entry.size < 0L || entry.size > maxBytes) return null
        if (entry.size == 0L) return ByteArray(0)
        if (entry.firstCluster < 2L) return null
        val bytes = readChainBytes(entry.firstCluster, entry.size, true) ?: return null
        if (bytes.size.toLong() != entry.size) return null
        return bytes
    }

    /** Baca berkas reguler sebagai teks (batas 1 MB, sama seperti
     *  pembaca arsip/ext4). Null bila bukan berkas reguler / biner /
     *  rusak. */
    fun readFileText(path: String, maxBytes: Int = 1_000_000): String? {
        val entry = resolveEntry(path) ?: return null
        if (entry.isDir) return null
        val bytes = readFileBytes(entry, maxBytes.toLong()) ?: return null
        if (!ImgOps.looksLikeText(bytes)) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\u0000')

    /** Ekstrak satu berkas reguler ke [destDir] (nama komponen tunggal,
     *  dijaga zip-slip). Null bila gagal / bukan berkas reguler /
     *  melebihi batas 2 GiB. Berkas setengah ditulis dihapus. */
    fun extractEntry(path: String, destDir: File): File? {
        val entry = resolveEntry(path) ?: return null
        if (entry.isDir) return null
        if (!isSafeEntryName(entry.name)) return null
        if (entry.size < 0L || entry.size > FileOps.MAX_EXTRACT_BYTES) return null
        val out = File(destDir, entry.name)
        val destCanonical = try { destDir.canonicalPath } catch (_: Exception) { return null }
        val outCanonical = try { out.canonicalPath } catch (_: Exception) { return null }
        if (!FileOps.isPathInside(destCanonical, outCanonical)) return null
        destDir.mkdirs()
        return if (writeFileData(entry, out)) out else null
    }

    /** Tulis isi berkas ke [out] secara streaming per klaster (tidak
     *  pernah memuat berkas utuh ke memori). False bila gagal; berkas
     *  setengah ditulis dihapus. */
    private fun writeFileData(entry: FatEntry, out: File): Boolean {
        var written = 0L
        var ok = false
        try {
            FileOutputStream(out).use { fos ->
                if (entry.size == 0L) {
                    ok = true
                    return@use
                }
                var cur = entry.firstCluster
                val visited = BitSet()
                val maxSteps = entry.size / clusterBytes + 2
                var steps = 0L
                while (written < entry.size) {
                    if (cur < 2L || cur > totalClusters + 1) return@use
                    if (++steps > maxSteps) return@use
                    if (visited.get(cur.toInt())) return@use
                    visited.set(cur.toInt())
                    val bytes = readCluster(cur) ?: return@use
                    val take = minOf(bytes.size.toLong(), entry.size - written).toInt()
                    fos.write(bytes, 0, take)
                    written += take
                    if (written > FileOps.MAX_EXTRACT_BYTES) return@use
                    if (written >= entry.size) break
                    val next = nextCluster(cur)
                    if (next == EOC || next == BROKEN) return@use
                    cur = next
                }
                ok = written == entry.size
            }
        } catch (_: Exception) {
            ok = false
        }
        if (!ok) out.delete()
        return ok
    }

    /** Ekstrak seluruh isi citra ke [destDir] (batas arsip yang sama:
     *  10.000 entri / 2 GiB). False bila gagal; berkas yang sudah
     *  tertulis dihapus. LD3 r160: [onProgress] menerima (entri
     *  selesai, total entri) — total dihitung dulu lewat [countEntries]
     *  (jalan metadata saja); [cancelFlag] yang terpasang membatalkan
     *  lewat ImgCancelledException. */
    fun extractAll(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val total = countEntries(ROOT_CLUSTER, 0, mutableSetOf())
        var done = 0L
        val onEntry: () -> Unit = { done++; onProgress(done, total) }
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        val visitedDirs = mutableSetOf<Long>()
        val destCanonical = try {
            destDir.mkdirs()
            destDir.canonicalPath
        } catch (_: Exception) { return false }
        return try {
            val ok = walkExtract(
                ROOT_CLUSTER, "", 0, destDir, destCanonical,
                budget, written, visitedDirs, onEntry, cancelFlag
            )
            if (!ok) {
                for (f in written) f.delete()
            }
            ok
        } catch (e: ImgCancelledException) {
            for (f in written) f.delete()
            throw e
        } catch (_: Exception) {
            for (f in written) f.delete()
            false
        }
    }

    /** LD3 r160: hitung entri untuk total progres [extractAll] —
     *  jalan metadata saja (tanpa baca data berkas); traversal-nya
     *  cerminan [walkExtract] (penjaga kunjungan + batas kedalaman
     *  sama) supaya total sama dengan jumlah onEntry(). */
    private fun countEntries(dirCluster: Long, depth: Int, visited: MutableSet<Long>): Long {
        if (depth > 64) return 0L
        if (!visited.add(dirCluster)) return 0L
        val entries = listDirCluster(dirCluster) ?: return 0L
        var n = 0L
        for (e in entries) {
            n++
            if (e.isDir) n += countEntries(e.firstCluster, depth + 1, visited)
        }
        return n
    }

    private fun walkExtract(
        dirCluster: Long,
        relPath: String,
        depth: Int,
        destDir: File,
        destCanonical: String,
        budget: ExtractBudget,
        written: MutableList<File>,
        visitedDirs: MutableSet<Long>,
        onEntry: () -> Unit,
        cancelFlag: AtomicBoolean?
    ): Boolean {
        if (depth > 64) return false
        if (!visitedDirs.add(dirCluster)) return false
        val entries = listDirCluster(dirCluster) ?: return false
        for (e in entries) {
            if (cancelFlag?.get() == true) throw ImgCancelledException()
            if (!budget.allowEntry()) return false
            if (!isSafeEntryName(e.name)) return false
            val childRel = if (relPath.isEmpty()) e.name else "$relPath/${e.name}"
            val out = File(destDir, childRel)
            val outCanonical = try { out.canonicalPath } catch (_: Exception) { return false }
            if (!FileOps.isPathInside(destCanonical, outCanonical)) return false
            if (e.isDir) {
                out.mkdirs()
                if (!walkExtract(
                        e.firstCluster, childRel, depth + 1, destDir,
                        destCanonical, budget, written, visitedDirs,
                        onEntry, cancelFlag
                    )
                ) return false
                onEntry()
            } else {
                if (e.size < 0L || e.size > FileOps.MAX_EXTRACT_BYTES) return false
                if (!budget.addBytes(e.size)) return false
                out.parentFile?.mkdirs()
                if (!writeFileData(e, out)) {
                    out.delete()
                    return false
                }
                written += out
                onEntry()
            }
        }
        return true
    }
}
