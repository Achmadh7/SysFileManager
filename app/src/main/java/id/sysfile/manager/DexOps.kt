package id.sysfile.manager

import java.io.File
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * Pembaca berkas .dex (Dalvik Executable) — DX1+DX2+DX3 (r186).
 *
 * Format diverifikasi dari dokumen resmi Android (diambil 2026-11-20):
 * source.android.com/docs/core/runtime/dex-format dan
 * source.android.com/docs/core/runtime/dalvik-bytecode +
 * source.android.com/docs/core/runtime/instruction-formats.
 *
 * Aturan keamanan: semua offset dan jumlah divalidasi terhadap ukuran
 * berkas SEBELUM dibaca (fail closed — return null, tidak pernah throw
 * atau baca di luar batas). Checksum/signature yang tidak cocok HANYA
 * ditampilkan sebagai peringatan, bukan alasan gagal baca.
 */
object DexOps {
    /** Tolak berkas di atas 64 MiB (baca utuh ke memori). */
    const val MAX_DEX_BYTES = 64L * 1024L * 1024L
    const val HEADER_SIZE = 112
    const val NO_INDEX = -1 // 0xFFFFFFFF sebagai Int bertanda

    /** Batas instruksi smali yang didekode per metode (sisanya dipotong + dicatat). */
    const val MAX_SMALI_INSTRUCTIONS = 2000

    /** Hasil pembukaan berkas: berhasil, terlalu besar, atau rusak. */
    sealed interface OpenResult {
        data class Ok(val file: DexFile) : OpenResult
        data object TooLarge : OpenResult
        data object Invalid : OpenResult
    }

