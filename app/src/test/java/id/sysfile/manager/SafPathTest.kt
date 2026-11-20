package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure SAF initial-URI logic (SafPath). */
class SafPathTest {

    @Test
    fun initialDocId_restrictedRelFallsBackToVolumeRoot() {
        assertEquals("primary:", SafPath.initialDocId("primary", "Android/obb/com.garena.game.mho"))
        assertEquals("primary:", SafPath.initialDocId("primary", "Android/data/com.example.app"))
        assertEquals("primary:", SafPath.initialDocId("primary", "Android/sandbox/com.example.app/files"))
        assertEquals("primary:", SafPath.initialDocId("primary", "Android/obb"))
        assertEquals("primary:", SafPath.initialDocId("primary", "Android/data"))
    }

    @Test
    fun initialDocId_unrestrictedRelKept() {
        assertEquals("primary:Download", SafPath.initialDocId("primary", "Download"))
        assertEquals("primary:Android", SafPath.initialDocId("primary", "Android"))
        assertEquals("primary:", SafPath.initialDocId("primary", ""))
        assertEquals("ABCD-1234:Music", SafPath.initialDocId("ABCD-1234", "Music"))
    }

    @Test
    fun isRestrictedRel_matchesAospPattern() {
        assertTrue(SafPath.isRestrictedRel("Android/obb/com.x"))
        assertTrue(SafPath.isRestrictedRel("Android/data"))
        assertTrue(SafPath.isRestrictedRel("Android/sandbox/com.x"))
        assertFalse(SafPath.isRestrictedRel("Android"))
        assertFalse(SafPath.isRestrictedRel("Android/data2/com.x"))
        assertFalse(SafPath.isRestrictedRel("Download"))
        assertFalse(SafPath.isRestrictedRel(""))
    }

    @Test
    fun isSubtreeBlocked_may2025PatchLevelAndLater() {
        // Perbaikan CVE-2025-26427 masuk buletin Mei 2025 (patch 2025-05-01).
        assertTrue(SafPath.isSubtreeBlocked("2025-05-01"))
        assertTrue(SafPath.isSubtreeBlocked("2025-05-05"))
        assertTrue(SafPath.isSubtreeBlocked("2025-06-01"))
        assertTrue(SafPath.isSubtreeBlocked("2026-01-05"))
        assertFalse(SafPath.isSubtreeBlocked("2025-04-01"))
        assertFalse(SafPath.isSubtreeBlocked("2024-12-05"))
        assertFalse(SafPath.isSubtreeBlocked(""))
        assertFalse(SafPath.isSubtreeBlocked("unknown"))
    }
}
