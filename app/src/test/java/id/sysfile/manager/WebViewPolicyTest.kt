package id.sysfile.manager

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Unit tests for the pure WebView subresource/link policy (WebViewPolicy, r176). */
class WebViewPolicyTest {

    private val roots = mutableListOf<File>()

    @After
    fun tearDown() {
        for (r in roots) r.deleteRecursively()
        roots.clear()
    }

    private fun tmpRoot(name: String): File {
        val d = File(System.getProperty("java.io.tmpdir"), "wvpolicy-$name-${System.nanoTime()}")
        assertTrue(d.mkdirs())
        roots += d
        return d
    }

    private fun writeFile(parent: File, rel: String, text: String = "x"): File {
        val f = File(parent, rel)
        f.parentFile?.mkdirs()
        f.writeText(text)
        return f
    }

    @Test
    fun fileInsideDirAllowed() {
        val root = tmpRoot("inside")
        val book = File(root, "book").apply { mkdirs() }
        val pic = writeFile(book, "images/pic.png")
        val got = WebViewPolicy.resolveAllowedSubresource("file://" + pic.absolutePath, book)
        assertEquals(pic.canonicalFile, got)
    }

    @Test
    fun fileOutsideDirBlocked() {
        val root = tmpRoot("outside")
        val book = File(root, "book").apply { mkdirs() }
        val secret = writeFile(root, "Private/secret.png")
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + secret.absolutePath, book))
    }

    @Test
    fun dotDotEscapeBlocked() {
        val root = tmpRoot("dotdot")
        val book = File(root, "book").apply { mkdirs() }
        writeFile(book, "images/pic.png")
        val secret = writeFile(root, "secret.png")
        val escaped = "file://" + book.absolutePath + "/images/../../secret.png"
        assertEquals(secret.canonicalFile, File(secret.absolutePath).canonicalFile)
        assertNull(WebViewPolicy.resolveAllowedSubresource(escaped, book))
    }

    @Test
    fun siblingPrefixBlocked() {
        // Batas separator: /book2/ bukan di dalam /book/ walau berawalan sama.
        val root = tmpRoot("sibling")
        val book = File(root, "book").apply { mkdirs() }
        val evil = writeFile(root, "book2/evil.png")
        assertEquals(evil.canonicalFile, File(evil.absolutePath).canonicalFile)
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + evil.absolutePath, book))
    }

    @Test
    fun percentEncodedInsideAllowed() {
        val root = tmpRoot("encoded")
        val book = File(root, "book").apply { mkdirs() }
        val pic = writeFile(book, "my pic.png")
        val url = "file://" + book.absolutePath.replace(" ", "%20") + "/my%20pic.png"
        assertEquals(pic.canonicalFile, WebViewPolicy.resolveAllowedSubresource(url, book))
    }

    @Test
    fun symlinkInsidePointingOutsideBlocked() {
        val root = tmpRoot("symlink")
        val book = File(root, "book").apply { mkdirs() }
        val secret = writeFile(root, "secret.png")
        val link = File(book, "link.png")
        Files.createSymbolicLink(link.toPath(), secret.toPath())
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + link.absolutePath, book))
    }

    @Test
    fun httpUrlNotFile() {
        val root = tmpRoot("http")
        val book = File(root, "book").apply { mkdirs() }
        assertNull(WebViewPolicy.resolveAllowedSubresource("https://example.com/x.png", book))
        assertNull(WebViewPolicy.resolveAllowedSubresource("http://example.com/x.png", book))
    }

    @Test
    fun intentUrlBlocked() {
        val root = tmpRoot("intent")
        val book = File(root, "book").apply { mkdirs() }
        assertNull(WebViewPolicy.resolveAllowedSubresource("intent://x#Intent;scheme=https;end", book))
        assertNull(WebViewPolicy.resolveAllowedSubresource("tel:+621234", book))
    }

    @Test
    fun nullDirBlocksAll() {
        val root = tmpRoot("nulldir")
        val pic = writeFile(root, "pic.png")
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + pic.absolutePath, null))
    }

    @Test
    fun relativeUrlBlocked() {
        val root = tmpRoot("relative")
        val book = File(root, "book").apply { mkdirs() }
        writeFile(book, "images/pic.png")
        assertNull(WebViewPolicy.resolveAllowedSubresource("images/pic.png", book))
    }

    @Test
    fun missingFileBlocked() {
        val root = tmpRoot("missing")
        val book = File(root, "book").apply { mkdirs() }
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + book.absolutePath + "/gone.png", book))
    }

    @Test
    fun directoryItselfBlocked() {
        val root = tmpRoot("diritself")
        val book = File(root, "book").apply { mkdirs() }
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + book.absolutePath, book))
        assertNull(WebViewPolicy.resolveAllowedSubresource("file://" + book.absolutePath + "/", book))
    }

    @Test
    fun isExternalHttpLink_onlyHttpHttps() {
        assertTrue(WebViewPolicy.isExternalHttpLink("https://example.com/a"))
        assertTrue(WebViewPolicy.isExternalHttpLink("http://example.com/a"))
        assertTrue(WebViewPolicy.isExternalHttpLink("HTTPS://EXAMPLE.COM/A"))
        assertFalse(WebViewPolicy.isExternalHttpLink("file:///storage/emulated/0/x.png"))
        assertFalse(WebViewPolicy.isExternalHttpLink("intent://x#Intent;scheme=https;end"))
        assertFalse(WebViewPolicy.isExternalHttpLink("tel:+621234"))
        assertFalse(WebViewPolicy.isExternalHttpLink("sms:123"))
        assertFalse(WebViewPolicy.isExternalHttpLink("market://details?id=x"))
        assertFalse(WebViewPolicy.isExternalHttpLink("chapter2.html"))
        assertFalse(WebViewPolicy.isExternalHttpLink("images/pic.png"))
        assertFalse(WebViewPolicy.isExternalHttpLink(""))
        assertFalse(WebViewPolicy.isExternalHttpLink("http://exa mple.com"))
    }

    @Test
    fun mimeTypeFor_knownAndFallback() {
        assertEquals("image/png", WebViewPolicy.mimeTypeFor("pic.png"))
        assertEquals("image/jpeg", WebViewPolicy.mimeTypeFor("pic.JPG"))
        assertEquals("image/jpeg", WebViewPolicy.mimeTypeFor("pic.jpeg"))
        assertEquals("image/svg+xml", WebViewPolicy.mimeTypeFor("pic.svg"))
        assertEquals("text/css", WebViewPolicy.mimeTypeFor("style.css"))
        assertEquals("text/html", WebViewPolicy.mimeTypeFor("page.htm"))
        assertEquals("font/woff2", WebViewPolicy.mimeTypeFor("f.woff2"))
        assertEquals("application/octet-stream", WebViewPolicy.mimeTypeFor("data.xyz"))
        assertEquals("application/octet-stream", WebViewPolicy.mimeTypeFor("noext"))
    }

    @Test
    fun baseUrlForDir_encodesSpecialCharsAndResolvesSubresource() {
        val root = java.nio.file.Files.createTempDirectory("webviewtest").toFile()
        try {
            val dir = java.io.File(root, "C# Notes").apply { mkdirs() }
            val css = java.io.File(dir, "theme.css").apply { writeText("body{}") }
            val base = WebViewPolicy.baseUrlForDir(dir)
            val resolved = WebViewPolicy.resolveAllowedSubresource(
                java.net.URI.create(base).resolve("theme.css").toString(),
                dir
            )
            assertEquals(css.canonicalPath, resolved?.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }
}
