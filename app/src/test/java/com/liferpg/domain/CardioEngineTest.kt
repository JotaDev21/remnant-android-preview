package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CardioEngineTest {
    private val first = CardioClock(10_000, 4, 1_700_000_000_000)
    @Test fun sleepingPhoneAndRestartKeepTimeButPausesDoNotBecomeExercise() {
        val start = CardioEngine.start(CardioActivity.RUN, CardioGoal.TIME, 20.0, first)
        val tenMinutes = first.copy(elapsed = first.elapsed + 600_000, wall = first.wall + 600_000)
        assertEquals(600_000L, CardioEngine.elapsed(start, tenMinutes))
        val pause = CardioEngine.pause(start, tenMinutes)
        val afterBreak = tenMinutes.copy(elapsed = tenMinutes.elapsed + 300_000, wall = tenMinutes.wall + 300_000)
        assertEquals(600_000L, CardioEngine.elapsed(pause, afterBreak))
        val resumed = CardioEngine.resume(pause, afterBreak)
        assertEquals(660_000L, CardioEngine.elapsed(resumed, afterBreak.copy(elapsed = afterBreak.elapsed + 60_000)))
        assertEquals(660_000L, CardioEngine.elapsed(resumed, afterBreak.copy(elapsed = afterBreak.elapsed + 60_000, wall = 1)))
    }
    @Test fun rebootPausesAtTheLastCheckpointInsteadOfInventingOfflineDuration() {
        val start = CardioEngine.start(CardioActivity.BIKE, CardioGoal.FREE, 0.0, first)
        val checkpoint = CardioEngine.checkpoint(start, first.copy(elapsed = 70_000))
        val reboot = first.copy(elapsed = 90_000, boot = 5, wall = first.wall + 100_000_000)
        val restored = CardioEngine.checkpoint(checkpoint, reboot)
        assertEquals("PAUSED", restored.status); assertEquals(60_000L, restored.accumulatedMs)
        assertEquals(60_000L, CardioEngine.elapsed(restored, reboot.copy(elapsed = 120_000)))
        val otherPhone = CardioEngine.checkpoint(checkpoint, first.copy(elapsed = 600_000, device = "another-phone"))
        assertEquals("PAUSED", otherPhone.status); assertEquals(60_000L, otherPhone.accumulatedMs)
    }
    @Test fun distanceCannotInventPaceOrAnUnlimitedReward() {
        assertNull(CardioEngine.pace(600, null)); assertNull(CardioEngine.speed(600, 0.0)); assertNull(CardioEngine.pace(600, Double.NaN))
        assertEquals("6:00", CardioEngine.pace(1800, 5.0)); assertEquals("20,0", CardioEngine.speed(1800, 10.0))
        assertEquals(0, CardioEngine.reward(299, 0)); assertEquals(10, CardioEngine.reward(300, 0))
        assertEquals(120, CardioEngine.reward(21_600, 0)); assertEquals(2, CardioEngine.reward(1800, 118)); assertEquals(0, CardioEngine.reward(1800, 120))
        assertThrows(IllegalArgumentException::class.java) { CardioEngine.start(CardioActivity.AEROBIC, CardioGoal.DISTANCE, 3.0, first) }
    }
    @Test fun calendarWeekCountsActualDaysAndDoesNotFillFutureBars() {
        val monday = LocalDate.of(2026, 10, 5).toEpochDay()
        fun row(day: Long, seconds: Int, km: Double?) = CardioEngine.start(CardioActivity.RUN, CardioGoal.FREE, 0.0, first)
            .copy(status = "FINISHED", accumulatedMs = seconds * 1000L, epochDay = day, distanceKm = km, endedAt = first.wall + seconds * 1000)
        val rows = listOf(row(monday - 1, 600, 1.0), row(monday, 600, 2.0), row(monday, 1200, null), row(monday + 2, 300, .5), row(monday + 3, 600, 2.0))
        val week = CardioEngine.week(rows, monday + 2)
        assertEquals(35, week.minutes); assertEquals(3, week.sessions); assertEquals(2, week.days)
        assertEquals(2.5, week.distanceKm, .001); assertEquals(listOf(30, 0, 5, 0, 0, 0, 0), week.dailyMinutes)
        val review = EvolutionEngine.week(emptyList(), monday + 2, 3, CardioEngine.qualifyingDays(rows))
        assertEquals(2, review.trainedDays); assertEquals(0, review.sets)
        val archive = WeeklyArchive.build(emptyList(), monday + 2, 3, 2, CardioEngine.qualifyingDays(rows))
        assertEquals(2, archive.first().days.size); assertEquals(1, archive.last().days.size)
    }
}
