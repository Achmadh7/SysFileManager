package id.sysfile.manager

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Sumber byte akses-acak: berkas mentah atau citra sparse yang
 *  dibaca sebagai isi logisnya (hasil unsparse) tanpa pernah
 *  mengurai seluruh citra ke temp. */
interface RandomSource : Closeable {
    /** Panjang logis dalam byte (untuk sparse: total_blks * blk_sz). */
    fun length(): Long

    /** Baca tepat [size] byte di [offset] logis; null bila di luar
     *  jangkauan atau gagal. */
    fun readAt(offset: Long, size: Int): ByteArray?
}

/** RandomSource di atas RandomAccessFile biasa. */
class FileSource(private val raf: RandomAccessFile) : RandomSource {
    private val lock = Any()

    override fun length(): Long {
        synchronized(lock) {
            return try { raf.length() } catch (_: Exception) { 0L }
        }
    }

    override fun readAt(offset: Long, size: Int): ByteArray? {
        if (offset < 0L || size < 0) return null
        synchronized(lock) {
            val len = try { raf.length() } catch (_: Exception) { return null }
            if (offset > len || size > len - offset) return null
            return try {
                val buf = ByteArray(size)
                raf.seek(offset)
                var read = 0
                while (read < size) {
                    val n = raf.read(buf, read, size - read)
                    if (n < 0) return null
                    read += n
                }
                buf
            } catch (_: Exception) { null }
        }
    }

    override fun close() {
        try { raf.close() } catch (_: Exception) { }
    }
}

/** Pembaca sparse Android akses-acak (LD2 r157): header + tabel chunk
 *  diparse SEKALI menjadi indeks (offset logis -> chunk), lalu
 *  [readAt] melayani byte dari chunk RAW, mengulang pola 4 byte
 *  untuk chunk FILL, dan nol untuk DONT_CARE — tanpa pernah
 *  mengurai seluruh citra. Chunk CRC32 tidak membawa data logis
 *  (dilewati, seperti unsparse).
 *
 *  Validasi header/chunk persis seperti unsparseRun di ImgOps:
 *  magic 0xED26FF3A, major 1, blk_sz kelipatan 4, total_sz per
 *  tipe chunk, chunk tidak melebihi total_blks, dan jumlah blok
 *  tertulis harus sama dengan total_blks. Satu-satunya yang TIDAK
 *  dicek adalah image_checksum header: memverifikasinya butuh
 *  membaca semua data — justru yang dihindari baca langsung.
 *  Data rusak di dalam chunk RAW akan gagal saat dibaca (magic
 *  superblock dll.), sama seperti citra mentah yang rusak.
 *
 *  Batasan yang disengaja:
 *  - total_chunks dibatasi 1 juta (citra nyata punya ribuan);
 *    tanpa batas, header jahat bisa memaksa alokasi indeks raksasa.
 *  - Panjang logis dibatasi 8 GiB (ImgOps.MAX_UNSPARSE_BYTES),
 *    sama seperti unsparse.
 *  - Semua akses RandomAccessFile dikunci satu lock.
 */
