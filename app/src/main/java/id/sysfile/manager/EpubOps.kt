package id.sysfile.manager

import java.io.ByteArrayOutputStream
import java.io.File
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Parser EPUB minimal (BC2, v1): baca saja, tanpa pencarian/bookmark.
 *  EPUB = ZIP berisi META-INF/container.xml → OPF (manifest + spine) →
 *  bab-bab XHTML. Ekstraksi memakai FileOps.unzip (penjaga zip-slip +
 *  batas ekstraksi r95 sudah termasuk). Parse XML memakai
 *  DocumentBuilder yang dikeraskan terhadap XXE (entitas eksternal
 *  dimatikan; DOCTYPE tetap diizinkan karena toc.ncx memakainya). */
data class EpubChapter(val title: String, val href: String)

data class EpubBook(val title: String, val opfDir: String, val chapters: List<EpubChapter>)

object EpubOps {

    /** M8: batas XML metadata (container/OPF/NCX) yang di-parse ke DOM. */
    const val MAX_META_XML_BYTES = 1024L * 1024L

    /** Parse berkas XML dengan cap ukuran (M8). Null bila hilang /
     *  melebihi cap / gagal parse. */
    private fun parseXmlFile(file: File, maxBytes: Long = MAX_META_XML_BYTES): org.w3c.dom.Document? {
        if (!file.isFile || file.length() > maxBytes) return null
        return try {
            newSafeDocumentBuilder().parse(file)
        } catch (_: Exception) {
            null
        }
    }

    /** Buka EPUB: ekstrak ke [destDir] (dihapus dulu bila sudah ada) lalu
     *  parse container + OPF (+ toc.ncx bila ada). Null bila bukan EPUB
     *  valid / ekstraksi gagal / spine kosong. */
    fun open(epub: File, destDir: File): EpubBook? {
        try {
            if (!epub.isFile) return null
            if (destDir.exists()) FileOps.deleteRecursively(destDir)
            if (!FileOps.unzip(epub, destDir)) return null
            val container = File(destDir, "META-INF/container.xml")
            if (!container.isFile) return null
            val containerDoc = parseXmlFile(container) ?: return null
            val rootfile = containerDoc.getElementsByTagNameNS("*", "rootfile")
                .item(0) as? Element ?: return null
            val opfPath = rootfile.getAttribute("full-path").ifBlank { return null }
            val opfFile = File(destDir, opfPath)
            if (!opfFile.isFile) return null
            if (!FileOps.isPathInside(destDir.canonicalPath, opfFile.canonicalPath)) return null
            val opf = parseXmlFile(opfFile) ?: return null
            val opfDir = opfPath.substringBeforeLast('/', "")
            val manifest = mutableMapOf<String, String>()
            var ncxHref: String? = null
            val items = opf.getElementsByTagNameNS("*", "item")
            for (i in 0 until items.length) {
                val el = items.item(i) as? Element ?: continue
                val id = el.getAttribute("id")
                val href = el.getAttribute("href")
                if (id.isBlank() || href.isBlank()) continue
                manifest[id] = href
                if (el.getAttribute("media-type") == "application/x-dtbncx+xml") ncxHref = href
            }
            val title = opf.getElementsByTagNameNS("*", "title")
                .item(0)?.textContent?.trim().orEmpty()
            val spine = opf.getElementsByTagNameNS("*", "itemref")
            val hrefs = mutableListOf<String>()
            for (i in 0 until spine.length) {
                val el = spine.item(i) as? Element ?: continue
                manifest[el.getAttribute("idref")]?.let { hrefs += it }
            }
            if (hrefs.isEmpty()) return null
            val titles = ncxHref?.let { readNcxTitles(destDir, opfDir, it) } ?: emptyMap()
            val chapters = hrefs.map { href ->
                val clean = href.substringBefore('#')
                val label = titles[clean] ?: File(clean).nameWithoutExtension
                EpubChapter(label, clean)
            }
            return EpubBook(title.ifBlank { epub.nameWithoutExtension }, opfDir, chapters)
        } catch (e: Exception) {
            return null
        }
    }

