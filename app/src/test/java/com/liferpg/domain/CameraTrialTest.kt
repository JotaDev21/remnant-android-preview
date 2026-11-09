package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class CameraTrialTest {
    private val boss = CameraBoss.ASH_WARDEN

    @Test fun diamondRequiresFiftyCompleteRepsInOneAttempt() {
        var run = CameraTrialState(rank = Patente.DIAMANTE)
        repeat(39) { run = run.accept(boss, it + 1, (it + 1) * 2000L) }
        assertEquals(39, run.attemptReps)
        assertEquals(0, run.victories)
        run = run.interrupt()
        assertEquals(0, run.attemptReps)
        assertEquals(39, run.totalReps)
        assertEquals(1, run.deaths)
        repeat(49) { run = run.accept(boss, 40 + it, 100_000L + it * 2000) }
        assertEquals(0, run.victories)
        run = run.accept(boss, 89, 212_000L)
        // The final rep is too late: it cannot complete the expired attempt.
        assertEquals(0, run.victories)
        assertEquals(1, run.attemptReps)
    }

    @Test fun victoryAdvancesEnemyWithoutStoppingOrLosingNextRep() {
        var run = CameraTrialState(rank = Patente.DIAMANTE)
        repeat(50) { run = run.accept(boss, it + 1, (it + 1) * 2000L) }
        assertEquals(Patente.MESTRE, run.rank)
        assertEquals(1, run.victories)
        assertEquals(0, run.attemptReps)
        assertEquals(Patente.DIAMANTE.ordinal, run.bestRank)
        run = run.accept(boss, 51, 102_000)
        assertEquals(1, run.attemptReps)
        assertEquals(51, run.totalReps)
        assertEquals(1, run.victories)
        assertEquals(run, run.accept(boss, 51, 103_000))
    }

    @Test fun gracePeriodIsFifteenSecondsAndOnlyAppliesAfterFirstRep() {
        val waiting = CameraTrialState()
        assertEquals(waiting, waiting.expire(1_000_000))
        val active = waiting.accept(boss, 1, 1000)
        assertEquals(active, active.expire(15_999))
        val failed = active.expire(16_000)
        assertEquals(1, failed.deaths)
        assertEquals(0, failed.attemptReps)
        assertEquals(1, failed.totalReps)
        assertEquals(failed, failed.expire(100_000))
    }

    @Test fun everyTierHasDistinctEnemyAndMonotonicallyIncreasingTarget() {
        CameraBoss.entries.forEach { movement ->
            val enemies = Patente.ranqueadas.map { CameraTrials.enemy(movement, it) }
            assertEquals(14, enemies.map { it.name }.distinct().size)
            assertTrue(enemies.zipWithNext().all { (a, b) -> b.hp > a.hp })
        }
        assertEquals(50, CameraTrials.enemy(boss, Patente.DIAMANTE).hp)
        assertEquals(Patente.IMORTAL, CameraTrials.next(Patente.IMORTAL))
    }

    @Test fun failedAndSuccessfulAttemptsAreSeparateWorkoutSets() {
        var run = CameraTrialState(rank = Patente.DIAMANTE)
        repeat(39) { run = run.accept(boss, it + 1, (it + 1) * 2000L) }
        run = run.interrupt()
        repeat(50) { run = run.accept(boss, 40 + it, 100_000L + it * 2000L) }
        assertEquals(listOf(39, 50), run.completedSets)
        assertEquals(89, run.totalReps)
        assertEquals(1, run.victories)
    }
}
