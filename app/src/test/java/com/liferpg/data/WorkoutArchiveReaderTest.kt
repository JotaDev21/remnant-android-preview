package com.liferpg.data

import com.liferpg.data.sync.CircleCodec
import com.liferpg.domain.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class WorkoutArchiveReaderTest {
    private val ex=Exercicio("bench","Supino",Musculo.PEITO,emptyList(),"Barra",LocalTreino.ACADEMIA,ModoMedida.CARGA,1f,"",emptyList(),"",null,emptyList(),"")
    private fun session(receipt: String="")=TreinoSessao(1,20000,"Peito",50,2,1,1,600,601000,receiptJson=receipt)
    @Test fun exactReceiptPreservesKgRepsAndPrRatherThanUsingDayTotals() {
        val state=EstadoSessao("Peito",listOf(ItemSessao(ItemTreino(ex,3,8,12,90,""),listOf(SerieFeita(80f,10,null,true),SerieFeita(75f,12,null,false)))),iniciadaEm=1000)
        val result=WorkoutArchiveReader.workout(session(CircleCodec.receipt(state,601000)))
        assertTrue(result.exact);assertEquals(2,result.exercises.single().sets.size)
        assertEquals(80f,result.exercises.single().sets.first().kg);assertTrue(result.exercises.single().sets.first().pr)
        assertFalse(result.exercises.single().sets.last().pr);assertEquals(50,result.session.xpGanho)
    }
    @Test fun legacyAndCorruptReceiptsKeepSummaryWithoutInventingSets() {
        listOf("","{}","broken").forEach { body ->
            val result=WorkoutArchiveReader.workout(session(body))
            assertFalse(result.exact);assertTrue(result.exercises.isEmpty());assertEquals(2,result.session.series)
        }
    }
    @Test fun duplicatedExercisePrescriptionsMergeWithoutDiscardingSets() {
        val item=ItemSessao(ItemTreino(ex,3,8,12,90,""),listOf(SerieFeita(80f,10,null,false)))
        val state=EstadoSessao("Peito",listOf(item,item),iniciadaEm=1000)
        val result=WorkoutArchiveReader.workout(session(CircleCodec.receipt(state,601000)))
        assertEquals(1,result.exercises.size);assertEquals(2,result.exercises.single().sets.size)
    }
}
