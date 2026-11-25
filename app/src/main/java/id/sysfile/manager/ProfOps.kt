package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/** Satu entri berkas DEX yang terdeteksi di dalam ART Profile (.prof / .profm). */
data class ProfDexEntry(
    val index: Int,
    val name: String,
    val checksumHex: String?,
    val methodIdsCount: Long?,
    val hotRegionBytes: Long?
)

/** Ringkasan struktur berkas ART Baseline Profile / Profile Metadata. */
data class ProfFileInfo(
    val isMetadata: Boolean,
    val versionCode: String,
    val androidTarget: String,
    val rawSize: Long,
    val compressedBytes: Long,
    val inflatedBytes: Long,
    val zlibStreamCount: Int,
    val dexCountDeclared: Int?,
    val dexEntries: List<ProfDexEntry>,
    val extractedStrings: List<String>
)

/**
 * Pembaca berkas ART Baseline Profile (`.prof`) & Metadata (`.profm`).
 *
 * Tata letak biner AOSP `androidx.profileinstaller.ProfileTranscoder`:
 * - Magic 4 byte: `"pro\0"` (`.prof`) atau `"prm\0"` (`.profm`)
 * - Versi 4 byte: `"015\0"`, `"010\0"`, `"009\0"`, `"005\0"`, `"001\0"`, atau metadata `"001\0"` / `"002\0"`
 * - Payload terkompresi zlib didekode dengan batas ukuran (`MAX_INFLATED_BYTES`).
 *
 * Berkas non-biner `.prof`/`.profm` berupa teks UTF-8 polos diarahkan ke `PlainText`.
 */
object ProfOps {
    const val MAX_PROF_BYTES = 16L * 1024L * 1024L
    private const val MAX_INFLATED_BYTES = 32 * 1024 * 1024
    private const val MAX_STRINGS = 400

    sealed interface OpenResult {
        data class ArtProfile(val info: ProfFileInfo) : OpenResult
        data object PlainText : OpenResult
        data object BinaryFallback : OpenResult
        data object TooLarge : OpenResult
    }

