package com.liferpg.domain

import com.liferpg.data.sync.RecordeAmigo
import com.liferpg.data.sync.RotinaAmigo
import org.junit.Assert.*
import org.junit.Test

class FriendCompetitionTest {
    private fun record(id: String, kg: Float? = 80f, mode: String = "CARGA", reps: Int = 10, seconds: Int = 0) =
        RecordeAmigo(id, id, "Pernas", mode, kg, reps, seconds, 20000, "Ouro II", 12)

    @Test fun differentMachinesAndAbsentRecordsNeverProduceWins() {
        val rows = CompeticaoAmigos.comparar(listOf(record("leg45", 200f)), listOf(record("legHorizontal", 80f)))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.lideranca == LiderancaExercicio.SEM_COMPARACAO })
    }

    @Test fun kilogramsAreComparedWithoutInventingAnEstimatedMaximum() {
        val mine = record("extensora", 80f, reps=1)
        val theirs = record("extensora", 75f, reps=20)
        assertEquals(LiderancaExercicio.VOCE, CompeticaoAmigos.comparar(listOf(mine), listOf(theirs)).single().lideranca)
        assertEquals(LiderancaExercicio.EMPATE, CompeticaoAmigos.comparar(listOf(mine), listOf(mine.copy(reps=15))).single().lideranca)
    }

    @Test fun cameraAndTimedExercisesUseTheirOwnMeasurement() {
        assertEquals(LiderancaExercicio.ALIADO, CompeticaoAmigos.comparar(listOf(record("push",null,"REPS",12)),listOf(record("push",null,"REPS",15))).single().lideranca)
        assertEquals(LiderancaExercicio.VOCE, CompeticaoAmigos.comparar(listOf(record("plank",null,"TEMPO",seconds=90)),listOf(record("plank",null,"TEMPO",seconds=60))).single().lideranca)
        assertEquals(LiderancaExercicio.SEM_COMPARACAO, CompeticaoAmigos.comparar(listOf(record("push",null,"REPS")),listOf(record("push",40f))).single().lideranca)
    }

    @Test fun importingKeepsPrescriptionAndRejectsMissingIdsInvalidRangesAndDuplicates() {
        val exercise = Exercicio("bench", "Supino", Musculo.PEITO, emptyList(), "Barra", LocalTreino.ACADEMIA,
            ModoMedida.CARGA, 1f, "Composto", emptyList(), "", null, emptyList(), "")
        val valid = ItemRotinaDef("bench",4,8,12,90)
        val routine = RotinaAmigo("Push",listOf("Supino"),listOf(1),listOf(valid,valid,valid.copy(exercicioId="unknown"),valid.copy(series=99),valid.copy(repMin=20,repMax=10)))
        assertEquals(listOf(valid),CompeticaoAmigos.itensImportaveis(routine,listOf(exercise)))
        assertEquals(emptyList<ItemRotinaDef>(),CompeticaoAmigos.itensImportaveis(routine.copy(itens=emptyList()),listOf(exercise)))
    }
}
