package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalRecordRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private val ex = Exercicio("test-bench", "Supino teste", Musculo.PEITO, emptyList(), "Barra",
        LocalTreino.ACADEMIA, ModoMedida.CARGA, 1f, "Composto", emptyList(), "", null, emptyList(), "")
    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AppRepository(db, CatalogoRepo(context))
    }
    @After fun tearDown() = db.close()
    private suspend fun old(weight: Float, reps: Int, score: Float, valid: Boolean = true) {
        db.dao().inserirSerie(SerieRegistro(exercicioId = ex.id, musculo = "PEITO", pesoKg = weight,
            reps = reps, segundos = null, score = score, xpGanho = 10,
            epochDay = LocalDate.now().toEpochDay(), timestamp = System.currentTimeMillis() - 60_000, valido = valid))
    }

    @Test fun previousSnapshotIsTheValidScoreWinnerAndNotTheHeaviestOrSuspectSet() = runTest {
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "AVENTUREIRO", pesoCorporalKg = 80f, criadoEm = 1L))
        old(100f, 1, 1.25f)
        old(90f, 6, 1.35f)
        old(200f, 10, 5f, false)
        val result = repo.registrarSerie(ex, 95f, 6, null) as RegistroSerie.Ok
        assertTrue(result.resultado.novoRecorde)
        val snapshot = requireNotNull(result.resultado.recorde)
        assertEquals(90f, snapshot.anterior?.pesoKg)
        assertEquals(6, snapshot.anterior?.reps)
        assertEquals(95f, snapshot.atual.pesoKg)
        assertEquals("Supino teste", snapshot.exercicioNome)
        assertEquals(4, db.dao().todasSeries().size)
        assertEquals(95f, db.dao().melhorSerieDe(ex.id)?.pesoKg)
    }

    @Test fun firstMarkHasNoInventedPreviousSet() = runTest {
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "AVENTUREIRO", pesoCorporalKg = 80f, criadoEm = 1L))
        val first = repo.registrarSerie(ex, 80f, 5, null) as RegistroSerie.Ok
        assertNull(requireNotNull(first.resultado.recorde).anterior)
        assertTrue(first.resultado.novoRecorde)
        assertEquals(80f, first.resultado.recorde?.atual?.pesoKg)
    }

    @Test fun equalScoreCannotProduceAnotherRecord() = runTest {
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "AVENTUREIRO", pesoCorporalKg = 80f, criadoEm = 1L))
        old(80f, 5, RankEngine.scoreSerie(ModoMedida.CARGA, 80f, 80f, 5, null))
        val repeated = repo.registrarSerie(ex, 80f, 5, null) as RegistroSerie.Ok
        assertFalse(repeated.resultado.novoRecorde)
        assertNull(repeated.resultado.recorde)
    }
    @Test fun xpReceiptMatchesPersistedSetMissionsAndPreservesHistoricalXp() = runTest {
        val originalXp = 19048L
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "AVENTUREIRO", pesoCorporalKg = 80f,
            xpTotal = originalXp, criadoEm = 1L))
        old(80f, 5, RankEngine.scoreSerie(ModoMedida.CARGA, 80f, 80f, 5, null))
        val result = (repo.registrarSerie(ex, 90f, 6, null) as RegistroSerie.Ok).resultado
        val receipt = requireNotNull(result.experiencia)
        assertEquals(receipt.total, result.xpGanho)
        assertEquals(result.missoesConcluidas.sumOf { it.xpRecompensa }, receipt.missoes)
        assertEquals(originalXp + receipt.total, db.dao().perfil()!!.xpTotal)
        val sets = db.dao().todasSeries()
        assertEquals(10, sets.first().xpGanho)
        assertEquals(receipt.total - receipt.missoes, sets.last().xpGanho)
    }
    @Test fun suspectSetCannotAwardPerformanceBonusesOrMissions() = runTest {
        db.dao().salvarPerfil(Perfil(nome = "Teste", classe = "AVENTUREIRO", pesoCorporalKg = 80f,
            xpTotal = 1000L, criadoEm = 1L))
        old(80f, 1, 1f)
        val result = repo.registrarSerie(ex, 300f, 20, null) as RegistroSerie.Ok
        assertTrue(result.suspeito)
        assertEquals(0, result.resultado.xpGanho)
        assertEquals(0, result.resultado.experiencia!!.total)
        assertEquals(1000L, db.dao().perfil()!!.xpTotal)
    }
}
