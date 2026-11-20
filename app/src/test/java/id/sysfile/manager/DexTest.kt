package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * r186: pembaca .dex — fixture .dex sintetis minimal yang dibangun tangan
 * (header + 10 string + 4 tipe + 1 proto + 1 field + 4 metode + 1 kelas +
 * 4 code_item) dengan Adler32 + SHA-1 yang benar, supaya verifikasi
 * checksum/signature bisa diuji. Kelas: com.example.Test (super
 * java.lang.Object, sumber Test.java); field instance private bar:int;
 * metode direct public-static foo/bar/baz/qux.
 */
class DexTest {

    // ---- fixture builder (little-endian, offset dihitung dinamis) ----

    private class W {
        val out = ByteArrayOutputStream()
        fun pos(): Int = out.size()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun u16(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }

        fun u32(v: Long) {
            out.write((v and 0xFF).toInt())
            out.write(((v ushr 8) and 0xFF).toInt())
            out.write(((v ushr 16) and 0xFF).toInt())
            out.write(((v ushr 24) and 0xFF).toInt())
        }

        fun uleb(v: Int) {
            var x = v
            do {
                var b = x and 0x7F
                x = x ushr 7
                if (x != 0) b = b or 0x80
                out.write(b)
            } while (x != 0)
        }

        /** Selalu tulis uleb 2 byte (dipakai sebagai placeholder code_off
         *  pada putaran pertama builder supaya layout-nya sama). */
        fun uleb2(v: Int) {
            out.write((v and 0x7F) or 0x80)
            out.write((v ushr 7) and 0x7F)
        }

        fun bytes(bs: ByteArray) = out.write(bs)
        fun pad4() {
            while (out.size() % 4 != 0) out.write(0)
        }

        fun patchU32(at: Int, v: Long) {
            val bs = out.toByteArray()
            bs[at] = (v and 0xFF).toByte()
            bs[at + 1] = ((v ushr 8) and 0xFF).toByte()
            bs[at + 2] = ((v ushr 16) and 0xFF).toByte()
            bs[at + 3] = ((v ushr 24) and 0xFF).toByte()
            out.reset()
            out.write(bs)
        }
    }

