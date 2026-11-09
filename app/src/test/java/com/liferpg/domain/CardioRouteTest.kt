package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class CardioRouteTest {
    private val first = CardioFix(-23.5505, -46.6333, 4f, 1000, 1)
    @Test fun realMovementCountsWhileNoiseStaleFixesAndTeleportingDoNot() {
        val initial = CardioRouteEngine.accept(CardioRouteState(segment = 1), first, 1000, "WALK")
        assertEquals(1, initial.points.size); assertEquals(0.0, initial.meters, .001)
        val next = first.copy(lat = first.lat + .00008, elapsed = 6000)
        val walked = CardioRouteEngine.accept(initial, next, 6000, "WALK")
        assertEquals(8.9, walked.meters, .2)
        listOf(next.copy(accuracy = 90f), next.copy(elapsed = 800), next.copy(lat = first.lat + .1, elapsed = 7000),
            next.copy(lat = Double.NaN), next.copy(lat = 91.0)).forEach { assertEquals(walked, CardioRouteEngine.accept(walked, it, 7000, "WALK")) }
        val noise = CardioRouteEngine.accept(walked, next.copy(elapsed = 6500, lat = next.lat + .000005), 6500, "WALK")
        assertEquals(walked.points, noise.points); assertEquals(walked.meters, noise.meters, .001)
        assertEquals(walked, CardioRouteEngine.accept(walked, next.copy(elapsed = 9000), 30_000, "WALK"))
    }
    @Test fun slowWalkingKeepsTheRouteConnectedWhileStationaryFixesNeverAddDistance() {
        val anchor = first.copy(accuracy = 25f)
        var state = CardioRouteEngine.accept(CardioRouteState(segment = 1), anchor, 1000, "WALK")
        for (second in 4..80 step 4) {
            val fix = anchor.copy(lat = anchor.lat + second * .00000225, elapsed = 1000 + second * 1000L)
            state = CardioRouteEngine.accept(state, fix, fix.elapsed, "WALK")
        }
        assertEquals(1, state.segment); assertTrue(state.meters > 17.0)
        assertEquals(1, state.points.map { it.segment }.distinct().size)
        for (second in 84..160 step 4) {
            val fix = state.points.last().copy(elapsed = 1000 + second * 1000L)
            val next = CardioRouteEngine.accept(state, fix, fix.elapsed, "WALK")
            assertEquals(state.meters, next.meters, .001); state = next
        }
        val gap = state.points.last().copy(lat = anchor.lat + .001, elapsed = state.lastSignalElapsed + 31_000)
        val resumed = CardioRouteEngine.accept(state, gap, gap.elapsed, "WALK")
        assertEquals(2, resumed.segment); assertEquals(state.meters, resumed.meters, .001)
    }
    @Test fun pauseAndSignalGapNeverAddADistanceConnectingSeparateSegments() {
        val initial = CardioRouteState(listOf(first), 0.0, segment = 1)
        val other = first.copy(lat = first.lat + .001, elapsed = 10_000, segment = 2)
        val resumed = CardioRouteEngine.accept(initial.copy(segment = 2), other, 10_000, "WALK")
        assertEquals(0.0, resumed.meters, .001); assertEquals(2, resumed.points.size)
        val gap = CardioRouteEngine.accept(initial, other.copy(segment = 1, elapsed = 60_000), 60_000, "WALK")
        assertEquals(0.0, gap.meters, .001); assertEquals(2, gap.segment)
        assertTrue(CardioRouteEngine.marks(gap.points).isEmpty())
    }
    @Test fun kilometerMarksInterpolateOnlyRecordedLegs() {
        val end = first.copy(lat = first.lat + .025, elapsed = 1_000_000)
        val marks = CardioRouteEngine.marks(listOf(first, end))
        assertEquals(listOf(1, 2), marks.map { it.km })
        assertEquals(1000.0, CardioRouteEngine.distance(first, first.copy(lat = marks[0].lat)), .1)
        assertTrue(CardioRouteEngine.marks(listOf(first, end.copy(segment = 2))).isEmpty())
        val partial = CardioRouteEngine.marks(listOf(first, first.copy(lat = first.lat + .01)), 1200.0)
        assertEquals(listOf(2), partial.map { it.km })
        assertEquals(800.0, CardioRouteEngine.distance(first, first.copy(lat = partial.single().lat)), .1)
        assertEquals("850 m", CardioRouteEngine.meters(850.0)); assertEquals("1,50 km", CardioRouteEngine.meters(1500.0))
    }
    @Test fun stepCounterNeverUsesTheBootTotalAndRebaselinesAfterResumeOrReset() {
        val tracker = SessionStepCounter()
        assertEquals(0L, tracker.sample(120_000)); assertEquals(31L, tracker.sample(120_031))
        assertEquals(0L, tracker.sample(120_031)); assertEquals(0L, tracker.sample(10)); assertEquals(4L, tracker.sample(14))
        val resumed = SessionStepCounter(); assertEquals(0L, resumed.sample(200_000)); assertEquals(20L, resumed.sample(200_020))
        assertEquals(3000L, resumed.sample(203_020)) // A genuine delayed/batched delivery is not discarded.
    }
}
