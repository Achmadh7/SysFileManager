package id.sysfile.manager

/**
 * Logika murni seputar URI awal SAF — tanpa API Android, bisa diuji di JVM.
 *
 * Latar: DocumentsUI (AOSP) membuang URI awal yang diminta aplikasi bila
 * letaknya di /Android/data, /Android/obb atau /Android/sandbox beserta
 * subfoldernya, lalu membuka pemilih di lokasi terakhir — pengguna mendarat
 * entah di mana dan tidak pernah sampai ke folder yang diminta. URI awal
 * diarahkan ke akar volume saja. Sebelum perbaikan CVE-2025-26427 pemilih
 * masih mengizinkan pengguna MEMILIH subfolder paket itu secara manual;
 * sejak buletin Mei 2025 (patch 2025-05-01) subfolder itu pun diblokir
 * dari pemilihan (FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE) — lihat
 * isSubtreeBlocked.
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

    /** Pola tanggal patch keamanan: YYYY-MM-DD. */
    private val patchDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * Benar bila patch keamanan perangkat sudah memuat perbaikan
     * CVE-2025-26427 (buletin Mei 2025, patch level 2025-05-01):
     * sejak itu ExternalStorageProvider menyembunyikan seluruh
     * subtree Android/data|obb|sandbox dan DocumentsUI menolak
     * memilih subfolder di dalamnya (FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE),
     * jadi tawaran "Beri Akses" untuk folder semacam itu tidak bisa
     * berhasil. Patch kosong atau bukan tanggal dianggap belum diblokir
     * (tawaran SAF tetap tampil).
     */
    fun isSubtreeBlocked(securityPatch: String): Boolean =
        patchDatePattern.matches(securityPatch) && securityPatch >= "2025-05-01"
}
