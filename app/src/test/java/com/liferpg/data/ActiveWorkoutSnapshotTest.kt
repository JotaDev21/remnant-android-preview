package com.liferpg.data

import com.liferpg.domain.EstadoSessao
import com.liferpg.domain.Exercicio
import com.liferpg.domain.ItemSessao
import com.liferpg.domain.ItemTreino
import com.liferpg.domain.LocalTreino
import com.liferpg.domain.ModoMedida
import com.liferpg.domain.Musculo
import com.liferpg.domain.Patente
import com.liferpg.domain.PatenteSubida
import com.liferpg.domain.SerieFeita
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutSnapshotTest {
    private val exercise = Exercicio(
        "row", "Remada", Musculo.COSTAS, emptyList(), "Máquina", LocalTreino.ACADEMIA,
        ModoMedida.CARGA, 1f, "", listOf("Execute"), "Controle", listOf(.2f, .4f, .6f, .8f, 1f, 1.2f, 1.4f), emptyList(), "",
    )

    @Test fun activeWorkoutRestoresEveryUserVisibleState() {
        val second = exercise.copy(id = "curl", nome = "Rosca")
        val state = EstadoSessao(
            nome = "Costas e bíceps",
            itens = listOf(
                ItemSessao(ItemTreino(exercise, 3, 8, 12, 90, "Sua rotina"), listOf(SerieFeita(50f, 10, null, true))),
                ItemSessao(ItemTreino(second, 4, 10, 15, 60, "Sua rotina"), conclusaoManual = true),
            ),
            indiceAtual = 1,
            iniciadaEm = 1234L,
            xpGanho = 42,
            recordes = listOf("Remada"),
            patentesSubidas = listOf(PatenteSubida(Musculo.COSTAS, Patente.OURO)),
        )

        val restored = state.toActiveSnapshot().resolve(listOf(exercise, second))!!

        assertEquals("Costas e bíceps", restored.nome)
        assertEquals(1, restored.indiceAtual)
        assertEquals(42, restored.xpGanho)
        assertEquals(1, restored.seriesFeitas)
        assertTrue(restored.itens[1].conclusaoManual)
        assertEquals(Patente.OURO, restored.patentesSubidas.single().patente)
    }

    @Test fun snapshotWithoutAnyKnownExerciseIsRejected() {
        val state = EstadoSessao(
            nome = "Treino",
            itens = listOf(ItemSessao(ItemTreino(exercise, 3, 8, 12, 90, "Sua rotina"))),
            iniciadaEm = 1L,
        )

        assertNull(state.toActiveSnapshot().resolve(emptyList()))
    }
}