    private fun buildTestDex(): ByteArray {
        val w = W()
        val strings = listOf(
            "Lcom/example/Test;", "V", "foo", "I", "bar",
            "Ljava/lang/Object;", "Test.java", "hi", "baz", "qux", "add", "rng", "lit"
        )
        // Header (112 byte) — isi placeholder dulu, patch belakangan.
        w.bytes("dex\n038\u0000".toByteArray(Charsets.US_ASCII))
        w.u32(0) // checksum
        repeat(20) { w.u8(0) } // signature
        val fileSizePos = w.pos(); w.u32(0) // file_size
        w.u32(112) // header_size
        w.u32(0x12345678L) // endian_tag
        w.u32(0); w.u32(0) // link_size, link_off
        w.u32(0) // map_off
        val stringIdsPos = w.pos(); w.u32(strings.size.toLong()); val stringIdsOffPos = w.pos(); w.u32(0)
        val typeIdsPos = w.pos(); w.u32(4); val typeIdsOffPos = w.pos(); w.u32(0)
        val protoIdsPos = w.pos(); w.u32(1); val protoIdsOffPos = w.pos(); w.u32(0)
        val fieldIdsPos = w.pos(); w.u32(1); val fieldIdsOffPos = w.pos(); w.u32(0)
        val methodIdsPos = w.pos(); w.u32(7); val methodIdsOffPos = w.pos(); w.u32(0)
        val classDefsPos = w.pos(); w.u32(1); val classDefsOffPos = w.pos(); w.u32(0)
        w.u32(0); w.u32(0) // data_size, data_off (diisi belakangan)
        assertEquals(112, w.pos())
        // string_ids
        val stringIdsOff = w.pos()
        w.patchU32(stringIdsOffPos, stringIdsOff.toLong())
        val stringDataOffs = IntArray(strings.size)
        repeat(strings.size) { w.u32(0) } // patch belakangan
        // type_ids
        val typeIdsOff = w.pos()
        w.patchU32(typeIdsOffPos, typeIdsOff.toLong())
        // type 0..3 -> string 0,1,3,5
        listOf(0, 1, 3, 5).forEach { w.u32(it.toLong()) }
        // proto_ids (shorty=1 "V", return=1, params=0)
        val protoIdsOff = w.pos()
        w.patchU32(protoIdsOffPos, protoIdsOff.toLong())
        w.u32(1); w.u32(1); w.u32(0)
        // field_ids: class 0, type 2 (I), name 4 ("bar")
        val fieldIdsOff = w.pos()
        w.patchU32(fieldIdsOffPos, fieldIdsOff.toLong())
        w.u16(0); w.u16(2); w.u32(4)
        // method_ids: class 0, proto 0, name 2/7/8/9
        val methodIdsOff = w.pos()
        w.patchU32(methodIdsOffPos, methodIdsOff.toLong())
        listOf(2, 7, 8, 9, 10, 11, 12).forEach { w.u16(0); w.u16(0); w.u32(it.toLong()) }
        // class_defs (patch class_data_off belakangan)
        val classDefsOff = w.pos()
        w.patchU32(classDefsOffPos, classDefsOff.toLong())
        w.u32(0) // class_idx
        w.u32(0x1) // public
        w.u32(3) // superclass Object
        w.u32(0) // interfaces_off
        w.u32(6) // source_file "Test.java"
        w.u32(0) // annotations_off
        val classDataOffPos = w.pos(); w.u32(0) // class_data_off
        w.u32(0) // static_values_off
        // string_data
        val dataOff = w.pos()
        strings.forEachIndexed { i, s ->
            stringDataOffs[i] = w.pos()
            w.uleb(s.length)
            w.bytes(s.toByteArray(Charsets.UTF_8))
            w.u8(0)
        }
        repeat(strings.size) { i ->
            // patch string_ids[i] — posisi string_ids_off + i*4
            val bs = w.out.toByteArray()
            val at = stringIdsOff + i * 4
            val v = stringDataOffs[i].toLong()
            bs[at] = (v and 0xFF).toByte()
            bs[at + 1] = ((v ushr 8) and 0xFF).toByte()
            bs[at + 2] = ((v ushr 16) and 0xFF).toByte()
            bs[at + 3] = ((v ushr 24) and 0xFF).toByte()
            w.out.reset()
            w.out.write(bs)
        }
        // class_data
        val classDataOff = w.pos()
        w.patchU32(classDataOffPos, classDataOff.toLong())
        w.uleb(0) // static_fields
        w.uleb(1) // instance_fields
        w.uleb(7) // direct_methods
        w.uleb(0) // virtual_methods
        w.uleb(0); w.uleb(0x2) // field 0, private
        val codeOffPos = IntArray(7)
        // metode 0..3: diff 0,1,1,1; acc 0x9; code_off (patch belakangan)
        for (i in 0 until 7) {
            w.uleb(if (i == 0) 0 else 1)
            w.uleb(0x9)
            codeOffPos[i] = w.pos()
            w.uleb2(0) // placeholder 2 byte — code_off nyata < 512 (2 byte)
        }
        // code_items (4-byte aligned)
        val codeOffs = IntArray(7)
        val insnsList = listOf(
            intArrayOf(0x1012, 0x106e, 0x0000, 0x0000, 0x010a, 0x000e), // foo
            intArrayOf(0x001a, 0x0007, 0x0038, 0x0003, 0x000e, 0x7012, 0x000e), // bar
            intArrayOf(0x1052, 0x0000, 0x000e), // baz
            intArrayOf(0x0073, 0x000e), // qux (opcode tak dikenal)
            intArrayOf(0x0090, 0x0201, 0x000e), // add: add-int v0, v1, v2
            intArrayOf(0x0274, 0x0000, 0x0000, 0x000e), // rng: invoke-virtual/range {v0..v1}
            intArrayOf(0x0013, 0xFFFB, 0x000e) // lit: const/16 v0, #-5
        )
        val regsList = intArrayOf(2, 1, 2, 1, 3, 2, 1)
        insnsList.forEachIndexed { i, insns ->
            w.pad4()
            codeOffs[i] = w.pos()
            w.u16(regsList[i]); w.u16(1); w.u16(0); w.u16(0) // regs, ins, outs, tries
            w.u32(0) // debug_info_off
            w.u32(insns.size.toLong())
            insns.forEach { w.u16(it) }
        }
        // patch code_off (uleb128) — tulis ulang seluruh class_data dengan offset benar.
        // Lebih mudah: bangun ulang code_off di posisi yang dicatat — karena
        // uleb bisa 1-2 byte, kita tulis ulang berkas dari classDataOff.
        // Sederhana: code_off semuanya < 512 (2 byte) dan placeholder awal
        // sudah 1 byte (uleb 0) — jadi geser tidak bisa inplace. Sebagai gantinya
        // patch dengan menulis ulang: rebuild seluruh dex dengan offset yang
        // sudah diketahui tidak mungkin di sini; gunakan trik: karena semua
        // code_off < 128? Tidak — ~350. Maka rebuild: panggil ulang builder
        // dengan code_off yang sudah diketahui? Tidak bisa (chicken-egg).
        // Solusi: simpan posisi code_off dan tulis 2-byte uleb dengan menggeser
        // byte setelahnya — ByteArrayOutputStream tidak mendukung insert.
        // Maka: bangun ulang dex dari nol dengan code_off yang dihitung dari
        // layout di atas (deterministik): ulangi seluruh proses dengan
        // codeOffs yang sudah diketahui dari putaran pertama.
        // (Putaran pertama ini hanya untuk mengukur offset.)
        return rebuildWithCodeOffs(codeOffs, stringDataOffs, strings)
    }