    /** Berkas bab di dalam folder hasil ekstrak, atau null bila href
     *  lolos ke luar folder buku (href jahat di OPF). */
    fun chapterFile(bookDir: File, book: EpubBook, chapter: EpubChapter): File? {
        return try {
            val base = if (book.opfDir.isEmpty()) bookDir else File(bookDir, book.opfDir)
            val f = File(base, decodeHref(chapter.href))
            if (FileOps.isPathInside(bookDir.canonicalPath, f.canonicalPath)) f else null
        } catch (e: Exception) {
            null
        }
    }

    /** Judul bab dari toc.ncx (src tanpa fragmen -> label). Gagal parse =
     *  peta kosong (judul jatuh ke nama berkas). */
    private fun readNcxTitles(destDir: File, opfDir: String, ncxHref: String): Map<String, String> {
        return try {
            val base = if (opfDir.isEmpty()) destDir else File(destDir, opfDir)
            val ncx = File(base, decodeHref(ncxHref.substringBefore('#')))
            if (!ncx.isFile) return emptyMap()
            if (!FileOps.isPathInside(destDir.canonicalPath, ncx.canonicalPath)) return emptyMap()
            // M8: NCX raksasa ditolak -> judul kosong, fallback nama file.
            val doc = parseXmlFile(ncx) ?: return emptyMap()
            val out = mutableMapOf<String, String>()
            val points = doc.getElementsByTagNameNS("*", "navPoint")
            for (i in 0 until points.length) {
                val el = points.item(i) as? Element ?: continue
                val label = el.getElementsByTagNameNS("*", "text")
                    .item(0)?.textContent?.trim().orEmpty()
                val src = (el.getElementsByTagNameNS("*", "content")
                    .item(0) as? Element)?.getAttribute("src").orEmpty()
                    .substringBefore('#')
                if (label.isNotBlank() && src.isNotBlank()) out[src] = label
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Dekode %XX pada href (URI) menjadi UTF-8. '+' dibiarkan apa adanya
     *  (di path URI, '+' adalah karakter literal, bukan spasi). */
    internal fun decodeHref(href: String): String {
        if ('%' !in href) return href
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < href.length) {
            val c = href[i]
            if (c == '%' && i + 2 < href.length) {
                val h = href.substring(i + 1, i + 3).toIntOrNull(16)
                if (h != null) {
                    out.write(h)
                    i += 3
                    continue
                }
            }
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/** DocumentBuilder yang aman dari XXE: entitas eksternal + DTD eksternal
 *  dimatikan, DOCTYPE tetap diizinkan (toc.ncx memakainya). Dipakai juga
 *  oleh OfficeText (BC3). */
internal fun newSafeDocumentBuilder(allowDoctype: Boolean = true): DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isXIncludeAware = false
    factory.isExpandEntityReferences = false
    // L11: gagal men-set fitur keamanan XML kini FAIL-CLOSED (dulu
    // ditelan diam-diam — parser berjalan tanpa proteksi XXE).
    fun requireFeature(name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (e: Exception) {
            throw IllegalStateException("Fitur XML tidak didukung: $name", e)
        }
    }
    requireFeature("http://xml.org/sax/features/external-general-entities", false)
    requireFeature("http://xml.org/sax/features/external-parameter-entities", false)
    requireFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    // M8: pemanggil yang tidak pernah memakai DOCTYPE (Office) boleh
    // memintanya ditolak — entity expansion tidak lagi bisa mengembang
    // sebelum parser membaca isinya. Jalur EPUB memakai default true
    // karena toc.ncx yang sah memakai DOCTYPE.
    if (!allowDoctype) {
        requireFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    return factory.newDocumentBuilder()
}
