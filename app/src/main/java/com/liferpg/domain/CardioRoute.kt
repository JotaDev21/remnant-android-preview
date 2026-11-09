package com.liferpg.domain

import kotlin.math.*

data class CardioFix(val lat: Double, val lon: Double, val accuracy: Float, val elapsed: Long, val segment: Int)
data class CardioRouteState(val points: List<CardioFix> = emptyList(), val meters: Double = 0.0,
    val steps: Long? = null, val segment: Int = 0, val lastSignalElapsed: Long = 0)
data class KilometerMark(val km: Int, val lat: Double, val lon: Double)

/** Distance comes from accepted GPS fixes, never from guessed stride or connecting a pause. */
object CardioRouteEngine {
    const val MAX_POINTS = 6000
    fun distance(a: CardioFix, b: CardioFix): Double {
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun accept(state: CardioRouteState, fix: CardioFix, nowElapsed: Long, activity: String): CardioRouteState {
        if (state.points.size >= MAX_POINTS || !fix.lat.isFinite() || !fix.lon.isFinite() || fix.lat !in -90.0..90.0 || fix.lon !in -180.0..180.0 ||
            !fix.accuracy.isFinite() || fix.accuracy !in 0f..25f || fix.elapsed <= 0 || nowElapsed - fix.elapsed !in 0L..15_000L) return state
        val previous = state.points.lastOrNull()
        val lastSignal = maxOf(state.lastSignalElapsed, previous?.elapsed ?: 0)
        if (previous != null && fix.elapsed <= lastSignal && fix.segment == previous.segment) return state
        var next = fix
        var delta = 0.0
        if (previous != null && previous.segment == fix.segment) {
            val gap = fix.elapsed - previous.elapsed
            // A filtered stationary/slow fix still proves GPS continuity. Comparing only
            // with the last moved anchor erased slow walking after 30 seconds.
            if (fix.elapsed - lastSignal > 30_000) next = fix.copy(segment = state.segment + 1)
            else {
                val traveled = distance(previous, fix)
                val maxSpeed = when (activity) { "WALK" -> 4.5; "BIKE" -> 25.0; else -> 12.0 }
                if (gap <= 0 || traveled > maxSpeed * gap / 1000.0 + 5.0) return state
                // Keep the last accepted anchor: many tiny fixes can eventually form real movement.
                if (traveled < maxOf(3.0, (previous.accuracy + fix.accuracy) * .35)) return state.copy(lastSignalElapsed = fix.elapsed)
                delta = traveled
            }
        }
        if (state.meters + delta > 200_000) return state
        return state.copy(points = state.points + next, meters = state.meters + delta, segment = next.segment, lastSignalElapsed = fix.elapsed)
    }
    fun pathMeters(points: List<CardioFix>) = points.zipWithNext().filter { (a, b) -> a.segment == b.segment }.sumOf { (a, b) -> distance(a, b) }
    fun marks(points: List<CardioFix>, initialMeters: Double = 0.0): List<KilometerMark> {
        val marks = mutableListOf<KilometerMark>(); var total = initialMeters.coerceIn(0.0, 200_000.0); var next = (floor(total / 1000) + 1) * 1000
        points.zipWithNext().forEach { (a, b) ->
            if (a.segment == b.segment) {
                val leg = distance(a, b)
                while (total + leg >= next && marks.size < 200) {
                    val fraction = if (leg > 0) ((next - total) / leg).coerceIn(0.0, 1.0) else 0.0
                    marks += KilometerMark((next / 1000).toInt(), a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)
                    next += 1000
                }
                total += leg
            }
        }
        return marks
    }
    fun meters(value: Double): String = if (value < 1000) "${value.roundToInt()} m" else "${CardioEngine.km(value / 1000)} km"
}

/** Counter is cumulative since boot. Rebaseline on every pause/resume/service restart. */
class SessionStepCounter {
    private var previous: Long? = null
    fun sample(total: Long): Long {
        if (total < 0) return 0
        val old = previous; previous = total
        return if (old == null || total < old) 0 else (total - old).coerceAtMost(200_000)
    }
}
