package id.sysfile.manager

/**
 * Decoder instruksi Dalvik ke smali — DX3 (r186).
 *
 * Tabel opcode (256 entri, nama + format + jenis referensi) dihasilkan dari
 * dokumen resmi Android "Dalvik bytecode"
 * (source.android.com/docs/core/runtime/dalvik-bytecode, diambil 2026-11-20);
 * layout tiap format diverifikasi dari "Dalvik executable instruction
 * formats" (source.android.com/docs/core/runtime/instruction-formats).
 * JANGAN tulis tabel ini dari ingatan — regenerasi dari dokumen bila ragu.
 *
 * Aturan decoder: opcode yang tidak dikenal / (unused) / instruksi yang
 * terpotong di akhir TIDAK PERNAH membuat pembaca crash — decode metode
 * itu berhenti bersih dengan catatan sebagai baris terakhir. Referensi
 * yang indeksnya di luar tabel ditampilkan sebagai `<?>`, bukan ditebak.
 *
 * Gaya smali yang ditampilkan: nama kelas bertitik (com.example.Main),
 * bukan garis miring smali (Lcom/example/Main;) — supaya konsisten dengan
 * daftar kelas dan mudah dibaca di HP. Tanda tangan metode ditulis
 * `nama(param1, param2)return`, mis. `foo(int)void`.
 */
enum class RefKind {
    NONE, STRING, TYPE, FIELD, METHOD, PROTO, METHOD_HANDLE, CALL_SITE, BRANCH, LITERAL
}

data class OpInfo(val name: String, val format: String, val ref: RefKind)

