package com.liferpg.domain

data class TrialEnemy(val rank: Patente, val name: String, val hp: Int, val atlasIndex: Int)

object CameraTrials {
    const val BREAK_MS = 15_000L
    private val names = listOf("Cavaleiro Oco", "Sentinela Cega", "Carrasco da Coroa", "Espectro das Marés",
        "Lobo da Geada", "Oráculo do Vazio", "Demônio das Cinzas", "Devorador de Ferro",
        "Lâmina do Exílio", "Fera Escarlate", "Guardião da Cripta", "Soberano da Tempestade",
        "Anjo da Ruína", "Rei do Sol Negro")
    private val pushHp = listOf(10, 20, 30, 40, 50, 60, 75, 90, 105, 120, 140, 160, 180, 200)
    private val squatHp = listOf(15, 25, 35, 45, 55, 70, 85, 100, 120, 140, 160, 180, 200, 220)

    fun enemy(boss: CameraBoss, rank: Patente): TrialEnemy {
        val normalized = rank.takeUnless { it == Patente.SEM_PATENTE } ?: Patente.BRONZE
        val index = normalized.benchmarkIndex
        return TrialEnemy(normalized, names[index], (if (boss == CameraBoss.ASH_WARDEN) pushHp else squatHp)[index], index)
    }

    fun next(rank: Patente): Patente = Patente.entries[(rank.ordinal + 1).coerceAtMost(Patente.IMORTAL.ordinal)]
}

/** Only completed camera reps can damage a trial. Failed attempts retain their workout reps. */
data class CameraTrialState(
    val rank: Patente = Patente.BRONZE,
    val attemptReps: Int = 0,
    val totalReps: Int = 0,
    val victories: Int = 0,
    val deaths: Int = 0,
    val bestRank: Int = 0,
    val lastRepAt: Long = 0L,
    val revision: Int = 0,
    val completedSets: List<Int> = emptyList(),
) {
    fun accept(boss: CameraBoss, completedTotal: Int, now: Long): CameraTrialState {
        if (completedTotal <= totalReps) return this
        // A rep arriving after the grace period belongs to a fresh attempt.
        val start = expire(now)
        var tier = start.rank
        var hits = start.attemptReps
        var kills = start.victories
        var best = start.bestRank
        val completed = start.completedSets.toMutableList()
        repeat(completedTotal - totalReps) {
            hits++
            if (hits >= CameraTrials.enemy(boss, tier).hp) {
                completed += hits
                best = maxOf(best, tier.ordinal)
                kills++
                hits = 0
                tier = CameraTrials.next(tier)
            }
        }
        return start.copy(rank = tier, attemptReps = hits, totalReps = completedTotal,
            victories = kills, bestRank = best, lastRepAt = if (hits > 0) now else 0L,
            revision = start.revision + 1, completedSets = completed)
    }

    fun interrupt(): CameraTrialState = if (attemptReps == 0) this else copy(
        attemptReps = 0, deaths = deaths + 1, lastRepAt = 0L, revision = revision + 1,
        completedSets = completedSets + attemptReps)

    fun expire(now: Long): CameraTrialState =
        if (attemptReps > 0 && (now < lastRepAt || now - lastRepAt >= CameraTrials.BREAK_MS)) interrupt() else this

    fun remainingSeconds(now: Long): Int = if (attemptReps == 0) 15 else
        ((CameraTrials.BREAK_MS - (now - lastRepAt).coerceAtLeast(0) + 999) / 1000).toInt().coerceIn(0, 15)
}
