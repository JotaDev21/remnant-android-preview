package com.liferpg.data.sync

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class CircleWeekMember(val uid: String, val nickname: String, val goal: Int,
    val dates: Set<LocalDate>, val workouts: Int, val sets: Int, val prs: Int, val seconds: Long) {
    val days get() = dates.size
    val progress get() = (days.toFloat() / goal).coerceAtMost(1f)
    val remaining get() = (goal - days).coerceAtLeast(0)
}

data class CircleWeek(val circleId: String, val start: LocalDate, val first: LocalDate,
    val current: LocalDate, val today: LocalDate, val timezone: String, val members: List<CircleWeekMember>) {
    val end get() = start.plusDays(6)
    val isCurrent get() = start == current
    val board get() = members.sortedWith(compareByDescending<CircleWeekMember> { it.progress }
        .thenBy { it.nickname.lowercase() }.thenBy { it.uid })
    fun position(member: CircleWeekMember) = members.count { it.progress > member.progress } + 1
    val goalsMet get() = members.count { it.remaining == 0 }
}

object CircleWeekCodec {
    fun read(body: String): CircleWeek {
        val r = JSONObject(body)
        val start = LocalDate.parse(r.getString("week_start"))
        val first = LocalDate.parse(r.getString("first_week"))
        val current = LocalDate.parse(r.getString("current_week"))
        val today = LocalDate.parse(r.getString("today"))
        require(listOf(start, first, current).all { it.dayOfWeek == DayOfWeek.MONDAY })
        require(first <= start && start <= current && first >= current.minusWeeks(11))
        require(today >= current && today <= current.plusDays(6))
        val timezone = r.getString("timezone"); ZoneId.of(timezone)
        val rows = r.getJSONArray("members"); require(rows.length() in 1..30)
        val members = (0 until rows.length()).map { i ->
            val m = rows.getJSONObject(i); val days = m.getJSONArray("dates")
            require(days.length() <= 7)
            val dates = (0 until days.length()).map { LocalDate.parse(days.getString(it)) }
            require(dates.distinct().size == dates.size && dates.all { it >= start && it <= start.plusDays(6) })
            val goal = m.getInt("goal"); val workouts = m.getInt("workouts")
            val sets = m.getInt("sets"); val prs = m.getInt("prs"); val seconds = m.getLong("seconds")
            require(goal in 1..7 && workouts >= dates.size && sets >= workouts && prs in 0..sets && seconds >= 0)
            require((workouts == 0) == dates.isEmpty() && (workouts != 0 || (sets == 0 && prs == 0 && seconds == 0L)))
            val uid = m.getString("uid"); require(uid.isNotBlank())
            CircleWeekMember(uid, m.getString("nickname"), goal, dates.toSet(), workouts, sets, prs, seconds)
        }
        require(members.map { it.uid }.distinct().size == members.size)
        val circle = r.getString("circle_id"); require(circle.isNotBlank())
        return CircleWeek(circle, start, first, current, today, timezone, members)
    }
}
