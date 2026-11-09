package com.liferpg.domain

import com.liferpg.data.SerieRegistro
import com.liferpg.data.TreinoSessao
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class WorkoutArchiveTest {
    private val today=LocalDate.of(2026,10,9).toEpochDay()
    private fun row(id: Long,day: Long=today,ex: String="bench",valid: Boolean=true,time: Long=id) =
        SerieRegistro(id,ex,"PEITO",80f,10,null,1f,20,day,time,valido=valid,equipamento="Máquina A")
    @Test fun sameExerciseIsGroupedOnceAndInvalidAndStandaloneSetsRemainVisible() {
        val days=WorkoutArchiveEngine.days(listOf(row(2),row(1),row(3,valid=false),row(4,today-1,"camera")),emptyList())
        assertEquals(listOf(today,today-1),days.map { it.day });assertEquals(1,days.first().exercises.size)
        assertEquals(3,days.first().sets);assertFalse(days.first().exercises.first().sets.last().valid)
        assertEquals("Máquina A",days.first().exercises.first().sets.first().equipment)
    }
    @Test fun periodsUseMondayAndExactlyThirtyCalendarDaysAndExcludeFutureRows() {
        assertTrue(WorkoutArchiveEngine.contains(today-4,today,ArchivePeriod.WEEK))
        assertFalse(WorkoutArchiveEngine.contains(today-5,today,ArchivePeriod.WEEK))
        assertTrue(WorkoutArchiveEngine.contains(today-29,today,ArchivePeriod.MONTH))
        assertFalse(WorkoutArchiveEngine.contains(today-30,today,ArchivePeriod.MONTH))
        assertFalse(WorkoutArchiveEngine.contains(today+1,today,ArchivePeriod.ALL))
    }
    @Test fun searchIsAccentInsensitiveAndFiltersExerciseRowsWithinADay() {
        val archive=WorkoutArchive(emptyList(),listOf(ArchiveDay(today,listOf(
            ArchivedExercise("a","Tríceps francês","Tríceps",listOf(ArchivedSet(20f,10,null))),
            ArchivedExercise("b","Supino","Peito",listOf(ArchivedSet(80f,10,null)))))))
        assertEquals(listOf("a"),WorkoutArchiveEngine.days(archive,today,ArchivePeriod.ALL,"frances triceps").single().exercises.map { it.id })
        assertTrue(WorkoutArchiveEngine.days(archive,today,ArchivePeriod.ALL,"inexistente").isEmpty())
    }
    @Test fun archiveDoesNotTruncateOldSessionsAndMatchesExerciseNames() {
        val sessions=(1L..75L).map { id -> ArchivedWorkout(TreinoSessao(id,today-id,"Treino $id",20,1,1,0,600,id),
            listOf(ArchivedExercise("b","Supino inclinado","Peito",listOf(ArchivedSet(80f,10,null)))),true) }
        assertEquals(75,WorkoutArchiveEngine.sessions(WorkoutArchive(sessions,emptyList()),today,ArchivePeriod.ALL,"supino peito").size)
    }
}