class SparseSource private constructor(
    private val raf: RandomAccessFile,
    val blockSize: Long,
    private val chunks: List<SparseChunk>,
    private val totalBytes: Long
) : RandomSource {

    /** Satu chunk yang membawa data logis (RAW/FILL/DONT_CARE).
     *  [fileOffset] hanya berarti untuk RAW (offset data di berkas);
     *  [fill] hanya berarti untuk FILL (nilai 4 byte). */
    private data class SparseChunk(
        val logicalStart: Long,
        val type: Int,
        val fileOffset: Long,
        val fill: Int,
        val size: Long
    )

    companion object {
        private const val SPARSE_MAGIC = 0xED26FF3AL
        private const val SPARSE_HEADER_SIZE = 28
        private const val SPARSE_CHUNK_HEADER_SIZE = 12
        private const val SPARSE_CHUNK_RAW = 0xCAC1
        private const val SPARSE_CHUNK_FILL = 0xCAC2
        private const val SPARSE_CHUNK_DONT_CARE = 0xCAC3
        private const val SPARSE_CHUNK_CRC32 = 0xCAC4
        private const val MAX_CHUNKS = 1_000_000

        /** Buka citra sparse; null bila bukan sparse / rusak / terpotong
         *  / terlalu besar. Tidak pernah throw. */
        fun open(file: File): SparseSource? {
            if (!file.isFile) return null
            val raf = try { RandomAccessFile(file, "r") } catch (_: Exception) { return null }
            return try {
                val src = openOn(raf)
                if (src == null) {
                    try { raf.close() } catch (_: Exception) { }
                    null
                } else src
            } catch (_: Exception) {
                try { raf.close() } catch (_: Exception) { }
                null
            }
        }

        private fun openOn(raf: RandomAccessFile): SparseSource? {
            val fileLen = try { raf.length() } catch (_: Exception) { return null }
            val header = readAt(raf, 0L, SPARSE_HEADER_SIZE) ?: return null
            if (u32le(header, 0) != SPARSE_MAGIC) return null
            if (u16le(header, 4) != 1) return null
            val fileHdrSz = u16le(header, 8)
            val chunkHdrSz = u16le(header, 10)
            if (fileHdrSz < SPARSE_HEADER_SIZE || chunkHdrSz < SPARSE_CHUNK_HEADER_SIZE) return null
            val blkSz = u32le(header, 12)
            val totalBlks = u32le(header, 16)
            val totalChunks = u32le(header, 20)
            if (blkSz == 0L || blkSz % 4L != 0L) return null
            if (totalBlks > ImgOps.MAX_UNSPARSE_BYTES / blkSz) return null
            if (totalChunks > MAX_CHUNKS) return null
            val totalBytes = totalBlks * blkSz
            // Posisi baca: sesudah header (termasuk sisa file_hdr_sz).
            var filePos = fileHdrSz.toLong()
            val chunks = mutableListOf<SparseChunk>()
            var logicalPos = 0L
            var blocksWritten = 0L
            for (chunkIndex in 0L until totalChunks) {
                val ch = readAt(raf, filePos, SPARSE_CHUNK_HEADER_SIZE) ?: return null
                filePos += chunkHdrSz.toLong()
                val type = u16le(ch, 0)
                val chunkSz = u32le(ch, 4)
                val totalSz = u32le(ch, 8)
                if (totalSz < chunkHdrSz) return null
                val dataSz = totalSz - chunkHdrSz
                when (type) {
                    SPARSE_CHUNK_RAW -> {
                        if (chunkSz > Long.MAX_VALUE / blkSz) return null
                        val bytes = chunkSz * blkSz
                        if (dataSz != bytes) return null
                        if (chunkSz > totalBlks - blocksWritten) return null
                        if (bytes > fileLen - filePos) return null
                        if (bytes > 0) {
                            chunks += SparseChunk(logicalPos, type, filePos, 0, bytes)
                        }
                        filePos += bytes
                        logicalPos += bytes
                        blocksWritten += chunkSz
                    }
                    SPARSE_CHUNK_FILL -> {
                        if (dataSz != 4L) return null
                        if (chunkSz > totalBlks - blocksWritten) return null
                        val fillBytes = readAt(raf, filePos, 4) ?: return null
                        val fill = u32le(fillBytes, 0).toInt()
                        if (chunkSz > Long.MAX_VALUE / blkSz) return null
                        val bytes = chunkSz * blkSz
                        if (bytes > 0) {
                            chunks += SparseChunk(logicalPos, type, 0L, fill, bytes)
                        }
                        filePos += 4
                        logicalPos += bytes
                        blocksWritten += chunkSz
                    }
                    SPARSE_CHUNK_DONT_CARE -> {
                        if (dataSz != 0L) return null
                        if (chunkSz > totalBlks - blocksWritten) return null
                        if (chunkSz > Long.MAX_VALUE / blkSz) return null
                        val bytes = chunkSz * blkSz
                        if (bytes > 0) {
                            chunks += SparseChunk(logicalPos, type, 0L, 0, bytes)
                        }
                        logicalPos += bytes
                        blocksWritten += chunkSz
                    }
                    SPARSE_CHUNK_CRC32 -> {
                        if (dataSz != 4L) return null
                        filePos += 4
                    }
                    else -> return null
                }
            }
            if (blocksWritten != totalBlks) return null
            if (logicalPos != totalBytes) return null
            return SparseSource(raf, blkSz, chunks, totalBytes)
        }

        private fun readAt(raf: RandomAccessFile, offset: Long, size: Int): ByteArray? {
            if (offset < 0L || size < 0) return null
            return try {
                val len = raf.length()
                if (offset > len || size > len - offset) return null
                val buf = ByteArray(size)
                raf.seek(offset)
                var read = 0
                while (read < size) {
                    val n = raf.read(buf, read, size - read)
                    if (n < 0) return null
                    read += n
                }
                buf
            } catch (_: Exception) { null }
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

    /** Jumlah chunk data (RAW/FILL/DONT_CARE) dalam indeks. */
    val chunkCount: Int get() = chunks.size

    override fun length(): Long = totalBytes

    override fun readAt(offset: Long, size: Int): ByteArray? {
        if (offset < 0L || size < 0) return null
        if (offset > totalBytes || size > totalBytes - offset) return null
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        synchronized(lock) {
            var pos = offset
            var done = 0
            var idx = chunks.binarySearchBy(offset) { it.logicalStart }
            if (idx < 0) idx = -idx - 2
            while (done < size) {
                if (idx < 0 || idx >= chunks.size) return null
                val chunk = chunks[idx]
                val chunkEnd = chunk.logicalStart + chunk.size
                if (pos < chunk.logicalStart || pos >= chunkEnd) return null
                val take = minOf(chunkEnd - pos, (size - done).toLong()).toInt()
                when (chunk.type) {
                    SPARSE_CHUNK_RAW -> {
                        val src = readRaw(chunk.fileOffset + (pos - chunk.logicalStart), take) ?: return null
                        src.copyInto(out, done)
                    }
                    SPARSE_CHUNK_FILL -> {
                        val fillBytes = byteArrayOf(
                            (chunk.fill and 0xFF).toByte(),
                            ((chunk.fill ushr 8) and 0xFF).toByte(),
                            ((chunk.fill ushr 16) and 0xFF).toByte(),
                            ((chunk.fill ushr 24) and 0xFF).toByte()
                        )
                        val phase = ((pos - chunk.logicalStart) % 4).toInt()
                        for (i in 0 until take) out[done + i] = fillBytes[(phase + i) % 4]
                    }
                    SPARSE_CHUNK_DONT_CARE -> {
                        // ByteArray sudah nol.
                    }
                    else -> return null
                }
                pos += take
                done += take
                idx++
            }
        }
        return out
    }

    private fun readRaw(offset: Long, size: Int): ByteArray? {
        return try {
            val buf = ByteArray(size)
            raf.seek(offset)
            var read = 0
            while (read < size) {
                val n = raf.read(buf, read, size - read)
                if (n < 0) return null
                read += n
            }
            buf
        } catch (_: Exception) { null }
    }

    override fun close() {
        try { raf.close() } catch (_: Exception) { }
    }
}
