package com.liferpg.ui
import com.liferpg.data.ResultadoSerie
import com.liferpg.domain.*
import org.junit.Assert.*
import org.junit.Test
class MentorReactionTest {
    private val base = ResultadoSerie(10, false, Patente.BRONZE, Patente.BRONZE, 1, 1, 10f, emptyList(), emptyList())
    @Test fun reactsToActualRecordsAndPromotion() {
        assertTrue(mentorReaction(base.copy(novoRecorde = true)).contains("Novo recorde"))
        assertFalse(mentorReaction(base).contains("recorde"))
        assertTrue(mentorReaction(base.copy(patenteNova = Patente.PRATA)).contains("Prata"))
    }
    @Test fun zeroXpDoesNotClaimEarnedExperience() {
        assertFalse(mentorReaction(base.copy(xpGanho = 0)).contains("+"))
    }
    @Test fun missionRewardsNeverPretendThatWaterWasARecordedSet() {
        val text=mentorReaction(base.copy(tipoRegistro="MISSAO",xpGanho=20))
        assertTrue(text.contains("Ordem cumprida"));assertFalse(text.contains("Série"))
        assertFalse(mentorReaction(base.copy(tipoRegistro="MISSAO",nivelNovo=2)).contains("série"))
    }
    @Test fun quarterTurnExposesDepthWithoutChangingSize() {
        val v = Vertex3(1f, 0f, 0f).rotate((Math.PI / 2).toFloat(),0f)
        assertEquals(0f,v.x,.0001f)
        assertEquals(-1f,v.z,.0001f)
        val original = Vertex3(.4f,.8f,.3f)
        val rotated = original.rotate(.9f,-.3f)
        assertEquals(original.dot(original),rotated.dot(rotated),.0001f)
    }
}
