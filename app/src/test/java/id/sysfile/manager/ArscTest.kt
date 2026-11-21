package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * r235: pembaca .arsc (AR3) — fixture .arsc sintetis minimal yang
 * dibangun tangan (table + global pool UTF-8/UTF-16 + 1 package +
 * 3 tipe: string dense, color OFFSET16 + compact, style SPARSE +
 * kompleks). Parse 6 berkas .arsc ASLI (cross-check pyarsc.py) hanya
 * di rig (~/workspace/jvtest/r190/ArscTest.kt), bukan di sini.
 *
 * Fixture sintetis:
 * - global: ["hello", "world"] (UTF-8; varian UTF-16 ["héllo","world"])
 * - type pool: ["string","color","style"], key pool:
 *   ["app_name","greeting","accent","MyStyle"]
 * - string (id 1): default en-rUS-port-hdpi-v33 — e0 app_name -> "hello",
 *   e1 greeting -> "world", e2 greeting -> @color/accent (0x7F020000);
 *   varian land en-rUS-land — e0 app_name -> "world"
 * - color (id 2, OFFSET16): e0 accent -> #FF123456 (ARGB8);
 *   e1 accent (compact) -> true
 * - style (id 3, SPARSE): e0 MyStyle kompleks, parent 0, 1 map
 *   (nameRef 0x01000000 -> "type", INT_DEC 4)
 */
class ArscTest {

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

