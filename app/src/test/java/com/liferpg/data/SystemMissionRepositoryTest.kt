package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class SystemMissionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private val day=LocalDate.now().toEpochDay()
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        repo=AppRepository(db,CatalogoRepo(context))
    }
    @After fun close()=db.close()
    private suspend fun profile() { db.dao().salvarPerfil(Perfil(nome="System teste",classe="TITAN",pesoCorporalKg=80f,xpTotal=900,criadoEm=1)) }
    private suspend fun record(d: Long=day,valid: Boolean=true) = db.dao().inserirSerie(SerieRegistro(exercicioId="Pushups",musculo="PEITO",pesoKg=null,reps=12,segundos=null,score=1f,xpGanho=10,epochDay=d,timestamp=1,valido=valid))
    @Test fun reconciliationPaysOnceAcrossRestartAndKeepsInvalidSetsOut()=runTest {
        profile(); repo.garantirMissoes(day); repo.garantirMissoes(day)
        assertEquals(3,db.dao().systemDay(day).size); assertEquals(1,db.dao().systemWeekCount(MissaoEngine.inicioSemana(day)))
        record(valid=false); assertNull(repo.garantirMissoes(day)); assertEquals(900L,db.dao().perfil()!!.xpTotal)
        record(); assertEquals(30,repo.garantirMissoes(day)!!.xpGanho)
        repeat(5) { record() }; assertEquals(60,repo.garantirMissoes(day)!!.xpGanho)
        repo=AppRepository(db,CatalogoRepo(ApplicationProvider.getApplicationContext()))
        assertNull(repo.garantirMissoes(day)); assertEquals(990L,db.dao().perfil()!!.xpTotal)
    }
    @Test fun adaptationIsAvailableOnlyBeforeActivityAndNeverResetsCompletedRewards()=runTest {
        profile(); repo.garantirMissoes(day); val week=db.dao().systemWeek(MissaoEngine.inicioSemana(day)).first().single()
        repo.adaptarMissoes(day,SystemMode.HOME)
        assertEquals(SystemMode.HOME,MissaoEngine.mode(db.dao().systemDay(day)))
        assertEquals(week,db.dao().systemWeek(MissaoEngine.inicioSemana(day)).first().single())
        record(); repo.garantirMissoes(day)
        try { repo.adaptarMissoes(day,SystemMode.CARDIO); fail("Started orders must remain") } catch (_: IllegalArgumentException) { }
        assertEquals(SystemMode.HOME,MissaoEngine.mode(db.dao().systemDay(day)))
        assertEquals(930L,db.dao().perfil()!!.xpTotal)
    }
    @Test fun waterAwardsOnceWithoutRewardingHistoricalRecords()=runTest {
        profile(); repo.garantirMissoes(day)
        assertEquals(20,repo.registrarAgua(day,200)!!.xpGanho)
        assertNull(repo.registrarAgua(day,200)); assertNull(repo.registrarAgua(day-1,200))
        assertEquals(920L,db.dao().perfil()!!.xpTotal)
        assertEquals(2,db.dao().systemWater(day)); assertEquals(1,db.dao().systemWater(day-1))
    }
    @Test fun finishedCardioPaysItsOrdersAtomicallyWithoutConsumingTheCardioXpCap()=runTest {
        profile(); repo.garantirMissoes(day); repo.adaptarMissoes(day,SystemMode.CARDIO)
        val wall=LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()+43_200_000
        val clock=CardioClock(10_000,7,wall)
        repo.startCardio(CardioActivity.WALK,CardioGoal.FREE,0.0,clock)
        val row=db.dao().activeCardio()!!; val end=clock.copy(elapsed=clock.elapsed+900_000,wall=wall+900_000)
        val reward=repo.finishCardio(row.id,end,null,"EASY","")!!
        assertEquals(120,reward.xpGanho); assertEquals(2,reward.missoesConcluidas.size)
        assertEquals(30,db.dao().allCardio().single().xpGained)
        assertNull(repo.finishCardio(row.id,end,null,"EASY","")); assertNull(repo.garantirMissoes(day))
        assertEquals(1020L,db.dao().perfil()!!.xpTotal)
    }
    @Test fun weeklyRewardAndNewTypesSurvivePortableBackupWithoutReaward()=runTest {
        profile()
        val week=MissaoEngine.inicioSemana(day)
        // All seven days are fixture records; only this week's past and current days count.
        db.dao().salvarPreferencias(Preferencias(objetivo="FORCA",experiencia="INICIANTE",localPreferido="CASA",diasPorSemana=1,descansoPadraoSeg=90,metaProteinaG=120,rotinaAtiva=null,lembretesAtivos=false))
        record(week); val reward=repo.garantirMissoes(day)!!
        assertTrue(reward.missoesConcluidas.any { MissaoEngine.semanal(it) })
        val xp=db.dao().perfil()!!.xpTotal; val backup=repo.exportarBackupJson()
        repo.restaurarBackupJson(backup)
        assertEquals(xp,db.dao().perfil()!!.xpTotal); assertNull(repo.garantirMissoes(day))
        assertEquals(SystemMode.HOME,MissaoEngine.mode(db.dao().systemDay(day)))
        assertTrue(db.dao().systemWeek(week).first().single().concluida)
    }
    @Test fun existingLegacyDailyOrdersAreNotReplacedOnUpgrade()=runTest {
        profile(); val original=MissaoDiaria(epochDay=day,tipo="TREINAR_MUSCULO",descricao="Treino antigo",alvoMusculo="PEITO",alvoQtd=6,progresso=4,xpRecompensa=60)
        db.dao().inserirMissoes(listOf(original)); assertNull(repo.garantirMissoes(day))
        assertEquals(4,db.dao().systemDay(day).single().progresso)
        assertEquals("TREINAR_MUSCULO",db.dao().systemDay(day).single().tipo)
        assertEquals(900L,db.dao().perfil()!!.xpTotal)
    }
}
