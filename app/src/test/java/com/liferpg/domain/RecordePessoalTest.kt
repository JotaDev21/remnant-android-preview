package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class RecordePessoalTest {
    private fun record(old: MarcaPessoal?, now: MarcaPessoal, mode: ModoMedida = ModoMedida.CARGA) =
        RecordePessoal("leg", "Leg press 45°", mode, old, now).apresentacao()

    @Test fun kilogramsAreActualLoadAndNeverTheEstimatedOneRepMax() {
        val r = record(MarcaPessoal(360f, 10, null, 6f), MarcaPessoal(400f, 11, null, 6.8f))
        assertEquals("Leg press 45°", r.nome)
        assertEquals(400f, r.para)
        assertEquals("360 kg × 10 reps → 400 kg × 11 reps", r.comparacao)
        assertEquals("+40 KG", r.ganho)
        assertEquals("11 repetições", r.detalhe)
    }

    @Test fun moreRepsWithTheSameLoadIsNotInventedKilograms() {
        val r = record(MarcaPessoal(80f, 8, null, 1f), MarcaPessoal(80f, 10, null, 1.1f))
        assertEquals(80f, r.para)
        assertEquals("+2 REPS", r.ganho)
    }

    @Test fun LowerLoadWithHigherScoreClearlyLabelsTheEstimatedImprovement() {
        val r = record(MarcaPessoal(100f, 1, null, 1f), MarcaPessoal(90f, 10, null, 1.2f))
        assertEquals(100f, r.de)
        assertEquals(90f, r.para)
        assertEquals("+20% FORÇA ESTIMADA", r.ganho)
        assertTrue(r.criterio.contains("estimada"))
    }

    @Test fun firstRecordDoesNotPretendTheUserPreviouslyLiftedZero() {
        val r = record(null, MarcaPessoal(42.5f, 8, null, 1f))
        assertEquals(42.5f, r.de)
        assertEquals("42,5 kg × 8 reps", r.comparacao)
        assertEquals("PRIMEIRA MARCA", r.ganho)
    }

    @Test fun bodyweightAndTimedRecordsUseTheirOwnUnits() {
        val reps = record(MarcaPessoal(null, 39, null, 39f), MarcaPessoal(null, 50, null, 50f), ModoMedida.REPS)
        assertEquals("REPETIÇÕES", reps.unidade)
        assertEquals("+11 REPS", reps.ganho)
        val time = record(MarcaPessoal(null, null, 90, 90f), MarcaPessoal(null, null, 120, 120f), ModoMedida.TEMPO)
        assertEquals("SEGUNDOS", time.unidade)
        assertEquals("90 s → 120 s", time.comparacao)
        assertEquals("+30 S", time.ganho)
    }
}