        fun u32i(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)
        fun bytes(bs: ByteArray) = out.write(bs)
    }

    private fun writeLen8(w: W, len: Int) {
        if (len < 0x80) w.u8(len)
        else {
            w.u8((len ushr 8) or 0x80)
            w.u8(len and 0xFF)
        }
    }

    private fun writeLen16(w: W, len: Int) {
        if (len < 0x8000) w.u16(len)
        else {
            w.u16((len ushr 16) or 0x8000)
            w.u16(len and 0xFFFF)
        }
    }

    /** String pool: dua panjang untuk UTF-8 (utf16 lalu utf8), satu u16 untuk UTF-16. */
    private fun stringPool(strings: List<String>, utf8: Boolean): ByteArray {
        val dataW = W()
        val offsets = ArrayList<Int>()
        for (s in strings) {
            offsets.add(dataW.pos())
            if (utf8) {
                val b = s.toByteArray(Charsets.UTF_8)
                writeLen8(dataW, s.length)
                writeLen8(dataW, b.size)
                dataW.bytes(b)
                dataW.u8(0)
            } else {
                writeLen16(dataW, s.length)
                for (ch in s) dataW.u16(ch.code)
                dataW.u16(0)
            }
        }
        val dataStart = 28 + strings.size * 4
        var size = dataStart + dataW.pos()
        val pad = (4 - size % 4) % 4
        size += pad
        val w = W()
        w.u16(0x0001)
        w.u16(28)
        w.u32(size.toLong())
        w.u32(strings.size.toLong())
        w.u32(0)
        w.u32(if (utf8) 0x100 else 0)
        w.u32(dataStart.toLong())
        w.u32(0)
        for (o in offsets) w.u32(o.toLong())
        w.bytes(dataW.out.toByteArray())
        repeat(pad) { w.u8(0) }
        return w.out.toByteArray()
    }

    /** Config 28 byte: mcc/mnc 0, bahasa/region 2 byte, orientasi/density/sdk. */
    private fun config28(
        lang0: Int, lang1: Int, reg0: Int, reg1: Int,
        orientation: Int, density: Int, sdk: Int
    ): ByteArray {
        val w = W()
        w.u32(28)
        w.u16(0)
        w.u16(0)
        w.u8(lang0)
        w.u8(lang1)
        w.u8(reg0)
        w.u8(reg1)
        w.u8(orientation)
        w.u8(0)
        w.u16(density)
        w.u8(0)
        w.u8(0)
        w.u8(0)
        w.u8(0)
        w.u16(0)
        w.u16(0)
        w.u16(sdk)
        w.u16(0)
        return w.out.toByteArray()
    }

    private fun simpleEntry(key: Int, dataType: Int, data: Int): ByteArray {
        val w = W()
        w.u16(8)
        w.u16(0)
        w.u32(key.toLong())
        w.u16(8)
        w.u8(0)
        w.u8(dataType)
        w.u32i(data)
        return w.out.toByteArray()
    }

    private fun compactEntry(key: Int, dataType: Int, data: Int): ByteArray {
        val w = W()
        w.u16(key)
        w.u16(0x0008 or (dataType shl 8))
        w.u32i(data)
        return w.out.toByteArray()
    }

    private fun complexEntry(
        key: Int, parentRef: Int, maps: List<Triple<Int, Int, Int>>
    ): ByteArray {
        val w = W()
        w.u16(16)
        w.u16(0x0001)
        w.u32(key.toLong())
        w.u32i(parentRef)
        w.u32(maps.size.toLong())
        for ((nameRef, dataType, data) in maps) {
            w.u32i(nameRef)
            w.u16(8)
            w.u8(0)
            w.u8(dataType)
            w.u32i(data)
        }
        return w.out.toByteArray()
    }

    private fun typeSpec(id: Int, typesCount: Int, entryCount: Int): ByteArray {
        val w = W()
        w.u16(0x0202)
        w.u16(16)
        w.u32((16 + entryCount * 4).toLong())
        w.u8(id)
        w.u8(0)
        w.u16(typesCount)
        w.u32(entryCount.toLong())
        repeat(entryCount) { w.u32(0) }
        return w.out.toByteArray()
    }

    /**
     * Chunk TYPE. [offsets] = u32 dense / u32 raw sparse (idx | off<<16) /
     * u16 OFFSET16 (off/4); [offsetBytes] = ukuran tiap elemen array offset.
     */
    private fun typeChunk(
        id: Int, flags: Int, config: ByteArray, entryCount: Int,
        offsets: List<Int>, offsetBytes: Int, entries: ByteArray
    ): ByteArray {
        val w = W()
        val headerSize = 20 + config.size
        val entriesStart = headerSize + entryCount * offsetBytes
        w.u16(0x0201)
        w.u16(headerSize)
        w.u32((entriesStart + entries.size).toLong())
        w.u8(id)
        w.u8(flags)
        w.u16(0)
        w.u32(entryCount.toLong())
        w.u32(entriesStart.toLong())
        w.bytes(config)
        for (o in offsets) {
            if (offsetBytes == 2) w.u16(o) else w.u32(o.toLong())
        }
        w.bytes(entries)
        return w.out.toByteArray()
    }

    private fun buildSynthetic(
        utf8Global: Boolean = true,
        globals: List<String> = listOf("hello", "world")
    ): ByteArray {
        val globalPool = stringPool(globals, utf8Global)
        val typePool = stringPool(listOf("string", "color", "style"), true)
        val keyPool = stringPool(
            listOf("app_name", "greeting", "accent", "MyStyle"), true
        )
        val cfgDefault = config28('e'.code, 'n'.code, 'U'.code, 'S'.code, 1, 240, 33)
        val cfgLand = config28('e'.code, 'n'.code, 'U'.code, 'S'.code, 2, 0, 0)
        val cfgPlain = config28(0, 0, 0, 0, 0, 0, 0)
        val t1Entries = simpleEntry(0, 0x03, 0) +
            simpleEntry(1, 0x03, 1) +
            simpleEntry(1, 0x01, 0x7F020000)
        val t1Default = typeChunk(1, 0, cfgDefault, 3, listOf(0, 16, 32), 4, t1Entries)
        val t1Land = typeChunk(
            1, 0, cfgLand, 1, listOf(0), 4, simpleEntry(0, 0x03, 1)
        )
        val t2Entries = simpleEntry(2, 0x1C, 0xFF123456.toInt()) +
            compactEntry(2, 0x12, 1)
        val t2 = typeChunk(2, 0x02, cfgPlain, 2, listOf(0, 4), 2, t2Entries)
        val t3Entries = complexEntry(3, 0, listOf(Triple(0x01000000, 0x10, 4)))
        val t3 = typeChunk(3, 0x01, cfgPlain, 1, listOf(0), 4, t3Entries)
        val children = typePool + keyPool +
            typeSpec(1, 2, 3) + t1Default + t1Land +
            typeSpec(2, 1, 2) + t2 +
            typeSpec(3, 1, 1) + t3
        // typeIdOffset = 0: id tipe mulai dari 1, nama = typeNames[id - 1].
        // (Bukan byte offset — AOSP: typeStr index = typeIndex - typeIdOffset.)
        val pkgSize = 288 + children.size
        val w = W()
        w.u16(0x0200)
        w.u16(288)
        w.u32(pkgSize.toLong())
        w.u32(127)
        val name = "com.test"
        for (i in 0 until 128) w.u16(if (i < name.length) name[i].code else 0)
        w.u32(288)
        w.u32(0)
        w.u32((288 + typePool.size).toLong())
        w.u32(0)
        w.u32(0)
        w.bytes(children)
        val pkg = w.out.toByteArray()
        val total = 12 + globalPool.size + pkg.size
        val t = W()
        t.u16(0x0002)
        t.u16(12)
        t.u32(total.toLong())
        t.u32(1)
        t.bytes(globalPool)
        t.bytes(pkg)
        return t.out.toByteArray()
    }

    private fun openSynthetic(
        utf8Global: Boolean = true,
        globals: List<String> = listOf("hello", "world")
    ): ArscFile {
        val bytes = buildSynthetic(utf8Global, globals)
        val result = ArscOps.open(bytes)
        assertTrue("expected Ok, got $result", result is ArscOps.OpenResult.Ok)
        return (result as ArscOps.OpenResult.Ok).file
    }

    // ---- fixture sintetis ----

    @Test
    fun syntheticHeaderAndCounts() {
        val file = openSynthetic()
        assertEquals(1, file.header.packageCount)
        assertEquals(2, file.header.globalStringCount)
        assertTrue(file.header.globalUtf8)
        assertEquals(1, file.packages.size)
        val pkg = file.packages[0]
        assertEquals(127, pkg.id)
        assertEquals("com.test", pkg.name)
        assertEquals(3, file.totalTypes)
        // string 3+1, color 2, style 1
        assertEquals(7, file.totalEntries)
        assertEquals(3, pkg.types.size)
        assertEquals("string", pkg.types[0].name)
        assertEquals("color", pkg.types[1].name)
        assertEquals("style", pkg.types[2].name)
    }

    @Test
    fun syntheticStringDecodeAndVariants() {
        val file = openSynthetic()
        val type = file.packages[0].types.first { it.name == "string" }
        assertEquals(2, type.variants.size)
        assertEquals("en-rUS-port-hdpi-v33", type.variants[0].config.qualifier)
        assertEquals("en-rUS-land", type.variants[1].config.qualifier)
        val appName = type.groups.first { it.key == "app_name" }
        assertEquals(2, appName.variants.size)
        assertEquals(0x7F010000, appName.resId)
        assertEquals("hello", appName.variants[0].entry.display)
        assertEquals("world", appName.variants[1].entry.display)
        val greeting = type.groups.first { it.index == 1 }
        assertEquals(1, greeting.variants.size)
        assertEquals(0x7F010001, greeting.resId)
        assertEquals("world", greeting.variants[0].entry.display)
    }

    @Test
    fun syntheticReferenceAndColor() {
        val file = openSynthetic()
        val stringType = file.packages[0].types.first { it.name == "string" }
        // Grup greeting ada dua (indeks 1 = string, indeks 2 = referensi).
        val refGroup = stringType.groups.first { it.index == 2 }
        assertEquals("@color/accent", refGroup.variants[0].entry.display)
        assertEquals("@color/accent", file.resolveRef(0x7F020000))
        assertNull(file.resolveRef(0x01010000)) // package android tidak ada
        val colorType = file.packages[0].types.first { it.name == "color" }
        val accent = colorType.groups.first { it.index == 0 }
        assertEquals(1, accent.variants.size)
        assertEquals("#FF123456", accent.variants[0].entry.display)
        assertFalse(accent.variants[0].entry.isCompact)
        // Entri compact (indeks 1) adalah grup sendiri.
        val accentCompact = colorType.groups.first { it.index == 1 }
        assertTrue(accentCompact.variants[0].entry.isCompact)
        assertEquals("true", accentCompact.variants[0].entry.display)
    }

    @Test
    fun syntheticCompactAndComplex() {
        val file = openSynthetic()
        val colorType = file.packages[0].types.first { it.name == "color" }
        val compact = colorType.groups.first { it.index == 1 }.variants[0].entry
        assertTrue(compact.isCompact)
        assertEquals("true", compact.display)
        val styleType = file.packages[0].types.first { it.name == "style" }
        val style = styleType.groups.first { it.key == "MyStyle" }
        val entry = style.variants[0].entry
        assertTrue(entry.isComplex)
        assertEquals("", entry.display)
        assertEquals(1, entry.maps.size)
        assertEquals("type", file.mapName(entry.maps[0].nameRef))
        assertEquals("4", file.formatValue(entry.maps[0].value))
    }

    @Test
    fun syntheticSearch() {
        val file = openSynthetic()
        val hello = file.search("hello")
        assertEquals(1, hello.size)
        assertEquals("app_name", hello[0].group.key)
        val world = file.search("WORLD") // case-insensitive
        assertEquals(2, world.size)
        assertTrue(file.search("zzz_no_such").isEmpty())
        assertTrue(file.search("  ").isEmpty())
    }

    @Test
    fun syntheticUtf16Pool() {
        val file = openSynthetic(
            utf8Global = false, globals = listOf("héllo", "world")
        )
        assertFalse(file.header.globalUtf8)
        assertEquals("héllo", file.globalStringAt(0))
        val type = file.packages[0].types.first { it.name == "string" }
        val appName = type.groups.first { it.key == "app_name" }
        assertEquals("héllo", appName.variants[0].entry.display)
        assertEquals("world", appName.variants[1].entry.display)
    }

    @Test
    fun syntheticPackedLanguageAndRegion() {
        // "fil" -> packLanguageOrRegion AOSP: out[0]=0xB1, out[1]=0x26.
        // "419" -> out[0]=0xA8, out[1]=0x45.
        assertEquals("fil", ArscOps.decodeLanguage(0xB1.toByte(), 0x26.toByte()))
        assertEquals("419", ArscOps.decodeRegion(0xA8.toByte(), 0x45.toByte()))
        assertEquals("en", ArscOps.decodeLanguage('e'.code.toByte(), 'n'.code.toByte()))
        assertNull(ArscOps.decodeLanguage(0, 0))
    }

    @Test
    fun invalidInputs() {
        assertTrue(ArscOps.open(ByteArray(0)) is ArscOps.OpenResult.Invalid)
        assertTrue(ArscOps.open("bukan arsc".toByteArray()) is ArscOps.OpenResult.Invalid)
        val bytes = buildSynthetic()
        val badMagic = bytes.copyOf().also { it[0] = 0x99.toByte() }
        assertTrue(ArscOps.open(badMagic) is ArscOps.OpenResult.Invalid)
        assertTrue(ArscOps.open(bytes.copyOf(bytes.size / 2)) is ArscOps.OpenResult.Invalid)
        // Seperti DexOps: file tak ada / bukan file -> TooLarge.
        assertTrue(ArscOps.open(File("/tidak/ada.arsc")) is ArscOps.OpenResult.TooLarge)
    }

    @Test
    fun tooLarge() {
        val result = ArscOps.open(ByteArray((ArscOps.MAX_ARSC_BYTES + 1).toInt()))
        assertTrue(result is ArscOps.OpenResult.TooLarge)
    }
}
