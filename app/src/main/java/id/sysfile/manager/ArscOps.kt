package id.sysfile.manager

import java.io.File
import java.util.Locale

/**
 * Pembaca berkas .arsc (tabel resource Android terkompilasi) — AR3 (r235).
 *
 * Format diverifikasi dari AOSP master (diambil 2026-11-21):
 * raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/
 * libs/androidfw/include/androidfw/ResourceTypes.h (ResChunk_header,
 * ResTable_header/package/typeSpec/type/entry/map, Res_value, ResTable_config)
 * + libs/androidfw/StringPool.cpp (EncodeLength/EncodeString: panjang
 * UTF-8 = dua panjang, utf16 lalu utf8, masing-masing 1 byte atau 2 byte
 * bila bit tinggi set) + android/configuration.h (ACONFIGURATION_*).
 *
 * Aturan keamanan (seperti DexOps): semua offset dan jumlah divalidasi
 * terhadap ukuran berkas SEBELUM dibaca (fail closed — return null,
 * tidak pernah throw atau baca di luar batas). Berkas di atas 64 MiB
 * ditolak. String yang gagal didekode menggagalkan seluruh parse.
 *
 * Catatan format yang diverifikasi dari berkas nyata (bukan dari header):
 * - headerSize chunk ResTable_type SELALU == 20 + config.size (dicek
 *   pada 6 fixture: overissue, tccm, scene, bitchord, gboard).
 * - complexToFloat: mantissa = bit 8..30 (23 bit bertanda) x RADIX_MULTS
 *   {1, 1/128, 1/32768, 1/8388608}; diverifikasi pada nilai AppCompat
 *   nyata: 0x1001 -> 16dip, 0x4801 -> 72dip, 0x3801 -> 56dip.
 * - TYPE_FRACTION ditampilkan x100 seperti TypedValue.coerceToString:
 *   0x66666630 -> 80%, 0x53333330 -> 65% (abc_dialog_fixed_height_major
 *   AppCompat memang 80%).
 * - Pool UTF-8 nyata bisa berisi urutan 4-byte (emoji, mis. Gboard) di
 *   samping MUTF-8 baku (C0 80, surrogate 3-byte) — keduanya diterima.
 */
object ArscOps {
    /** Tolak berkas di atas 64 MiB (baca utuh ke memori). */
    const val MAX_ARSC_BYTES = 64L * 1024L * 1024L

    // Chunk types (ResChunk_header.type).
    const val CHUNK_STRING_POOL = 0x0001
    const val CHUNK_TABLE = 0x0002
    const val CHUNK_PACKAGE = 0x0200
    const val CHUNK_TYPE = 0x0201
    const val CHUNK_TYPE_SPEC = 0x0202
    const val CHUNK_LIBRARY = 0x0203

    // Res_value.dataType.
    const val TYPE_NULL = 0x00
    const val TYPE_REFERENCE = 0x01
    const val TYPE_ATTRIBUTE = 0x02
    const val TYPE_STRING = 0x03
    const val TYPE_FLOAT = 0x04
    const val TYPE_DIMENSION = 0x05
    const val TYPE_FRACTION = 0x06
    const val TYPE_DYNAMIC_REFERENCE = 0x07
    const val TYPE_DYNAMIC_ATTRIBUTE = 0x08
    const val TYPE_INT_DEC = 0x10
    const val TYPE_INT_HEX = 0x11
    const val TYPE_INT_BOOLEAN = 0x12
    const val TYPE_INT_COLOR_ARGB8 = 0x1c
    const val TYPE_INT_COLOR_RGB8 = 0x1d
    const val TYPE_INT_COLOR_ARGB4 = 0x1e
    const val TYPE_INT_COLOR_RGB4 = 0x1f

    // ResTable_entry.flags.
    const val FLAG_COMPLEX = 0x0001
    const val FLAG_PUBLIC = 0x0002
    const val FLAG_WEAK = 0x0004
    const val FLAG_COMPACT = 0x0008

    // ResTable_type.flags.
    const val TYPE_FLAG_SPARSE = 0x01
    const val TYPE_FLAG_OFFSET16 = 0x02

    const val NO_ENTRY = 0xFFFFFFFFL

    /** Hasil pembukaan berkas: berhasil, terlalu besar, atau rusak. */
    sealed interface OpenResult {
        data class Ok(val file: ArscFile) : OpenResult
        data object TooLarge : OpenResult
        data object Invalid : OpenResult
    }

