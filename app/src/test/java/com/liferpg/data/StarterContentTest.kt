package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import com.liferpg.domain.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StarterContentTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AppRepository(db, CatalogoRepo(context))
    }

    @After fun tearDown() = db.close()

    @Test fun `new profile receives three usable starter workouts`() = runTest {
        repo.criarPerfil(
            Perfil(nome = "Novo", classe = "AVENTUREIRO", pesoCorporalKg = 75f, criadoEm = 1L),
            Preferencias(
                objetivo = "GERAL",
                experiencia = "INICIANTE",
                localPreferido = "AMBOS",
                diasPorSemana = 3,
                descansoPadraoSeg = 90,
                metaProteinaG = 135,
                rotinaAtiva = null,
                lembretesAtivos = true,
            ),
        )

        val routines = db.dao().todasRotinas()
        assertEquals(3, routines.size)
        assertEquals(setOf(1, 3, 5), routines.flatMap { repo.decodificarDiasRotina(it.itensJson) }.toSet())
        assertTrue(routines.all { repo.decodificarItensRotina(it.itensJson).size >= 5 })
    }

    @Test fun `personal plan respects every schedule goal and uses only available exercises`() = runTest {
        val catalog = repo.catalogo.exercicios()
        for (local in LocalTreino.entries) for (days in 1..6) for (goal in Objetivo.entries) {
            val agenda = StarterPlan.diasPadrao(days)
            val plan = StarterPlan.criar(catalog, agenda, goal, NivelExperiencia.INICIANTE, local, Musculo.BICEPS)
            assertEquals(days, plan.size)
            assertEquals(agenda, plan.flatMap { it.dias }.toSet())
            assertTrue(plan.all { it.itens.isNotEmpty() && it.itens.size <= 6 && it.itens.all { i -> i.series == 2 } })
            assertTrue(plan.flatMap { it.itens }.all { item ->
                val ex = catalog.first { it.id == item.exercicioId }
                ex.local == LocalTreino.AMBOS || local == LocalTreino.AMBOS || ex.local == local
            })
            assertTrue(plan.all { w -> w.itens.map { it.exercicioId }.distinct().size == w.itens.size })
        }
    }
    @Test fun `repeated onboarding cannot replace an existing save or workouts`() = runTest {
        val original = Perfil(nome = "Jota", classe = "COLOSSO", pesoCorporalKg = 80f, xpTotal = 19048, criadoEm = 1L)
        db.dao().salvarPerfil(original)
        val prefs = TreinoEngine.preferenciasPadrao(80f)
        repo.salvarRotina("Minha rotina", listOf(ItemRotinaDef("Leg_Press", 4, 8, 12, 90)), setOf(2, 5))
        assertEquals(false, repo.criarPerfil(original.copy(nome = "Novo", xpTotal = 0), prefs))
        assertEquals(original, db.dao().perfil())
        assertEquals("Minha rotina", db.dao().todasRotinas().single().nome)
    }
    @Test fun `real leg press catalog rewards the stronger example with equal milestones`() = runTest {
        val leg = repo.catalogo.exercicios().first { it.id == "Leg_Press" }
        val lower = Experience.calcular(leg, ClassePersonagem.COLOSSO, RankEngine.scoreSerie(leg.modo, 80f, 360f, 10, null))
        val higher = Experience.calcular(leg, ClassePersonagem.COLOSSO, RankEngine.scoreSerie(leg.modo, 80f, 400f, 11, null))
        assertTrue(higher.treino > lower.treino)
    }
}
