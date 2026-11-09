package com.liferpg.domain

import com.liferpg.data.SerieRegistro
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeeklyArchiveTest {
    private val monday = LocalDate.of(2026, 10, 5).toEpochDay()
    private fun row(day: Long, id: String = "bench", muscle: String = "PEITO", kg: Float = 60f,
        gear: String = "A", valid: Boolean = true, time: Long = day * 1000) =
        SerieRegistro(exercicioId = id, musculo = muscle, pesoKg = kg, reps = 10, segundos = null,
            score = 1f, xpGanho = 10, epochDay = day, timestamp = time, valido = valid, equipamento = gear)

    @Test fun currentWeekStopsTodayAndPreviousWeeksCloseOnSundayWithoutOverlap() {
        val history = listOf(row(monday - 1), row(monday), row(monday + 3), row(monday + 4),
            row(monday + 2, valid = false), row(monday - 7))
        val weeks = WeeklyArchive.build(history, monday + 3, 3, 3)
        assertEquals(monday + 3, weeks[0].lastDay)
        assertEquals(setOf(monday, monday + 3), weeks[0].days)
        assertEquals(2, weeks[0].review.sets)
        assertFalse(weeks[0].closed)
        assertEquals(monday - 1, weeks[1].lastDay)
        assertEquals(monday - 7, weeks[1].review.firstDay)
        assertEquals(setOf(monday - 7, monday - 1), weeks[1].days)
        assertTrue(weeks[1].closed)
        assertTrue(weeks[0].days.intersect(weeks[1].days).isEmpty())
        assertEquals(0, weeks[2].review.sets)
    }

    @Test fun sundayStillBelongsToTheCurrentWeekAndNextMondayOpensAnother() {
        val history = listOf(row(monday + 6), row(monday + 7))
        assertEquals(1, WeeklyArchive.build(history, monday + 6, 3).first().review.sets)
        val next = WeeklyArchive.build(history, monday + 7, 3)
        assertEquals(setOf(monday + 7), next[0].days)
        assertEquals(setOf(monday + 6), next[1].days)
    }

    @Test fun countsExercisesAndMuscleSetsSeparatelyFromTrainingDays() {
        val rows = listOf(row(monday), row(monday), row(monday, "incline"),
            row(monday + 1, "squat", "QUADRICEPS"), row(monday + 1, "squat", "QUADRICEPS"))
        val snapshot = WeeklyArchive.build(rows, monday + 3, 3).first()
        assertEquals(2, snapshot.review.trainedDays)
        assertEquals(5, snapshot.review.sets)
        assertEquals(3, snapshot.exercises)
        assertEquals(mapOf("PEITO" to 3, "QUADRICEPS" to 2), snapshot.muscles)
    }

    @Test fun equipmentReferenceIsChronologicalRatherThanDependentOnDatabaseRowOrder() {
        val rows = listOf(row(monday - 1, kg = 50f, gear = "A"), row(monday - 1, kg = 100f, gear = "B", time = 1),
            row(monday, kg = 110f, gear = "B"), row(monday + 1, kg = 60f, gear = "A"))
        val forward = EvolutionEngine.week(rows, monday + 3, 3)
        val backward = EvolutionEngine.week(rows.reversed(), monday + 3, 3)
        assertEquals(forward, backward)
        assertEquals(20f, forward.improvements.single().second, .01f)
        val unmatched = rows + row(monday + 2, kg = 300f, gear = "C")
        assertTrue(EvolutionEngine.week(unmatched, monday + 3, 3).improvements.isEmpty())
    }

    @Test fun completedPersonalCommitmentsTieRegardlessOfNumberOfTrainingDays() {
        val mine = WeeklyReview(monday, 1, 1, 3, emptyList(), "")
        val result = WeeklyCompetition.compare(mine, monday, 5, 5)
        assertEquals(CommitmentLead.TIED, result.lead)
        assertEquals(1f, result.mine, .001f)
        assertEquals(1f, result.ally, .001f)
    }

    @Test fun comparesGoalPercentagesAndCapsExtraDays() {
        val mine = WeeklyReview(monday, 3, 5, 10, emptyList(), "")
        val result = WeeklyCompetition.compare(mine, monday, 2, 2)
        assertEquals(CommitmentLead.ALLY, result.lead)
        assertEquals(.6f, result.mine, .001f)
        assertEquals(1f, WeeklyCompetition.compare(mine, monday, 7, 2).ally, .001f)
    }

    @Test fun staleFriendWeekCannotProduceALeader() {
        val result = WeeklyCompetition.compare(WeeklyReview(monday, 7, 3, 20, emptyList(), ""), monday - 7, 0, 3)
        assertEquals(CommitmentLead.DIFFERENT_WEEK, result.lead)
        assertFalse(result.comparable)
    }

    @Test fun rejectsMalformedCountsInsteadOfInventingProgress() {
        assertThrows(IllegalArgumentException::class.java) { WeeklyArchive.build(emptyList(), monday, 3, 0) }
        val mine = WeeklyReview(monday, 0, 3, 0, emptyList(), "")
        assertThrows(IllegalArgumentException::class.java) { WeeklyCompetition.compare(mine, monday, 8, 3) }
        assertThrows(IllegalArgumentException::class.java) { WeeklyCompetition.compare(mine, monday, 2, 0) }
    }
}
