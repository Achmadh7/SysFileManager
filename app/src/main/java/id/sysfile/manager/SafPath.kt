package id.sysfile.manager

/**
 * Logika murni seputar URI awal SAF — tanpa API Android, bisa diuji di JVM.
 *
 * Latar: DocumentsUI (AOSP) membuang URI awal yang diminta aplikasi bila
 * letaknya di /Android/data, /Android/obb atau /Android/sandbox beserta
 * subfoldernya, lalu membuka pemilih di lokasi terakhir — pengguna mendarat
 * entah di mana dan tidak pernah sampai ke folder yang diminta. Pemilih
 * sendiri tetap mengizinkan pengguna MEMILIH subfolder paket itu secara
 * manual (yang diblokir hanya akar Android/data dan Android/obb), jadi URI
 * awal diarahkan ke akar volume saja.
 */
object SafPath {

    /** Benar bila rel (dari akar volume) masuk /Android/data|obb|sandbox. */
    fun isRestrictedRel(rel: String): Boolean {
        if (!rel.startsWith("Android/")) return false
        val second = rel.removePrefix("Android/").substringBefore('/')
        return second == "data" || second == "obb" || second == "sandbox"
    }

    /** docId untuk URI awal: akar volume bila rel terlarang, rel itu bila tidak. */
    fun initialDocId(vol: String, rel: String): String =
        if (rel.isEmpty() || isRestrictedRel(rel)) "$vol:" else "$vol:$rel"
}