    fun open(file: File): OpenResult {
        if (!file.isFile || file.length() > MAX_ARSC_BYTES) return OpenResult.TooLarge
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return OpenResult.Invalid
        }
        return open(bytes)
    }

    fun open(bytes: ByteArray): OpenResult {
        if (bytes.size.toLong() > MAX_ARSC_BYTES) return OpenResult.TooLarge
        val file = parse(bytes) ?: return OpenResult.Invalid
        return OpenResult.Ok(file)
    }

    /** Parse penuh dari byte array; null bila struktur tidak valid. */
    internal fun parse(bytes: ByteArray): ArscFile? {
        if (bytes.size < 12) return null
        if (u16(bytes, 0) != CHUNK_TABLE) return null
        val tableHeaderSize = u16(bytes, 2)
        if (tableHeaderSize < 12 || tableHeaderSize > bytes.size) return null
        val tableSize = u32(bytes, 4)
        if (tableSize != bytes.size.toLong()) return null
        val packageCount = u32(bytes, 8).toIntOrNull() ?: return null
        if (packageCount < 0 || packageCount > 255) return null

        val globalStrings = parseStringPool(bytes, tableHeaderSize, bytes.size)
            ?: return null
        val globalPoolEnd = tableHeaderSize + poolChunkSize(bytes, tableHeaderSize)
        if (globalPoolEnd < 0 || globalPoolEnd > bytes.size) return null

        val packages = ArrayList<ArscPackage>(packageCount)
        var p = align4(globalPoolEnd)
        while (p < bytes.size) {
            val (type, _, size) = readChunkHeader(bytes, p, bytes.size)
                ?: return null
            when (type) {
                CHUNK_PACKAGE -> packages.add(parsePackage(bytes, p, size) ?: return null)
                CHUNK_LIBRARY -> {
                    // Library mapping (package-id -> nama): tidak dipakai
                    // untuk menampilkan isi tabel — lewati.
                }
                // Chunk top-level lain (overlayable/policy/staged alias):
                // lewati, jangan gagalkan parse.
                else -> Unit
            }
            p += size
        }
        if (packages.size != packageCount) return null

        val file = ArscFile(
            header = ArscHeader(
                packageCount = packageCount,
                fileSize = bytes.size.toLong(),
                globalStringCount = globalStrings.size,
                globalUtf8 = poolIsUtf8(bytes, tableHeaderSize)
            ),
            globalStrings = globalStrings,
            packages = packages
        )
        file.fillDisplays()
        return file
    }

    /** Header satu chunk: (type, headerSize, size); null bila tidak valid. */
    private fun readChunkHeader(
        bytes: ByteArray, off: Int, limit: Int
    ): Triple<Int, Int, Int>? {
        if (!inRange(off, 8, limit)) return null
        val type = u16(bytes, off)
        val headerSize = u16(bytes, off + 2)
        val size = u32(bytes, off + 4).toIntOrNull() ?: return null
        if (headerSize < 8 || headerSize > size) return null
        if (size < 8 || off + size > limit) return null
        return Triple(type, headerSize, size)
    }

    private fun poolChunkSize(bytes: ByteArray, off: Int): Int =
        u32(bytes, off + 4).toIntOrNull() ?: -1

    private fun poolIsUtf8(bytes: ByteArray, off: Int): Boolean {
        if (!inRange(off, 28, bytes.size)) return false
        return u32(bytes, off + 16) and (1L shl 8) != 0L
    }

    /**
     * Parse satu string pool pada [off] (batas [limit]); null bila tidak
     * valid. Mengembalikan daftar string (style diabaikan — tidak dipakai
     * untuk menampilkan nilai).
     */
    internal fun parseStringPool(bytes: ByteArray, off: Int, limit: Int): List<String>? {
        if (!inRange(off, 28, limit)) return null
        if (u16(bytes, off) != CHUNK_STRING_POOL) return null
        val headerSize = u16(bytes, off + 2)
        val size = u32(bytes, off + 4).toIntOrNull() ?: return null
        if (headerSize < 28 || size < 28 || off + size > limit) return null
        val stringCount = u32(bytes, off + 8).toIntOrNull() ?: return null
        val styleCount = u32(bytes, off + 12).toIntOrNull() ?: return null
        if (stringCount < 0 || stringCount > 1_000_000) return null
        if (styleCount < 0 || styleCount > 1_000_000) return null
        val flags = u32(bytes, off + 16)
        val stringsStart = u32(bytes, off + 20).toIntOrNull() ?: return null
        val utf8 = flags and (1L shl 8) != 0L
        // Array offset stringCount (+ styleCount) x u32 harus muat di chunk.
        val offsetsEnd = 28L + (stringCount + styleCount).toLong() * 4
        if (offsetsEnd > size) return null
        if (stringsStart < 0 || stringsStart > size) return null
        val poolEnd = off + size
        val out = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val rel = u32(bytes, off + 28 + i * 4).toIntOrNull() ?: return null
            val pos = off + stringsStart + rel
            if (pos < 0 || pos >= poolEnd) return null
            out.add(decodePoolString(bytes, pos, utf8, poolEnd) ?: return null)
        }
        return out
    }

    /**
     * Dekode satu string pool pada posisi absolut [pos].
     * UTF-8: dua panjang (utf16, lalu utf8), masing-masing 1 byte atau
     * 2 byte bila bit tinggi set; lalu byte MUTF-8 (termasuk urutan
     * 4-byte). UTF-16: panjang 1 u16 atau 2 u16 bila bit tinggi set.
     */
    internal fun decodePoolString(
        bytes: ByteArray, pos: Int, utf8: Boolean, limit: Int
    ): String? {
        var p = pos
        if (utf8) {
            val (_, p1) = readPoolLength8(bytes, p, limit) ?: return null
            val (byteLen, p2) = readPoolLength8(bytes, p1, limit) ?: return null
            p = p2
            if (byteLen < 0 || byteLen > 4_000_000) return null
            if (!inRange(p, byteLen, limit)) return null
            val s = decodeMutf8Bytes(bytes, p, p + byteLen) ?: return null
            return s
        } else {
            val (charLen, p1) = readPoolLength16(bytes, p, limit) ?: return null
            p = p1
            if (charLen < 0 || charLen > 2_000_000) return null
            if (charLen.toLong() * 2 > limit - p) return null
            return String(bytes, p, charLen * 2, Charsets.UTF_16LE)
        }
    }

    /** Panjang pool UTF-8: 1 byte, atau 2 byte bila bit tinggi set. */
    private fun readPoolLength8(bytes: ByteArray, off: Int, limit: Int): Pair<Int, Int>? {
        if (off < 0 || off >= limit) return null
        val b0 = bytes[off].toInt() and 0xFF
        if (b0 and 0x80 != 0) {
            if (off + 1 >= limit) return null
            val b1 = bytes[off + 1].toInt() and 0xFF
            return ((b0 and 0x7F) shl 8) or b1 to off + 2
        }
        return b0 to off + 1
    }

    /** Panjang pool UTF-16: 1 u16, atau 2 u16 bila bit tinggi set. */
    private fun readPoolLength16(bytes: ByteArray, off: Int, limit: Int): Pair<Int, Int>? {
        if (!inRange(off, 2, limit)) return null
        val w0 = u16(bytes, off)
        if (w0 and 0x8000 != 0) {
            if (!inRange(off + 2, 2, limit)) return null
            val w1 = u16(bytes, off + 2)
            return ((w0 and 0x7FFF) shl 16) or w1 to off + 4
        }
        return w0 to off + 2
    }

    /**
     * Dekode byte MUTF-8 pada [from, to): 1/2/3 byte baku + urutan
     * 4-byte (emoji). C0 80 = NUL. Null bila ada byte tidak valid.
     */
    internal fun decodeMutf8Bytes(bytes: ByteArray, from: Int, to: Int): String? {
        val sb = StringBuilder()
        var p = from
        while (p < to) {
            val c = bytes[p].toInt() and 0xFF
            when {
                c < 0x80 -> {
                    sb.append(c.toChar())
                    p += 1
                }
                c and 0xE0 == 0xC0 -> {
                    if (p + 1 >= to) return null
                    val c2 = bytes[p + 1].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80) return null
                    sb.append((((c and 0x1F) shl 6) or (c2 and 0x3F)).toChar())
                    p += 2
                }
                c and 0xF0 == 0xE0 -> {
                    if (p + 2 >= to) return null
                    val c2 = bytes[p + 1].toInt() and 0xFF
                    val c3 = bytes[p + 2].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80 || c3 and 0xC0 != 0x80) return null
                    val cp = ((c and 0x0F) shl 12) or ((c2 and 0x3F) shl 6) or (c3 and 0x3F)
                    sb.append(cp.toChar())
                    p += 3
                }
                c and 0xF8 == 0xF0 -> {
                    if (p + 3 >= to) return null
                    val c2 = bytes[p + 1].toInt() and 0xFF
                    val c3 = bytes[p + 2].toInt() and 0xFF
                    val c4 = bytes[p + 3].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80 || c3 and 0xC0 != 0x80 ||
                        c4 and 0xC0 != 0x80
                    ) return null
                    val cp = ((c and 0x07) shl 18) or ((c2 and 0x3F) shl 12) or
                        ((c3 and 0x3F) shl 6) or (c4 and 0x3F)
                    if (cp < 0x10000 || cp > 0x10FFFF) return null
                    sb.appendCodePoint(cp)
                    p += 4
                }
                else -> return null
            }
        }
        return sb.toString()
    }

    /** Parse satu package chunk pada [start] (ukuran [size]). */
    private fun parsePackage(bytes: ByteArray, start: Int, size: Int): ArscPackage? {
        val end = start + size
        if (!inRange(start, 288, end)) return null
        val id = u32(bytes, start + 8).toIntOrNull() ?: return null
        if (id < 0 || id > 255) return null
        val name = run {
            var n = 0
            while (n < 128 && u16(bytes, start + 12 + n * 2) != 0) n++
            String(bytes, start + 12, n * 2, Charsets.UTF_16LE)
        }
        val typeStringsOff = u32(bytes, start + 268).toIntOrNull() ?: return null
        val keyStringsOff = u32(bytes, start + 276).toIntOrNull() ?: return null
        val typeIdOffset = u32(bytes, start + 284).toIntOrNull() ?: return null
        // Pool type/key: offset 0 = package turunan (warisi dari package
        // lain) — nama tidak tersedia, pakai fallback "?<idx>".
        val typeNames = if (typeStringsOff == 0) emptyList()
        else parseStringPool(bytes, start + typeStringsOff, end) ?: return null
        val keyNames = if (keyStringsOff == 0) emptyList()
        else parseStringPool(bytes, start + keyStringsOff, end) ?: return null

        val specs = ArrayList<ArscTypeSpec>()
        val variantsByType = LinkedHashMap<Int, MutableList<ArscVariant>>()
        val headerSize = u16(bytes, start + 2)
        if (headerSize < 288) return null
        var p = start + headerSize
        while (p < end) {
            val (type, chHeaderSize, csize) = readChunkHeader(bytes, p, end)
                ?: return null
            when (type) {
                CHUNK_STRING_POOL -> Unit // pool type/key, sudah diparse
                CHUNK_TYPE_SPEC -> {
                    if (chHeaderSize < 16) return null
                    val specId = bytes[p + 8].toInt() and 0xFF
                    if (specId < 1 || specId > 255) return null
                    val entryCount = u32(bytes, p + 12).toIntOrNull() ?: return null
                    if (entryCount < 0 || entryCount > 1_000_000) return null
                    if (16L + entryCount.toLong() * 4 > csize) return null
                    specs.add(ArscTypeSpec(specId, entryCount))
                }
                CHUNK_TYPE -> {
                    val variant = parseTypeChunk(
                        bytes, p, csize, id, typeNames, keyNames
                    ) ?: return null
                    variantsByType.getOrPut(variant.typeId) { ArrayList() }.add(variant)
                }
                CHUNK_LIBRARY -> Unit
                else -> return null // chunk package tak dikenal: fail closed
            }
            p += csize
        }

        val types = ArrayList<ArscType>()
        for ((typeId, variants) in variantsByType) {
            val typeName = typeNames.getOrNull(typeId - 1 - typeIdOffset) ?: "?$typeId"
            types.add(buildType(typeId, typeName, variants))
        }
        types.sortBy { it.id }
        return ArscPackage(id = id, name = name, types = types, specs = specs)
    }

    /** Gabungkan varian-varian satu tipe menjadi grup per indeks entry. */
    private fun buildType(
        typeId: Int, typeName: String, variants: List<ArscVariant>
    ): ArscType {
        val groups = LinkedHashMap<Int, MutableList<ArscVariantEntry>>()
        for ((vi, variant) in variants.withIndex()) {
            for (entry in variant.entries) {
                groups.getOrPut(entry.index) { ArrayList() }
                    .add(ArscVariantEntry(vi, entry))
            }
        }
        val groupList = groups.entries.sortedBy { it.key }.map { (index, list) ->
            val first = list.first().entry
            ArscEntryGroup(index = index, key = first.key, resId = first.resId, variants = list)
        }
        return ArscType(
            id = typeId,
            name = typeName,
            variants = variants,
            groups = groupList,
            variantEntryCount = variants.sumOf { it.entries.size }
        )
    }

    /** Parse satu type chunk (0x0201) menjadi satu varian konfigurasi. */
    private fun parseTypeChunk(
        bytes: ByteArray, start: Int, size: Int, packageId: Int,
        typeNames: List<String>, keyNames: List<String>
    ): ArscVariant? {
        val end = start + size
        if (!inRange(start, 20, end)) return null
        val headerSize = u16(bytes, start + 2)
        if (headerSize < 20 || headerSize > size) return null
        val typeId = bytes[start + 8].toInt() and 0xFF
        if (typeId < 1 || typeId > 255) return null
        val flags = bytes[start + 9].toInt() and 0xFF
        val entryCount = u32(bytes, start + 12).toIntOrNull() ?: return null
        if (entryCount < 0 || entryCount > 1_000_000) return null
        val entriesStart = u32(bytes, start + 16).toIntOrNull() ?: return null
        if (entriesStart < headerSize || entriesStart > size) return null
        val configSize = u32(bytes, start + 20).toIntOrNull() ?: return null
        if (configSize < 4 || 20L + configSize > size) return null
        if (headerSize < 20 + configSize) return null
        val config = parseConfig(bytes, start + 20, configSize) ?: return null

        // Array offset entry: dense u32 / sparse u32 / offset16 u16.
        val sparse = flags and TYPE_FLAG_SPARSE != 0
        val offset16 = flags and TYPE_FLAG_OFFSET16 != 0
        val offsetsBase = start + headerSize
        val entryOffsets = LinkedHashMap<Int, Int>()
        if (sparse) {
            if (offsetsBase.toLong() + entryCount.toLong() * 4 > end) return null
            for (i in 0 until entryCount) {
                // Raw u32 bisa melewati Int.MAX (offset di 16 bit atas):
                // hitung dengan Long, jangan toIntOrNull.
                val raw = u32(bytes, offsetsBase + i * 4)
                val idx = (raw and 0xFFFF).toInt()
                val off = ((raw ushr 16) and 0xFFFF).toInt() * 4
                if (entryOffsets.put(idx, off) != null) return null
            }
        } else if (offset16) {
            if (offsetsBase.toLong() + entryCount.toLong() * 2 > end) return null
            for (i in 0 until entryCount) {
                val o = u16(bytes, offsetsBase + i * 2)
                if (o == 0xFFFF) continue
                entryOffsets[i] = o * 4
            }
        } else {
            if (offsetsBase.toLong() + entryCount.toLong() * 4 > end) return null
            for (i in 0 until entryCount) {
                val o = u32(bytes, offsetsBase + i * 4)
                if (o == NO_ENTRY) continue
                val off = o.toIntOrNull() ?: return null
                entryOffsets[i] = off
            }
        }

        val entries = ArrayList<ArscEntry>(entryOffsets.size)
        for ((index, off) in entryOffsets) {
            val ep = start + entriesStart + off
            if (ep < 0 || ep + 8 > end) return null
            val entry = parseEntry(bytes, ep, end, index, packageId, typeId, keyNames)
                ?: return null
            entries.add(entry)
        }
        entries.sortBy { it.index }
        return ArscVariant(
            typeId = typeId,
            config = config,
            entries = entries
        )
    }

    /** Parse satu ResTable_entry pada [ep] (batas [chunkEnd]). */
    private fun parseEntry(
        bytes: ByteArray, ep: Int, chunkEnd: Int, index: Int,
        packageId: Int, typeId: Int, keyNames: List<String>
    ): ArscEntry? {
        val flags = u16(bytes, ep + 2)
        val isComplex = flags and FLAG_COMPLEX != 0
        val isPublic = flags and FLAG_PUBLIC != 0
        val isCompact = flags and FLAG_COMPACT != 0
        val resId = (packageId shl 24) or (typeId shl 16) or index
        if (isCompact) {
            val keyIndex = u16(bytes, ep)
            val key = keyNames.getOrNull(keyIndex) ?: "?$keyIndex"
            val dataType = (flags shr 8) and 0xFF
            val data = u32(bytes, ep + 4).toInt()
            return ArscEntry(
                index = index, keyIndex = keyIndex, key = key,
                resId = resId, isComplex = false, isPublic = isPublic,
                isCompact = true, value = ArscValue(dataType, data),
                maps = emptyList(), parentRef = 0
            )
        }
        val size = u16(bytes, ep)
        if (size < 8 || ep + size > chunkEnd) return null
        val keyIndex = u32(bytes, ep + 4).toIntOrNull() ?: return null
        val key = if (keyNames.isEmpty()) "?$keyIndex"
        else keyNames.getOrNull(keyIndex) ?: return null
        if (isComplex) {
            // Header map entry = 16 byte (Full 8 + parent 4 + count 4);
            // parent/count ada DI DALAM size — cukup size >= 16 dan
            // ep+size sudah dicek muat di atas. (Cek lama "ep+size+8"
            // salah: menolak entry kompleks count=0 di ujung chunk.)
            if (size < 16) return null
            val parentRef = u32(bytes, ep + 8).toInt()
            val count = u32(bytes, ep + 12).toIntOrNull() ?: return null
            if (count < 0 || count > 10_000) return null
            val mapsStart = ep + size
            if (mapsStart.toLong() + count.toLong() * 12 > chunkEnd) return null
            val maps = ArrayList<ArscMapEntry>(count)
            for (j in 0 until count) {
                val mp = mapsStart + j * 12
                val nameRef = u32(bytes, mp).toInt()
                if (!inRange(mp + 4, 8, chunkEnd)) return null
                val vType = bytes[mp + 7].toInt() and 0xFF
                val vData = u32(bytes, mp + 8).toInt()
                maps.add(ArscMapEntry(nameRef, ArscValue(vType, vData)))
            }
            return ArscEntry(
                index = index, keyIndex = keyIndex, key = key,
                resId = resId, isComplex = true, isPublic = isPublic,
                isCompact = false, value = null, maps = maps,
                parentRef = parentRef
            )
        }
        val vp = ep + size
        if (!inRange(vp, 8, chunkEnd)) return null
        val vType = bytes[vp + 3].toInt() and 0xFF
        val vData = u32(bytes, vp + 4).toInt()
        return ArscEntry(
            index = index, keyIndex = keyIndex, key = key,
            resId = resId, isComplex = false, isPublic = isPublic,
            isCompact = false, value = ArscValue(vType, vData),
            maps = emptyList(), parentRef = 0
        )
    }

    /**
     * Parse ResTable_config pada [off] (ukuran [size] byte). Field hanya
     * dibaca sejauh size mengizinkan — config lama (size 28/32) tidak
     * punya field yang lebih baru.
     */
    internal fun parseConfig(bytes: ByteArray, off: Int, size: Int): ArscConfig? {
        if (size < 4 || !inRange(off, size, bytes.size)) return null
        fun u16f(at: Int): Int = if (size >= at + 2) u16(bytes, off + at) else 0
        fun u8f(at: Int): Int =
            if (size >= at + 1) bytes[off + at].toInt() and 0xFF else 0
        fun strf(at: Int, n: Int): String {
            if (size < at + n) return ""
            val raw = bytes.copyOfRange(off + at, off + at + n)
            var end = 0
            while (end < n && raw[end] != 0.toByte()) end++
            return String(raw, 0, end, Charsets.US_ASCII)
        }
        val language = if (size >= 10) decodeLanguage(bytes[off + 8], bytes[off + 9]) else null
        val region = if (size >= 12) decodeRegion(bytes[off + 10], bytes[off + 11]) else null
        val uiMode = u8f(29)
        val colorMode = u8f(49)
        val config = ArscConfig(
            size = size,
            mcc = u16f(4),
            mnc = u16f(6),
            language = language,
            region = region,
            script = strf(36, 4),
            variant = strf(40, 8),
            orientation = u8f(12),
            density = u16f(14),
            sdkVersion = u16f(24),
            smallestScreenWidthDp = u16f(30),
            screenWidthDp = u16f(32),
            screenHeightDp = u16f(34),
            night = (uiMode shr 4) and 0x3,
            round = u8f(48) and 0x3,
            wideColorGamut = colorMode and 0x3,
            hdr = (colorMode shr 2) and 0x3,
            qualifier = ""
        )
        return config.copy(qualifier = qualifierFor(config))
    }

    /**
     * Dekode bahasa: dua huruf ASCII, atau 3 huruf ISO-639-2 yang
     * dipadatkan. Urutan huruf mengikuti AOSP unpackLanguageOrRegion
     * (ResourceTypes.cpp): huruf PERTAMA ada di 5 bit rendah byte
     * kedua, huruf kedua = 3 bit tinggi byte kedua + 2 bit rendah
     * byte pertama, huruf ketiga = bit 2..6 byte pertama.
     * (Versi sebelumnya membalik urutannya — "fil" jadi "lif".)
     */
    internal fun decodeLanguage(b0: Byte, b1: Byte): String? {
        val i0 = b0.toInt() and 0xFF
        val i1 = b1.toInt() and 0xFF
        if (i0 and 0x80 != 0) {
            val c1 = (i1 and 0x1F) + 'a'.code - 1
            val c2 = ((i1 and 0xE0) shr 5) + ((i0 and 0x03) shl 3) + 'a'.code - 1
            val c3 = ((i0 and 0x7C) shr 2) + 'a'.code - 1
            return String(charArrayOf(c1.toChar(), c2.toChar(), c3.toChar()))
        }
        if (i0 == 0) return null
        val s = String(byteArrayOf(b0, b1), Charsets.US_ASCII).trim { it == '\u0000' }
        return if (s.isEmpty()) null else s
    }

    /**
     * Dekode region: dua huruf ASCII, atau kode M.49 3 digit yang
     * dipadatkan dengan cara yang sama seperti bahasa (basis '0').
     */
    internal fun decodeRegion(b0: Byte, b1: Byte): String? {
        val i0 = b0.toInt() and 0xFF
        val i1 = b1.toInt() and 0xFF
        if (i0 and 0x80 != 0) {
            val d1 = (i1 and 0x1F) + '0'.code - 1
            val d2 = ((i1 and 0xE0) shr 5) + ((i0 and 0x03) shl 3) + '0'.code - 1
            val d3 = ((i0 and 0x7C) shr 2) + '0'.code - 1
            return String(charArrayOf(d1.toChar(), d2.toChar(), d3.toChar()))
        }
        if (i0 == 0) return null
        val s = String(byteArrayOf(b0, b1), Charsets.US_ASCII).trim { it == '\u0000' }
        return if (s.isEmpty()) null else s
    }

    /** String qualifier gaya aapt ("en-rUS-land-hdpi-v33"); "" = default. */
    internal fun qualifierFor(c: ArscConfig): String {
        val parts = ArrayList<String>()
        if (c.mcc != 0) parts.add("mcc${c.mcc}")
        if (c.mnc != 0) parts.add("mnc${c.mnc}")
        val lang = c.language
        if (lang != null) {
            var s = lang
            if (c.region != null) s += "-r${c.region}"
            if (c.script.isNotEmpty()) s += "-${c.script}"
            if (c.variant.isNotEmpty()) s += "+${c.variant}"
            parts.add(s)
        }
        if (c.smallestScreenWidthDp != 0) parts.add("sw${c.smallestScreenWidthDp}dp")
        if (c.screenWidthDp != 0) parts.add("w${c.screenWidthDp}dp")
        if (c.screenHeightDp != 0) parts.add("h${c.screenHeightDp}dp")
        when (c.orientation) {
            1 -> parts.add("port")
            2 -> parts.add("land")
            3 -> parts.add("square")
        }
        when (c.night) {
            1 -> parts.add("notnight")
            2 -> parts.add("night")
        }
        if (c.density != 0) {
            parts.add(
                when (c.density) {
                    120 -> "ldpi"
                    160 -> "mdpi"
                    213 -> "tvdpi"
                    240 -> "hdpi"
                    320 -> "xhdpi"
                    480 -> "xxhdpi"
                    640 -> "xxxhdpi"
                    0xFFFE -> "anydpi"
                    0xFFFF -> "nodpi"
                    else -> "${c.density}dpi"
                }
            )
        }
        if (c.sdkVersion != 0) parts.add("v${c.sdkVersion}")
        when (c.round) {
            1 -> parts.add("notround")
            2 -> parts.add("round")
        }
        if (c.wideColorGamut == 2) parts.add("widecg")
        if (c.hdr == 2) parts.add("highdr")
        return parts.joinToString("-")
    }

    /**
     * complex (dimension/fraction) -> float. Mantissa = bit 8..30
     * (23 bit bertanda) x RADIX_MULTS.
     */
    internal fun complexToFloat(data: Int): Float {
        val mantissa = data shr 8 // shift aritmetik: tanda ikut (bit 31)
        val radix = (data shr 4) and 0x3
        val mult = when (radix) {
            0 -> 1.0f
            1 -> 1.0f / 128f
            2 -> 1.0f / 32768f
            else -> 1.0f / 8388608f
        }
        return mantissa * mult
    }

    /** Float untuk tampilan: "16" bukan "16.0", "0.8" bukan "0.8000". */
    internal fun formatFloat(v: Float): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        if (v == v.toInt().toFloat() && v >= -1_000_000_000f && v <= 1_000_000_000f) {
            return v.toInt().toString()
        }
        val s = String.format(Locale.US, "%.4f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    internal fun u16(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

    internal fun u32(bytes: ByteArray, off: Int): Long =
        (bytes[off].toLong() and 0xFF) or
            ((bytes[off + 1].toLong() and 0xFF) shl 8) or
            ((bytes[off + 2].toLong() and 0xFF) shl 16) or
            ((bytes[off + 3].toLong() and 0xFF) shl 24)

    private fun Long.toIntOrNull(): Int? = if (this in 0..Int.MAX_VALUE) toInt() else null

    /** [off, off+n) muat di [0, limit) tanpa overflow. */
    private fun inRange(off: Int, n: Int, limit: Int): Boolean =
        off >= 0 && n >= 0 && off.toLong() + n <= limit

    private fun align4(v: Int): Int = (v + 3) and 3.inv()
}