    fun open(file: File): OpenResult {
        if (!file.isFile) return OpenResult.BinaryFallback
        val len = file.length()
        if (len > MAX_PROF_BYTES) return OpenResult.TooLarge
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return OpenResult.BinaryFallback
        }
        return parse(bytes)
    }

    internal fun parse(bytes: ByteArray): OpenResult {
        if (bytes.size >= 8 && isArtHeader(bytes)) {
            val isMeta = bytes[1] == 'r'.code.toByte() && bytes[2] == 'm'.code.toByte()
            val version = String(bytes, 4, 3, Charsets.US_ASCII)
            val androidTarget = mapAndroidTarget(isMeta, version)

            val parsedBuffers = mutableListOf<ByteArray>()
            var totalCompressed = 0L
            var totalInflated = 0L
            var zlibStreams = 0
            var dexCountDeclared: Int? = null
            val structuredEntries = mutableListOf<ProfDexEntry>()

            // Layout A: 17-byte header (u8 numDexFiles + u32 uncompressed + u32 compressed + zlib stream)
            var matchedSingleStream = false
            if (bytes.size >= 17) {
                val numDex = bytes[8].toInt() and 0xFF
                val expectedUncomp = u32LE(bytes, 9)
                val compSize = u32LE(bytes, 13)
                if (compSize in 2..bytes.size.toLong() &&
                    17L + compSize <= bytes.size &&
                    expectedUncomp in 1..MAX_INFLATED_BYTES.toLong() &&
                    isZlibHeader(bytes, 17)
                ) {
                    val inflated = tryInflateExact(bytes, 17, compSize.toInt(), expectedUncomp.toInt())
                    if (inflated != null) {
                        matchedSingleStream = true
                        zlibStreams = 1
                        dexCountDeclared = numDex
                        totalCompressed = compSize
                        totalInflated = inflated.size.toLong()
                        parsedBuffers.add(inflated)
                        if (!isMeta && (version == "010" || version == "009")) {
                            structuredEntries.addAll(parsePStyleEntries(inflated, numDex))
                        }
                    }
                }
            }

            // Layout B: V015_S / V002_METADATA (multi-section zlib) atau format tanpa 17-byte stream
            if (!matchedSingleStream) {
                val blocks = scanZlibStreams(bytes, startOffset = 8)
                zlibStreams = blocks.size
                for ((buf, compUsed) in blocks) {
                    parsedBuffers.add(buf)
                    totalCompressed += compUsed.toLong()
                    totalInflated += buf.size.toLong()
                }
                if (parsedBuffers.isEmpty() && bytes.size > 8) {
                    parsedBuffers.add(bytes.copyOfRange(8, bytes.size))
                }
            }

            val strings = extractReadableStrings(parsedBuffers)
            val finalDexEntries = if (structuredEntries.isNotEmpty()) {
                structuredEntries
            } else {
                extractHeuristicDexEntries(strings)
            }
            val finalDexCount = dexCountDeclared ?: finalDexEntries.size.takeIf { it > 0 }

            return OpenResult.ArtProfile(
                ProfFileInfo(
                    isMetadata = isMeta,
                    versionCode = version,
                    androidTarget = androidTarget,
                    rawSize = bytes.size.toLong(),
                    compressedBytes = totalCompressed,
                    inflatedBytes = totalInflated,
                    zlibStreamCount = zlibStreams,
                    dexCountDeclared = finalDexCount,
                    dexEntries = finalDexEntries,
                    extractedStrings = strings
                )
            )
        }

        if (bytes.size <= 2_000_000 && bytes.none { it == 0.toByte() }) {
            return OpenResult.PlainText
        }
        return OpenResult.BinaryFallback
    }

    private fun isArtHeader(b: ByteArray): Boolean {
        val isProf = b[0] == 'p'.code.toByte() && b[1] == 'r'.code.toByte() && b[2] == 'o'.code.toByte() && b[3] == 0.toByte()
        val isMeta = b[0] == 'p'.code.toByte() && b[1] == 'r'.code.toByte() && b[2] == 'm'.code.toByte() && b[3] == 0.toByte()
        if (!isProf && !isMeta) return false
        return b[4] in '0'.code.toByte()..'9'.code.toByte() &&
            b[5] in '0'.code.toByte()..'9'.code.toByte() &&
            b[6] in '0'.code.toByte()..'9'.code.toByte() &&
            b[7] == 0.toByte()
    }

    private fun mapAndroidTarget(isMeta: Boolean, version: String): String {
        return if (isMeta) {
            when (version) {
                "002" -> "Android 12+ (S / API 31+)"
                "001" -> "Android 7–11 (N–R / API 24–30)"
                else -> "Metadata v$version"
            }
        } else {
            when (version) {
                "015" -> "Android 12+ (S / API 31+)"
                "010" -> "Android 9–11 (P–R / API 28–30)"
                "009" -> "Android 8.1 (Oreo MR1 / API 27)"
                "005" -> "Android 8.0 (Oreo / API 26)"
                "001" -> "Android 7.0–7.1 (Nougat / API 24–25)"
                else -> "ART Profile v$version"
            }
        }
    }

    private fun isZlibHeader(b: ByteArray, off: Int): Boolean {
        if (off + 1 >= b.size) return false
        val cmf = b[off].toInt() and 0xFF
        val flg = b[off + 1].toInt() and 0xFF
        if ((cmf and 0x0F) != 8) return false
        if ((cmf shr 4) > 7) return false
        return ((cmf shl 8) or flg) % 31 == 0
    }

    private fun tryInflateExact(src: ByteArray, offset: Int, len: Int, expectedMax: Int): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(src, offset, len)
            val out = ByteArrayOutputStream(expectedMax.coerceAtMost(65536))
            val tmp = ByteArray(8192)
            var total = 0
            while (!inflater.finished()) {
                val n = inflater.inflate(tmp)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return null
                    break
                }
                total += n
                if (total > MAX_INFLATED_BYTES) return null
                out.write(tmp, 0, n)
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun scanZlibStreams(src: ByteArray, startOffset: Int): List<Pair<ByteArray, Int>> {
        val results = mutableListOf<Pair<ByteArray, Int>>()
        var i = startOffset
        var totalInflated = 0
        while (i + 2 < src.size && results.size < 32) {
            if (isZlibHeader(src, i)) {
                val inflater = Inflater()
                try {
                    inflater.setInput(src, i, src.size - i)
                    val out = ByteArrayOutputStream()
                    val tmp = ByteArray(4096)
                    var blockSize = 0
                    var ok = true
                    while (!inflater.finished()) {
                        val n = inflater.inflate(tmp)
                        if (n == 0) {
                            if (inflater.needsInput() || inflater.needsDictionary()) {
                                ok = false
                                break
                            }
                            break
                        }
                        blockSize += n
                        if (totalInflated + blockSize > MAX_INFLATED_BYTES) {
                            ok = false
                            break
                        }
                        out.write(tmp, 0, n)
                    }
                    val read = inflater.bytesRead.toInt()
                    if (ok && read >= 6 && blockSize > 0) {
                        results.add(out.toByteArray() to read)
                        totalInflated += blockSize
                        i += read
                        continue
                    }
                } catch (_: Exception) {
                } finally {
                    inflater.end()
                }
            }
            i++
        }
        return results
    }

    /** Parse entri DEX pada header terurai P/O_MR1: `[u16 nameLen][u16 typeIdCount][u32 hotSize][u32 dexChecksum][u32 numMethodIds][nameBytes]`. */
    private fun parsePStyleEntries(buf: ByteArray, numDex: Int): List<ProfDexEntry> {
        val entries = mutableListOf<ProfDexEntry>()
        var off = 0
        for (idx in 0 until numDex.coerceAtMost(128)) {
            if (off + 16 > buf.size) return emptyList()
            val nameLen = u16LE(buf, off)
            off += 2
            off += 2 // typeIdSetSize
            val hotSize = u32LE(buf, off)
            off += 4
            val checksum = u32LE(buf, off)
            off += 4
            val numMethods = u32LE(buf, off)
            off += 4
            if (nameLen <= 0 || nameLen > 512 || off + nameLen > buf.size) return emptyList()
            val name = String(buf, off, nameLen, Charsets.UTF_8)
            if (name.any { it.code < 32 }) return emptyList()
            off += nameLen
            entries.add(
                ProfDexEntry(
                    index = idx,
                    name = name,
                    checksumHex = String.format("0x%08X", checksum),
                    methodIdsCount = numMethods,
                    hotRegionBytes = hotSize
                )
            )
        }
        return entries
    }

    private fun extractReadableStrings(buffers: List<ByteArray>): List<String> {
        val seen = LinkedHashSet<String>()
        val sb = StringBuilder()
        for (buf in buffers) {
            sb.setLength(0)
            for (raw in buf) {
                val c = raw.toInt() and 0xFF
                if (c in 32..126) {
                    sb.append(c.toChar())
                } else {
                    if (sb.length >= 4) {
                        val s = sb.toString().trim()
                        if (looksMeaningful(s)) {
                            seen.add(s)
                            if (seen.size >= MAX_STRINGS) return seen.toList()
                        }
                    }
                    sb.setLength(0)
                }
            }
            if (sb.length >= 4) {
                val s = sb.toString().trim()
                if (looksMeaningful(s)) {
                    seen.add(s)
                    if (seen.size >= MAX_STRINGS) return seen.toList()
                }
            }
        }
        return seen.toList()
    }

    private fun looksMeaningful(s: String): Boolean {
        if (s.length < 4 || s.length > 240) return false
        if (s.contains(".dex") || s.contains(".apk")) return true
        var letters = 0
        for (ch in s) {
            if (ch in 'a'..'z' || ch in 'A'..'Z') letters++
        }
        return letters >= 3 && (s.contains('/') || s.contains('.') || s.contains('_') || s.startsWith("L"))
    }

    private fun extractHeuristicDexEntries(strings: List<String>): List<ProfDexEntry> {
        return strings
            .filter { it.endsWith(".dex") || it.contains(".dex") || it.contains(".apk") }
            .distinct()
            .take(128)
            .mapIndexed { idx, name ->
                ProfDexEntry(
                    index = idx,
                    name = name,
                    checksumHex = null,
                    methodIdsCount = null,
                    hotRegionBytes = null
                )
            }
    }

    private fun u16LE(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u32LE(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFFL) or
            ((b[o + 1].toLong() and 0xFFL) shl 8) or
            ((b[o + 2].toLong() and 0xFFL) shl 16) or
            ((b[o + 3].toLong() and 0xFFL) shl 24)
}
