package id.sysfile.manager

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/** Satu entri direktori ext4. [symlinkTarget] hanya terisi bila [isSymlink]. */
data class Ext4Entry(
    val name: String,
    val inode: Long,
    val isDir: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val symlinkTarget: String? = null
)

/** Sesi baca ext4: citra yang dibaca + temp hasil unsparse + reader.
 *  Sejak LD2 (r157) temp selalu null — sparse dibaca langsung tanpa
 *  unsparse, mentah dibuka langsung. [close] menutup reader dan
 *  menghapus temp bila ada. */
class Ext4Session(
    val rawFile: File,
    val tempFile: File?,
    val reader: Ext4Reader
) : Closeable {
    override fun close() {
        try { reader.close() } catch (_: Exception) { }
        tempFile?.delete()
    }
}

/** Pembaca ext4 read-only untuk citra .img mentah (system.img / super.img).
 *
 *  Dibaca lewat [RandomSource] (berkas mentah, atau citra sparse
 *  langsung sejak LD2 r157) — citra multi-GB TIDAK PERNAH dimuat
 *  utuh ke memori; yang dibaca hanya superblock, deskriptor grup, tabel
 *  inode, blok extent, dan blok direktori yang sedang dituju.
 *
 *  Tata letak (Documentation/filesystems/ext4, kernel + ext4.h):
 *  superblock 1024 byte di offset 1024, magic 0xEF53 @0x38; GDT mulai
 *  di blok s_first_data_block + 1; inode ke-grup
 *  (ino-1)/s_inodes_per_group; extent header magic 0xF30A; entri
 *  direktori ext4_dir_entry_2 (inode, rec_len, name_len, file_type).
 *
 *  Batasan yang disengaja:
 *  - Inode tanpa flag EXTENTS (block map lama / inline data / terenkripsi)
 *    ditolak bersih (null) — bukan crash, bukan hasil setengah.
 *  - Metadata checksum diabaikan: read-only, tidak pernah diverifikasi.
 *  - Symlink TIDAK PERNAH diikuti ke luar citra: [resolveEntry] hanya
 *    menormalisasi path secara leksikal di dalam citra (".." mentok di
 *    root), rantai symlink dibatasi 8 lompatan.
 *  - Semua akses RandomAccessFile dikunci satu lock: layar dan MainActivity
 *    berbagi satu reader yang sama.
 */