data class ArscHeader(
    val packageCount: Int,
    val fileSize: Long,
    val globalStringCount: Int,
    val globalUtf8: Boolean
)

data class ArscTypeSpec(val id: Int, val entryCount: Int)

data class ArscConfig(
    val size: Int,
    val mcc: Int,
    val mnc: Int,
    val language: String?,
    val region: String?,
    val script: String,
    val variant: String,
    val orientation: Int,
    val density: Int,
    val sdkVersion: Int,
    val smallestScreenWidthDp: Int,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val night: Int,
    val round: Int,
    val wideColorGamut: Int,
    val hdr: Int,
    val qualifier: String
)

data class ArscValue(val dataType: Int, val data: Int)

/** Satu name/value di dalam entry kompleks (map). */
data class ArscMapEntry(val nameRef: Int, val value: ArscValue)

data class ArscEntry(
    val index: Int,
    val keyIndex: Int,
    val key: String,
    val resId: Int,
    val isComplex: Boolean,
    val isPublic: Boolean,
    val isCompact: Boolean,
    val value: ArscValue?,
    val maps: List<ArscMapEntry>,
    val parentRef: Int
) {
    /** Tampilan nilai ter-decode; diisi ArscFile.fillDisplays() ("" bila kompleks). */
    internal var display: String = ""
}

