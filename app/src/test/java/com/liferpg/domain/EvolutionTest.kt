package com.liferpg.domain

import com.liferpg.data.SerieRegistro
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EvolutionTest {
    private val today = LocalDate.of(2026, 10, 8).toEpochDay()
    private val ex = Exercicio("bench","Supino",Musculo.PEITO,emptyList(),"Máquina",LocalTreino.ACADEMIA,ModoMedida.CARGA,1f,"",emptyList(),"",listOf(.2f,.4f,.6f,.8f,1f,1.2f,1.4f),emptyList(),"")
    private val item = ItemTreino(ex,3,8,12,90,"")
    private fun row(day:Long=today-1,reps:Int=12,kg:Float=60f,valid:Boolean=true,effort:String="",gear:String="",time:Long=day*1000) =
        SerieRegistro(exercicioId=ex.id,musculo="PEITO",pesoKg=kg,reps=reps,segundos=null,score=.8f,xpGanho=10,epochDay=day,timestamp=time,valido=valid,esforco=effort,equipamento=gear)
    private fun target(rows:List<SerieRegistro>,gear:EquipmentSetup=EquipmentSetup(step=2.5f),current:List<SerieRegistro> = emptyList()) =
        AdaptiveProgression.target(item,rows,gear,today*1000,today,current)
    @Test fun increasesOnlyAfterAllPlannedSetsReachTheCeiling() {
        val rows=List(3){ row(time=(today-1)*1000+it) }
        assertEquals(62.5f,target(rows).kg!!,.001f);assertEquals(8,target(rows).reps)
        assertEquals(60f,target(rows.take(2)).kg!!,.001f)
        assertEquals(60f,target(rows.dropLast(1)+row(reps=10)).kg!!,.001f)
        assertEquals(11,target(rows.dropLast(1)+row(reps=10)).reps)
    }
    @Test fun followsMostRecentSessionRatherThanBestLifetimeSet() {
        val rows=listOf(row(day=today-9,kg=100f),row(day=today-1,kg=50f,reps=9))
        assertEquals(50f,target(rows).kg!!,.001f);assertEquals(10,target(rows).reps)
    }
    @Test fun excludesInvalidFutureAndDifferentMachines() {
        val rows=listOf(row(valid=false),row(day=today+1),row(gear="Outra máquina"))
        assertFalse(target(rows).hasReference);assertNull(target(rows).kg)
    }
    @Test fun difficultFeedbackInCurrentOrPreviousSessionBlocksAnIncrease() {
        val rows=List(3){row()}
        assertEquals(60f,target(rows,current=listOf(row(day=today,effort="PESADO"))).kg!!,.001f)
        assertEquals(60f,target(rows.dropLast(1)+row(effort="PESADO")).kg!!,.001f)
    }
    @Test fun returnAfterTwoWeeksAndLargeMachineJumpsAreConservative() {
        assertEquals(60f,target(List(3){row(day=today-14)}).kg!!,.001f)
        assertEquals(60f,target(List(3){row()},EquipmentSetup(step=20f)).kg!!,.001f)
        assertEquals(60f,target(List(3){row()},EquipmentSetup(step=2.5f,maximum=60f)).kg!!,.001f)
        assertEquals(60f,target(List(3){row()},EquipmentSetup()).kg!!,.001f)
    }
    @Test fun incompleteSetsDoNotCreateAPhantomRepetitionGoal() {
        assertEquals(6,target(listOf(row(reps=6))).reps)
    }
    @Test fun weeklyCommitmentUsesCalendarDaysAndExcludesInvalidOrFutureRows() {
        val start=today-3 // Thursday, week starts Monday.
        val rows=listOf(row(day=start-1),row(day=start),row(day=start),row(day=today,valid=false),row(day=today+1))
        val review=EvolutionEngine.week(rows,today,3)
        assertEquals(start,review.firstDay);assertEquals(1,review.trainedDays);assertEquals(2,review.sets)
    }
    @Test fun weeklyEvolutionComparesTheSameMachineToPreviousSession() {
        val rows=listOf(row(day=today-9,kg=50f),row(day=today-4,kg=60f),row(kg=70f),row(kg=200f,gear="Other",time=today*1000))
        val review=EvolutionEngine.week(rows,today,3)
        assertTrue(review.improvements.isEmpty()) // Latest current machine has no baseline.
        val withReference=EvolutionEngine.week(rows.dropLast(1),today,3)
        assertEquals(1,withReference.improvements.size);assertEquals(16.6667f,withReference.improvements.single().second,.01f)
    }
    @Test fun campaignDoesNotCountDaysOrRecordsBeforeItsCreation() {
        val c=EvolutionEngine.start("Forja",today,today*1000+10,4,1,listOf(row(day=today,kg=60f)))
        val rows=listOf(row(day=today,time=today*1000),row(day=today,time=today*1000+20,kg=65f),row(day=today,time=today*1000+30,kg=65f),row(day=today+1,valid=false))
        val view=EvolutionEngine.campaign(c,rows,today)
        assertEquals(1,view.chapters.first().days);assertEquals(listOf("bench"),view.improvements);assertFalse(view.conquered)
    }
    @Test fun changingEquipmentDoesNotCreateACampaignRecord() {
        val c=EvolutionEngine.start("Forja",today,today*1000+10,4,1,listOf(row()))
        assertTrue(EvolutionEngine.campaign(c,listOf(row(day=today,time=today*1000+20,kg=200f,gear="Other")),today).improvements.isEmpty())
    }
    @Test fun campaignVictoryRequiresEveryWeekAndTheCampaignToEnd() {
        val c=EvolutionEngine.start("Forja",today,today*1000,4,1,emptyList())
        val rows=List(4){ row(day=today+7*it) }
        assertFalse(EvolutionEngine.campaign(c,rows,today+27).conquered)
        assertTrue(EvolutionEngine.campaign(c,rows,today+28).conquered)
        assertFalse(EvolutionEngine.campaign(c,rows.dropLast(1),today+28).conquered)
    }
    @Test fun archivingClosesHistoryWithoutDeletingIt() {
        val c=EvolutionEngine.start("Forja",today,today*1000,4,1,emptyList()).copy(archived=true,closedAt=(today+1)*1000)
        val view=EvolutionEngine.campaign(c,listOf(row(day=today),row(day=today+8)),today+28)
        assertEquals(1,view.chapters.sumOf{it.days});assertFalse(view.conquered)
    }
}
