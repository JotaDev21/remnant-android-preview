package com.liferpg.domain

import com.liferpg.data.SerieRegistro
import java.time.LocalDate

data class WeeklySnapshot(val review: WeeklyReview, val lastDay: Long, val closed: Boolean,
    val days: Set<Long>, val exercises: Int, val muscles: Map<String, Int>)

object WeeklyArchive {
    /** Past weeks end on Sunday; the current week ends today. No future or invalid rows. */
    fun build(history: List<SerieRegistro>, today: Long, goal: Int, count: Int = 8, movementDays: Set<Long> = emptySet()): List<WeeklySnapshot> {
        require(count in 1..52)
        val monday = today - LocalDate.ofEpochDay(today).dayOfWeek.value + 1
        val valid = history.filter { it.valido && it.epochDay <= today }
        return (0 until count).map { offset ->
            val first = monday - offset * 7L
            val end = minOf(first + 6, today)
            val sets = valid.filter { it.epochDay in first..end }
            WeeklySnapshot(EvolutionEngine.week(valid, end, goal, movementDays), end, offset > 0,
                (sets.map { it.epochDay } + movementDays.filter { it in first..end }).toSet(), sets.map { it.exercicioId }.distinct().size,
                sets.groupingBy { it.musculo }.eachCount().toList().sortedByDescending { it.second }.toMap())
        }
    }
}

enum class CommitmentLead { YOU, ALLY, TIED, DIFFERENT_WEEK }
data class WeeklyRivalry(val mine: Float, val ally: Float, val lead: CommitmentLead) {
    val comparable get() = lead != CommitmentLead.DIFFERENT_WEEK
}

object WeeklyCompetition {
    /** Everyone competes against their own commitment; five days is not required to beat a one-day goal. */
    fun compare(mine: WeeklyReview, allyWeek: Long, allyDays: Int, allyGoal: Int): WeeklyRivalry {
        require(allyDays in 0..7 && allyGoal in 1..7)
        val other = (allyDays.toFloat() / allyGoal).coerceIn(0f, 1f)
        val own = mine.progress
        val lead = when {
            mine.firstDay != allyWeek -> CommitmentLead.DIFFERENT_WEEK
            kotlin.math.abs(own - other) < .0001f -> CommitmentLead.TIED
            own > other -> CommitmentLead.YOU
            else -> CommitmentLead.ALLY
        }
        return WeeklyRivalry(own, other, lead)
    }
}
