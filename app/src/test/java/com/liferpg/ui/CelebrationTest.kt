package com.liferpg.ui

import com.liferpg.data.ResultadoSerie
import com.liferpg.domain.Patente
import org.junit.Assert.*
import org.junit.Test

class CelebrationTest {
    private val base = ResultadoSerie(10, false, Patente.BRONZE, Patente.BRONZE, 1, 1, 10f, emptyList(), emptyList())
    @Test fun ordinarySetsDoNotReplayDailyCelebration() {
        assertTrue(celebrationStages(base.copy(streakAtual = 7)).isEmpty())
        assertEquals(listOf("streak"), celebrationStages(base.copy(streakAtual = 7, primeiroTreinoDoDia = true)).map { it.kind })
    }
    @Test fun simultaneousAchievementsArePreservedInOrder() {
        val stages = celebrationStages(base.copy(nivelNovo = 2, patenteNova = Patente.PRATA, streakAtual = 2, primeiroTreinoDoDia = true))
        assertEquals(listOf("level", "rank", "streak"), stages.map { it.kind })
        assertEquals(Patente.PRATA, stages[1].rank)
    }
}
