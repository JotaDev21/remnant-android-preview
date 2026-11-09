package com.liferpg.domain

enum class StreakDay { NEW, SECURED, PENDING, BROKEN }

data class TrainingStreak(val count: Int, val best: Int, val day: StreakDay, val nextMilestone: Int) {
    companion object {
        /** Opening the app never counts. Only the persisted day of a valid registered set does. */
        fun of(stored: Int, best: Int, lastTrainingDay: Long, today: Long): TrainingStreak {
            val state = when {
                lastTrainingDay <= 0 -> StreakDay.NEW
                lastTrainingDay == today -> StreakDay.SECURED
                lastTrainingDay == today - 1 && stored > 0 -> StreakDay.PENDING
                else -> StreakDay.BROKEN
            }
            val count = if (state == StreakDay.SECURED || state == StreakDay.PENDING) stored.coerceAtLeast(1) else 0
            val milestone = listOf(3, 7, 14, 30, 50, 100, 200, 365).firstOrNull { it > count }
                ?: ((count / 100 + 1) * 100)
            return TrainingStreak(count, maxOf(best, stored, count), state, milestone)
        }
    }
}
