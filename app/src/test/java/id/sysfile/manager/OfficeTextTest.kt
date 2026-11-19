package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Unit tests for Office text extraction (BC3). JVM-only: minimal
 *  office files are built in the test from ZIPs, no Android APIs. */
class OfficeTextTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeZip(file: File, entries: List<Pair<String, String>>) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    @Test
    fun extractDocx_paragraphsAndTab() {
        val file = File(tmp.root, "dok.docx")
        makeZip(file, listOf(
            "word/document.xml" to
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                "<w:body>" +
                "<w:p><w:r><w:t>Hello</w:t></w:r><w:r><w:t> world</w:t></w:r></w:p>" +
                "<w:p><w:r><w:t>Baris</w:t></w:r><w:r><w:tab/><w:t>dua</w:t></w:r></w:p>" +
                "</w:body></w:document>"
        ))
        assertEquals("Hello world\nBaris\tdua", OfficeText.extract(file))
    }

    @Test
    fun extractXlsx_sharedStringsAndInline() {
        val file = File(tmp.root, "data.xlsx")
        makeZip(file, listOf(
            "xl/workbook.xml" to
                "<?xml version=\"1.0\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<sheets><sheet name=\"Data\" sheetId=\"1\"/></sheets></workbook>",
            "xl/sharedStrings.xml" to
                "<?xml version=\"1.0\"?>" +
                "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"2\" uniqueCount=\"2\">" +
                "<si><t>Nama</t></si><si><t>Nilai</t></si></sst>",
            "xl/worksheets/sheet1.xml" to
                "<?xml version=\"1.0\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<sheetData>" +
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                "<row r=\"2\"><c r=\"A2\"><v>42</v></c>" +
                "<c r=\"B2\" t=\"inlineStr\"><is><t>x</t></is></c></row>" +
                "</sheetData></worksheet>"
        ))
        assertEquals("== Data ==\nNama\tNilai\n42\tx", OfficeText.extract(file))
    }

    @Test
    fun extractPptx_slidesInOrder() {
        val file = File(tmp.root, "pres.pptx")
        fun slide(text: String, second: String? = null): String {
            val secondPara = if (second == null) "" else
                "<a:p><a:r><a:t>$second</a:t></a:r></a:p>"
            return "<?xml version=\"1.0\"?>" +
                "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">" +
                "<p:cSld><p:spTree><p:sp><p:txBody>" +
                "<a:p><a:r><a:t>$text</a:t></a:r></a:p>$secondPara" +
                "</p:txBody></p:sp></p:spTree></p:cSld></p:sld>"
        }
        makeZip(file, listOf(
            "ppt/slides/slide1.xml" to slide("Slide", "Baris dua"),
            "ppt/slides/slide2.xml" to slide("Slide dua")
        ))
        // "Slide" + "Baris dua" di slide 1 (paragraf terpisah), slide 2 setelahnya.
        val result = OfficeText.extract(file)
        assertTrue(result != null)
        assertEquals("Slide\nBaris dua\n\nSlide dua", result)
    }

    @Test
    fun extractOdt_headingsAndParagraphs() {
        val file = File(tmp.root, "dok.odt")
        makeZip(file, listOf(
            "content.xml" to
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<office:document-content " +
                "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" " +
                "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">" +
                "<office:body><office:text>" +
                "<text:h text:outline-level=\"1\">Judul</text:h>" +
                "<text:p>Paragraf satu.</text:p>" +
                "<text:p>Paragraf dua.</text:p>" +
                "</office:text></office:body></office:document-content>"
        ))
        assertEquals("Judul\nParagraf satu.\nParagraf dua.", OfficeText.extract(file))
    }

    @Test
    fun extract_oldDoc_returnsNull() {
        val file = File(tmp.root, "lama.doc")
        file.writeText("bukan zip")
        assertNull(OfficeText.extract(file))
    }

    @Test
    fun extract_corruptDocx_returnsNull() {
        val file = File(tmp.root, "rusak.docx")
        file.writeText("ini bukan zip")
        assertNull(OfficeText.extract(file))
    }

    @Test
    fun extract_docxWithoutDocument_returnsNull() {
        val file = File(tmp.root, "kosong.docx")
        makeZip(file, listOf("word/other.xml" to "<w:document/>"))
        assertNull(OfficeText.extract(file))
    }
}
