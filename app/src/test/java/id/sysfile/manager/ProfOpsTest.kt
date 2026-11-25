package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

class ProfOpsTest {

    private fun deflateBytes(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    @Test
    fun parseV010ProfileDecodesDexEntryAndMetadata() {
        val nameBytes = "classes.dex".toByteArray(Charsets.UTF_8)
        val inner = ByteBuffer.allocate(16 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(nameBytes.size.toShort()) // profileKeySize
            putShort(4) // typeIdSetSize
            putInt(128) // hotMethodRegionSize
            putInt(0x12345678) // dexChecksum
            putInt(512) // numMethodIds
            put(nameBytes)
        }.array()
        val compressed = deflateBytes(inner)

        val header = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf('p'.code.toByte(), 'r'.code.toByte(), 'o'.code.toByte(), 0))
            put(byteArrayOf('0'.code.toByte(), '1'.code.toByte(), '0'.code.toByte(), 0))
            put(1.toByte()) // numDexFiles
            putInt(inner.size)
            putInt(compressed.size)
        }.array()

        val result = ProfOps.parse(header + compressed)
        assertTrue(result is ProfOps.OpenResult.ArtProfile)
        val info = (result as ProfOps.OpenResult.ArtProfile).info
        assertFalse(info.isMetadata)
        assertEquals("010", info.versionCode)
        assertEquals(1, info.dexCountDeclared)
        assertEquals(1, info.dexEntries.size)
        assertEquals("classes.dex", info.dexEntries[0].name)
        assertEquals("0x12345678", info.dexEntries[0].checksumHex)
        assertEquals(512L, info.dexEntries[0].methodIdsCount)
    }

    @Test
    fun parseV002ProfmMetadataScansZlibSection() {
        val compressed = deflateBytes("base.apk!classes2.dex\u0000".toByteArray(Charsets.UTF_8))
        val header = byteArrayOf(
            'p'.code.toByte(), 'r'.code.toByte(), 'm'.code.toByte(), 0,
            '0'.code.toByte(), '0'.code.toByte(), '2'.code.toByte(), 0,
            0, 0, 0, 0
        )
        val result = ProfOps.parse(header + compressed)
        assertTrue(result is ProfOps.OpenResult.ArtProfile)
        val info = (result as ProfOps.OpenResult.ArtProfile).info
        assertTrue(info.isMetadata)
        assertEquals("002", info.versionCode)
        assertEquals(1, info.zlibStreamCount)
        assertTrue(info.dexEntries.any { it.name.contains("classes2.dex") })
    }

    @Test
    fun parsePlainTextProfFallsBackToPlainText() {
        val text = "HSPLcom/example/MainActivity;->onCreate()V\n".toByteArray(Charsets.UTF_8)
        assertEquals(ProfOps.OpenResult.PlainText, ProfOps.parse(text))
    }

    @Test
    fun parseRandomBinaryFallsBackToHex() {
        val bin = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0, 1, 2)
        assertEquals(ProfOps.OpenResult.BinaryFallback, ProfOps.parse(bin))
    }

    @Test
    fun textExtensionsIncludeListSha256AndMf() {
        assertTrue(FileOps.isTextFile(File("system.transfer.list")))
        assertTrue(FileOps.isTextFile(File("firmware.sha256")))
        assertTrue(FileOps.isTextFile(File("MANIFEST.MF")))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("system.transfer.list"))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("firmware.sha256"))
        assertEquals(Category.DOCUMENT, FileOps.categoryOf("MANIFEST.MF"))
        assertFalse(FileOps.isTextFile(File("baseline.prof")))
        assertFalse(FileOps.isTextFile(File("baseline.profm")))
    }
}
