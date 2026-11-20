package id.sysfile.manager

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/** Ekstraksi teks dari dokumen Office modern (BC3, v1): DOCX / XLSX /
 *  PPTX / ODT — semuanya ZIP + XML. Entri ZIP dibaca langsung (tanpa
 *  diekstrak ke disk, jadi tidak ada risiko zip-slip); XML diparse
 *  dengan newSafeDocumentBuilder() (anti-XXE, dari EpubOps.kt).
 *  Hasilnya teks polos READ-ONLY (ditampilkan lewat TextViewerScreen
 *  mode read-only — mengedit teks hasil ekstrak akan merusak berkas).
 *  Format lama .doc/.xls/.ppt TIDAK didukung (butuh Apache POI) dan
 *  tetap dilempar ke aplikasi luar; extract() mengembalikan null untuk
 *  ekstensi yang tidak didukung atau berkas rusak. */
object OfficeText {

    /** M8: batas XML yang di-parse ke DOM. ZipEntry.size bisa -1
     *  (streamed) dan bisa dipalsukan, jadi selain cek deklarasi,
     *  baca dibatasi max+1 byte dan ditolak bila lebih — teks apa pun
     *  tidak butuh XML lebih dari ini. */
    const val MAX_XML_BYTES = 4 * 1024 * 1024L

