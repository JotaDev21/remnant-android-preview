package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class ExperienceTest {
    private val leg = Exercicio("leg", "Leg press", Musculo.QUADRICEPS, emptyList(), "Máquina",
        LocalTreino.ACADEMIA, ModoMedida.CARGA, 1.2f, "Composto", emptyList(), "",
        listOf(1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f), emptyList(), "")
    private fun xp(kg: Float, reps: Int) = Experience.calcular(leg, ClassePersonagem.COLOSSO,
        RankEngine.scoreSerie(ModoMedida.CARGA, 80f, kg, reps, null))

    @Test fun legPress400By11EarnsMoreTrainingXpThan360By10WithEqualBonuses() {
        assertTrue(xp(400f, 11).treino > xp(360f, 10).treino)
        assertEquals(0, xp(400f, 11).recorde)
    }
    @Test fun moreWeightOrMoreRepsCannotReduceTrainingXp() {
        for (weight in 20..400 step 10) for (reps in 1..20) {
            assertTrue(xp(weight + 10f, reps).treino >= xp(weight.toFloat(), reps).treino)
            assertTrue(xp(weight.toFloat(), reps + 1).treino >= xp(weight.toFloat(), reps).treino)
        }
    }
    @Test fun milestonesAreExplicitAndNotDisguisedAsPerformance() {
        val score = RankEngine.scoreSerie(ModoMedida.CARGA, 80f, 360f, 10, null)
        val reward = Experience.calcular(leg, ClassePersonagem.COLOSSO, score, true, true).copy(missoes = 75)
        assertEquals(xp(360f, 10).treino + 50 + 150 + 75, reward.total)
        assertTrue(reward.detalhes().contains("PR +50"))
        assertTrue(reward.detalhes().contains("Missões +75"))
    }
    @Test fun invalidMeasurementsCannotAwardExperience() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(0, Experience.calcular(leg, ClassePersonagem.COLOSSO, it, true, true).total)
        }
    }
    @Test fun repsAndTimeUseTheirOwnReferencesAndBoundedGrowth() {
        for (mode in listOf(ModoMedida.REPS, ModoMedida.TEMPO)) {
            val ex = leg.copy(modo = mode, benchmarks = null)
            assertTrue(Experience.calcular(ex, ClassePersonagem.MONGE, 50f).treino >
                Experience.calcular(ex, ClassePersonagem.MONGE, 20f).treino)
            assertTrue(Experience.calcular(ex, ClassePersonagem.MONGE, 100_000f).desempenho <= 90)
        }
    }
    @Test fun machineBenchmarkPreventsRawKgFromDominatingOtherExercises() {
        val bench = leg.copy(musculo = Musculo.PEITO, benchmarks = leg.benchmarks!!.map { it / 2 })
        assertEquals(Experience.calcular(leg, ClassePersonagem.AVENTUREIRO, 4f).treino,
            Experience.calcular(bench, ClassePersonagem.AVENTUREIRO, 2f).treino)
    }
}