/** Tabel opcode lengkap (indeks = byte opcode). */
val OPCODE_TABLE: Array<OpInfo> = arrayOf(
    OpInfo("nop", "10x", RefKind.NONE), // 0x00
    OpInfo("move", "12x", RefKind.NONE), // 0x01
    OpInfo("move/from16", "22x", RefKind.NONE), // 0x02
    OpInfo("move/16", "32x", RefKind.NONE), // 0x03
    OpInfo("move-wide", "12x", RefKind.NONE), // 0x04
    OpInfo("move-wide/from16", "22x", RefKind.NONE), // 0x05
    OpInfo("move-wide/16", "32x", RefKind.NONE), // 0x06
    OpInfo("move-object", "12x", RefKind.NONE), // 0x07
    OpInfo("move-object/from16", "22x", RefKind.NONE), // 0x08
    OpInfo("move-object/16", "32x", RefKind.NONE), // 0x09
    OpInfo("move-result", "11x", RefKind.NONE), // 0x0a
    OpInfo("move-result-wide", "11x", RefKind.NONE), // 0x0b
    OpInfo("move-result-object", "11x", RefKind.NONE), // 0x0c
    OpInfo("move-exception", "11x", RefKind.NONE), // 0x0d
    OpInfo("return-void", "10x", RefKind.NONE), // 0x0e
    OpInfo("return", "11x", RefKind.NONE), // 0x0f
    OpInfo("return-wide", "11x", RefKind.NONE), // 0x10
    OpInfo("return-object", "11x", RefKind.NONE), // 0x11
    OpInfo("const/4", "11n", RefKind.LITERAL), // 0x12
    OpInfo("const/16", "21s", RefKind.LITERAL), // 0x13
    OpInfo("const", "31i", RefKind.LITERAL), // 0x14
    OpInfo("const/high16", "21h", RefKind.LITERAL), // 0x15
    OpInfo("const-wide/16", "21s", RefKind.LITERAL), // 0x16
    OpInfo("const-wide/32", "31i", RefKind.LITERAL), // 0x17
    OpInfo("const-wide", "51l", RefKind.LITERAL), // 0x18
    OpInfo("const-wide/high16", "21h", RefKind.LITERAL), // 0x19
    OpInfo("const-string", "21c", RefKind.STRING), // 0x1a
    OpInfo("const-string/jumbo", "31c", RefKind.STRING), // 0x1b
    OpInfo("const-class", "21c", RefKind.TYPE), // 0x1c
    OpInfo("monitor-enter", "11x", RefKind.NONE), // 0x1d
    OpInfo("monitor-exit", "11x", RefKind.NONE), // 0x1e
    OpInfo("check-cast", "21c", RefKind.TYPE), // 0x1f
    OpInfo("instance-of", "22c", RefKind.TYPE), // 0x20
    OpInfo("array-length", "12x", RefKind.NONE), // 0x21
    OpInfo("new-instance", "21c", RefKind.TYPE), // 0x22
    OpInfo("new-array", "22c", RefKind.TYPE), // 0x23
    OpInfo("filled-new-array", "35c", RefKind.TYPE), // 0x24
    OpInfo("filled-new-array/range", "3rc", RefKind.TYPE), // 0x25
    OpInfo("fill-array-data", "31t", RefKind.BRANCH), // 0x26
    OpInfo("throw", "11x", RefKind.NONE), // 0x27
    OpInfo("goto", "10t", RefKind.BRANCH), // 0x28
    OpInfo("goto/16", "20t", RefKind.BRANCH), // 0x29
    OpInfo("goto/32", "30t", RefKind.BRANCH), // 0x2a
    OpInfo("packed-switch", "31t", RefKind.BRANCH), // 0x2b
    OpInfo("sparse-switch", "31t", RefKind.BRANCH), // 0x2c
    OpInfo("cmpl-float", "23x", RefKind.NONE), // 0x2d
    OpInfo("cmpg-float", "23x", RefKind.NONE), // 0x2e
    OpInfo("cmpl-double", "23x", RefKind.NONE), // 0x2f
    OpInfo("cmpg-double", "23x", RefKind.NONE), // 0x30
    OpInfo("cmp-long", "23x", RefKind.NONE), // 0x31
    OpInfo("if-eq", "22t", RefKind.BRANCH), // 0x32
    OpInfo("if-ne", "22t", RefKind.BRANCH), // 0x33
    OpInfo("if-lt", "22t", RefKind.BRANCH), // 0x34
    OpInfo("if-ge", "22t", RefKind.BRANCH), // 0x35
    OpInfo("if-gt", "22t", RefKind.BRANCH), // 0x36
    OpInfo("if-le", "22t", RefKind.BRANCH), // 0x37
    OpInfo("if-eqz", "21t", RefKind.BRANCH), // 0x38
    OpInfo("if-nez", "21t", RefKind.BRANCH), // 0x39
    OpInfo("if-ltz", "21t", RefKind.BRANCH), // 0x3a
    OpInfo("if-gez", "21t", RefKind.BRANCH), // 0x3b
    OpInfo("if-gtz", "21t", RefKind.BRANCH), // 0x3c
    OpInfo("if-lez", "21t", RefKind.BRANCH), // 0x3d
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x3e
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x3f
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x40
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x41
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x42
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x43
    OpInfo("aget", "23x", RefKind.NONE), // 0x44
    OpInfo("aget-wide", "23x", RefKind.NONE), // 0x45
    OpInfo("aget-object", "23x", RefKind.NONE), // 0x46
    OpInfo("aget-boolean", "23x", RefKind.NONE), // 0x47
    OpInfo("aget-byte", "23x", RefKind.NONE), // 0x48
    OpInfo("aget-char", "23x", RefKind.NONE), // 0x49
    OpInfo("aget-short", "23x", RefKind.NONE), // 0x4a
    OpInfo("aput", "23x", RefKind.NONE), // 0x4b
    OpInfo("aput-wide", "23x", RefKind.NONE), // 0x4c
    OpInfo("aput-object", "23x", RefKind.NONE), // 0x4d
    OpInfo("aput-boolean", "23x", RefKind.NONE), // 0x4e
    OpInfo("aput-byte", "23x", RefKind.NONE), // 0x4f
    OpInfo("aput-char", "23x", RefKind.NONE), // 0x50
    OpInfo("aput-short", "23x", RefKind.NONE), // 0x51
    OpInfo("iget", "22c", RefKind.FIELD), // 0x52
    OpInfo("iget-wide", "22c", RefKind.FIELD), // 0x53
    OpInfo("iget-object", "22c", RefKind.FIELD), // 0x54
    OpInfo("iget-boolean", "22c", RefKind.FIELD), // 0x55
    OpInfo("iget-byte", "22c", RefKind.FIELD), // 0x56
    OpInfo("iget-char", "22c", RefKind.FIELD), // 0x57
    OpInfo("iget-short", "22c", RefKind.FIELD), // 0x58
    OpInfo("iput", "22c", RefKind.FIELD), // 0x59
    OpInfo("iput-wide", "22c", RefKind.FIELD), // 0x5a
    OpInfo("iput-object", "22c", RefKind.FIELD), // 0x5b
    OpInfo("iput-boolean", "22c", RefKind.FIELD), // 0x5c
    OpInfo("iput-byte", "22c", RefKind.FIELD), // 0x5d
    OpInfo("iput-char", "22c", RefKind.FIELD), // 0x5e
    OpInfo("iput-short", "22c", RefKind.FIELD), // 0x5f
    OpInfo("sget", "21c", RefKind.FIELD), // 0x60
    OpInfo("sget-wide", "21c", RefKind.FIELD), // 0x61
    OpInfo("sget-object", "21c", RefKind.FIELD), // 0x62
    OpInfo("sget-boolean", "21c", RefKind.FIELD), // 0x63
    OpInfo("sget-byte", "21c", RefKind.FIELD), // 0x64
    OpInfo("sget-char", "21c", RefKind.FIELD), // 0x65
    OpInfo("sget-short", "21c", RefKind.FIELD), // 0x66
    OpInfo("sput", "21c", RefKind.FIELD), // 0x67
    OpInfo("sput-wide", "21c", RefKind.FIELD), // 0x68
    OpInfo("sput-object", "21c", RefKind.FIELD), // 0x69
    OpInfo("sput-boolean", "21c", RefKind.FIELD), // 0x6a
    OpInfo("sput-byte", "21c", RefKind.FIELD), // 0x6b
    OpInfo("sput-char", "21c", RefKind.FIELD), // 0x6c
    OpInfo("sput-short", "21c", RefKind.FIELD), // 0x6d
    OpInfo("invoke-virtual", "35c", RefKind.METHOD), // 0x6e
    OpInfo("invoke-super", "35c", RefKind.METHOD), // 0x6f
    OpInfo("invoke-direct", "35c", RefKind.METHOD), // 0x70
    OpInfo("invoke-static", "35c", RefKind.METHOD), // 0x71
    OpInfo("invoke-interface", "35c", RefKind.METHOD), // 0x72
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x73
    OpInfo("invoke-virtual/range", "3rc", RefKind.METHOD), // 0x74
    OpInfo("invoke-super/range", "3rc", RefKind.METHOD), // 0x75
    OpInfo("invoke-direct/range", "3rc", RefKind.METHOD), // 0x76
    OpInfo("invoke-static/range", "3rc", RefKind.METHOD), // 0x77
    OpInfo("invoke-interface/range", "3rc", RefKind.METHOD), // 0x78
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x79
    OpInfo("(unused)", "10x", RefKind.NONE), // 0x7a
    OpInfo("neg-int", "12x", RefKind.NONE), // 0x7b
    OpInfo("not-int", "12x", RefKind.NONE), // 0x7c
    OpInfo("neg-long", "12x", RefKind.NONE), // 0x7d
    OpInfo("not-long", "12x", RefKind.NONE), // 0x7e
    OpInfo("neg-float", "12x", RefKind.NONE), // 0x7f
    OpInfo("neg-double", "12x", RefKind.NONE), // 0x80
    OpInfo("int-to-long", "12x", RefKind.NONE), // 0x81
    OpInfo("int-to-float", "12x", RefKind.NONE), // 0x82
    OpInfo("int-to-double", "12x", RefKind.NONE), // 0x83
    OpInfo("long-to-int", "12x", RefKind.NONE), // 0x84
    OpInfo("long-to-float", "12x", RefKind.NONE), // 0x85
    OpInfo("long-to-double", "12x", RefKind.NONE), // 0x86
    OpInfo("float-to-int", "12x", RefKind.NONE), // 0x87
    OpInfo("float-to-long", "12x", RefKind.NONE), // 0x88
    OpInfo("float-to-double", "12x", RefKind.NONE), // 0x89
    OpInfo("double-to-int", "12x", RefKind.NONE), // 0x8a
    OpInfo("double-to-long", "12x", RefKind.NONE), // 0x8b
    OpInfo("double-to-float", "12x", RefKind.NONE), // 0x8c
    OpInfo("int-to-byte", "12x", RefKind.NONE), // 0x8d
    OpInfo("int-to-char", "12x", RefKind.NONE), // 0x8e
    OpInfo("int-to-short", "12x", RefKind.NONE), // 0x8f
    OpInfo("add-int", "23x", RefKind.NONE), // 0x90
    OpInfo("sub-int", "23x", RefKind.NONE), // 0x91
    OpInfo("mul-int", "23x", RefKind.NONE), // 0x92
    OpInfo("div-int", "23x", RefKind.NONE), // 0x93
    OpInfo("rem-int", "23x", RefKind.NONE), // 0x94
    OpInfo("and-int", "23x", RefKind.NONE), // 0x95
    OpInfo("or-int", "23x", RefKind.NONE), // 0x96
    OpInfo("xor-int", "23x", RefKind.NONE), // 0x97
    OpInfo("shl-int", "23x", RefKind.NONE), // 0x98
    OpInfo("shr-int", "23x", RefKind.NONE), // 0x99
    OpInfo("ushr-int", "23x", RefKind.NONE), // 0x9a
    OpInfo("add-long", "23x", RefKind.NONE), // 0x9b
    OpInfo("sub-long", "23x", RefKind.NONE), // 0x9c
    OpInfo("mul-long", "23x", RefKind.NONE), // 0x9d
    OpInfo("div-long", "23x", RefKind.NONE), // 0x9e
    OpInfo("rem-long", "23x", RefKind.NONE), // 0x9f
    OpInfo("and-long", "23x", RefKind.NONE), // 0xa0
    OpInfo("or-long", "23x", RefKind.NONE), // 0xa1
    OpInfo("xor-long", "23x", RefKind.NONE), // 0xa2
    OpInfo("shl-long", "23x", RefKind.NONE), // 0xa3
    OpInfo("shr-long", "23x", RefKind.NONE), // 0xa4
    OpInfo("ushr-long", "23x", RefKind.NONE), // 0xa5
    OpInfo("add-float", "23x", RefKind.NONE), // 0xa6
    OpInfo("sub-float", "23x", RefKind.NONE), // 0xa7
    OpInfo("mul-float", "23x", RefKind.NONE), // 0xa8
    OpInfo("div-float", "23x", RefKind.NONE), // 0xa9
    OpInfo("rem-float", "23x", RefKind.NONE), // 0xaa
    OpInfo("add-double", "23x", RefKind.NONE), // 0xab
    OpInfo("sub-double", "23x", RefKind.NONE), // 0xac
    OpInfo("mul-double", "23x", RefKind.NONE), // 0xad
    OpInfo("div-double", "23x", RefKind.NONE), // 0xae
    OpInfo("rem-double", "23x", RefKind.NONE), // 0xaf
    OpInfo("add-int/2addr", "12x", RefKind.NONE), // 0xb0
    OpInfo("sub-int/2addr", "12x", RefKind.NONE), // 0xb1
    OpInfo("mul-int/2addr", "12x", RefKind.NONE), // 0xb2
    OpInfo("div-int/2addr", "12x", RefKind.NONE), // 0xb3
    OpInfo("rem-int/2addr", "12x", RefKind.NONE), // 0xb4
    OpInfo("and-int/2addr", "12x", RefKind.NONE), // 0xb5
    OpInfo("or-int/2addr", "12x", RefKind.NONE), // 0xb6
    OpInfo("xor-int/2addr", "12x", RefKind.NONE), // 0xb7
    OpInfo("shl-int/2addr", "12x", RefKind.NONE), // 0xb8
    OpInfo("shr-int/2addr", "12x", RefKind.NONE), // 0xb9
    OpInfo("ushr-int/2addr", "12x", RefKind.NONE), // 0xba
    OpInfo("add-long/2addr", "12x", RefKind.NONE), // 0xbb
    OpInfo("sub-long/2addr", "12x", RefKind.NONE), // 0xbc
    OpInfo("mul-long/2addr", "12x", RefKind.NONE), // 0xbd
    OpInfo("div-long/2addr", "12x", RefKind.NONE), // 0xbe
    OpInfo("rem-long/2addr", "12x", RefKind.NONE), // 0xbf
    OpInfo("and-long/2addr", "12x", RefKind.NONE), // 0xc0
    OpInfo("or-long/2addr", "12x", RefKind.NONE), // 0xc1
    OpInfo("xor-long/2addr", "12x", RefKind.NONE), // 0xc2
    OpInfo("shl-long/2addr", "12x", RefKind.NONE), // 0xc3
    OpInfo("shr-long/2addr", "12x", RefKind.NONE), // 0xc4
    OpInfo("ushr-long/2addr", "12x", RefKind.NONE), // 0xc5
    OpInfo("add-float/2addr", "12x", RefKind.NONE), // 0xc6
    OpInfo("sub-float/2addr", "12x", RefKind.NONE), // 0xc7
    OpInfo("mul-float/2addr", "12x", RefKind.NONE), // 0xc8
    OpInfo("div-float/2addr", "12x", RefKind.NONE), // 0xc9
    OpInfo("rem-float/2addr", "12x", RefKind.NONE), // 0xca
    OpInfo("add-double/2addr", "12x", RefKind.NONE), // 0xcb
    OpInfo("sub-double/2addr", "12x", RefKind.NONE), // 0xcc
    OpInfo("mul-double/2addr", "12x", RefKind.NONE), // 0xcd
    OpInfo("div-double/2addr", "12x", RefKind.NONE), // 0xce
    OpInfo("rem-double/2addr", "12x", RefKind.NONE), // 0xcf
    OpInfo("add-int/lit16", "22s", RefKind.LITERAL), // 0xd0
    OpInfo("rsub-int", "22s", RefKind.LITERAL), // 0xd1
    OpInfo("mul-int/lit16", "22s", RefKind.LITERAL), // 0xd2
    OpInfo("div-int/lit16", "22s", RefKind.LITERAL), // 0xd3
    OpInfo("rem-int/lit16", "22s", RefKind.LITERAL), // 0xd4
    OpInfo("and-int/lit16", "22s", RefKind.LITERAL), // 0xd5
    OpInfo("or-int/lit16", "22s", RefKind.LITERAL), // 0xd6
    OpInfo("xor-int/lit16", "22s", RefKind.LITERAL), // 0xd7
    OpInfo("add-int/lit8", "22b", RefKind.LITERAL), // 0xd8
    OpInfo("rsub-int/lit8", "22b", RefKind.LITERAL), // 0xd9
    OpInfo("mul-int/lit8", "22b", RefKind.LITERAL), // 0xda
    OpInfo("div-int/lit8", "22b", RefKind.LITERAL), // 0xdb
    OpInfo("rem-int/lit8", "22b", RefKind.LITERAL), // 0xdc
    OpInfo("and-int/lit8", "22b", RefKind.LITERAL), // 0xdd
    OpInfo("or-int/lit8", "22b", RefKind.LITERAL), // 0xde
    OpInfo("xor-int/lit8", "22b", RefKind.LITERAL), // 0xdf
    OpInfo("shl-int/lit8", "22b", RefKind.LITERAL), // 0xe0
    OpInfo("shr-int/lit8", "22b", RefKind.LITERAL), // 0xe1
    OpInfo("ushr-int/lit8", "22b", RefKind.LITERAL), // 0xe2
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe3
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe4
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe5
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe6
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe7
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe8
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xe9
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xea
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xeb
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xec
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xed
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xee
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xef
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf0
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf1
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf2
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf3
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf4
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf5
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf6
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf7
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf8
    OpInfo("(unused)", "10x", RefKind.NONE), // 0xf9
    OpInfo("invoke-polymorphic", "45cc", RefKind.METHOD), // 0xfa
    OpInfo("invoke-polymorphic/range", "4rcc", RefKind.METHOD), // 0xfb
    OpInfo("invoke-custom", "35c", RefKind.CALL_SITE), // 0xfc
    OpInfo("invoke-custom/range", "3rc", RefKind.CALL_SITE), // 0xfd
    OpInfo("const-method-handle", "21c", RefKind.METHOD_HANDLE), // 0xfe
    OpInfo("const-method-type", "21c", RefKind.PROTO), // 0xff
)

