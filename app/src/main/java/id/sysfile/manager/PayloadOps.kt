package id.sysfile.manager

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pengekstrak payload.bin (format pembaruan OTA Android / update_engine) dengan
 * parser protobuf minimal yang ditulis tangan. Hanya payload PENUH yang
 * didukung, mengikuti enum InstallOperation update_engine yang sebenarnya:
 * REPLACE (0), REPLACE_BZ (1), REPLACE_XZ (8), ZERO (9), dan DISCARD (10).
 * Operasi delta (MOVE 2, BSDIFF 3, SOURCE_COPY 4, SOURCE_BSDIFF 5,
 * BROTLI_BSDIFF 11, PUFFDIFF 12, ZUCCHINI 13, LZ4DIFF 14/15) ditolak sebagai
 * "unsupported" sebelum ada berkas yang ditulis. Setiap partisi yang selesai
 * diekstrak diverifikasi ukurannya dan (bila manifest menyertakannya) hash
 * SHA-256-nya terhadap new_partition_info; ketidakcocokan membuang hasil
 * ekstrak dan dilaporkan sebagai "hash". Ekstraksi melaporkan kemajuan
 * (byte selesai/total) dan bisa dibatalkan lewat AtomicBoolean ("cancelled").
 */
object PayloadOps {

    /** Hasil ekstraksi: jumlah partisi yang ditulis, atau kode galat. */
    data class Outcome(val partitions: Int, val error: String?)

    /** Satu partisi untuk layar isi payload: nama + ukuran berkas .img hasil ekstrak. */
    data class PartitionInfo(val name: String, val size: Long)

    /** Hasil pemeriksaan payload: daftar partisi, atau kode galat ("unsupported"/"invalid"). */
    data class Listing(val partitions: List<PartitionInfo>, val error: String?)

    /** Hasil ekstrak satu partisi: berkas .img yang dibuat, atau kode galat. */
    data class OneOutcome(val file: File?, val error: String?)

    private class Op(
        val type: Long,
        val dataOffset: Long,
        val dataLength: Long,
        val dstExtents: MutableList<Pair<Long, Long>> = mutableListOf()
    )

    private class Partition(
        val name: String,
        val ops: MutableList<Op> = mutableListOf(),
        val expectedSize: Long = 0L,
        val expectedHash: ByteArray? = null
    )

    private class CancelledException : Exception()

    private class HashMismatchException : Exception()

    /** Pembaca protobuf minimal: varint dan field length-delimited saja. */
    private class Cursor(private val buf: ByteArray, private var pos: Int, private val end: Int) {
        fun hasNext(): Boolean = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= end || shift > 63) throw IllegalArgumentException("varint")
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun sub(): Cursor {
            val len = varint().toInt()
            if (len < 0 || pos + len > end) throw IllegalArgumentException("length")
            val c = Cursor(buf, pos, pos + len)
            pos += len
            return c
        }

