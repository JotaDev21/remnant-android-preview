package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import org.json.JSONObject
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class CardioGpsPersistenceTest {
    private lateinit var db: AppDatabase; private lateinit var routes: CardioRouteStore; private lateinit var repo: AppRepository
    private val clock = CardioClock(1000, 1, 1_791_480_000_000, "phone")
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        routes = CardioRouteStore(context); routes.clear(); repo = AppRepository(db, CatalogoRepo(context), routes)
    }
    @After fun cleanup() { routes.clear(); db.close() }
    private suspend fun start(): CardioSession {
        db.dao().salvarPerfil(Perfil(nome = "GPS teste", classe = "TITAN", pesoCorporalKg = 80f, criadoEm = 1L))
        repo.startCardio(CardioActivity.WALK, CardioGoal.FREE, 0.0, clock, true)
        return db.dao().activeCardio()!!
    }
    @Test fun callbacksCannotWriteAfterPauseFinishOrAccountResetAndGpsDistanceCannotBeOverridden() = runTest {
        val row = start()
        assertTrue(repo.recordCardioTracking(row.id, clock) { CardioRouteState(listOf(CardioFix(-23.0, -46.0, 4f, 1000, 1)), 1200.0, 1300, 1) })
        assertEquals(1.2, db.dao().activeCardio()!!.distanceKm!!, .001)
        val end = clock.copy(elapsed = 601_000, wall = clock.wall + 600_000)
        repo.updateCardio(row.id, end, true)
        assertFalse(repo.recordCardioTracking(row.id, end) { it.copy(meters = 9999.0) })
        repo.finishCardio(row.id, end, 100.0, "", "Parque")
        val finished = db.dao().allCardio().single(); assertEquals(1.2, finished.distanceKm!!, .001); assertEquals(1300L, finished.steps)
        assertFalse(repo.recordCardioTracking(row.id, end) { it.copy(meters = 9999.0) })
        val backup = repo.exportarBackupJson()
        assertFalse(backup.contains("latitude")); assertFalse(backup.contains("-23.0")); assertFalse(backup.contains("points"))
        repo.restaurarBackupJson(backup); assertTrue(routes.read(row.id).points.isEmpty()); assertEquals(1300L, db.dao().allCardio().single().steps)
        repo.resetTotal(); assertFalse(repo.recordCardioTracking(row.id, end) { it }); assertTrue(routes.read(row.id).points.isEmpty())
    }
    @Test fun interruptedTrackerFreezesAtItsCheckpointRatherThanInventingMissingMovementTime() = runTest {
        val row = start(); repo.updateCardio(row.id, clock.copy(elapsed = 31_000))
        repo.freezeInterruptedCardio(row.id, clock.copy(elapsed = 601_000))
        assertEquals(30_000L, db.dao().activeCardio()!!.accumulatedMs); assertEquals("PAUSED", db.dao().activeCardio()!!.status)
        assertFalse(repo.recordCardioTracking(row.id, clock) { it.copy(steps = 500) })
    }
    @Test fun missingPrivateRouteAfterAndroidTransferDoesNotResetMeasuredDistanceOrSteps() = runTest {
        val row = start()
        repo.recordCardioTracking(row.id, clock) { it.copy(meters = 1200.0, steps = 1300) }
        routes.clear() // Precise coordinates intentionally do not transfer with the Android database.
        assertTrue(repo.recordCardioTracking(row.id, clock) { it.copy(segment = it.segment + 1) })
        assertEquals(1.2, db.dao().activeCardio()!!.distanceKm!!, .001)
        assertEquals(1300L, db.dao().activeCardio()!!.steps)
    }
    @Test fun preciseRoutesLiveOnlyInNoBackupStorageAndAtomicFileRecovers() {
        val id = UUID.randomUUID().toString(); val state = CardioRouteState(listOf(CardioFix(-23.0, -46.0, 4f, 1000, 2)), 0.0, 0, 2)
        routes.write(id, state); assertEquals(state, routes.read(id))
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(File(context.noBackupFilesDir, "cardio_routes/$id.json").isFile)
        val file = File(context.noBackupFilesDir, "cardio_routes/$id.json")
        // An interrupted AtomicFile update leaves the previous committed document intact.
        android.util.AtomicFile(file).startWrite().use { it.write("broken incomplete update".toByteArray()) }
        assertEquals(state, routes.read(id)); routes.delete(id); assertTrue(routes.read(id).points.isEmpty())
        try { routes.read("../escape"); fail("Path traversal") } catch (_: IllegalArgumentException) { }
    }
    @Test fun mapFeaturesPreserveLongitudeLatitudeAndDoNotConnectPauses() {
        val a = CardioFix(-23.0, -46.0, 4f, 1000, 1)
        val features = JSONObject(com.liferpg.ui.cardioRouteGeoJson(listOf(a, a.copy(lat = -22.99, elapsed = 20_000), a.copy(segment = 2, elapsed = 30_000)), true)).getJSONArray("features")
        val lines = (0 until features.length()).map { features.getJSONObject(it) }.filter { it.getJSONObject("geometry").getString("type") == "LineString" }
        assertEquals(1, lines.size); assertEquals(-46.0, lines[0].getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0).getDouble(0), .001)
        assertEquals(2, lines[0].getJSONObject("geometry").getJSONArray("coordinates").length())
        val kinds = (0 until features.length()).map { features.getJSONObject(it).getJSONObject("properties").optString("kind") }
        assertTrue(kinds.contains("start")); assertTrue(kinds.contains("finish")); assertFalse(kinds.contains("current"))
        val live = JSONObject(com.liferpg.ui.cardioRouteGeoJson(listOf(a, a.copy(lat = -22.99, elapsed = 20_000)), false)).getJSONArray("features")
        assertTrue((0 until live.length()).any { live.getJSONObject(it).getJSONObject("properties").optString("kind") == "current" })
    }
    @Test fun signalContinuityPersistsAndOlderRouteFilesRemainReadable() {
        val id = UUID.randomUUID().toString()
        val state = CardioRouteState(listOf(CardioFix(-23.0, -46.0, 25f, 1000, 1)), 0.0, segment = 1, lastSignalElapsed = 50_000)
        routes.write(id, state); assertEquals(state, routes.read(id))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.noBackupFilesDir, "cardio_routes/$id.json")
        val legacy = JSONObject(file.readText()); legacy.remove("lastSignalElapsed"); file.writeText(legacy.toString())
        assertEquals(state.copy(lastSignalElapsed = 0), routes.read(id))
    }
}