    /** Putaran kedua builder: layout identik, code_off sudah diketahui. */
    private fun rebuildWithCodeOffs(codeOffs: IntArray, stringDataOffs: IntArray, strings: List<String>): ByteArray {
        val w = W()
        w.bytes("dex\n038\u0000".toByteArray(Charsets.US_ASCII))
        w.u32(0)
        repeat(20) { w.u8(0) }
        val fileSizePos = w.pos(); w.u32(0)
        w.u32(112); w.u32(0x12345678L); w.u32(0); w.u32(0); w.u32(0)
        w.u32(strings.size.toLong()); val sOffPos = w.pos(); w.u32(0)
        w.u32(4); val tOffPos = w.pos(); w.u32(0)
        w.u32(1); val pOffPos = w.pos(); w.u32(0)
        w.u32(1); val fOffPos = w.pos(); w.u32(0)
        w.u32(7); val mOffPos = w.pos(); w.u32(0)
        w.u32(1); val cOffPos = w.pos(); w.u32(0)
        val dataSizePos = w.pos(); w.u32(0); val dataOffPos = w.pos(); w.u32(0)
        assertEquals(112, w.pos())
        val stringIdsOff = w.pos()
        w.patchU32(sOffPos, stringIdsOff.toLong())
        repeat(strings.size) { w.u32(0) }
        val typeIdsOff = w.pos()
        w.patchU32(tOffPos, typeIdsOff.toLong())
        listOf(0, 1, 3, 5).forEach { w.u32(it.toLong()) }
        val protoIdsOff = w.pos()
        w.patchU32(pOffPos, protoIdsOff.toLong())
        w.u32(1); w.u32(1); w.u32(0)
        val fieldIdsOff = w.pos()
        w.patchU32(fOffPos, fieldIdsOff.toLong())
        w.u16(0); w.u16(2); w.u32(4)
        val methodIdsOff = w.pos()
        w.patchU32(mOffPos, methodIdsOff.toLong())
        listOf(2, 7, 8, 9, 10, 11, 12).forEach { w.u16(0); w.u16(0); w.u32(it.toLong()) }
        val classDefsOff = w.pos()
        w.patchU32(cOffPos, classDefsOff.toLong())
        w.u32(0); w.u32(0x1); w.u32(3); w.u32(0); w.u32(6); w.u32(0)
        val classDataOffPos = w.pos(); w.u32(0)
        w.u32(0)
        val dataOff = w.pos()
        strings.forEachIndexed { i, s ->
            // string_data harus di offset yang sama dengan putaran pertama:
            // layout deterministik, jadi stringDataOffs[i] cocok.
            assertEquals(stringDataOffs[i], w.pos())
            w.uleb(s.length)
            w.bytes(s.toByteArray(Charsets.UTF_8))
            w.u8(0)
        }
        repeat(strings.size) { i ->
            val bs = w.out.toByteArray()
            val at = stringIdsOff + i * 4
            val v = stringDataOffs[i].toLong()
            bs[at] = (v and 0xFF).toByte()
            bs[at + 1] = ((v ushr 8) and 0xFF).toByte()
            bs[at + 2] = ((v ushr 16) and 0xFF).toByte()
            bs[at + 3] = ((v ushr 24) and 0xFF).toByte()
            w.out.reset()
            w.out.write(bs)
        }
        val classDataOff = w.pos()
        w.patchU32(classDataOffPos, classDataOff.toLong())
        w.uleb(0); w.uleb(1); w.uleb(7); w.uleb(0)
        w.uleb(0); w.uleb(0x2)
        for (i in 0 until 7) {
            w.uleb(if (i == 0) 0 else 1)
            w.uleb(0x9)
            w.uleb(codeOffs[i])
        }
        val insnsList = listOf(
            intArrayOf(0x1012, 0x106e, 0x0000, 0x0000, 0x010a, 0x000e),
            intArrayOf(0x001a, 0x0007, 0x0038, 0x0003, 0x000e, 0x7012, 0x000e),
            intArrayOf(0x1052, 0x0000, 0x000e),
            intArrayOf(0x0073, 0x000e),
            intArrayOf(0x0090, 0x0201, 0x000e),
            intArrayOf(0x0274, 0x0000, 0x0000, 0x000e),
            intArrayOf(0x0013, 0xFFFB, 0x000e)
        )
        val regsList = intArrayOf(2, 1, 2, 1, 3, 2, 1)
        insnsList.forEachIndexed { i, insns ->
            w.pad4()
            assertEquals(codeOffs[i], w.pos())
            w.u16(regsList[i]); w.u16(1); w.u16(0); w.u16(0)
            w.u32(0)
            w.u32(insns.size.toLong())
            insns.forEach { w.u16(it) }
        }
        // Patch file_size, data_size, data_off, lalu hash.
        val bytes = w.out.toByteArray()
        val fileSize = bytes.size
        val dataSize = fileSize - dataOff
        fun patch32(at: Int, v: Long) {
            bytes[at] = (v and 0xFF).toByte()
            bytes[at + 1] = ((v ushr 8) and 0xFF).toByte()
            bytes[at + 2] = ((v ushr 16) and 0xFF).toByte()
            bytes[at + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        patch32(fileSizePos, fileSize.toLong())
        patch32(dataSizePos, dataSize.toLong())
        patch32(dataOffPos, dataOff.toLong())
        val sig = MessageDigest.getInstance("SHA-1").apply { update(bytes, 32, bytes.size - 32) }.digest()
        sig.copyInto(bytes, 12)
        val chk = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        patch32(8, chk)
        return bytes
    }

    private fun openFixture(): DexFile {
        val result = DexOps.open(buildTestDex())
        assertTrue(result is DexOps.OpenResult.Ok)
        return (result as DexOps.OpenResult.Ok).file
    }

    @Test
    fun headerParses() {
        val dex = openFixture()
        assertEquals("038", dex.header.version)
        assertEquals(13, dex.header.stringCount)
        assertEquals(4, dex.header.typeCount)
        assertEquals(1, dex.header.protoCount)
        assertEquals(1, dex.header.fieldCount)
        assertEquals(7, dex.header.methodCount)
        assertEquals(1, dex.header.classCount)
        assertTrue(dex.header.checksumOk)
        assertTrue(dex.header.signatureOk)
    }

    @Test
    fun checksumMismatchIsWarningNotError() {
        val bytes = buildTestDex()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val result = DexOps.open(bytes)
        assertTrue(result is DexOps.OpenResult.Ok)
        val dex = (result as DexOps.OpenResult.Ok).file
        assertFalse(dex.header.checksumOk)
        assertFalse(dex.header.signatureOk)
    }

    @Test
    fun badMagicRejected() {
        val bytes = buildTestDex()
        bytes[0] = 'x'.code.toByte()
        assertTrue(DexOps.open(bytes) is DexOps.OpenResult.Invalid)
    }

    @Test
    fun tooLargeRejected() {
        val big = ByteArray((DexOps.MAX_DEX_BYTES + 1).toInt())
        assertTrue(DexOps.open(big) is DexOps.OpenResult.TooLarge)
    }

    @Test
    fun classListAndDetail() {
        val dex = openFixture()
        assertEquals(listOf("com.example.Test"), dex.classNames)
        val detail = dex.classDetail(0)
        assertNotNull(detail)
        detail!!
        assertEquals("com.example.Test", detail.info.name)
        assertEquals("java.lang.Object", detail.info.superclass)
        assertEquals("Test.java", detail.info.sourceFile)
        assertTrue(detail.info.interfaces.isEmpty())
        assertEquals(1, detail.fields.size)
        val f = detail.fields[0]
        assertEquals("bar", f.name)
        assertEquals("int", f.type)
        assertEquals(0x2, f.accessFlags)
        assertFalse(f.isStatic)
        assertEquals(7, detail.methods.size)
        val foo = detail.methods[0]
        assertEquals("foo", foo.name)
        assertEquals("void", foo.returnType)
        assertTrue(foo.paramTypes.isEmpty())
        assertEquals("foo()void", foo.signature())
        assertEquals(0x9, foo.accessFlags)
        assertTrue(foo.codeOff != 0)
        assertTrue(foo.isDirect)
    }

    @Test
    fun methodSmaliFoo() {
        val dex = openFixture()
        val foo = dex.classDetail(0)!!.methods[0]
        val smali = dex.methodSmali(foo)
        assertFalse(smali.truncated)
        assertNull(smali.note)
        assertEquals(
            listOf(
                "0000: const/4 v0, #+1",
                "0001: invoke-virtual {v0}, com.example.Test.foo()void",
                "0004: move-result v1",
                "0005: return-void"
            ),
            smali.lines
        )
    }

    @Test
    fun methodSmaliBarBranchAndString() {
        val dex = openFixture()
        val bar = dex.classDetail(0)!!.methods[1]
        val smali = dex.methodSmali(bar)
        assertNull(smali.note)
        assertEquals(
            listOf(
                "0000: const-string v0, \"hi\"",
                "0002: if-eqz v0, 0005",
                "0004: return-void",
                "0005: const/4 v0, #+7",
                "0006: return-void"
            ),
            smali.lines
        )
    }

    @Test
    fun methodSmaliBazField() {
        val dex = openFixture()
        val baz = dex.classDetail(0)!!.methods[2]
        val smali = dex.methodSmali(baz)
        assertEquals(
            listOf(
                "0000: iget v0, v1, com.example.Test.bar:int",
                "0002: return-void"
            ),
            smali.lines
        )
    }

    @Test
    fun unknownOpcodeStopsCleanly() {
        val dex = openFixture()
        val qux = dex.classDetail(0)!!.methods[3]
        val smali = dex.methodSmali(qux)
        assertTrue(smali.lines.isEmpty())
        assertNotNull(smali.note)
        assertTrue(smali.note!!.contains("0x73"))
    }

    @Test
    fun methodSmaliAdd23x() {
        val dex = openFixture()
        val add = dex.classDetail(0)!!.methods[4]
        val smali = dex.methodSmali(add)
        assertNull(smali.note)
        assertEquals(
            listOf(
                "0000: add-int v0, v1, v2",
                "0002: return-void"
            ),
            smali.lines
        )
    }

    @Test
    fun methodSmaliRng3rc() {
        val dex = openFixture()
        val rng = dex.classDetail(0)!!.methods[5]
        val smali = dex.methodSmali(rng)
        assertNull(smali.note)
        assertEquals(
            listOf(
                "0000: invoke-virtual/range {v0..v1}, com.example.Test.foo()void",
                "0003: return-void"
            ),
            smali.lines
        )
    }

    @Test
    fun methodSmaliLitNegative() {
        val dex = openFixture()
        val lit = dex.classDetail(0)!!.methods[6]
        val smali = dex.methodSmali(lit)
        assertNull(smali.note)
        assertEquals(
            listOf(
                "0000: const/16 v0, #-5",
                "0002: return-void"
            ),
            smali.lines
        )
    }

    @Test
    fun descriptorFormatting() {
        assertEquals("com.example.Main", DexOps.formatDescriptor("Lcom/example/Main;"))
        assertEquals("int[][]", DexOps.formatDescriptor("[[I"))
        assertEquals("java.lang.String[]", DexOps.formatDescriptor("[Ljava/lang/String;"))
        assertEquals("boolean", DexOps.formatDescriptor("Z"))
        assertEquals("void", DexOps.formatDescriptor("V"))
    }

    @Test
    fun uleb128Decoding() {
        val bytes = byteArrayOf(0x80.toByte(), 0x01, 0xE5.toByte(), 0x8E.toByte(), 0x26)
        assertEquals(128L to 2, DexOps.readUleb128(bytes, 0))
        assertEquals(624485L to 5, DexOps.readUleb128(bytes, 2))
        assertNull(DexOps.readUleb128(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01), 0))
    }

    @Test
    fun mutf8Decoding() {
        // string_data: utf16_size=1, C0 80 (NUL), 00
        val nul = byteArrayOf(0x01, 0xC0.toByte(), 0x80.toByte(), 0x00)
        assertEquals("\u0000", DexOps.decodeMutf8(nul, 0))
        // U+1F600 sebagai pasangan surrogate: ED A0 BD ED B8 80
        val emoji = byteArrayOf(
            0x02, 0xED.toByte(), 0xA0.toByte(), 0xBD.toByte(),
            0xED.toByte(), 0xB8.toByte(), 0x80.toByte(), 0x00
        )
        val decoded = DexOps.decodeMutf8(emoji, 0)
        assertEquals("\uD83D\uDE00", decoded)
        // tanpa terminator -> null
        assertNull(DexOps.decodeMutf8(byteArrayOf(0x01, 0x61), 0))
    }

    @Test
    fun accessFlagsFormatting() {
        assertEquals("public static final", DexOps.formatAccessFlags(0x1 or 0x8 or 0x10))
        assertEquals("private", DexOps.formatAccessFlags(0x2))
        assertEquals("", DexOps.formatAccessFlags(0))
    }

    @Test
    fun opcodeTableComplete() {
        assertEquals(256, OPCODE_TABLE.size)
        assertEquals("nop", OPCODE_TABLE[0x00].name)
        assertEquals("return-void", OPCODE_TABLE[0x0E].name)
        assertEquals("invoke-virtual", OPCODE_TABLE[0x6E].name)
        assertEquals("const-string", OPCODE_TABLE[0x1A].name)
        assertEquals("invoke-polymorphic", OPCODE_TABLE[0xFA].name)
        assertEquals("const-method-type", OPCODE_TABLE[0xFF].name)
        assertEquals("(unused)", OPCODE_TABLE[0x73].name)
        assertEquals(RefKind.METHOD, OPCODE_TABLE[0x6E].ref)
        assertEquals(RefKind.STRING, OPCODE_TABLE[0x1A].ref)
        assertEquals(RefKind.FIELD, OPCODE_TABLE[0x52].ref)
        assertEquals(RefKind.BRANCH, OPCODE_TABLE[0x28].ref)
    }
}
