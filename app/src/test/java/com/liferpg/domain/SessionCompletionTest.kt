package com.liferpg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCompletionTest {
    private val exercise = Exercicio(
        "test", "Remada teste", Musculo.COSTAS, emptyList(), "Máquina",
        LocalTreino.ACADEMIA, ModoMedida.CARGA, 1f, "", emptyList(), "",
        listOf(.2f, .4f, .6f, .8f, 1f, 1.2f, 1.4f), emptyList(), "",
    )
    private val prescription = ItemTreino(exercise, 3, 8, 12, 90, "Teste")
    private val set = SerieFeita(50f, 10, null, false)

    @Test fun exerciseCanBeCompletedManuallyBeforeAllSets() {
        val item = ItemSessao(prescription, feitas = listOf(set), conclusaoManual = true)

        assertTrue(item.concluido)
    }

    @Test fun plannedSetCountStillCompletesExerciseAutomatically() {
        val partial = ItemSessao(prescription, feitas = listOf(set, set))
        val complete = partial.copy(feitas = listOf(set, set, set))

        assertFalse(partial.concluido)
        assertTrue(complete.concluido)
    }

    @Test fun exerciseProgressUsesCompletedItems() {
        val state = EstadoSessao(
            nome = "Costas e bíceps",
            itens = listOf(ItemSessao(prescription, conclusaoManual = true), ItemSessao(prescription)),
            iniciadaEm = 0L,
        )

        assertEquals(.5f, state.progressoExercicios, .001f)
    }
}
