package com.liferpg.data

import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.BuscaExercicios
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogoRepoTest {
    @Test
    fun `bundled arsenal is complete`() = runTest {
        val exercises = CatalogoRepo(ApplicationProvider.getApplicationContext()).exercicios()

        assertEquals(412, exercises.size)
        assertTrue(exercises.all { it.id.isNotBlank() && it.nome.isNotBlank() })
        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        assertTrue(exercises.groupBy { it.musculo }.values.all { it.size >= 19 })
        assertTrue(exercises.count { it.imagens.size >= 2 } >= 380)
        assertTrue(exercises.any { it.nome == "Tríceps Testa na Polia Alta em Pé" })
        assertTrue(exercises.any { it.nome == "Cadeira Abdutora" })
        assertTrue(exercises.any { it.nome == "Remada Unilateral na Barra Landmine" })
    }

    @Test
    fun `gym variants remain discoverable with common names and without accents`() = runTest {
        val exercises = CatalogoRepo(ApplicationProvider.getApplicationContext()).exercicios()

        assertTrue(exercises.any { BuscaExercicios.corresponde(it, "supino inclinado maquina") })
        assertTrue(exercises.any { BuscaExercicios.corresponde(it, "triceps frances polia") })
        assertTrue(exercises.any { BuscaExercicios.corresponde(it, "hack squat") })
        assertTrue(exercises.any { BuscaExercicios.corresponde(it, "triceps testa polia pe") })
    }
}
