package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Unit tests for the minimal EPUB reader (BC2). JVM-only: the EPUB is
 *  built in the test from a ZIP, no Android APIs. */
class EpubTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeEpub(
        file: File,
        withNcx: Boolean = true,
        withContainer: Boolean = true,
        spine: List<String> = listOf("ch1.xhtml", "ch2.xhtml")
    ) {
        // Id berbasis indeks: substringBefore('.') menghasilkan id KOSONG
        // untuk href yang diawali '.' (mis. "../../evil.xhtml").
        val opfItems = spine.mapIndexed { idx, href ->
            "    <item id=\"item$idx\" href=\"$href\" media-type=\"application/xhtml+xml\"/>"
        }.joinToString("\n")
        val opfSpine = spine.indices.joinToString("\n") { idx ->
            "    <itemref idref=\"item$idx\"/>"
        }
        val ncxItem = if (withNcx) {
            "    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
        } else ""
        val ncxPoints = spine.mapIndexed { idx, href ->
            "    <navPoint id=\"np${idx + 1}\" playOrder=\"${idx + 1}\">" +
                "<navLabel><text>Bab ${listOf("Satu", "Dua", "Tiga")[idx]}</text></navLabel>" +
                "<content src=\"$href\"/></navPoint>"
        }.joinToString("\n")
        val entries = mutableListOf<Pair<String, String>>()
        if (withContainer) {
            entries += "META-INF/container.xml" to
                "<?xml version=\"1.0\"?><container version=\"1.0\" " +
                "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
                "media-type=\"application/oebps-package+xml\"/></rootfiles></container>"
        }
        entries += "OEBPS/content.opf" to
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"id\">" +
            "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<dc:title>Buku Tes</dc:title></metadata><manifest>\n$opfItems\n$ncxItem\n</manifest>" +
            "<spine toc=\"ncx\">\n$opfSpine\n</spine></package>"
        for (href in spine) {
            // Href jahat ("..") hanya ada di teks OPF, bukan sebagai berkas
            // di ZIP — kalau ikut jadi entri, penjaga zip-slip unzip menolak
            // seluruh ekstraksi dan open() mengembalikan null.
            if (".." in href) continue
            entries += "OEBPS/$href" to
                "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                "<head><title>$href</title></head><body><p>Isi $href</p></body></html>"
        }
        if (withNcx) {
            entries += "OEBPS/toc.ncx" to
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" " +
                "\"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">" +
                "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">" +
                "<navMap>\n$ncxPoints\n</navMap></ncx>"
        }
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    @Test
    fun open_spineOrderAndNcxTitles() {
        val epub = File(tmp.root, "buku.epub")
        makeEpub(epub)
        val dest = File(tmp.root, "out")
        val book = EpubOps.open(epub, dest)
        assertTrue(book != null)
        assertEquals("Buku Tes", book!!.title)
        assertEquals("OEBPS", book.opfDir)
        assertEquals(listOf("Bab Satu", "Bab Dua"), book.chapters.map { it.title })
        assertEquals(listOf("ch1.xhtml", "ch2.xhtml"), book.chapters.map { it.href })
        assertTrue(File(dest, "OEBPS/ch1.xhtml").isFile)
    }

    @Test
    fun open_withoutNcx_usesFilenames() {
        val epub = File(tmp.root, "buku.epub")
        makeEpub(epub, withNcx = false)
        val book = EpubOps.open(epub, File(tmp.root, "out"))
        assertTrue(book != null)
        assertEquals(listOf("ch1", "ch2"), book!!.chapters.map { it.title })
    }

    @Test
    fun openRejectsOversizedOpf() {
        val file = File(tmp.root, "bigopf.epub")
        val bigOpf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"id\">" +
            "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" +
            "x".repeat(1024 * 1024 + 1) +
            "</dc:title></metadata><manifest>" +
            "<item id=\"item0\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>" +
            "</manifest><spine><itemref idref=\"item0\"/></spine></package>"
        // ZIP dibuat langsung (makeEpub tidak menyediakan OPF sebesar ini).
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write((
                "<?xml version=\"1.0\"?><container version=\"1.0\" " +
                "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
                "media-type=\"application/oebps-package+xml\"/></rootfiles></container>"
            ).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(bigOpf.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write("<html><body>Bab</body></html>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val dest = File(tmp.root, "bigopf-out")
        assertNull(EpubOps.open(file, dest))
    }

    @Test
    fun openOversizedNcxFallsBackToFileNames() {
        val file = File(tmp.root, "bigncx.epub")
        val bigNcx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">" +
            "<head></head><docTitle><text>" + "x".repeat(1024 * 1024 + 1) + "</text></docTitle>" +
            "<navMap></navMap></ncx>"
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write((
                "<?xml version=\"1.0\"?><container version=\"1.0\" " +
                "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
                "media-type=\"application/oebps-package+xml\"/></rootfiles></container>"
            ).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write((
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"id\">" +
                "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>T</dc:title></metadata>" +
                "<manifest><item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>" +
                "<item id=\"item0\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/></manifest>" +
                "<spine toc=\"ncx\"><itemref idref=\"item0\"/></spine></package>"
            ).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zos.write(bigNcx.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write("<html><body>Bab</body></html>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val dest = File(tmp.root, "bigncx-out")
        val book = EpubOps.open(file, dest)
        assertNotNull(book)
        assertEquals("ch1", book!!.chapters[0].title)
    }

    @Test
    fun open_missingContainer_returnsNull() {
        val epub = File(tmp.root, "buku.epub")
        makeEpub(epub, withContainer = false)
        assertNull(EpubOps.open(epub, File(tmp.root, "out")))
    }

    @Test
    fun open_notAZip_returnsNull() {
        val epub = File(tmp.root, "bukan.epub")
        epub.writeText("ini bukan zip")
        assertNull(EpubOps.open(epub, File(tmp.root, "out")))
    }

    @Test
    fun chapterFile_escapingHref_returnsNull() {
        val epub = File(tmp.root, "jahat.epub")
        makeEpub(epub, spine = listOf("../../evil.xhtml", "ch2.xhtml"))
        val dest = File(tmp.root, "out")
        val book = EpubOps.open(epub, dest)
        assertTrue(book != null)
        assertNull(EpubOps.chapterFile(dest, book!!, book.chapters[0]))
        assertTrue(EpubOps.chapterFile(dest, book, book.chapters[1]) != null)
    }

    @Test
    fun decodeHref_percentEncoded() {
        assertEquals("bab 1.xhtml", EpubOps.decodeHref("bab%201.xhtml"))
        assertEquals("a+b.xhtml", EpubOps.decodeHref("a+b.xhtml"))
        assertEquals("plain.xhtml", EpubOps.decodeHref("plain.xhtml"))
    }
}