    fun extract(file: File): String? {
        return try {
            when (FileOps.extOf(file.name)) {
                "docx" -> extractDocx(file)
                "xlsx" -> extractXlsx(file)
                "pptx" -> extractPptx(file)
                "odt" -> extractOdt(file)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readEntry(zip: ZipFile, name: String): Element? {
        val entry = zip.getEntry(name) ?: return null
        // M8: tolak bila ukuran deklarasi melebihi cap — lalu baca
        // dibatasi juga (size bisa -1/palsu), supaya DOM tidak pernah
        // dibangun dari XML raksasa (zip-bomb / entity expansion).
        if (entry.size > MAX_XML_BYTES) return null
        return try {
            zip.getInputStream(entry).use { stream ->
                val bytes = readCapped(stream, MAX_XML_BYTES) ?: return null
                newSafeDocumentBuilder(allowDoctype = false)
                    .parse(ByteArrayInputStream(bytes)).documentElement
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Baca stream sampai [max] + 1 byte; null bila lebih dari [max]
     *  (byte kelebihannya membuktikan data melebihi cap) atau gagal. */
    private fun readCapped(stream: java.io.InputStream, max: Long): ByteArray? {
        return try {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun Element.children(localName: String): List<Element> {
        val out = mutableListOf<Element>()
        val kids = childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n is Element && n.localName == localName) out += n
        }
        return out
    }

    private fun Element.childElements(): List<Element> {
        val out = mutableListOf<Element>()
        val kids = childNodes
        for (i in 0 until kids.length) {
            (kids.item(i) as? Element)?.let { out += it }
        }
        return out
    }

    private fun NodeList.elements(): List<Element> {
        val out = mutableListOf<Element>()
        for (i in 0 until length) {
            (item(i) as? Element)?.let { out += it }
        }
        return out
    }

    private fun Element.descendants(localName: String): List<Element> {
        return getElementsByTagNameNS("*", localName).elements()
    }

    // --- DOCX: word/document.xml, <w:p> -> baris, <w:t> teks,
    // <w:tab/> -> tab, <w:br/> -> baris baru. ---
    private fun extractDocx(file: File): String? {
        ZipFile(file).use { zip ->
            val root = readEntry(zip, "word/document.xml") ?: return null
            val paras = root.descendants("p")
            if (paras.isEmpty()) return null
            val out = StringBuilder()
            for (p in paras) {
                for (r in p.descendants("r")) {
                    for (n in r.childElements()) {
                        when (n.localName) {
                            "t" -> out.append(n.textContent)
                            "tab" -> out.append('\t')
                            "br" -> out.append('\n')
                        }
                    }
                }
                out.append('\n')
            }
            return out.toString().trimEnd('\n')
        }
    }

    // --- XLSX: sharedStrings + workbook (nama sheet) + worksheet.
    // Sel: t="s" -> sharedStrings[v], t="inlineStr" -> <is><t>, sisanya
    // <v> apa adanya. Sel dipisah tab, baris dipisah baris baru. ---
    private fun extractXlsx(file: File): String? {
        ZipFile(file).use { zip ->
            val shared = mutableListOf<String>()
            readEntry(zip, "xl/sharedStrings.xml")?.let { sst ->
                for (si in sst.descendants("si")) {
                    val text = StringBuilder()
                    for (t in si.descendants("t")) text.append(t.textContent)
                    shared += text.toString()
                }
            }
            val names = mutableListOf<String>()
            readEntry(zip, "xl/workbook.xml")?.let { wb ->
                for (sheet in wb.descendants("sheet")) {
                    sheet.getAttribute("name").takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
            val sheets = zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { it.substringAfter("sheet").substringBefore(".xml").toIntOrNull() ?: Int.MAX_VALUE }
                .toList()
            if (sheets.isEmpty()) return null
            val out = StringBuilder()
            for ((sheetIdx, sheetName) in sheets.withIndex()) {
                val name = names.getOrNull(sheetIdx) ?: File(sheetName).nameWithoutExtension
                val root = readEntry(zip, sheetName) ?: continue
                if (out.isNotEmpty()) out.append("\n\n")
                out.append("== ").append(name).append(" ==\n")
                var firstRow = true
                for (row in root.descendants("row")) {
                    if (!firstRow) out.append('\n')
                    firstRow = false
                    val cells = row.children("c")
                    for ((cellIdx, c) in cells.withIndex()) {
                        if (cellIdx > 0) out.append('\t')
                        val kind = c.getAttribute("t")
                        val v = c.children("v").firstOrNull()?.textContent
                        when {
                            kind == "s" -> out.append(shared.getOrNull(v?.toIntOrNull() ?: -1) ?: "")
                            kind == "inlineStr" -> {
                                val text = StringBuilder()
                                val isEl = c.children("is").firstOrNull()
                                if (isEl != null) {
                                    for (t in isEl.descendants("t")) text.append(t.textContent)
                                }
                                out.append(text.toString())
                            }
                            v != null -> out.append(v)
                        }
                    }
                }
            }
            val result = out.toString().trimEnd('\n')
            return result.ifBlank { null }
        }
    }

    // --- PPTX: ppt/slides/slideN.xml, <a:t> per paragraf. ---
    private fun extractPptx(file: File): String? {
        ZipFile(file).use { zip ->
            val slides = zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedBy { it.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: Int.MAX_VALUE }
                .toList()
            if (slides.isEmpty()) return null
            val out = StringBuilder()
            for (slideName in slides) {
                val root = readEntry(zip, slideName) ?: continue
                if (out.isNotEmpty()) out.append("\n\n")
                var firstPara = true
                for (p in root.descendants("p")) {
                    if (!firstPara) out.append('\n')
                    firstPara = false
                    for (t in p.descendants("t")) out.append(t.textContent)
                }
            }
            val result = out.toString().trimEnd('\n')
            return result.ifBlank { null }
        }
    }

    // --- ODT: content.xml, <text:p>/<text:h> seurutan dokumen. ---
    private fun extractOdt(file: File): String? {
        ZipFile(file).use { zip ->
            val root = readEntry(zip, "content.xml") ?: return null
            val all = root.getElementsByTagName("*").elements()
            val out = StringBuilder()
            for (el in all) {
                if (el.localName == "p" || el.localName == "h") {
                    out.append(el.textContent).append('\n')
                }
            }
            val result = out.toString().trimEnd('\n')
            return result.ifBlank { null }
        }
    }
}