    fun open(file: File): OpenResult {
        if (!file.isFile || file.length() > MAX_DEX_BYTES) return OpenResult.TooLarge
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return OpenResult.Invalid
        }
        return open(bytes)
    }

    fun open(bytes: ByteArray): OpenResult {
        if (bytes.size.toLong() > MAX_DEX_BYTES) return OpenResult.TooLarge
        val file = parse(bytes) ?: return OpenResult.Invalid
        return OpenResult.Ok(file)
    }

    /** Parse penuh dari byte array; null bila struktur tidak valid. */
    internal fun parse(bytes: ByteArray): DexFile? {
        if (bytes.size < HEADER_SIZE) return null
        // Magic: "dex\n" + versi 3 digit + NUL.
        if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() ||
            bytes[2] != 'x'.code.toByte() || bytes[3] != '\n'.code.toByte() ||
            bytes[7] != 0.toByte()
        ) return null
        val version = String(bytes, 4, 3, Charsets.US_ASCII)
        if (version !in setOf("035", "036", "037", "038", "039")) return null
        val fileSize = u32(bytes, 32)
        if (fileSize != bytes.size.toLong()) return null
        if (u32(bytes, 36) != HEADER_SIZE.toLong()) return null
        if (u32(bytes, 40) != 0x12345678L) return null

        // checksum = Adler32 atas semua byte kecuali magic + checksum;
        // signature = SHA-1 atas semua byte kecuali magic + checksum + signature.
        val checksumOk = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value == u32(bytes, 8)
        val sig = MessageDigest.getInstance("SHA-1").apply { update(bytes, 32, bytes.size - 32) }.digest()
        var signatureOk = true
        for (i in 0 until 20) {
            if (sig[i] != bytes[12 + i]) {
                signatureOk = false
                break
            }
        }

        val stringCount = u32(bytes, 56).toIntOrNull() ?: return null
        val stringOff = u32(bytes, 60)
        val typeCount = u32(bytes, 64).toIntOrNull() ?: return null
        val typeOff = u32(bytes, 68)
        val protoCount = u32(bytes, 72).toIntOrNull() ?: return null
        val protoOff = u32(bytes, 76)
        val fieldCount = u32(bytes, 80).toIntOrNull() ?: return null
        val fieldOff = u32(bytes, 84)
        val methodCount = u32(bytes, 88).toIntOrNull() ?: return null
        val methodOff = u32(bytes, 92)
        val classCount = u32(bytes, 96).toIntOrNull() ?: return null
        val classOff = u32(bytes, 100)
        // Tiap seksi harus muat di dalam berkas.
        if (!sectionOk(bytes.size, stringOff, stringCount, 4) ||
            !sectionOk(bytes.size, typeOff, typeCount, 4) ||
            !sectionOk(bytes.size, protoOff, protoCount, 12) ||
            !sectionOk(bytes.size, fieldOff, fieldCount, 8) ||
            !sectionOk(bytes.size, methodOff, methodCount, 8) ||
            !sectionOk(bytes.size, classOff, classCount, 32)
        ) return null

        // string_ids -> string_data (MUTF-8).
        val strings = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val dataOff = u32(bytes, (stringOff + i * 4L).toInt()).toIntOrNull() ?: return null
            if (dataOff < 0 || dataOff >= bytes.size) return null
            strings.add(decodeMutf8(bytes, dataOff) ?: return null)
        }

        val types = ArrayList<String>(typeCount)
        for (i in 0 until typeCount) {
            val descIdx = u32(bytes, (typeOff + i * 4L).toInt()).toIntOrNull() ?: return null
            if (descIdx < 0 || descIdx >= stringCount) return null
            types.add(formatDescriptor(strings[descIdx]))
        }

        val protos = ArrayList<DexProto>(protoCount)
        for (i in 0 until protoCount) {
            val base = (protoOff + i * 12L).toInt()
            val retIdx = u32(bytes, base + 4).toIntOrNull() ?: return null
            if (retIdx < 0 || retIdx >= typeCount) return null
            val paramsOff = u32(bytes, base + 8)
            val params = ArrayList<Int>()
            if (paramsOff != 0L) {
                val list = readTypeList(bytes, paramsOff, typeCount) ?: return null
                params.addAll(list)
            }
            protos.add(DexProto(retIdx, params))
        }

        val fields = ArrayList<DexFieldId>(fieldCount)
        for (i in 0 until fieldCount) {
            val base = (fieldOff + i * 8L).toInt()
            val classIdx = u16(bytes, base)
            val typeIdx = u16(bytes, base + 2)
            val nameIdx = u32(bytes, base + 4).toIntOrNull() ?: return null
            if (classIdx >= typeCount || typeIdx >= typeCount ||
                nameIdx < 0 || nameIdx >= stringCount
            ) return null
            fields.add(DexFieldId(classIdx, typeIdx, nameIdx))
        }

        val methods = ArrayList<DexMethodId>(methodCount)
        for (i in 0 until methodCount) {
            val base = (methodOff + i * 8L).toInt()
            val classIdx = u16(bytes, base)
            val protoIdx = u16(bytes, base + 2)
            val nameIdx = u32(bytes, base + 4).toIntOrNull() ?: return null
            if (classIdx >= typeCount || protoIdx >= protoCount ||
                nameIdx < 0 || nameIdx >= stringCount
            ) return null
            methods.add(DexMethodId(classIdx, protoIdx, nameIdx))
        }

        val classDefs = ArrayList<DexClassDef>(classCount)
        for (i in 0 until classCount) {
            val base = (classOff + i * 32L).toInt()
            val classIdx = u32(bytes, base).toIntOrNull() ?: return null
            if (classIdx < 0 || classIdx >= typeCount) return null
            val access = u32(bytes, base + 4).toIntOrNull() ?: return null
            val superIdx = u32(bytes, base + 8).toInt()
            if (superIdx != NO_INDEX && (superIdx < 0 || superIdx >= typeCount)) return null
            val ifacesOff = u32(bytes, base + 12)
            val sourceIdx = u32(bytes, base + 16).toInt()
            if (sourceIdx != NO_INDEX && (sourceIdx < 0 || sourceIdx >= stringCount)) return null
            val classDataOff = u32(bytes, base + 24)
            if (classDataOff != 0L && (classDataOff < 0 || classDataOff >= bytes.size)) return null
            classDefs.add(
                DexClassDef(
                    classIdx = classIdx,
                    accessFlags = access,
                    superclassIdx = superIdx,
                    interfacesOff = ifacesOff,
                    sourceFileIdx = sourceIdx,
                    classDataOff = classDataOff
                )
            )
        }

        val header = DexHeader(
            version = version,
            fileSize = bytes.size.toLong(),
            checksumOk = checksumOk,
            signatureOk = signatureOk,
            stringCount = stringCount,
            typeCount = typeCount,
            protoCount = protoCount,
            fieldCount = fieldCount,
            methodCount = methodCount,
            classCount = classCount
        )
        return DexFile(bytes, header, strings, types, protos, fields, methods, classDefs)
    }

    private fun sectionOk(fileSize: Int, off: Long, count: Int, entrySize: Int): Boolean {
        if (count == 0) return true
        if (off < HEADER_SIZE || off >= fileSize) return false
        // off + count * entrySize <= fileSize (hitung Long, anti-overflow).
        return off + count.toLong() * entrySize <= fileSize
    }

    /** type_list: u32 size + size * u16 type_idx; null bila tidak valid. */
    internal fun readTypeList(bytes: ByteArray, off: Long, typeCount: Int): List<Int>? {
        if (off < 0 || off + 4 > bytes.size) return null
        val size = u32(bytes, off.toInt()).toIntOrNull() ?: return null
        if (size < 0 || size > 65535) return null
        if (off + 4 + size.toLong() * 2 > bytes.size) return null
        val out = ArrayList<Int>(size)
        for (i in 0 until size) {
            val idx = u16(bytes, (off + 4 + i * 2).toInt())
            if (idx >= typeCount) return null
            out.add(idx)
        }
        return out
    }

    /** uleb128 (maks 5 byte); null bila habis / terlalu panjang / > 32 bit. */
    internal fun readUleb128(bytes: ByteArray, off: Int, limit: Int = bytes.size): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var p = off
        while (p < limit && p - off < 5) {
            val b = (bytes[p].toInt() and 0xFF).toLong()
            p++
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0L) {
                if (result > 0xFFFFFFFFL) return null
                return result to p
            }
            shift += 7
        }
        return null
    }

    internal fun ulebInt(bytes: ByteArray, off: Int, limit: Int = bytes.size): Pair<Int, Int>? {
        val (v, next) = readUleb128(bytes, off, limit) ?: return null
        if (v > Int.MAX_VALUE) return null
        return v.toInt() to next
    }

    /**
     * Dekode satu string_data_item MUTF-8: uleb128 utf16_size (nilainya
     * diabaikan — hanya dilewati) + byte MUTF-8 + terminator 0x00.
     * NUL dikodekan sebagai C0 80; karakter suplemen dikodekan sebagai
     * pasangan surrogate (dua urutan 3 byte). Null bila tidak ada
     * terminator sebelum akhir berkas atau ada byte yang tidak valid.
     */
    internal fun decodeMutf8(bytes: ByteArray, off: Int): String? {
        val (_, p0) = readUleb128(bytes, off) ?: return null
        var p = p0
        val sb = StringBuilder()
        while (p < bytes.size) {
            val c = bytes[p].toInt() and 0xFF
            if (c == 0) return sb.toString()
            when {
                c < 0x80 -> {
                    sb.append(c.toChar())
                    p += 1
                }
                c and 0xE0 == 0xC0 -> {
                    if (p + 1 >= bytes.size) return null
                    val c2 = bytes[p + 1].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80) return null
                    sb.append((((c and 0x1F) shl 6) or (c2 and 0x3F)).toChar())
                    p += 2
                }
                c and 0xF0 == 0xE0 -> {
                    if (p + 2 >= bytes.size) return null
                    val c2 = bytes[p + 1].toInt() and 0xFF
                    val c3 = bytes[p + 2].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80 || c3 and 0xC0 != 0x80) return null
                    val cp = ((c and 0x0F) shl 12) or ((c2 and 0x3F) shl 6) or (c3 and 0x3F)
                    sb.append(cp.toChar())
                    p += 3
                }
                else -> return null
            }
        }
        return null
    }

    /** "Lcom/example/Main;" -> "com.example.Main"; "[I" -> "int[]"; "I" -> "int". */
    internal fun formatDescriptor(desc: String): String {
        if (desc.startsWith("[")) {
            var d = desc
            var dims = 0
            while (d.startsWith("[")) {
                dims++
                d = d.substring(1)
            }
            return formatDescriptor(d) + "[]".repeat(dims)
        }
        if (desc.startsWith("L") && desc.endsWith(";") && desc.length >= 2) {
            return desc.substring(1, desc.length - 1).replace('/', '.')
        }
        return when (desc) {
            "Z" -> "boolean"
            "B" -> "byte"
            "C" -> "char"
            "S" -> "short"
            "I" -> "int"
            "J" -> "long"
            "F" -> "float"
            "D" -> "double"
            "V" -> "void"
            else -> desc
        }
    }

    /** Nama flag akses (urutan tetap); "" bila tidak ada flag. */
    fun formatAccessFlags(flags: Int): String {
        val names = ArrayList<String>()
        if (flags and 0x1 != 0) names.add("public")
        if (flags and 0x2 != 0) names.add("private")
        if (flags and 0x4 != 0) names.add("protected")
        if (flags and 0x8 != 0) names.add("static")
        if (flags and 0x10 != 0) names.add("final")
        if (flags and 0x20 != 0) names.add("synchronized")
        if (flags and 0x40 != 0) names.add("volatile")
        if (flags and 0x80 != 0) names.add("transient")
        if (flags and 0x100 != 0) names.add("native")
        if (flags and 0x200 != 0) names.add("interface")
        if (flags and 0x400 != 0) names.add("abstract")
        if (flags and 0x800 != 0) names.add("strict")
        if (flags and 0x1000 != 0) names.add("synthetic")
        if (flags and 0x2000 != 0) names.add("annotation")
        if (flags and 0x4000 != 0) names.add("enum")
        if (flags and 0x10000 != 0) names.add("constructor")
        if (flags and 0x20000 != 0) names.add("declared_synchronized")
        return names.joinToString(" ")
    }

    internal fun u16(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

    internal fun u32(bytes: ByteArray, off: Int): Long =
        (bytes[off].toLong() and 0xFF) or
            ((bytes[off + 1].toLong() and 0xFF) shl 8) or
            ((bytes[off + 2].toLong() and 0xFF) shl 16) or
            ((bytes[off + 3].toLong() and 0xFF) shl 24)

    private fun Long.toIntOrNull(): Int? = if (this in 0..Int.MAX_VALUE) toInt() else null
}