data class ArscVariant(
    val typeId: Int,
    val config: ArscConfig,
    val entries: List<ArscEntry>
)

/** Satu varian dari satu entry (satu konfigurasi). */
data class ArscVariantEntry(val variantIndex: Int, val entry: ArscEntry)

/** Satu entry (satu indeks) beserta semua varian konfigurasinya. */
data class ArscEntryGroup(
    val index: Int,
    val key: String,
    val resId: Int,
    val variants: List<ArscVariantEntry>
)

data class ArscType(
    val id: Int,
    val name: String,
    val variants: List<ArscVariant>,
    val groups: List<ArscEntryGroup>,
    /** Jumlah entry di semua varian (cocok dengan hitungan parser luar). */
    val variantEntryCount: Int
) {
    /** Jumlah entry berbeda (grup) pada tipe ini. */
    val entryCount: Int get() = groups.size
}

data class ArscPackage(
    val id: Int,
    val name: String,
    val types: List<ArscType>,
    val specs: List<ArscTypeSpec>
)

data class ArscSearchHit(
    val packageName: String,
    val typeName: String,
    val group: ArscEntryGroup,
    /** Tampilan nilai varian pertama ("" bila kompleks). */
    val display: String
)

/**
 * Berkas .arsc yang sudah diparse. Semua entry diparse penuh saat buka
 * (nilai ter-decode dihitung sekali di fillDisplays supaya daftar +
 * pencarian cepat).
 */
