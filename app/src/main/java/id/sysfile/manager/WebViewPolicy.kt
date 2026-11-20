package id.sysfile.manager

import java.io.File
import java.net.URI

/** Kebijakan muat resource untuk WebView pratinjau konten lokal (r176:
 *  H5/M11/M12). Murni (tanpa Android) supaya bisa diuji unit. Prinsipnya:
 *  WebView tidak pernah memuat file:// atau http(s) sendiri — subresource
 *  file:// hanya dilayani bila kanonisnya berada di dalam direktori yang
 *  diizinkan (folder induk berkas / folder bab EPUB), subresource jaringan
 *  selalu diblokir, dan tautan hanya boleh http/https (dibuka eksternal). */
object WebViewPolicy {

    /** Selesaikan URL subresource `file://` terhadap [allowedDir]: kembalikan
     *  File kanonis bila berada di dalam [allowedDir] dan benar-benar berkas,
     *  null bila di luar / bukan file:// / URL rusak / dir tidak diberikan /
     *  target bukan berkas. Kanonikalisasi menutup `..` dan symlink yang
     *  menunjuk ke luar; batas separator diwarisi dari isPathInside. */
    fun resolveAllowedSubresource(requestUrl: String, allowedDir: File?): File? {
        val dir = allowedDir ?: return null
        val uri = try { URI(requestUrl) } catch (_: Exception) { return null }
        if (uri.scheme?.lowercase() != "file") return null
        val rawPath = uri.path ?: return null
        val canonDir = try { dir.canonicalFile } catch (_: Exception) { return null }
        val canonTarget = try { File(rawPath).canonicalFile } catch (_: Exception) { return null }
        if (!FileOps.isPathInside(canonDir.path, canonTarget.path)) return null
        if (!canonTarget.isFile) return null
        return canonTarget
    }

    /** True hanya untuk tautan http/https (dibuka eksternal lewat chooser).
     *  Skema lain (file:, intent:, tel:, sms:, market:, ...) dan URL relatif
     *  menghasilkan false — WebViewClient memblokirnya. */
    fun isExternalHttpLink(url: String): Boolean {
        val scheme = try { URI(url).scheme } catch (_: Exception) { return false }?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    /** MIME untuk menyajikan subresource lokal yang diizinkan. */
    fun mimeTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "htm", "html" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "text/javascript"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