class Ext4Reader private constructor(
    private val src: RandomSource,
    val blockSize: Long,
    private val inodesPerGroup: Long,
    private val blocksPerGroup: Long,
    private val inodeSize: Int,
    private val descSize: Int,
    private val groupCount: Long,
    private val inodeTables: LongArray,
    private val has64bit: Boolean,
    private val totalBlocks: Long,
    private val inodesCount: Long
) : Closeable {

    companion object {
        const val ROOT_INODE = 2L
        const val MAX_DIR_BYTES = 16L * 1024L * 1024L
        const val MAX_SYMLINK_BYTES = 4096L
        private const val MAX_EXTENTS = 1_000_000
        private const val MAX_SYMLINK_HOPS = 8
        private const val EXTENTS_FLAG = 0x80000L
        private const val ENCRYPT_FLAG = 0x800L
        private const val INLINE_DATA_FLAG = 0x10000000L
        private const val MODE_MASK = 0xF000
        private const val MODE_DIR = 0x4000
        private const val MODE_REG = 0x8000
        private const val MODE_SYMLINK = 0xA000

        /** Buka citra ext4 mentah. Null bila bukan ext4 / rusak / terpotong. */
        fun open(rawFile: File): Ext4Reader? {
            if (!rawFile.isFile) return null
            val src = try { FileSource(RandomAccessFile(rawFile, "r")) } catch (_: Exception) { return null }
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

        /** Buka citra sparse langsung tanpa unsparse (LD2 r157):
         *  superblock, GDT, dan blok data dibaca dari chunk sparse
         *  lewat SparseSource — tidak ada temp multi-GB. Null bila
         *  bukan sparse/ext4 atau rusak. */
        fun openSparse(file: File): Ext4Reader? {
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

        /** Buka .img apa pun: EXT4 langsung, SPARSE dibaca langsung
         *  tanpa unsparse (LD2 r157 — dulu di-unsparse ke temp, r153).
         *  Null bila tipenya bukan EXT4/SPARSE atau citra tidak bisa
         *  dibaca. */
        fun openSession(file: File): Ext4Session? {
            return when (ImgOps.detectImgType(file)) {
                ImgType.EXT4 -> {
                    val reader = open(file) ?: return null
                    Ext4Session(file, null, reader)
                }
                ImgType.SPARSE -> {
                    // LD2 r157: baca langsung dari sparse, tanpa temp.
                    val reader = openSparse(file) ?: return null
                    Ext4Session(file, null, reader)
                }
                else -> null
            }
        }

        /** Normalisasi leksikal path di dalam citra: "." dibuang, ".."
         *  naik satu level tapi tidak pernah keluar dari root. Hasil
         *  tanpa garis miring di depan/belakang. */
        fun normalizePath(path: String): String {
            val out = mutableListOf<String>()
            for (seg in path.split('/')) {
                when (seg) {
                    "", "." -> { }
                    ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                    else -> out += seg
                }
            }
            return out.joinToString("/")
        }

        private fun openOn(src: RandomSource): Ext4Reader? {
            val len = src.length()
            if (len < 2048L) return null
            val sb = readBytes(src, 1024L, 1024) ?: return null
            if (u16le(sb, 0x38) != 0xEF53) return null
            val logBlockSize = u32le(sb, 0x18)
            if (logBlockSize > 6L) return null
            val blockSize = 1024L shl logBlockSize.toInt()
            val inodesCount = u32le(sb, 0x0)
            if (inodesCount <= 0L) return null
            val blocksLo = u32le(sb, 0x4)
            val blocksHi = u32le(sb, 0x150)
            val totalBlocks = blocksLo or (blocksHi shl 32)
            if (totalBlocks <= 0L) return null
            if (totalBlocks > Long.MAX_VALUE / blockSize) return null
            val blocksPerGroup = u32le(sb, 0x20)
            val inodesPerGroup = u32le(sb, 0x28)
            if (blocksPerGroup <= 0L || inodesPerGroup <= 0L) return null
            val firstDataBlock = u32le(sb, 0x14)
            if (firstDataBlock > 1L) return null
            val rawInodeSize = u16le(sb, 0x58)
            val inodeSize = if (rawInodeSize == 0) 128 else rawInodeSize
            if (inodeSize < 128 || inodeSize > blockSize) return null
            if (inodeSize and (inodeSize - 1) != 0) return null
            val featureIncompat = u32le(sb, 0x60)
            val has64bit = featureIncompat and 0x80L != 0L
            val descSize = if (has64bit) {
                val d = u16le(sb, 0xFE)
                if (d < 32) 64 else d
            } else 32
            val groupCount = (totalBlocks + blocksPerGroup - 1) / blocksPerGroup
            if (groupCount <= 0L) return null
            // Citra nyata terbesar sekalipun punya < 200 ribu grup;
            // 1 juta adalah batas longgar anti-OOM untuk superblock jahat.
            if (groupCount > 1_000_000L) return null
            // Citra valid: jumlah grup dari inode harus sama dengan dari blok.
            if ((inodesCount + inodesPerGroup - 1) / inodesPerGroup != groupCount) return null
            val gdtOffset = (firstDataBlock + 1) * blockSize
            if (groupCount > (len - gdtOffset) / descSize) return null
            val inodeTables = LongArray(groupCount.toInt())
            for (g in 0 until groupCount) {
                val desc = readBytes(src, gdtOffset + g * descSize, descSize) ?: return null
                var table = u32le(desc, 0x8)
                if (has64bit) table = table or (u32le(desc, 0x28) shl 32)
                if (table <= 0L || table >= totalBlocks) return null
                // Tabel inode harus muat di dalam file.
                val tableBytes = inodesPerGroup * inodeSize.toLong()
                if (table > (len - tableBytes) / blockSize) return null
                inodeTables[g.toInt()] = table
            }
            return Ext4Reader(
                src, blockSize, inodesPerGroup, blocksPerGroup, inodeSize,
                descSize, groupCount, inodeTables, has64bit, totalBlocks, inodesCount
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

    private data class InodeData(
        val mode: Int,
        val size: Long,
        val flags: Long,
        val blocksLo: Long,
        val fileAclLo: Long,
        val iBlock: ByteArray
    )

    private data class Extent(
        val fileBlock: Long,
        val physBlock: Long,
        val len: Long,
        val uninit: Boolean
    )

    override fun close() {
        try { src.close() } catch (_: Exception) { }
    }

    private fun readAt(offset: Long, size: Int): ByteArray? {
        synchronized(lock) { return readBytes(src, offset, size) }
    }

    private fun readBlock(n: Long): ByteArray? {
        if (n < 0L || n >= totalBlocks) return null
        if (blockSize > Int.MAX_VALUE) return null
        return readAt(n * blockSize, blockSize.toInt())
    }

    /** Baca inode; null bila nomor di luar jangkauan, inode kosong
     *  (mode 0), atau tabel inode tidak terbaca. */
    private fun readInode(ino: Long): InodeData? {
        if (ino < 1L || ino > inodesCount) return null
        val group = ((ino - 1) / inodesPerGroup).toInt()
        val index = (ino - 1) % inodesPerGroup
        if (group < 0 || group >= inodeTables.size) return null
        val offset = inodeTables[group] * blockSize + index * inodeSize.toLong()
        val raw = readAt(offset, inodeSize) ?: return null
        val mode = u16le(raw, 0x0)
        if (mode == 0) return null
        val sizeLo = u32le(raw, 0x4)
        val flags = u32le(raw, 0x20)
        val size = if ((mode and MODE_MASK) == MODE_REG ||
            (mode and MODE_MASK) == MODE_SYMLINK
        ) {
            if (has64bit) sizeLo or (u32le(raw, 0x6C) shl 32) else sizeLo
        } else sizeLo
        return InodeData(
            mode = mode,
            size = size,
            flags = flags,
            blocksLo = u32le(raw, 0x1C),
            fileAclLo = u32le(raw, 0x68),
            iBlock = raw.copyOfRange(0x28, 0x28 + 60)
        )
    }

    /** Uraikan pohon extent inode menjadi daftar extent terurut.
     *  Null bila inode bukan extent / rusak / berlebih. */
    private fun resolveExtents(inode: InodeData): List<Extent>? {
        if (inode.flags and EXTENTS_FLAG == 0L) return null
        if (inode.flags and INLINE_DATA_FLAG != 0L) return null
        val out = mutableListOf<Extent>()
        if (!walkExtents(inode.iBlock, null, out)) return null
        out.sortBy { it.fileBlock }
        var prevEnd = -1L
        var covered = 0L
        for (e in out) {
            if (e.fileBlock < prevEnd) return null
            if (e.len > totalBlocks - covered) return null
            covered += e.len
            prevEnd = maxOf(prevEnd, e.fileBlock + e.len)
        }
        return out
    }

    /** Jelajahi satu node pohon extent. [node] adalah isi i_block[]
     *  (root) atau satu blok daun/cabang. [depth] null berarti root
     *  (kedalaman diambil dari headernya sendiri). */
    private fun walkExtents(
        node: ByteArray,
        depth: Int?,
        out: MutableList<Extent>
    ): Boolean {
        if (node.size < 12) return false
        if (out.size > MAX_EXTENTS) return false
        if (u16le(node, 0x0) != 0xF30A) return false
        val entries = u16le(node, 0x2)
        val max = u16le(node, 0x4)
        if (entries > max) return false
        if (12 + entries * 12 > node.size) return false
        val nodeDepth = u16le(node, 0x6)
        if (depth != null && nodeDepth != depth) return false
        if (nodeDepth > 5) return false
        if (nodeDepth == 0) {
            for (i in 0 until entries) {
                val o = 12 + i * 12
                val fileBlock = u32le(node, o)
                val rawLen = u16le(node, o + 4)
                val uninit = rawLen > 32768
                val len = if (uninit) (rawLen - 32768).toLong() else rawLen.toLong()
                if (len <= 0L) return false
                val phys = u32le(node, o + 8) or (u16le(node, o + 6).toLong() shl 32)
                if (phys >= totalBlocks) return false
                if (len > totalBlocks - phys) return false
                out += Extent(fileBlock, phys, len, uninit)
            }
            return true
        }
        for (i in 0 until entries) {
            val o = 12 + i * 12
            val leaf = u32le(node, o + 4) or (u16le(node, o + 8).toLong() shl 32)
            if (leaf >= totalBlocks) return false
            val leafBytes = readBlock(leaf) ?: return false
            if (!walkExtents(leafBytes, nodeDepth - 1, out)) return false
        }
        return true
    }

    /** Baca isi data inode (berkas reguler / direktori / symlink lambat).
     *  Blok yang tidak terpetakan (lubang / extent uninitialized) dibaca
     *  sebagai nol. Null bila ukuran melebihi [maxBytes] atau data
     *  tidak bisa dibaca penuh. */
    fun readFile(inode: Long, maxBytes: Long): ByteArray? {
        val data = readInode(inode) ?: return null
        return readData(data, maxBytes)
    }

    private fun readData(data: InodeData, maxBytes: Long): ByteArray? {
        if (data.flags and ENCRYPT_FLAG != 0L) return null
        if (data.size < 0L || data.size > maxBytes) return null
        val extents = resolveExtents(data) ?: return null
        val out = ByteArray(data.size.toInt())
        for (e in extents) {
            val logicalStart = e.fileBlock * blockSize
            if (logicalStart >= data.size) break
            var j = 0L
            while (j < e.len) {
                val pos = logicalStart + j * blockSize
                if (pos >= data.size) break
                val src = if (e.uninit) null else readBlock(e.physBlock + j)
                if (src == null && !e.uninit) return null
                val take = minOf(blockSize, data.size - pos).toInt()
                if (src != null) src.copyInto(out, pos.toInt(), 0, take)
                j++
            }
        }
        return out
    }

    /** Daftar isi direktori [inode]. "." dan ".." tidak disertakan.
     *  Null bila bukan direktori / rusak (rec_len tidak valid membuat
     *  seluruh listing gagal bersih, bukan menampilkan setengah).
     *  Entri yang inode anaknya tidak terbaca dilewati saja. */
    fun listDir(inode: Long): List<Ext4Entry>? {
        val data = readInode(inode) ?: return null
        if (data.mode and MODE_MASK != MODE_DIR) return null
        if (data.flags and ENCRYPT_FLAG != 0L) return null
        if (data.size < 0L || data.size > MAX_DIR_BYTES) return null
        val raw = readFile(inode, MAX_DIR_BYTES) ?: return null
        val out = mutableListOf<Ext4Entry>()
        val blockSizeInt = blockSize.toInt()
        var blockStart = 0
        while (blockStart < raw.size) {
            val blockEnd = minOf(blockStart + blockSizeInt, raw.size)
            var pos = blockStart
            while (pos + 8 <= blockEnd) {
                val eIno = u32le(raw, pos)
                val recLen = u16le(raw, pos + 4)
                val nameLen = raw[pos + 6].toInt() and 0xFF
                val ftype = raw[pos + 7].toInt() and 0xFF
                if (recLen < 8 || recLen % 4 != 0 || pos + recLen > blockEnd) return null
                if (eIno != 0L && nameLen > 0) {
                    if (pos + 8 + nameLen > pos + recLen) return null
                    val name = String(raw, pos + 8, nameLen, Charsets.UTF_8)
                    if (name != "." && name != ".." && isSafeEntryName(name)) {
                        val child = readInode(eIno)
                        if (child != null) {
                            val type = child.mode and MODE_MASK
                            val isDir = type == MODE_DIR
                            val isSymlink = type == MODE_SYMLINK
                            val target = if (isSymlink) readSymlinkTarget(child) else null
                            out += Ext4Entry(
                                name = name,
                                inode = eIno,
                                isDir = isDir,
                                isSymlink = isSymlink,
                                size = child.size,
                                symlinkTarget = target
                            )
                            if (out.size > FileOps.MAX_ARCHIVE_ENTRIES) return null
                        }
                    }
                }
                pos += recLen
            }
            blockStart = blockEnd
        }
        return out.sortedWith(
            compareByDescending<Ext4Entry> { it.isDir }
                .thenBy { it.name.lowercase() }
        )
    }

    /** Target symlink: cepat (di i_block[], < 60 byte, tanpa blok data)
     *  atau lambat (blok data). Null bila tidak bisa dibaca. */
    private fun readSymlinkTarget(child: InodeData): String? {
        if (child.size < 0L || child.size > MAX_SYMLINK_BYTES) return null
        if (child.blocksLo - child.fileAclLo == 0L && child.size < 60L) {
            val bytes = child.iBlock.copyOf(child.size.toInt())
            val nul = bytes.indexOf(0)
            val clean = if (nul >= 0) bytes.copyOf(nul) else bytes
            return String(clean, Charsets.UTF_8)
        }
        val bytes = readData(child, MAX_SYMLINK_BYTES) ?: return null
        val nul = bytes.indexOf(0)
        val clean = if (nul >= 0) bytes.copyOf(nul) else bytes
        return String(clean, Charsets.UTF_8)
    }

    /** Uraikan path di dalam citra menjadi entri finalnya, mengikuti
     *  symlink di tengah maupun di ujung path (maks 8 lompatan, tidak
     *  pernah keluar dari citra). Null bila path tidak ada / bukan
     *  direktori di tengah jalan / rantai terlalu dalam. */
    fun resolveEntry(path: String): Ext4Entry? {
        var remaining = normalizePath(path)
        var hops = 0
        while (true) {
            val segments = remaining.split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return null
            var current = ROOT_INODE
            var currentPath = ""
            var result: Ext4Entry? = null
            var redirected = false
            for ((i, seg) in segments.withIndex()) {
                val entries = listDir(current) ?: return null
                val e = entries.firstOrNull { it.name == seg } ?: return null
                if (i == segments.lastIndex) {
                    result = e
                    break
                }
                if (e.isSymlink) {
                    val t = e.symlinkTarget ?: return null
                    val base = if (t.startsWith("/")) "" else currentPath
                    val tail = segments.subList(i + 1, segments.size).joinToString("/")
                    if (++hops > MAX_SYMLINK_HOPS) return null
                    remaining = normalizePath("$base/$t/$tail")
                    redirected = true
                    break
                }
                if (!e.isDir) return null
                current = e.inode
                currentPath = if (currentPath.isEmpty()) seg else "$currentPath/$seg"
            }
            if (redirected) continue
            val r = result ?: return null
            if (r.isSymlink) {
                val t = r.symlinkTarget ?: return null
                val base = if (t.startsWith("/")) "" else currentPath
                if (++hops > MAX_SYMLINK_HOPS) return null
                remaining = normalizePath("$base/$t")
                continue
            }
            return r
        }
    }

    /** Inode direktori untuk path (""/"/" = root). Null bila path
     *  tidak ada atau ujungnya bukan direktori. */
    fun resolvePath(path: String): Long? {
        if (normalizePath(path).isEmpty()) return ROOT_INODE
        val e = resolveEntry(path) ?: return null
        return if (e.isDir) e.inode else null
    }

    /** Baca berkas reguler sebagai teks (batas 1 MB, sama seperti
     *  pembaca arsip). Null bila bukan berkas reguler / biner / rusak. */
    fun readFileText(inode: Long, maxBytes: Int = 1_000_000): String? {
        val data = readInode(inode) ?: return null
        if (data.mode and MODE_MASK != MODE_REG) return null
        val bytes = readData(data, maxBytes.toLong()) ?: return null
        if (!ImgOps.looksLikeText(bytes)) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\u0000')

    /** Ekstrak satu berkas reguler ke [destDir] (nama komponen tunggal,
     *  dijaga zip-slip). Null bila gagal / bukan berkas reguler /
     *  melebihi batas 2 GiB. Berkas setengah ditulis dihapus. */
    fun extractEntry(inode: Long, name: String, destDir: File): File? {
        if (!isSafeEntryName(name)) return null
        val data = readInode(inode) ?: return null
        if (data.mode and MODE_MASK != MODE_REG) return null
        if (data.flags and ENCRYPT_FLAG != 0L) return null
        if (data.size < 0L || data.size > FileOps.MAX_EXTRACT_BYTES) return null
        // M4: tulis ke temp unik di dir yang sama lalu rename bila
        // sukses — berkas yang sudah ada (nama sama) tidak pernah
        // terpotong, dan tidak ada berkas sebagian yang tertinggal.
        // Tabrakan nama memakai nama unik (seperti extractSingle).
        val out = File(destDir, FileOps.uniqueName(destDir, name))
        val destCanonical = try { destDir.canonicalPath } catch (_: Exception) { return null }
        val outCanonical = try { out.canonicalPath } catch (_: Exception) { return null }
        if (!FileOps.isPathInside(destCanonical, outCanonical)) return null
        destDir.mkdirs()
        val tmp = FileOps.tempFileIn(destDir) ?: return null
        return try {
            if (!writeFileData(data, tmp)) null
            else if (tmp.renameTo(out)) out
            else null
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Tulis isi data inode ke [out] secara streaming per blok
     *  (tidak pernah memuat berkas utuh ke memori). False bila gagal;
     *  berkas setengah ditulis dihapus. M10: celah di peta extent
     *  (berkas sparse — blok tanpa extent) ditulis sebagai nol,
     *  seperti [readData]; dulu celah membuat `written != data.size`
     *  dan ekstrak gagal padahal isi sudah tertulis sebagian.
     *  Kegagalan di tengah loop tidak lagi `return` dari dalam `use`
     *  (dulu non-local return melewati `catch` sehingga berkas
     *  sebagian tidak terhapus — M4). */
    private fun writeFileData(data: InodeData, out: File): Boolean {
        val extents = resolveExtents(data) ?: return false
        var written = 0L
        var pos = 0L
        var ok = true
        try {
            FileOutputStream(out).use { fos ->
                for (e in extents) {
                    if (!ok) break
                    val logicalStart = e.fileBlock * blockSize
                    if (logicalStart >= data.size) break
                    // M10: celah sebelum extent ini — tulis nol (chunk
                    // 64 KiB supaya celah multi-MB tidak mengalokasi
                    // ByteArray sebesar celahnya).
                    if (logicalStart > pos) {
                        val gap = minOf(logicalStart, data.size) - pos
                        if (!writeZeros(fos, gap)) { ok = false; break }
                        written += gap
                        pos += gap
                    }
                    var j = 0L
                    while (j < e.len) {
                        val p = logicalStart + j * blockSize
                        if (p >= data.size) break
                        val take = minOf(blockSize, data.size - p)
                        if (e.uninit) {
                            val zeros = ByteArray(take.toInt())
                            fos.write(zeros)
                        } else {
                            val src = readBlock(e.physBlock + j)
                            if (src == null) { ok = false; break }
                            fos.write(src, 0, take.toInt())
                        }
                        written += take
                        pos = p + take
                        if (written > FileOps.MAX_EXTRACT_BYTES) { ok = false; break }
                        j++
                    }
                }
                // M10: celah di ujung (peta extent berakhir sebelum
                // ukuran berkas) — tulis nol sampai data.size.
                if (ok && pos < data.size) {
                    val gap = data.size - pos
                    if (!writeZeros(fos, gap)) ok = false
                    else { written += gap; pos = data.size }
                }
            }
        } catch (_: Exception) {
            ok = false
        }
        if (!ok || written != data.size) {
            out.delete()
            return false
        }
        return true
    }

    /** Tulis [count] byte nol ke [fos] dalam chunk 64 KiB. False bila gagal. */
    private fun writeZeros(fos: java.io.OutputStream, count: Long): Boolean {
        return try {
            val zeros = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = minOf(zeros.size.toLong(), left).toInt()
                fos.write(zeros, 0, n)
                left -= n
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Ekstrak seluruh isi citra ke [destDir] (batas arsip yang sama:
     *  10.000 entri / 2 GiB). Symlink tidak ditulis. False bila gagal;
     *  berkas yang sudah tertulis dihapus. LD3 r160: [onProgress]
     *  menerima (entri selesai, total entri) — total dihitung dulu
     *  lewat [countEntries] (jalan metadata saja); [cancelFlag] yang
     *  terpasang membatalkan lewat ImgCancelledException. */
    fun extractAll(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val total = countEntries(ROOT_INODE, 0, mutableSetOf())
        var done = 0L
        val onEntry: () -> Unit = { done++; onProgress(done, total) }
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { file -> file.delete() }
            FileOps.deleteCreatedDirs(createdDirs)
        }
        val visitedDirs = mutableSetOf<Long>()
        val destCanonical = try {
            destDir.mkdirs()
            destDir.canonicalPath
        } catch (_: Exception) { return false }
        return try {
            val ok = walkExtract(ROOT_INODE, "", 0, destDir, destCanonical, budget, written, createdDirs, visitedDirs, onEntry, cancelFlag)
            if (!ok) {
                cleanup()
            }
            ok
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (_: Exception) {
            cleanup()
            false
        }
    }

    /** LD3 r160: hitung entri untuk total progres [extractAll] —
     *  jalan metadata saja (tanpa baca data berkas); traversal-nya
     *  cerminan [walkExtract] (penjaga kunjungan + batas kedalaman
     *  sama) supaya total sama dengan jumlah onEntry(). */
    private fun countEntries(dirInode: Long, depth: Int, visited: MutableSet<Long>): Long {
        if (depth > 64) return 0L
        if (!visited.add(dirInode)) return 0L
        val entries = listDir(dirInode) ?: return 0L
        var n = 0L
        for (e in entries) {
            n++
            if (e.isDir) n += countEntries(e.inode, depth + 1, visited)
        }
        return n
    }

    private fun walkExtract(
        dirInode: Long,
        relPath: String,
        depth: Int,
        destDir: File,
        destCanonical: String,
        budget: ExtractBudget,
        written: MutableList<File>,
        createdDirs: MutableList<File>,
        visitedDirs: MutableSet<Long>,
        onEntry: () -> Unit,
        cancelFlag: AtomicBoolean?
    ): Boolean {
        if (depth > 64) return false
        if (!visitedDirs.add(dirInode)) return false
        val entries = listDir(dirInode) ?: return false
        for (e in entries) {
            if (cancelFlag?.get() == true) throw ImgCancelledException()
            if (!budget.allowEntry()) return false
            if (!isSafeEntryName(e.name)) return false
            val childRel = if (relPath.isEmpty()) e.name else "$relPath/${e.name}"
            val out = File(destDir, childRel)
            val outCanonical = try { out.canonicalPath } catch (_: Exception) { return false }
            if (!FileOps.isPathInside(destCanonical, outCanonical)) return false
            if (e.isDir) {
                FileOps.noteCreatedDir(out, destCanonical, createdDirs)
                out.mkdirs()
                // L1: validasi ulang canonical SETELAH mkdirs (TOCTOU).
                val dirCanon = try { out.canonicalPath } catch (_: Exception) { return false }
                if (!FileOps.isPathInside(destCanonical, dirCanon)) return false
                if (!walkExtract(e.inode, childRel, depth + 1, destDir, destCanonical, budget, written, createdDirs, visitedDirs, onEntry, cancelFlag)) return false
                onEntry()
            } else if (e.isSymlink) {
                // Symlink tidak ditulis: targetnya bisa menunjuk ke luar citra.
                onEntry()
            } else {
                val data = readInode(e.inode) ?: return false
                if (data.mode and MODE_MASK != MODE_REG) return false
                if (data.flags and ENCRYPT_FLAG != 0L) return false
                if (data.size < 0L || data.size > FileOps.MAX_EXTRACT_BYTES) return false
                if (!budget.addBytes(data.size)) return false
                // M3: tabrakan nama memakai nama unik — berkas yang
                // sudah ada tidak pernah ditimpa; tulis ke temp lalu
                // rename supaya gagal/batal tidak meninggalkan sebagian
                // dan tidak menghapus berkas lama (dulu cleanup
                // menghapus semua `written`, termasuk yang sudah ada).
                val parent = out.parentFile ?: destDir
                val final = File(parent, FileOps.uniqueName(parent, out.name))
                val finalCanonical = try { final.canonicalPath } catch (_: Exception) { return false }
                if (!FileOps.isPathInside(destCanonical, finalCanonical)) return false
                FileOps.noteCreatedDir(parent, destCanonical, createdDirs)
                parent.mkdirs()
                // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                val parentCanon = try { parent.canonicalPath } catch (_: Exception) { return false }
                if (!FileOps.isPathInside(destCanonical, parentCanon)) return false
                val tmp = FileOps.tempFileIn(parent) ?: return false
                if (!writeFileData(data, tmp)) {
                    tmp.delete()
                    return false
                }
                if (!tmp.renameTo(final)) {
                    tmp.delete()
                    return false
                }
                written += final
                onEntry()
            }
        }
        return true
    }
}
