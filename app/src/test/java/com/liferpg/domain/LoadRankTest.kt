package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class LoadRankTest {
    private val benchmarks = listOf(.2f, .4f, .6f, .8f, 1f, 1.2f, 1.4f)
    @Test fun repetitionsAndBodyWeightAffectRankAsInRecoveredFormula() {
        val five = RankEngine.scoreSerie(ModoMedida.CARGA, 80f, 60f, 5, null)
        val ten = RankEngine.scoreSerie(ModoMedida.CARGA, 80f, 60f, 10, null)
        assertEquals(80f, RankEngine.e1rm(60f, 10), .001f)
        assertEquals(Patente.PLATINA, RankEngine.patenteDoScore(five, benchmarks))
        assertEquals(Patente.DIAMANTE, RankEngine.patenteDoScore(ten, benchmarks))
        assertTrue(RankEngine.scoreSerie(ModoMedida.CARGA, 100f, 60f, 10, null) < ten)
        assertEquals(60f, RankEngine.e1rm(60f, 1), .001f)
    }
    @Test fun muscleAveragesNormalisedExerciseRanks() {
        val a = Exercicio("a", "A", Musculo.PEITO, emptyList(), "Máquina", LocalTreino.ACADEMIA, ModoMedida.CARGA, 1f, "", emptyList(), "", benchmarks, emptyList(), "")
        val b = a.copy(id = "b", benchmarks = benchmarks.map { it * 2 })
        val result = RankEngine.ratingMusculo(listOf(a, b), mapOf("a" to 1f, "b" to 1.5f))
        assertEquals("a", result.exercicioChave?.id)
        assertEquals((RankEngine.ratingDoScore(1f,a.benchmarks)+RankEngine.ratingDoScore(1.5f,b.benchmarks))/2,result.rating,.001f)
    }

    @Test fun unrecordedAndUnrankableExercisesDoNotDiluteTheAverage() {
        val a=Exercicio("a","A",Musculo.PEITO,emptyList(),"Máquina",LocalTreino.ACADEMIA,ModoMedida.CARGA,1f,"",emptyList(),"",benchmarks,emptyList(),"")
        val b=a.copy(id="b")
        val c=a.copy(id="c",benchmarks=null)
        val r=RankEngine.ratingMusculo(listOf(a,b,c,a),mapOf("a" to .7f,"c" to 50f))
        assertEquals(RankEngine.ratingDoScore(.7f,benchmarks),r.rating,.001f)
        assertEquals(0f,RankEngine.ratingMusculo(listOf(a),emptyMap()).rating,.001f)
    }
    @Test fun goldTwoAndGoldFourProduceGoldThreeAtDivisionFloors() {
        val a=Exercicio("a","A",Musculo.PEITO,emptyList(),"Máquina",LocalTreino.ACADEMIA,ModoMedida.CARGA,1f,"",emptyList(),"",listOf(1f,2f,3f,4f,5f,6f,7f),emptyList(),"")
        val b=a.copy(id="b")
        val r=RankEngine.ratingMusculo(listOf(a,b),mapOf("a" to 3.5f,"b" to 3f))
        assertEquals(Patente.OURO,r.patente)
        assertEquals(Divisao.III,r.divisao)
        assertEquals(3250f,r.rating,.001f)
        assertEquals(r.patente,RankEngine.patenteDoMusculo(listOf(a,b),mapOf("a" to 3.5f,"b" to 3f)).patente)
    }
}
