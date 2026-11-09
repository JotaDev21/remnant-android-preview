package com.liferpg.domain

import com.liferpg.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class SystemMissionTest {
    private val day=LocalDate.of(2026,10,8).toEpochDay()
    private fun set(d: Long=day, valid: Boolean=true, exercise: String="Pushups", reps: Int=12)=
        SerieRegistro(exercicioId=exercise,musculo="PEITO",pesoKg=null,reps=reps,segundos=null,score=1f,xpGanho=10,epochDay=d,timestamp=1,valido=valid)
    private fun cardio(d: Long=day, ms: Long=900_000,status: String="FINISHED")=
        CardioEngine.start(CardioActivity.WALK,CardioGoal.FREE,0.0,CardioClock(0,1,1)).copy(epochDay=d,status=status,accumulatedMs=ms)
    @Test fun modesOfferAchievableOrdersWithoutRequiringDailyMaximalEffort() {
        SystemMode.entries.forEach { mode ->
            val orders=MissaoEngine.gerarMissoes(day,mode)
            assertEquals(3,orders.size); assertEquals(110,orders.sumOf { it.xpRecompensa })
            assertEquals(mode,MissaoEngine.mode(orders)); assertFalse(orders.any { it.tipo=="BATER_RECORDE" })
        }
    }
    @Test fun homeCountsOnlyValidRecordedBodyweightRepetitionsFromToday() {
        val order=MissaoEngine.gerarMissoes(day,SystemMode.HOME)[1]
        val facts=MissionFacts(day,listOf(set(),set(exercise="Bodyweight_Squat"),set(valid=false,reps=200),set(day-1),set(exercise="Legpress",reps=50)),emptyList(),0)
        val result=MissaoEngine.avancar(order,facts)
        assertTrue(result.awarded); assertEquals(24,result.mission.progresso)
        assertFalse(MissaoEngine.avancar(result.mission,facts).awarded)
    }
    @Test fun runningAndPausedCardioDoNotCompleteOrders() {
        val order=MissaoEngine.gerarMissoes(day,SystemMode.CARDIO)[1]
        val facts=MissionFacts(day,emptyList(),listOf(cardio(ms=600_000),cardio(ms=600_000),cardio(status="RUNNING"),cardio(day-1)),0)
        assertEquals(15,MissaoEngine.avancar(order,facts).mission.progresso)
        assertEquals(0,MissaoEngine.avancar(order,facts.copy(cardio=listOf(cardio(status="PAUSED")))).mission.progresso)
        val start=MissaoEngine.gerarMissoes(day)[0]
        assertFalse(MissaoEngine.avancar(start,facts.copy(cardio=listOf(cardio(ms=299_999)))).awarded)
    }
    @Test fun weeklyContractCountsDistinctDaysAndNeverRequiresSevenGymDays() {
        val week=MissaoEngine.gerarSemana(day,3); val monday=MissaoEngine.inicioSemana(day)
        val facts=MissionFacts(day,listOf(set(monday),set(monday),set(monday+1),set(monday-1),set(day+1)),listOf(cardio(monday),cardio(day),cardio(monday+2,100_000)),0)
        assertTrue(MissaoEngine.avancar(week,facts).awarded)
        assertEquals(3,MissaoEngine.avancar(week,facts).mission.progresso)
        assertEquals(1,MissaoEngine.gerarSemana(day,0).alvoQtd)
        assertEquals(7,MissaoEngine.gerarSemana(day,99).alvoQtd)
        assertFalse(MissaoEngine.avancar(week,facts.copy(day=monday+7)).awarded)
    }
    @Test fun legacyProgressAndExpiredRewardsArePreservedWithoutPassivePrCredit() {
        val old=MissaoDiaria(epochDay=day,tipo="BATER_RECORDE",descricao="Recorde",alvoMusculo=null,alvoQtd=2,progresso=1,xpRecompensa=80)
        val facts=MissionFacts(day,emptyList(),emptyList(),0)
        assertEquals(old,MissaoEngine.avancar(old,facts).mission)
        assertTrue(MissaoEngine.avancar(old,facts,true).awarded)
        assertEquals(old,MissaoEngine.avancar(old,facts.copy(day=day+1),true).mission)
        val completed=old.copy(progresso=2,concluida=true)
        assertFalse(MissaoEngine.avancar(completed,facts,true).awarded)
    }
}
