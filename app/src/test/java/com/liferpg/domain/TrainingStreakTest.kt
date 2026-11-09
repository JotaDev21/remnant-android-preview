package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class TrainingStreakTest {
    private val day = 20_000L

    @Test fun openingTheAppCannotCreateAStreak() {
        val state = TrainingStreak.of(0, 0, 0, day)
        assertEquals(StreakDay.NEW, state.day)
        assertEquals(0, state.count)
    }
    @Test fun aRegisteredDayIsSecuredUntilTheLocalDayChanges() {
        assertEquals(StreakDay.SECURED, TrainingStreak.of(12, 20, day, day).day)
        assertEquals(StreakDay.PENDING, TrainingStreak.of(12, 20, day, day + 1).day)
        assertEquals(StreakDay.BROKEN, TrainingStreak.of(12, 20, day, day + 2).day)
    }
    @Test fun losingTheCurrentStreakPreservesTheBest() {
        val state = TrainingStreak.of(12, 20, day - 2, day)
        assertEquals(0, state.count)
        assertEquals(20, state.best)
    }
    @Test fun milestoneMovesForwardAfterReachingIt() {
        assertEquals(7, TrainingStreak.of(6, 6, day, day).nextMilestone)
        assertEquals(14, TrainingStreak.of(7, 7, day, day).nextMilestone)
        assertEquals(500, TrainingStreak.of(400, 400, day, day).nextMilestone)
    }
    @Test fun futureActivityCannotSecureToday() {
        val state = TrainingStreak.of(10, 10, day + 1, day)
        assertEquals(StreakDay.BROKEN, state.day)
        assertEquals(0, state.count)
    }
    @Test fun theStoredCounterIsNotWrittenOrLostWhenDisplayingPendingDay() {
        val state = TrainingStreak.of(30, 30, day - 1, day)
        assertEquals(30, state.count)
        assertEquals(StreakDay.PENDING, state.day)
        assertEquals(30, state.best)
    }
}