class ArscFile internal constructor(
    val header: ArscHeader,
    internal val globalStrings: List<String>,
    val packages: List<ArscPackage>
) {
    val totalTypes: Int get() = packages.sumOf { it.types.size }
    val totalEntries: Int get() = packages.sumOf { pkg -> pkg.types.sumOf { it.variantEntryCount } }

    fun globalStringAt(index: Int): String? = globalStrings.getOrNull(index)

    /** Isi display setiap entry (dipanggil sekali dari ArscOps.parse). */
    internal fun fillDisplays() {
        for (pkg in packages) {
            for (type in pkg.types) {
                for (variant in type.variants) {
                    for (entry in variant.entries) {
                        if (!entry.isComplex && entry.value != null) {
                            entry.display = formatValue(entry.value)
                        }
                    }
                }
            }
        }
    }

    /**
     * "@type/nama" untuk ident 0xpptteeee; null bila target tidak ada
     * di berkas ini (mis. package android 0x01 dari framework).
     */
    fun resolveRef(ident: Int): String? {
        val pkgId = (ident ushr 24) and 0xFF
        val typeId = (ident ushr 16) and 0xFF
        val entryIdx = ident and 0xFFFF
        val pkg = packages.firstOrNull { it.id == pkgId } ?: return null
        val type = pkg.types.firstOrNull { it.id == typeId } ?: return null
        val group = type.groups.firstOrNull { it.index == entryIdx } ?: return null
        return "@${type.name}/${group.key}"
    }

    /** Nama tampilan satu map entry (attr khusus ATTR_* / referensi). */
    fun mapName(ident: Int): String {
        if ((ident ushr 16) == 0x0100) {
            // Res_INTERNALID: 16 bit atas == 0x0100 -> Res_MAKEINTERNAL(n).
            return when (ident and 0xFFFF) {
                0 -> "type"
                1 -> "min"
                2 -> "max"
                3 -> "l10n"
                4 -> "other"
                5 -> "zero"
                6 -> "one"
                7 -> "two"
                8 -> "few"
                9 -> "many"
                else -> "@0x%08X".format(Locale.US, ident)
            }
        }
        return resolveRef(ident) ?: "@0x%08X".format(Locale.US, ident)
    }

    /** Nilai ter-decode menjadi teks (dipakai daftar entry + detail). */
    fun formatValue(value: ArscValue): String {
        val data = value.data
        return when (value.dataType) {
            ArscOps.TYPE_STRING -> globalStrings.getOrNull(data) ?: "?"
            ArscOps.TYPE_REFERENCE -> resolveRef(data)
                ?: "@0x%08X".format(Locale.US, data)
            ArscOps.TYPE_ATTRIBUTE -> {
                val r = resolveRef(data)
                if (r != null) "?${r.removePrefix("@")}" else "?0x%08X".format(Locale.US, data)
            }
            ArscOps.TYPE_FLOAT -> ArscOps.formatFloat(Float.fromBits(data))
            ArscOps.TYPE_DIMENSION -> {
                val v = ArscOps.complexToFloat(data)
                val unit = when (data and 0xF) {
                    0 -> "px"
                    1 -> "dip"
                    2 -> "sp"
                    3 -> "pt"
                    4 -> "in"
                    5 -> "mm"
                    else -> "?"
                }
                ArscOps.formatFloat(v) + unit
            }
            ArscOps.TYPE_FRACTION -> {
                val v = ArscOps.complexToFloat(data) * 100f
                ArscOps.formatFloat(v) + if ((data and 0xF) == 1) "%p" else "%"
            }
            ArscOps.TYPE_INT_DEC -> data.toString()
            ArscOps.TYPE_INT_HEX -> "0x" + data.toUInt().toString(16).uppercase()
            ArscOps.TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            ArscOps.TYPE_INT_COLOR_ARGB8 -> "#%08X".format(Locale.US, data)
            ArscOps.TYPE_INT_COLOR_RGB8 -> "#%06X".format(Locale.US, data and 0xFFFFFF)
            ArscOps.TYPE_INT_COLOR_ARGB4 -> "#%03X".format(Locale.US, data and 0xFFF)
            ArscOps.TYPE_INT_COLOR_RGB4 -> "#%03X".format(Locale.US, data and 0xFFF)
            ArscOps.TYPE_NULL -> if (data == 1) "@null" else "null"
            ArscOps.TYPE_DYNAMIC_REFERENCE -> "@0x%08X".format(Locale.US, data)
            ArscOps.TYPE_DYNAMIC_ATTRIBUTE -> "?0x%08X".format(Locale.US, data)
            else -> "0x%02X:0x%08X".format(Locale.US, value.dataType, data)
        }
    }

    /**
     * Cari di nama entry + nilai string ter-decode, case-insensitive,
     * di seluruh package/tipe. Satu hit per grup entry.
     */
    fun search(query: String): List<ArscSearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<ArscSearchHit>()
        for (pkg in packages) {
            for (type in pkg.types) {
                for (group in type.groups) {
                    val firstDisplay = group.variants.firstOrNull()?.entry?.display ?: ""
                    val hit = group.key.lowercase().contains(q) ||
                        group.variants.any { it.entry.display.lowercase().contains(q) }
                    if (hit) {
                        out.add(ArscSearchHit(pkg.name, type.name, group, firstDisplay))
                    }
                }
            }
        }
        return out
    }
}
