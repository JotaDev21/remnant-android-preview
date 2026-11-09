package com.liferpg.domain

import kotlin.math.ln
import kotlin.math.roundToInt

/** Game rewards, not a physiological measure of effort or a prescription to lift more. */
data class ExperienceReward(
    val series: Int = 0,
    val desempenho: Int = 0,
    val classe: Int = 0,
    val recorde: Int = 0,
    val patente: Int = 0,
    val missoes: Int = 0,
) {
    val total: Int get() = series + desempenho + classe + recorde + patente + missoes
    val treino: Int get() = series + desempenho + classe
    fun detalhes(): String = listOf(
        "Série" to series, "Desempenho" to desempenho, "Classe" to classe,
        "PR" to recorde, "Patente" to patente, "Missões" to missoes,
    ).filter { it.second > 0 }.joinToString(" · ") { "${it.first} +${it.second}" }
}

object Experience {
    /** Relative to this exercise's gold benchmark; raw machine kilograms are never compared. */
    fun calcular(ex: Exercicio, classe: ClassePersonagem, score: Float,
        novoRecorde: Boolean = false, subiuPatente: Boolean = false): ExperienceReward {
        if (!score.isFinite() || score <= 0f) return ExperienceReward()
        val referencia = ex.benchmarks?.takeIf { it.size == 7 }?.get(2)
            ?.takeIf { it.isFinite() && it > 0f } ?: when (ex.modo) {
                ModoMedida.CARGA -> 1f
                ModoMedida.REPS -> 10f
                ModoMedida.TEMPO -> 30f
            }
        val base = (RankEngine.XP_BASE_SERIE * ex.dificuldade.takeIf { it.isFinite() }
            .let { (it ?: 1f).coerceIn(.5f, 3f) }).roundToInt()
        // Logarithmic growth prevents enormous loads/counts from generating unbounded rewards.
        val desempenho = (30f * ln(1f + score / referencia)).roundToInt().coerceIn(0, 90)
        val favorito = ex.musculo in classe.musculosFavoritos ||
            (classe.bonusCorporal && ex.modo != ModoMedida.CARGA)
        val perk = (base + desempenho) *
            ((if (favorito) RankEngine.BONUS_CLASSE else 0f) + classe.bonusGeral)
        return ExperienceReward(base, desempenho, perk.roundToInt(),
            if (novoRecorde) RankEngine.XP_BONUS_RECORDE else 0,
            if (subiuPatente) RankEngine.XP_BONUS_SUBIU_PATENTE else 0)
    }
}
