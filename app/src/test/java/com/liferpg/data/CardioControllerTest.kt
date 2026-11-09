package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class CardioControllerTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private lateinit var controllerScope: CoroutineScope
    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AppRepository(db, CatalogoRepo(ctx))
        controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    @After fun close() = runBlocking { controllerScope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    @Test fun processRecreationLoadsThePersistedTimerBeforeStartingAndRebootFreezesCheckpoint() = runBlocking {
        withTimeout(10_000) {
        var clock = CardioClock(1000, 1, 1_791_480_000_000)
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "TITAN", pesoCorporalKg = 80f, criadoEm = 1L))
        repo.startCardio(CardioActivity.WALK, CardioGoal.FREE, 0.0, clock)
        val row = db.dao().activeCardio()!!
        clock = clock.copy(elapsed = 601_000)
        repo.updateCardio(row.id, clock)
        clock = clock.copy(elapsed = 10_000, boot = 2, wall = clock.wall + 10_000_000)
        val controller = CardioController(controllerScope, repo, { clock })
        controller.ready.first { it }
        assertEquals(row.id, controller.active.value!!.id); assertEquals("PAUSED", controller.active.value!!.status)
        assertEquals(600_000L, controller.active.value!!.accumulatedMs)
        controller.start(CardioActivity.RUN, CardioGoal.TIME, 20.0)
        controller.error.first { it != null }
        controller.busy.first { !it }
        assertEquals(row.id, db.dao().activeCardio()!!.id)
        controller.resume(); controller.active.first { it?.status == "RUNNING" }
        assertEquals(600_000L, CardioEngine.elapsed(controller.active.value!!, clock))
        }
    }
    @Test fun failedConclusionPreservesActiveSessionAndDoubleTapOnlyRewardsOnce() = runBlocking {
        withTimeout(10_000) {
        var clock = CardioClock(1000, 1, 1_791_480_000_000)
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "TITAN", pesoCorporalKg = 80f, criadoEm = 1L))
        repo.startCardio(CardioActivity.RUN, CardioGoal.TIME, 20.0, clock)
        var rewards = 0
        val controller = CardioController(controllerScope, repo, { clock }) { rewards++ }
        controller.ready.first { it }; val id = controller.active.value!!.id
        clock = clock.copy(elapsed = 1_801_000, wall = clock.wall + 1_800_000)
        controller.finish(id, Double.NaN, "", "")
        controller.error.first { it != null }
        controller.busy.first { !it }
        assertNotNull(db.dao().activeCardio()); assertNull(controller.saved.value); assertEquals(0, rewards)
        controller.finish(id, 5.0, "EASY", "Mantido"); controller.finish(id, 5.0, "EASY", "Mantido")
        controller.saved.first { it != null }; controller.busy.first { !it }
        assertEquals(1, rewards); assertEquals(1, db.dao().allCardio().size); assertEquals("Mantido", controller.saved.value!!.note)
        }
    }
}
