package id.sysfile.manager

/** Renderer Markdown minimal → HTML, untuk pratinjau berkas .md di
 *  TextViewerScreen (pola yang sama seperti pratinjau HTML: WebView yang
 *  sama, base file:// folder induk supaya gambar relatif termuat).
 *  Cakupan v1: heading 1-6, bold, italic (*...*), inline code, code block
 *  (fence ```), list berbutir/bernomor, tautan, gambar, quote, hr,
 *  paragraf, escape HTML. Tanpa dependensi baru; list bersarang, tabel,
 *  dan footnote TIDAK didukung (dirender sebagai teks/list datar).
 *  Semua teks pengguna di-escape dulu, jadi tag HTML di dalam Markdown
 *  tampil sebagai teks, bukan dieksekusi. */
object Markdown {

    private val UL_ITEM = Regex("""^[-*+]\s+(.+)$""")
    private val OL_ITEM = Regex("""^\d+[.)]\s+(.+)$""")
    private val CODE_SPAN = Regex("`([^`\n]+)`")
    private val IMAGE = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    private val LINK = Regex("""\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    private val BOLD = Regex("""\*\*(.+?)\*\*""")
    private val ITALIC = Regex("""\*(.+?)\*""")

    fun toHtml(md: String): String {
        val body = renderBody(md)
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<style>body{font-family:sans-serif;line-height:1.6;padding:8px 14px;word-wrap:break-word}" +
            "img{max-width:100%}pre{background:#263238;color:#eceff1;padding:10px;border-radius:8px;overflow-x:auto}" +
            "code{background:#eceff1;padding:1px 4px;border-radius:4px}pre code{background:none;padding:0}" +
            "blockquote{border-left:3px solid #90a4ae;margin:8px 0;padding:4px 12px;color:#546e7a}" +
            "h1,h2{border-bottom:1px solid #cfd8dc;padding-bottom:4px}</style></head><body>\n" +
            body + "</body></html>"
    }

    private fun renderBody(md: String): String {
        val body = StringBuilder()
        val lines = md.replace("\r\n", "\n").replace('\r', '\n').lines()
        var i = 0
        var inCode = false
        val code = StringBuilder()
        var listKind: String? = null
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotEmpty()) {
                body.append("<p>").append(inline(para.toString().trim())).append("</p>\n")
                para.setLength(0)
            }
        }

        fun closeList() {
            if (listKind != null) {
                body.append("</").append(listKind).append(">\n")
                listKind = null
            }
        }

        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val trimmed = line.trimStart()
            if (inCode) {
                if (trimmed.startsWith("```")) {
                    body.append("<pre><code>").append(escape(code.toString())).append("</code></pre>\n")
                    code.setLength(0)
                    inCode = false
                } else {
                    code.append(line).append('\n')
                }
                i++
                continue
            }
            val ul = UL_ITEM.matchEntire(trimmed)
            val ol = OL_ITEM.matchEntire(trimmed)
            val hashes = trimmed.takeWhile { it == '#' }.length
            when {
                trimmed.startsWith("```") -> {
                    flushPara()
                    closeList()
                    inCode = true
                }
                trimmed.startsWith("#") && hashes in 1..6 &&
                    trimmed.length > hashes && trimmed[hashes] == ' ' -> {
                    flushPara()
                    closeList()
                    body.append("<h").append(hashes).append(">")
                        .append(inline(trimmed.drop(hashes + 1).trim()))
                        .append("</h").append(hashes).append(">\n")
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    flushPara()
                    closeList()
                    body.append("<hr />\n")
                }
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    flushPara()
                    closeList()
                    body.append("<blockquote>")
                        .append(inline(trimmed.removePrefix(">").trim()))
                        .append("</blockquote>\n")
                }
                ul != null -> {
                    if (listKind != "ul") {
                        flushPara()
                        closeList()
                        body.append("<ul>\n")
                        listKind = "ul"
                    }
                    body.append("<li>").append(inline(ul.groupValues[1])).append("</li>\n")
                }
                ol != null -> {
                    if (listKind != "ol") {
                        flushPara()
                        closeList()
                        body.append("<ol>\n")
                        listKind = "ol"
                    }
                    body.append("<li>").append(inline(ol.groupValues[1])).append("</li>\n")
                }
                line.isBlank() -> {
                    flushPara()
                    closeList()
                }
                else -> {
                    closeList()
                    para.append(line).append(' ')
                }
            }
            i++
        }
        if (inCode) {
            body.append("<pre><code>").append(escape(code.toString())).append("</code></pre>\n")
        }
        flushPara()
        closeList()
        return body.toString()
    }

    /** Inline: escape dulu, lalu code span (dilindungi placeholder),
     *  gambar, tautan, bold, italic. Placeholder memakai karakter kontrol
     *  yang tidak mungkin muncul dari escape(). */
    private fun inline(s: String): String {
        var t = escape(s)
        val codes = mutableListOf<String>()
        t = CODE_SPAN.replace(t) { m ->
            codes += m.groupValues[1]
            "\u0000${codes.size - 1}\u0000"
        }
        t = IMAGE.replace(t) { m ->
            "<img src=\"" + m.groupValues[2] + "\" alt=\"" + m.groupValues[1] + "\" />"
        }
        t = LINK.replace(t) { m ->
            "<a href=\"" + m.groupValues[2] + "\">" + m.groupValues[1] + "</a>"
        }
        t = BOLD.replace(t) { m -> "<strong>" + m.groupValues[1] + "</strong>" }
        t = ITALIC.replace(t) { m -> "<em>" + m.groupValues[1] + "</em>" }
        for ((index, code) in codes.withIndex()) {
            t = t.replace("\u0000$index\u0000", "<code>" + code + "</code>")
        }
        return t
    }

    private fun escape(s: String): String {
        val b = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> b.append("&amp;")
                '<' -> b.append("&lt;")
                '>' -> b.append("&gt;")
                '"' -> b.append("&quot;")
                else -> b.append(c)
            }
        }
        return b.toString()
    }
}