/** Hasil disassembly satu metode. */
data class SmaliResult(
    val lines: List<String>,
    /** true bila decode berhenti di batas MAX_SMALI_INSTRUCTIONS. */
    val truncated: Boolean,
    /** Jumlah total unit kode (16-bit) metode ini. */
    val totalUnits: Int,
    /** Catatan bila decode berhenti sebelum akhir (opcode tak dikenal, dll). */
    val note: String?
)

object DexSmali {
    fun disassemble(dex: DexFile, codeOff: Int, maxInstructions: Int = DexOps.MAX_SMALI_INSTRUCTIONS): SmaliResult {
        val bytes = dex.bytes
        if (codeOff < 0 || codeOff + 16 > bytes.size) {
            return SmaliResult(emptyList(), false, 0, "code_off di luar berkas")
        }
        val insnsSize = DexOps.u32(bytes, codeOff + 12)
        if (insnsSize < 0 || codeOff + 16L + insnsSize * 2 > bytes.size) {
            return SmaliResult(emptyList(), false, 0, "ukuran kode di luar berkas")
        }
        val total = insnsSize.toInt()
        val lines = ArrayList<String>()
        var pc = 0
        var note: String? = null
        var truncated = false
        fun unitAt(i: Int): Int = DexOps.u16(bytes, codeOff + 16 + i * 2)
        while (pc < total) {
            if (lines.size >= maxInstructions) {
                truncated = true
                break
            }
            val unit0 = unitAt(pc)
            val op = unit0 and 0xFF
            val info = OPCODE_TABLE[op]
            if (info.name == "(unused)") {
                note = "opcode tak dikenal 0x%02x".format(op)
                break
            }
            // Payload switch/array (ident 0x0100/0x0200/0x0300) tidak pernah
            // muncul di dalam insns — bila muncul, berhenti bersih.
            if (op == 0x00 && (unit0 ushr 8) != 0) {
                note = "payload data 0x%04x".format(unit0)
                break
            }
            val len = formatLength(info.format)
            if (len <= 0 || pc + len > total) {
                note = "instruksi terpotong di akhir"
                break
            }
            val u1 = if (len > 1) unitAt(pc + 1) else 0
            val u2 = if (len > 2) unitAt(pc + 2) else 0
            val u3 = if (len > 3) unitAt(pc + 3) else 0
            val u4 = if (len > 4) unitAt(pc + 4) else 0
            lines.add(formatInstruction(dex, pc, info, unit0, u1, u2, u3, u4))
            pc += len
        }
        return SmaliResult(lines, truncated, total, note)
    }