data class DexHeader(
    val version: String,
    val fileSize: Long,
    val checksumOk: Boolean,
    val signatureOk: Boolean,
    val stringCount: Int,
    val typeCount: Int,
    val protoCount: Int,
    val fieldCount: Int,
    val methodCount: Int,
    val classCount: Int
)

internal data class DexProto(val returnTypeIdx: Int, val paramTypeIdx: List<Int>)
internal data class DexFieldId(val classIdx: Int, val typeIdx: Int, val nameIdx: Int)
internal data class DexMethodId(val classIdx: Int, val protoIdx: Int, val nameIdx: Int)

internal data class DexClassDef(
    val classIdx: Int,
    val accessFlags: Int,
    val superclassIdx: Int,
    val interfacesOff: Long,
    val sourceFileIdx: Int,
    val classDataOff: Long
)

data class DexFieldInfo(
    val definer: String,
    val type: String,
    val name: String,
    val accessFlags: Int,
    val isStatic: Boolean
)

data class DexMethodInfo(
    val index: Int,
    val definer: String,
    val name: String,
    val returnType: String,
    val paramTypes: List<String>,
    val accessFlags: Int,
    val codeOff: Int,
    val isDirect: Boolean
) {
    /** "foo(int, java.lang.String)void" — nama kelas penentu tidak termasuk. */
    fun signature(): String = "$name(${paramTypes.joinToString(", ")})$returnType"
}