        fun bytes(): ByteArray {
            val len = varint().toInt()
            if (len < 0 || pos + len > end) throw IllegalArgumentException("length")
            val out = buf.copyOfRange(pos, pos + len)
            pos += len
            return out
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> {
                    pos += 8
                    if (pos > end) throw IllegalArgumentException("skip")
                }
                2 -> {
                    val len = varint().toInt()
                    if (len < 0) throw IllegalArgumentException("skip")
                    pos += len
                    if (pos > end) throw IllegalArgumentException("skip")
                }
                5 -> {
                    pos += 4
                    if (pos > end) throw IllegalArgumentException("skip")
                }
                else -> throw IllegalArgumentException("wire $wire")
            }
        }
    }

    private fun parseExtent(c: Cursor): Pair<Long, Long> {
        var start = 0L
        var num = 0L
        while (c.hasNext()) {
            val tag = c.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 0 -> start = c.varint()
                field == 2 && wire == 0 -> num = c.varint()
                else -> c.skip(wire)
            }
        }
        return start to num
    }

    private fun parseOp(c: Cursor): Op {
        var type = -1L
        var dataOffset = 0L
        var dataLength = 0L
        val extents = mutableListOf<Pair<Long, Long>>()
        while (c.hasNext()) {
            val tag = c.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 0 -> type = c.varint()
                field == 2 && wire == 0 -> dataOffset = c.varint()
                field == 3 && wire == 0 -> dataLength = c.varint()
                field == 6 && wire == 2 -> extents += parseExtent(c.sub())
                else -> c.skip(wire)
            }
        }
        return Op(type, dataOffset, dataLength, extents)
    }

    private fun parsePartitionInfo(c: Cursor): Pair<Long, ByteArray?> {
        var size = 0L
        var hash: ByteArray? = null
        while (c.hasNext()) {
            val tag = c.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 0 -> size = c.varint()
                field == 2 && wire == 2 -> hash = c.bytes()
                else -> c.skip(wire)
            }
        }
        return size to hash
    }

    private fun parsePartition(c: Cursor): Partition {
        var name = ""
        val ops = mutableListOf<Op>()
        var expectedSize = 0L
        var expectedHash: ByteArray? = null
        while (c.hasNext()) {
            val tag = c.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 2 -> name = String(c.bytes(), Charsets.UTF_8)
                field == 7 && wire == 2 -> {
                    val (s, h) = parsePartitionInfo(c.sub())
                    expectedSize = s
                    expectedHash = h
                }
                field == 8 && wire == 2 -> ops += parseOp(c.sub())
                else -> c.skip(wire)
            }
        }
        return Partition(name, ops, expectedSize, expectedHash)
    }

    /**
     * Ekstrak semua partisi dari [file] (payload.bin) ke folder "payload" yang
     * unik di [parentDir], satu berkas .img per partisi.
     */
    fun extract(
        file: File,
        parentDir: File,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Outcome {
        return try {
            doExtract(file, parentDir, onProgress, cancelFlag)
        } catch (e: UnsupportedOperationException) {
            Outcome(0, "unsupported")
        } catch (e: CancelledException) {
            Outcome(0, "cancelled")
        } catch (e: HashMismatchException) {
            Outcome(0, "hash")
        } catch (e: Exception) {
            Outcome(0, "invalid")
        }
    }

    private class Parsed(val blockSize: Long, val blobOffset: Long, val partitions: List<Partition>)

    /** Baca header + manifest payload dari [raf]; melempar IllegalArgumentException bila tidak valid. */
    private fun readManifest(raf: RandomAccessFile): Parsed {
        val magic = ByteArray(4)
        raf.readFully(magic)
        if (String(magic, Charsets.US_ASCII) != "CrAU") throw IllegalArgumentException("magic")
        val version = raf.readLong()
        if (version < 1L || version > 2L) throw IllegalArgumentException("version")
        val manifestSize = raf.readLong()
        if (manifestSize <= 0L || manifestSize > 64L * 1024 * 1024) {
            throw IllegalArgumentException("manifest size")
        }
        val sigSize = if (version > 1L) raf.readInt().toLong() and 0xFFFFFFFFL else 0L
        val manifestBytes = ByteArray(manifestSize.toInt())
        raf.readFully(manifestBytes)
        val blobOffset = raf.filePointer + sigSize

        var blockSize = 4096L
        val partitions = mutableListOf<Partition>()
        val mc = Cursor(manifestBytes, 0, manifestBytes.size)
        while (mc.hasNext()) {
            val tag = mc.varint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 3 && wire == 0 -> blockSize = mc.varint()
                field == 13 && wire == 2 -> partitions += parsePartition(mc.sub())
                else -> mc.skip(wire)
            }
        }
        if (partitions.isEmpty() || partitions.size > 128) throw IllegalArgumentException("partitions")
        if (blockSize <= 0L || blockSize > 1024L * 1024) throw IllegalArgumentException("block size")
        return Parsed(blockSize, blobOffset, partitions)
    }

    /** Tolak payload delta: hanya operasi penulisan penuh yang didukung. */
    private fun checkSupported(partitions: List<Partition>) {
        for (partition in partitions) {
            for (op in partition.ops) {
                // Enum update_engine: REPLACE=0, REPLACE_BZ=1, REPLACE_XZ=8,
                // ZERO=9, DISCARD=10. Nilai lain = operasi delta.
                if (op.type != 0L && op.type != 1L && op.type != 8L &&
                    op.type != 9L && op.type != 10L
                ) {
                    throw UnsupportedOperationException("op ${op.type}")
                }
            }
        }
    }

    /**
     * Daftar partisi di dalam payload TANPA mengekstrak apa pun (untuk layar
     * isi payload). Ukuran = perkiraan ukuran berkas .img hasil ekstrak.
     */
    fun listPartitions(file: File): Listing {
        // Berkas .bin yang bukan payload (magic bukan "CrAU") ditandai
        // "notpayload" agar pemanggil bisa membukanya seperti berkas biasa.
        val isPayload = try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                var read = 0
                while (read < 4) {
                    val n = input.read(magic, read, 4 - read)
                    if (n < 0) break
                    read += n
                }
                read == 4 && String(magic, Charsets.US_ASCII) == "CrAU"
            }
        } catch (e: Exception) {
            false
        }
        if (!isPayload) return Listing(emptyList(), "notpayload")
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val parsed = readManifest(raf)
                checkSupported(parsed.partitions)
                Listing(parsed.partitions.map { partition ->
                    val blocks = partition.ops.flatMap { it.dstExtents }
                        .maxOfOrNull { it.first + it.second } ?: 0L
                    val extentSize = blocks * parsed.blockSize
                    PartitionInfo(
                        partition.name,
                        if (partition.expectedSize > 0L) partition.expectedSize else extentSize
                    )
                }, null)
            }
        } catch (e: UnsupportedOperationException) {
            Listing(emptyList(), "unsupported")
        } catch (e: Exception) {
            Listing(emptyList(), "invalid")
        }
    }

    /** Ekstrak SATU partisi ke berkas .img unik di [destDir]. */
    fun extractOne(
        file: File,
        partitionName: String,
        destDir: File,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): OneOutcome {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val parsed = readManifest(raf)
                val partition = parsed.partitions.firstOrNull { it.name == partitionName }
                    ?: throw IllegalArgumentException("partition")
                checkSupported(listOf(partition))
                val safeName = partition.name
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "partition" }
                val outFile = File(destDir, FileOps.uniqueName(destDir, "$safeName.img"))
                try {
                    extractPartition(
                        raf, parsed.blobOffset, parsed.blockSize, partition, outFile,
                        reportBase = 0L,
                        totalBytes = partitionBytes(partition, parsed.blockSize),
                        onProgress = onProgress,
                        cancelFlag = cancelFlag
                    )
                } catch (e: Exception) {
                    outFile.delete()
                    throw e
                }
                OneOutcome(outFile, null)
            }
        } catch (e: UnsupportedOperationException) {
            OneOutcome(null, "unsupported")
        } catch (e: CancelledException) {
            OneOutcome(null, "cancelled")
        } catch (e: HashMismatchException) {
            OneOutcome(null, "hash")
        } catch (e: Exception) {
            OneOutcome(null, "invalid")
        }
    }

    private fun partitionBytes(partition: Partition, blockSize: Long): Long {
        val blocks = partition.ops.flatMap { it.dstExtents }
            .maxOfOrNull { it.first + it.second } ?: 0L
        return blocks * blockSize
    }

    private fun doExtract(
        file: File,
        parentDir: File,
        onProgress: (Long, Long) -> Unit,
        cancelFlag: AtomicBoolean?
    ): Outcome {
        RandomAccessFile(file, "r").use { raf ->
            val parsed = readManifest(raf)
            // Tolak payload delta SEBELUM menulis apa pun.
            checkSupported(parsed.partitions)

            val destDir = File(parentDir, FileOps.uniqueName(parentDir, "payload"))
            if (!destDir.mkdirs() && !destDir.isDirectory) throw IllegalArgumentException("dest")

            try {
                val totalBytes = parsed.partitions.sumOf { partitionBytes(it, parsed.blockSize) }
                var base = 0L
                var done = 0
                for (partition in parsed.partitions) {
                    val safeName = partition.name
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .ifBlank { "partition" }
                    extractPartition(
                        raf, parsed.blobOffset, parsed.blockSize, partition,
                        File(destDir, "$safeName.img"),
                        reportBase = base,
                        totalBytes = totalBytes,
                        onProgress = onProgress,
                        cancelFlag = cancelFlag
                    )
                    base += partitionBytes(partition, parsed.blockSize)
                    done++
                }
                return Outcome(done, null)
            } catch (e: Exception) {
                // Ekstrak semua bersifat atomik: batal/gagal menghapus folder hasil.
                destDir.deleteRecursively()
                throw e
            }
        }
    }

    private fun extractPartition(
        raf: RandomAccessFile,
        blobOffset: Long,
        blockSize: Long,
        partition: Partition,
        outFile: File,
        reportBase: Long,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
        cancelFlag: AtomicBoolean?
    ) {
        var doneHere = 0L
        val onBytes: (Long) -> Unit = { n ->
            doneHere += n
            onProgress(reportBase + doneHere, totalBytes)
        }
        RandomAccessFile(outFile, "rw").use { out ->
            val totalBlocks = partition.ops.flatMap { it.dstExtents }
                .maxOfOrNull { it.first + it.second } ?: 0L
            out.setLength(totalBlocks * blockSize)
            for (op in partition.ops) {
                if (cancelFlag?.get() == true) throw CancelledException()
                when (op.type) {
                    0L, 1L, 8L -> {
                        if (op.dataLength < 0L || op.dataLength > 512L * 1024 * 1024) {
                            throw IllegalArgumentException("op size")
                        }
                        val chunk = ByteArray(op.dataLength.toInt())
                        raf.seek(blobOffset + op.dataOffset)
                        raf.readFully(chunk)
                        val stream: InputStream = when (op.type) {
                            0L -> ByteArrayInputStream(chunk)
                            1L -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
                                ByteArrayInputStream(chunk)
                            )
                            else -> org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                                ByteArrayInputStream(chunk)
                            )
                        }
                        stream.use { input ->
                            writeToExtents(input, out, op.dstExtents, blockSize, onBytes, cancelFlag)
                        }
                    }
                    9L, 10L -> writeZeros(out, op.dstExtents, blockSize, onBytes, cancelFlag)
                    else -> throw UnsupportedOperationException("op ${op.type}")
                }
            }
        }
        verifyPartition(outFile, partition)
    }

    /** Verifikasi ukuran + hash SHA-256 hasil ekstrak terhadap new_partition_info. */
    private fun verifyPartition(outFile: File, partition: Partition) {
        val expectedHash = partition.expectedHash
        val expectedSize = partition.expectedSize
        if (expectedHash == null && expectedSize <= 0L) return
        if (expectedSize > 0L && outFile.length() != expectedSize) {
            throw HashMismatchException()
        }
        if (expectedHash != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(outFile).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            if (!digest.digest().contentEquals(expectedHash)) {
                throw HashMismatchException()
            }
        }
    }

    private fun writeToExtents(
        input: InputStream,
        out: RandomAccessFile,
        extents: List<Pair<Long, Long>>,
        blockSize: Long,
        onBytes: (Long) -> Unit,
        cancelFlag: AtomicBoolean?
    ) {
        val buffer = ByteArray(64 * 1024)
        val zeros = ByteArray(64 * 1024)
        for ((startBlock, numBlocks) in extents) {
            out.seek(startBlock * blockSize)
            var remaining = numBlocks * blockSize
            while (remaining > 0) {
                if (cancelFlag?.get() == true) throw CancelledException()
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) {
                    // Aliran habis sebelum extent penuh: sisa extent diisi nol.
                    var pad = remaining
                    while (pad > 0) {
                        val n = minOf(zeros.size.toLong(), pad).toInt()
                        out.write(zeros, 0, n)
                        onBytes(n.toLong())
                        pad -= n
                    }
                    remaining = 0
                } else {
                    out.write(buffer, 0, read)
                    onBytes(read.toLong())
                    remaining -= read
                }
            }
        }
    }

    private fun writeZeros(
        out: RandomAccessFile,
        extents: List<Pair<Long, Long>>,
        blockSize: Long,
        onBytes: (Long) -> Unit,
        cancelFlag: AtomicBoolean?
    ) {
        val zeros = ByteArray(64 * 1024)
        for ((startBlock, numBlocks) in extents) {
            out.seek(startBlock * blockSize)
            var remaining = numBlocks * blockSize
            while (remaining > 0) {
                if (cancelFlag?.get() == true) throw CancelledException()
                val n = minOf(zeros.size.toLong(), remaining).toInt()
                out.write(zeros, 0, n)
                onBytes(n.toLong())
                remaining -= n
            }
        }
    }
}