    /** Panjang instruksi (unit 16-bit) per format; 0 = format tak dikenal. */
    internal fun formatLength(format: String): Int = when (format) {
        "10x", "11x", "12x", "11n", "10t" -> 1
        "20t", "21c", "21h", "21s", "21t", "22b", "22c", "22s", "22t", "22x", "23x" -> 2
        "30t", "31c", "31i", "31t", "32x", "35c", "3rc" -> 3
        "45cc", "4rcc" -> 4
        "51l" -> 5
        else -> 0
    }

    private fun addr(pc: Int): String = "%04d".format(pc)

    /** Literal smali: "#+123" atau "#-5" (bukan "#+-5"). */
    internal fun lit(v: Long): String = if (v >= 0) "#+$v" else "#$v"

    /** Satu baris smali: "000a: invoke-virtual {v0}, com.example.Main.foo()void". */
    internal fun formatInstruction(
        dex: DexFile, pc: Int, info: OpInfo,
        unit0: Int, u1: Int, u2: Int, u3: Int, u4: Int
    ): String {
        val name = info.name
        val hi = unit0 ushr 8 // byte atas unit pertama
        val a4 = hi and 0x0F // nibble bawah
        val b4 = (hi ushr 4) and 0x0F // nibble atas
        val prefix = "${addr(pc)}: $name"
        return when (info.format) {
            "10x" -> prefix
            "10t" -> "$prefix ${addr(pc + sign8(hi))}"
            "11x" -> "$prefix v$hi"
            "11n" -> "$prefix v$a4, ${lit(sign4(b4).toLong())}"
            "12x" -> "$prefix v$a4, v$b4"
            "20t" -> "$prefix ${addr(pc + sign16(u1))}"
            "21c" -> "$prefix v$hi, ${resolve21c(dex, info, u1)}"
            "21h" -> {
                // const/high16: (BBBB << 16) sebagai int 32-bit bertanda;
                // const-wide/high16: (BBBB << 48) sebagai long 64-bit bertanda.
                val v: Long = if (info.name == "const-wide/high16") (u1.toLong() shl 48)
                else (u1 shl 16).toLong()
                "$prefix v$hi, ${lit(v)}"
            }
            "21s" -> "$prefix v$hi, ${lit(sign16(u1).toLong())}"
            "21t" -> "$prefix v$hi, ${addr(pc + sign16(u1))}"
            "22b" -> "$prefix v$hi, v${u1 and 0xFF}, ${lit(sign8(u1 ushr 8).toLong())}"
            "22c" -> "$prefix v$a4, v$b4, ${resolveRef(dex, info.ref, u1)}"
            "22s" -> "$prefix v$a4, v$b4, ${lit(sign16(u1).toLong())}"
            "22t" -> "$prefix v$a4, v$b4, ${addr(pc + sign16(u1))}"
            "22x" -> "$prefix v$hi, v$u1"
            "23x" -> "$prefix v$hi, v${u1 and 0xFF}, v${u1 ushr 8}"
            "30t" -> "$prefix ${addr(pc + sign32(u1 or (u2 shl 16)))}"
            "31c" -> "$prefix v$hi, ${resolveRef(dex, info.ref, u1 or (u2 shl 16))}"
            "31i" -> "$prefix v$hi, ${lit((u1 or (u2 shl 16)).toLong())}"
            "31t" -> "$prefix v$hi, ${addr(pc + sign32(u1 or (u2 shl 16)))}"
            "32x" -> "$prefix v$u1, v$u2"
            "35c" -> {
                val count = b4
                val regs = regs35c(unit0, u2, count)
                "$prefix $regs, ${resolveRef(dex, info.ref, u1)}"
            }
            "3rc" -> {
                // unit1 = CCCC (register pertama), unit2 = BBBB (referensi).
                val count = hi
                val first = u1
                val regs = if (count == 0) "{}" else "{v$first..v${first + count - 1}}"
                "$prefix $regs, ${resolveRef(dex, info.ref, u2)}"
            }
            "45cc" -> {
                val count = b4
                val regs = regs35c(unit0, u2, count)
                val method = dex.methodRef(u1) ?: "method@$u1?"
                val proto = dex.protoSig(u3) ?: "proto@$u3?"
                "$prefix $regs, $method, $proto"
            }
            "4rcc" -> {
                // unit1 = CCCC (register pertama), unit2 = BBBB (metode),
                // unit3 = HHHH (proto).
                val count = hi
                val first = u1
                val regs = if (count == 0) "{}" else "{v$first..v${first + count - 1}}"
                val method = dex.methodRef(u2) ?: "method@$u2?"
                val proto = dex.protoSig(u3) ?: "proto@$u3?"
                "$prefix $regs, $method, $proto"
            }
            "51l" -> {
                val v = u1.toLong() or (u2.toLong() shl 16) or (u3.toLong() shl 32) or (u4.toLong() shl 48)
                "$prefix v$hi, ${lit(v)}"
            }
            else -> "$prefix <format ${info.format}?>"
        }
    }

