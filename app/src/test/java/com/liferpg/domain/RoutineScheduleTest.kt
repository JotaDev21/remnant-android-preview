package com.liferpg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RoutineScheduleTest {
    private fun routine(id: Long, name: String, vararg weekdays: Int) = RotinaResolvida(
        id = id,
        nome = name,
        itens = emptyList(),
        diasSemana = weekdays.toSet(),
    )

    @Test fun selectsRoutineForTheExactWeekday() {
        val monday = LocalDate.of(2026, 9, 14).toEpochDay()
        val routines = listOf(
            routine(1, "Peito e tríceps", 2, 4),
            routine(2, "Costas e bíceps", 1, 5),
        )

        assertEquals("Costas e bíceps", rotinaDoDia(routines, monday)?.nome)
    }

    @Test fun legacyUnscheduledRoutineIsNotOpenedAsTodaysWorkout() {
        val monday = LocalDate.of(2026, 9, 14).toEpochDay()

        assertNull(rotinaDoDia(listOf(routine(1, "Treino antigo")), monday))
    }

    @Test fun restDayHasNoRoutine() {
        val sunday = LocalDate.of(2026, 9, 20).toEpochDay()

        assertNull(rotinaDoDia(listOf(routine(1, "Costas e bíceps", 1, 3, 5)), sunday))
    }
}
