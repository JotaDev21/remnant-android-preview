package com.liferpg.domain

/** Outcome of an anti-cheat check on a logged set. */
sealed interface Veredito {
    /** The set counts in full. */
    data object Ok : Veredito

    /** The set is logged but flagged, so it never feeds leaderboards. */
    data class Suspeito(val motivo: String) : Veredito

    /** The set is refused outright. */
    data class Rejeitado(val motivo: String) : Veredito
}

/**
 * Anti-cheat gate for a logged set: hard physical limits first, then pacing and daily volume,
 * then a benchmark-relative sanity check on the load itself.
 */
object ValidadorTreino {

    const val CAP_DIARIO_EXERCICIO = 25
    const val MIN_INTERVALO_MS = 4000L
    const val PESO_MAX_KG = 600f
    const val REPS_MAX_CARGA = 100
    const val REPS_MAX_CORPORAL = 500
    const val TEMPO_MAX_SEG = 7200

    /** Beyond this multiple of the top threshold the lift is not humanly possible. */
    private const val FATOR_IMPOSSIVEL = 1.6f

    /** Beyond this multiple of the previous best the jump is flagged. */
    private const val FATOR_SALTO = 2.5f

    /** Clamps a self-reported bodyweight into a survivable range. */
    fun pesoCorporalSano(kg: Float): Float = kg.coerceIn(30f, 350f)

    fun validar(
        modo: ModoMedida,
        pesoKg: Float?,
        reps: Int?,
        segundos: Int?,
        score: Float,
        benchmarks: List<Float>?,
        melhorAnterior: Float,
        msDesdeUltima: Long,
        seriesHojeExercicio: Int = 0,
    ): Veredito {
        when (modo) {
            ModoMedida.CARGA -> {
                if ((pesoKg ?: 0f) > PESO_MAX_KG) {
                    return Veredito.Rejeitado("Peso acima do limite humano (600 kg).")
                }
                if ((reps ?: 0) > REPS_MAX_CARGA) {
                    return Veredito.Rejeitado("Repetições implausíveis (>100).")
                }
            }

            ModoMedida.REPS -> if ((reps ?: 0) > REPS_MAX_CORPORAL) {
                return Veredito.Rejeitado("Repetições implausíveis (>500).")
            }

            ModoMedida.TEMPO -> if ((segundos ?: 0) > TEMPO_MAX_SEG) {
                return Veredito.Rejeitado("Tempo implausível (acima de 2h).")
            }
        }

        if (msDesdeUltima in 0 until MIN_INTERVALO_MS) {
            return Veredito.Rejeitado("Séries rápidas demais — espere alguns segundos entre registros.")
        }
        if (seriesHojeExercicio >= CAP_DIARIO_EXERCICIO) {
            return Veredito.Suspeito("Volume diário implausível neste exercício — marcado como suspeito.")
        }

        if (modo == ModoMedida.CARGA && benchmarks != null && benchmarks.size == 7) {
            val teto = RankEngine.limiaresCompletos(benchmarks).lastOrNull() ?: return Veredito.Ok
            if (score > FATOR_IMPOSSIVEL * teto) {
                return Veredito.Rejeitado("Carga sobre-humana para este exercício.")
            }
            if (melhorAnterior > 0f && score > FATOR_SALTO * melhorAnterior && score > 0.5f * teto) {
                return Veredito.Suspeito("Salto de carga grande demais — marcado como suspeito.")
            }
        } else if (melhorAnterior > 0f && score > 3f * melhorAnterior) {
            return Veredito.Suspeito("Salto grande demais — marcado como suspeito.")
        }

        return Veredito.Ok
    }
}
