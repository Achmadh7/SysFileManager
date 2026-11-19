package id.sysfile.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the minimal Markdown renderer (BC1). JVM-only, no Android APIs. */
class MarkdownTest {

    @Test
    fun heading_levels() {
        val html = Markdown.toHtml("# Judul\n\n## Sub\n\nTeks")
        assertTrue(html.contains("<h1>Judul</h1>"))
        assertTrue(html.contains("<h2>Sub</h2>"))
        assertTrue(html.contains("<p>Teks</p>"))
    }

    @Test
    fun heading_without_space_is_plain_text() {
        val html = Markdown.toHtml("#Judul")
        assertFalse(html.contains("<h1>"))
        assertTrue(html.contains("#Judul"))
    }

    @Test
    fun bold_and_italic() {
        val html = Markdown.toHtml("**tebal** dan *miring*")
        assertTrue(html.contains("<strong>tebal</strong>"))
        assertTrue(html.contains("<em>miring</em>"))
    }

    @Test
    fun inline_code_is_escaped() {
        val html = Markdown.toHtml("pakai `x < y` di sini")
        assertTrue(html.contains("<code>x &lt; y</code>"))
        assertFalse(html.contains("<code>x < y</code>"))
    }

    @Test
    fun code_block_is_escaped_and_unclosed_still_renders() {
        val closed = Markdown.toHtml("```\n<tag> & \"x\"\n```")
        assertTrue(closed.contains("<pre><code>&lt;tag&gt; &amp; &quot;x&quot;"))
        assertFalse(closed.contains("<tag>"))
        val unclosed = Markdown.toHtml("```\nfoo")
        assertTrue(unclosed.contains("<pre><code>foo"))
    }

    @Test
    fun unordered_list() {
        val html = Markdown.toHtml("- a\n- b")
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>a</li>"))
        assertTrue(html.contains("<li>b</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun ordered_list() {
        val html = Markdown.toHtml("1. satu\n2. dua")
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li>satu</li>"))
        assertTrue(html.contains("<li>dua</li>"))
    }

    @Test
    fun link_and_image() {
        val html = Markdown.toHtml("[Teks](https://x.id/a?b=1&c=2)")
        assertTrue(html.contains("<a href=\"https://x.id/a?b=1&amp;c=2\">Teks</a>"))
        val img = Markdown.toHtml("![alt](gambar.png)")
        assertTrue(img.contains("<img src=\"gambar.png\" alt=\"alt\" />"))
    }

    @Test
    fun blockquote_and_hr() {
        val html = Markdown.toHtml("> kutipan\n\n---")
        assertTrue(html.contains("<blockquote>kutipan</blockquote>"))
        assertTrue(html.contains("<hr />"))
    }

    @Test
    fun html_is_escaped() {
        val html = Markdown.toHtml("<script>alert(1)</script>")
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>alert"))
    }

    @Test
    fun empty_input_has_no_paragraph() {
        val html = Markdown.toHtml("")
        assertTrue(html.contains("<body>"))
        assertFalse(html.contains("<p>"))
    }

    @Test
    fun multiline_paragraph_merges() {
        val html = Markdown.toHtml("baris satu\nbaris dua")
        assertTrue(html.contains("<p>baris satu baris dua</p>"))
    }

    @Test
    fun list_then_paragraph_closes_list() {
        val html = Markdown.toHtml("- a\n- b\nteks")
        assertTrue(html.indexOf("</ul>") < html.indexOf("<p>teks</p>"))
    }

    @Test
    fun bold_inside_heading() {
        val html = Markdown.toHtml("# Judul **tebal**")
        assertTrue(html.contains("<h1>Judul <strong>tebal</strong></h1>"))
    }
}
