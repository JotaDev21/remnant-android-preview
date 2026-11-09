package com.liferpg.domain

enum class CameraBoss(val exerciseId: String, val title: String, val subtitle: String, val baseHp: Int, val hpStep: Int) {
    ASH_WARDEN("Pushups", "Carrasco das Cinzas", "Provação de flexões", 12, 8),
    STONE_COLOSSUS("Bodyweight_Squat", "Colosso da Cripta", "Provação de agachamentos", 16, 10);

    fun hpFor(stage: Int): Int = (baseHp + stage.coerceIn(0, 20) * hpStep).coerceAtMost(200)

    companion object {
        fun forExercise(id: String): CameraBoss? = entries.firstOrNull { it.exerciseId == id }
    }
}

data class BossAdvance(val stage: Int, val damage: Int, val wins: Int)

object BossCampaign {
    fun advance(boss: CameraBoss, stage: Int, damage: Int, wins: Int, validReps: Int): BossAdvance {
        require(validReps >= 0)
        var currentStage = stage.coerceAtLeast(0)
        var currentDamage = damage.coerceIn(0, boss.hpFor(currentStage) - 1)
        var currentWins = wins.coerceAtLeast(0)
        var remaining = validReps
        while (remaining > 0) {
            val hp = boss.hpFor(currentStage)
            val dealt = minOf(remaining, hp - currentDamage)
            currentDamage += dealt
            remaining -= dealt
            if (currentDamage >= hp) {
                currentStage++
                currentWins++
                currentDamage = 0
            }
        }
        return BossAdvance(currentStage, currentDamage, currentWins)
    }
}
