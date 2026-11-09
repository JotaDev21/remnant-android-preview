package com.liferpg.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalRestoreStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private fun backup(name: String, xp: Long = 900) = JSONObject().put("app", "REMNANT").put("schema", 13)
        .put("perfil", JSONObject().put("nome", name).put("xpTotal", xp))
        .put("series", JSONArray().put(JSONObject().put("exercicioId", "bench"))).toString()

    @Test fun onlyTheOwningAccountCanReadCandidateOrCommittedCheckpoint() {
        val store = LocalRestoreStore(folder.root)
        store.prepare("owner", backup("Atleta"), 100)
        assertNull(store.read("other")); assertNull(store.read(""))
        assertEquals("Atleta", store.read("owner")!!.nickname)
        assertTrue(store.committed())
        assertNull(store.read("other"))
        val point = store.read("owner")!!
        assertEquals(900L, point.xp); assertEquals(1, point.sets); assertEquals(100L, point.createdAt)
    }

    @Test fun failedRestoreKeepsThePreviousCommittedCheckpoint() {
        val store = LocalRestoreStore(folder.root)
        store.prepare("owner", backup("Original"), 100); assertTrue(store.committed())
        store.prepare("owner", backup("Atual"), 200)
        assertEquals("Atual", store.read("owner")!!.nickname)
        store.failed()
        assertEquals("Original", store.read("owner")!!.nickname)
    }

    @Test fun processDeathBeforeFilePromotionStillLeavesARecoverableCheckpoint() {
        val store = LocalRestoreStore(folder.root)
        store.prepare("owner", backup("Antigo"), 100); store.committed()
        store.prepare("owner", backup("Antes da restauração"), 200)
        val reopened = LocalRestoreStore(folder.root)
        assertEquals("Antes da restauração", reopened.read("owner")!!.nickname)
        assertTrue(reopened.committed())
        assertEquals("Antes da restauração", LocalRestoreStore(folder.root).read("owner")!!.nickname)
    }

    @Test fun invalidInputCannotReplaceTheGoodCheckpointAndCorruptCandidateFallsBack() {
        val store = LocalRestoreStore(folder.root)
        store.prepare("owner", backup("Intacto"), 100); store.committed()
        assertThrows(IllegalArgumentException::class.java) { store.prepare("", backup("Errado")) }
        assertThrows(IllegalArgumentException::class.java) { store.prepare("owner", "{\"app\":\"OTHER\",\"schema\":13}") }
        File(folder.root, "restore-candidate-v2.json").writeText("broken")
        assertEquals("Intacto", store.read("owner")!!.nickname)
    }

    @Test fun clearingLocalDataRemovesCandidateCommittedAndLegacyCopies() {
        val store = LocalRestoreStore(folder.root)
        store.prepare("owner", backup("Antes"), 100); store.committed()
        store.prepare("owner", backup("Depois"), 200)
        val legacy = File(folder.root, "before-cloud-restore.json").also { it.writeText("legacy") }
        store.clear()
        assertNull(store.read("owner")); assertFalse(legacy.exists())
    }

    @Test @Config(sdk = [26]) fun androidEightIgnoresAnInterruptedNewFileAndKeepsTheCommittedCheckpoint() {
        val store = LocalRestoreStore(folder.root)
        store.prepare("owner", backup("Intacto"), 100); store.committed()
        File(folder.root, "restore-checkpoint-v2.json.new").writeText("interrupted")
        assertEquals("Intacto", LocalRestoreStore(folder.root).read("owner")!!.nickname)
    }
}
