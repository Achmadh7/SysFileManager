package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Unit tests for FontOps (TTF name-table parsing). JVM-only: the test
 * builds minimal synthetic TTF files (offset table + name table) —
 * no real font file needed.
 */
class FontOpsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class Rec(val platform: Int, val encoding: Int, val nameId: Int, val bytes: ByteArray)

    /** TTF minimal: offset table + 1 tabel (tag "name", atau "cmap" bila tanpa tabel nama). */
    private fun buildTtf(records: List<Rec>, withNameTable: Boolean = true): File {
        val stringBytes = records.map { it.bytes }
        val stringOffset = 6 + 12 * records.size
        val tableLen = stringOffset + stringBytes.sumOf { it.size }
        val buf = ByteBuffer.allocate(12 + 16 + tableLen).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(0x00010000) // sfnt version TrueType
        buf.putShort(1) // numTables
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(0)
        buf.put(if (withNameTable) "name".toByteArray(Charsets.US_ASCII) else "cmap".toByteArray(Charsets.US_ASCII))
        buf.putInt(0) // checksum (diabaikan parser)
        buf.putInt(28) // offset tabel
        buf.putInt(tableLen)
        buf.putShort(0) // format
        buf.putShort(records.size.toShort()) // count
        buf.putShort(stringOffset.toShort())
        var off = 0
        for (r in records) {
            buf.putShort(r.platform.toShort())
            buf.putShort(r.encoding.toShort())
            buf.putShort(0x409) // languageID
            buf.putShort(r.nameId.toShort())
            buf.putShort(r.bytes.size.toShort())
            buf.putShort(off.toShort())
            off += r.bytes.size
        }
        for (b in stringBytes) buf.put(b)
        val f = File(tmp.root, "test.ttf")
        f.writeBytes(buf.array())
        return f
    }

    @Test
    fun familyAndStyle_windowsUnicode_parsed() {
        val f = buildTtf(listOf(
            Rec(3, 1, 1, "TestFont".toByteArray(Charsets.UTF_16BE)),
            Rec(3, 1, 2, "Bold".toByteArray(Charsets.UTF_16BE))
        ))
        assertEquals("TestFont", FontOps.familyName(f))
        assertEquals("Bold", FontOps.styleName(f))
    }

    @Test
    fun family_macRoman_fallbackParsed_styleNull() {
        val f = buildTtf(listOf(
            Rec(1, 0, 1, "MacFont".toByteArray(Charsets.ISO_8859_1))
        ))
        assertEquals("MacFont", FontOps.familyName(f))
        assertNull(FontOps.styleName(f))
    }

    @Test
    fun missingNameTable_returnsNull() {
        val f = buildTtf(emptyList(), withNameTable = false)
        assertNull(FontOps.familyName(f))
        assertNull(FontOps.styleName(f))
    }

    @Test
    fun garbageFile_returnsNull() {
        val f = File(tmp.root, "junk.ttf")
        f.writeBytes("abcdefghijklmnopqrst".toByteArray(Charsets.US_ASCII))
        assertNull(FontOps.familyName(f))
        assertNull(FontOps.styleName(f))
    }
}
