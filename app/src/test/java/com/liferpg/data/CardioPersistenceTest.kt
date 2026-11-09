package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class CardioPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private val day = LocalDate.of(2026, 10, 8).toEpochDay()
    private val wall = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000
    private val clock = CardioClock(10_000, 7, wall)
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AppRepository(db, CatalogoRepo(context))
    }
    @After fun close() = db.close()
    private suspend fun profile(last: Long = day - 1) { db.dao().salvarPerfil(Perfil(nome = "Cardio teste", classe = "TITAN", pesoCorporalKg = 80f,
        xpTotal = 900, streak = 3, melhorStreak = 5, ultimoDiaTreino = last, criadoEm = 1L)) }
    private suspend fun start() : CardioSession {
        repo.startCardio(CardioActivity.RUN, CardioGoal.TIME, 20.0, clock)
        return db.dao().activeCardio()!!
    }
    @Test fun finishIsAtomicAndRetryCannotDuplicateXpStreakOrJournal() = runTest {
        profile(); val row = start(); val end = clock.copy(elapsed = clock.elapsed + 1_800_000, wall = wall + 1_800_000)
        val first = repo.finishCardio(row.id, end, 5.0, "MODERATE", "Parque")!!
        assertEquals(60, first.xpGanho); assertEquals("CARDIO", first.tipoRegistro); assertTrue(first.primeiroTreinoDoDia)
        assertEquals(4, db.dao().perfil()!!.streak); assertEquals(960L, db.dao().perfil()!!.xpTotal)
        assertNull(repo.finishCardio(row.id, end, 5.0, "MODERATE", "Parque"))
        assertEquals(1, db.dao().allCardio().size); assertNull(db.dao().activeCardio())
        assertTrue(db.dao().todasSeries().isEmpty()); assertTrue(db.dao().todasSessoes().isEmpty())
    }
    @Test fun invalidDistanceOrTooShortSessionKeepsTimerAndProfileUnchanged() = runTest {
        profile(); val row = start()
        try { repo.finishCardio(row.id, clock.copy(elapsed = clock.elapsed + 30_000), 1.0, "", ""); fail() } catch (_: IllegalArgumentException) { }
        try { repo.finishCardio(row.id, clock.copy(elapsed = clock.elapsed + 600_000), Double.NaN, "", ""); fail() } catch (_: IllegalArgumentException) { }
        assertEquals("RUNNING", db.dao().activeCardio()!!.status); assertTrue(db.dao().allCardio().isEmpty())
        assertEquals(900L, db.dao().perfil()!!.xpTotal); assertEquals(3, db.dao().perfil()!!.streak)
    }
    @Test fun sameDayStrengthAndMultipleCardioSessionsShareOneStreakAndDailyXpCap() = runTest {
        profile(day)
        repeat(3) { index ->
            val start = clock.copy(wall = wall + index * 4_000_000L)
            repo.startCardio(CardioActivity.BIKE, CardioGoal.FREE, 0.0, start)
            val row = db.dao().activeCardio()!!
            val result = repo.finishCardio(row.id, start.copy(elapsed = start.elapsed + 2_700_000, wall = start.wall + 2_700_000), 15.0, "", "")!!
            assertFalse(result.primeiroTreinoDoDia)
        }
        assertEquals(listOf(0, 30, 90), db.dao().allCardio().map { it.xpGained })
        assertEquals(1020L, db.dao().perfil()!!.xpTotal); assertEquals(3, db.dao().perfil()!!.streak)
    }
    @Test fun shortSessionsAreRealHistoryWithoutFakeStreakAndManualCannotReplaceAnActiveTimer() = runTest {
        profile(); val row = start()
        val manual = CardioEngine.start(CardioActivity.WALK, CardioGoal.FREE, 0.0, clock).copy(status = "PAUSED", source = "MANUAL", accumulatedMs = 120_000)
        try { repo.finishCardio(manual.id, clock, null, "", "", manual); fail() } catch (_: IllegalStateException) { }
        val result = repo.finishCardio(row.id, clock.copy(elapsed = clock.elapsed + 120_000, wall = wall + 120_000), null, "", "")!!
        assertEquals(0, result.xpGanho); assertFalse(result.primeiroTreinoDoDia); assertEquals(day - 1, db.dao().perfil()!!.ultimoDiaTreino)
        repo.finishCardio(manual.id, clock.copy(wall = wall + 300_000), null, "EASY", "Manual", manual)
        assertEquals(2, db.dao().allCardio().size)
    }
    @Test fun portableBackupRestoresCompletedCardioWithoutTransportingLiveTimersOrAwardingAgain() = runTest {
        profile(); val first = start()
        repo.finishCardio(first.id, clock.copy(elapsed = clock.elapsed + 600_000, wall = wall + 600_000), 2.0, "EASY", "Teste")
        start()
        val backup = repo.exportarBackupJson(); val root = JSONObject(backup)
        assertEquals(15, root.getInt("schema")); assertEquals(1, root.getJSONArray("cardio").length())
        val xp = db.dao().perfil()!!.xpTotal
        repo.restaurarBackupJson(backup)
        assertEquals(xp, db.dao().perfil()!!.xpTotal); assertNull(db.dao().activeCardio()); assertEquals("Teste", db.dao().allCardio().single().note)
        val legacy = root.apply { put("schema", 14); remove("cardio") }.toString()
        repo.restaurarBackupJson(legacy); assertTrue(db.dao().allCardio().isEmpty()); assertEquals(xp, db.dao().perfil()!!.xpTotal)
    }
    @Test fun failedBackupCannotWipeExistingCardioAndResetClearsItExplicitly() = runTest {
        profile(); start(); val backup = JSONObject(repo.exportarBackupJson())
        backup.put("cardio", org.json.JSONArray().put(JSONObject().put("id", "invalid")))
        try { repo.restaurarBackupJson(backup.toString()); fail() } catch (_: Exception) { }
        assertNotNull(db.dao().activeCardio()); assertEquals(900L, db.dao().perfil()!!.xpTotal)
        repo.resetTotal(); assertNull(db.dao().activeCardio()); assertTrue(db.dao().allCardio().isEmpty())
    }
}