    /** Register format 35c: nibble G (byte atas unit0) + C/D/E/F (unit2). */
    internal fun regs35c(unit0: Int, u2: Int, count: Int): String {
        if (count <= 0) return "{}"
        val all = intArrayOf(
            u2 and 0x0F, (u2 ushr 4) and 0x0F, (u2 ushr 8) and 0x0F,
            (u2 ushr 12) and 0x0F, (unit0 ushr 8) and 0x0F
        )
        val take = minOf(count, 5)
        return "{" + (0 until take).joinToString(", ") { "v${all[it]}" } + "}"
    }

    private fun resolve21c(dex: DexFile, info: OpInfo, index: Int): String = when (info.ref) {
        RefKind.STRING -> dex.stringAt(index)?.let { quoteString(it) } ?: "string@$index?"
        RefKind.TYPE -> dex.typeAt(index) ?: "type@$index?"
        RefKind.METHOD_HANDLE -> "method_handle@$index"
        RefKind.PROTO -> dex.protoSig(index) ?: "proto@$index?"
        else -> resolveRef(dex, info.ref, index)
    }

    internal fun resolveRef(dex: DexFile, ref: RefKind, index: Int): String = when (ref) {
        RefKind.STRING -> dex.stringAt(index)?.let { quoteString(it) } ?: "string@$index?"
        RefKind.TYPE -> dex.typeAt(index) ?: "type@$index?"
        RefKind.FIELD -> dex.fieldRef(index) ?: "field@$index?"
        RefKind.METHOD -> dex.methodRef(index) ?: "method@$index?"
        RefKind.PROTO -> dex.protoSig(index) ?: "proto@$index?"
        RefKind.METHOD_HANDLE -> "method_handle@$index"
        RefKind.CALL_SITE -> "call_site@$index"
        else -> "$index"
    }

    /** String smali: dikutip, escape, dipotong di 100 karakter. */
    internal fun quoteString(s: String): String {
        val cut = if (s.length > 100) s.substring(0, 100) + "\u2026" else s
        val esc = cut
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$esc\""
    }

    internal fun sign4(v: Int): Int = if (v and 0x8 != 0) v - 16 else v
    internal fun sign8(v: Int): Int = if (v and 0x80 != 0) v - 256 else v
    internal fun sign16(v: Int): Int = if (v and 0x8000 != 0) v - 65536 else v
    internal fun sign32(v: Int): Int = v // Int Kotlin sudah bertanda 32-bit
}
