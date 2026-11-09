package com.liferpg.ui

import com.liferpg.data.ResultadoSerie
import com.liferpg.domain.Patente
import com.liferpg.domain.RecordePessoal
import com.liferpg.domain.MarcaPessoal
import com.liferpg.domain.ModoMedida
import org.junit.Assert.*
import org.junit.Test

class CelebrationStagesTest {
    private fun result(before: Int, after: Int, xp: Int = 84) = ResultadoSerie(
        xpGanho = xp, novoRecorde = false, patenteAnterior = Patente.BRONZE,
        patenteNova = Patente.BRONZE, nivelAnterior = before, nivelNovo = after,
        score = 100f, missoesConcluidas = emptyList(), conquistasNovas = emptyList(),
    )

    @Test fun ascensionUsesTheCommittedLevelsAndActualRewardEvenWhenSkippingLevels() {
        val stage = celebrationStages(result(15, 18)).single()
        assertEquals(15, stage.previousLevel)
        assertEquals(18, stage.number)
        assertEquals(84, stage.xpGain)
        assertFalse(stage.isDemo)
    }

    @Test fun unchangedLevelCannotProduceAnAscension() {
        assertTrue(celebrationStages(result(20, 20)).isEmpty())
    }

    @Test fun levelRankAndStreakRemainSeparateAndOrdered() {
        val stages = celebrationStages(result(19, 20).copy(
            patenteNova = Patente.DIAMANTE, primeiroTreinoDoDia = true, streakAtual = 7,
        ))
        assertEquals(listOf("level", "rank", "streak"), stages.map { it.kind })
        assertEquals(Patente.DIAMANTE, stages[1].rank)
        assertEquals(7, stages[2].number)
        assertNull(stages[1].xpGain)
    }

    @Test fun recordIsPresentedOnceBeforeOtherSimultaneousAwards() {
        val record = RecordePessoal("bench", "Supino", ModoMedida.CARGA, null, MarcaPessoal(80f, 5, null, 1f))
        val stages = celebrationStages(result(19, 20).copy(novoRecorde = true, recorde = record,
            patenteNova = Patente.DIAMANTE, primeiroTreinoDoDia = true, streakAtual = 7))
        assertEquals(listOf("pr", "level", "rank", "streak"), stages.map { it.kind })
        assertEquals(record, stages.first().record)
        assertFalse(stages.first().isDemo)
        assertTrue(celebrationStages(result(20, 20).copy(recorde = record)).isEmpty())
    }
}