data class DexClassInfo(
    val index: Int,
    val name: String,
    val accessFlags: Int,
    val superclass: String?,
    val interfaces: List<String>,
    val sourceFile: String?
)

data class DexClassDetail(
    val info: DexClassInfo,
    val fields: List<DexFieldInfo>,
    val methods: List<DexMethodInfo>
)

/**
 * Berkas .dex yang sudah diparse. Tabel id dipegang utuh (string/type/
 * proto/field/method); class_data diparse malas per kelas saat dibuka
 * (classDetail) supaya daftar kelas 9000+ entri tetap cepat dibuka.
 */
class DexFile internal constructor(
    internal val bytes: ByteArray,
    val header: DexHeader,
    internal val strings: List<String>,
    internal val types: List<String>,
    internal val protos: List<DexProto>,
    internal val fieldIds: List<DexFieldId>,
    internal val methodIds: List<DexMethodId>,
    internal val classDefs: List<DexClassDef>
) {
    val classNames: List<String> get() = classDefs.map { types[it.classIdx] }

    fun stringAt(index: Int): String? = strings.getOrNull(index)
    fun typeAt(index: Int): String? = types.getOrNull(index)

    /** "com.example.Main.foo(int)void" atau null bila indeks tidak valid. */
    fun methodRef(index: Int): String? {
        val m = methodIds.getOrNull(index) ?: return null
        val proto = protos.getOrNull(m.protoIdx) ?: return null
        val params = proto.paramTypeIdx.mapNotNull { types.getOrNull(it) }
        if (params.size != proto.paramTypeIdx.size) return null
        val ret = types.getOrNull(proto.returnTypeIdx) ?: return null
        val definer = types.getOrNull(m.classIdx) ?: return null
        val name = strings.getOrNull(m.nameIdx) ?: return null
        return "$definer.$name(${params.joinToString(", ")})$ret"
    }

    /** "com.example.Main.count:int" atau null bila indeks tidak valid. */
    fun fieldRef(index: Int): String? {
        val f = fieldIds.getOrNull(index) ?: return null
        val definer = types.getOrNull(f.classIdx) ?: return null
        val type = types.getOrNull(f.typeIdx) ?: return null
        val name = strings.getOrNull(f.nameIdx) ?: return null
        return "$definer.$name:$type"
    }

    /** "(int, java.lang.String)void" atau null bila indeks tidak valid. */
    fun protoSig(index: Int): String? {
        val proto = protos.getOrNull(index) ?: return null
        val params = proto.paramTypeIdx.mapNotNull { types.getOrNull(it) }
        if (params.size != proto.paramTypeIdx.size) return null
        val ret = types.getOrNull(proto.returnTypeIdx) ?: return null
        return "(${params.joinToString(", ")})$ret"
    }

    /** Info ringkas kelas ke-[index] (tanpa class_data). */
    fun classInfo(index: Int): DexClassInfo? {
        val def = classDefs.getOrNull(index) ?: return null
        val name = types.getOrNull(def.classIdx) ?: return null
        val superclass = if (def.superclassIdx == DexOps.NO_INDEX) null
        else types.getOrNull(def.superclassIdx)
        val interfaces = if (def.interfacesOff == 0L) emptyList()
        else DexOps.readTypeList(bytes, def.interfacesOff, types.size)
            ?.mapNotNull { types.getOrNull(it) } ?: return null
        val sourceFile = if (def.sourceFileIdx == DexOps.NO_INDEX) null
        else strings.getOrNull(def.sourceFileIdx)
        return DexClassInfo(index, name, def.accessFlags, superclass, interfaces, sourceFile)
    }

    /** Detail lengkap kelas (field + metode) — parse class_data saat dipanggil. */
    fun classDetail(index: Int): DexClassDetail? {
        val info = classInfo(index) ?: return null
        val def = classDefs[index]
        if (def.classDataOff == 0L) return DexClassDetail(info, emptyList(), emptyList())
        val off = def.classDataOff.toInt()
        var p = off
        val limit = bytes.size
        val (staticCount, p1) = DexOps.ulebInt(bytes, p, limit) ?: return null
        val (instanceCount, p2) = DexOps.ulebInt(bytes, p1, limit) ?: return null
        val (directCount, p3) = DexOps.ulebInt(bytes, p2, limit) ?: return null
        val (virtualCount, p4) = DexOps.ulebInt(bytes, p3, limit) ?: return null
        if (staticCount > 100000 || instanceCount > 100000 ||
            directCount > 100000 || virtualCount > 100000
        ) return null
        p = p4
        val fields = ArrayList<DexFieldInfo>()
        for (round in 0 until 2) {
            // Basis indeks DI-RESET di tiap daftar (static lalu instance):
            // field pertama tiap daftar dinyatakan langsung (diff dari 0).
            // Diverifikasi pada classes.dex nyata: tanpa reset, 17.758
            // field/metode menunjuk ke kelas lain; dengan reset, 0 salah.
            var fieldIdx = 0
            val count = if (round == 0) staticCount else instanceCount
            repeat(count) {
                val (diff, q1) = DexOps.ulebInt(bytes, p, limit) ?: return null
                val (acc, q2) = DexOps.ulebInt(bytes, q1, limit) ?: return null
                p = q2
                fieldIdx += diff
                val f = fieldIds.getOrNull(fieldIdx) ?: return null
                fields.add(
                    DexFieldInfo(
                        definer = types.getOrNull(f.classIdx) ?: return null,
                        type = types.getOrNull(f.typeIdx) ?: return null,
                        name = strings.getOrNull(f.nameIdx) ?: return null,
                        accessFlags = acc,
                        isStatic = round == 0
                    )
                )
            }
        }
        val methods = ArrayList<DexMethodInfo>()
        for (round in 0 until 2) {
            // Basis indeks DI-RESET di tiap daftar (direct lalu virtual).
            var methodIdx = 0
            val count = if (round == 0) directCount else virtualCount
            repeat(count) {
                val (diff, q1) = DexOps.ulebInt(bytes, p, limit) ?: return null
                val (acc, q2) = DexOps.ulebInt(bytes, q1, limit) ?: return null
                val (codeOff, q3) = DexOps.ulebInt(bytes, q2, limit) ?: return null
                p = q3
                methodIdx += diff
                val m = methodIds.getOrNull(methodIdx) ?: return null
                val proto = protos.getOrNull(m.protoIdx) ?: return null
                val params = proto.paramTypeIdx.mapNotNull { types.getOrNull(it) }
                if (params.size != proto.paramTypeIdx.size) return null
                methods.add(
                    DexMethodInfo(
                        index = methodIdx,
                        definer = types.getOrNull(m.classIdx) ?: return null,
                        name = strings.getOrNull(m.nameIdx) ?: return null,
                        returnType = types.getOrNull(proto.returnTypeIdx) ?: return null,
                        paramTypes = params,
                        accessFlags = acc,
                        codeOff = codeOff,
                        isDirect = round == 0
                    )
                )
            }
        }
        return DexClassDetail(info, fields, methods)
    }

    /** Smali untuk satu metode (DexSmali.kt); metode tanpa kode -> baris kosong. */
    fun methodSmali(method: DexMethodInfo): SmaliResult {
        if (method.codeOff == 0) return SmaliResult(emptyList(), false, 0, null)
        return DexSmali.disassemble(this, method.codeOff)
    }
}
