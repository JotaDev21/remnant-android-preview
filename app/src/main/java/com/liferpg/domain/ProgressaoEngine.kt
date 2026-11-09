package com.liferpg.domain

/** The target for the next session of an exercise, plus the coaching line shown with it. */
data class ProximaCarga(
    val pesoKg: Float?,
    val reps: Int?,
    val segundos: Int?,
    val dica: String,
)

/** Double-progression: add reps until the top of the range, then add load and reset. */
object ProgressaoEngine {

    /** Plate-friendly jump: light bars move 1 kg, mid 2.5 kg, heavy 5 kg. */
    private fun incremento(pesoKg: Float): Float = when {
        pesoKg < 20f -> 1f
        pesoKg < 60f -> 2.5f
        else -> 5f
    }

    /** Returns null when the last session left nothing to progress from. */
    fun proxima(
        modo: ModoMedida,
        ultimoPeso: Float?,
        ultimasReps: Int?,
        ultimosSegundos: Int?,
        repMin: Int,
        repMax: Int,
    ): ProximaCarga? = when (modo) {
        ModoMedida.CARGA -> {
            val peso = ultimoPeso
            val reps = ultimasReps
            if (peso == null || reps == null) {
                null
            } else if (reps >= repMax) {
                ProximaCarga(
                    pesoKg = peso + incremento(peso),
                    reps = repMin,
                    segundos = null,
                    dica = "Você bateu o teto de reps — suba a carga e recomece o ciclo.",
                )
            } else {
                ProximaCarga(
                    pesoKg = peso,
                    reps = (reps + 1).coerceAtMost(repMax),
                    segundos = null,
                    dica = "Mantenha a carga e adicione 1 repetição.",
                )
            }
        }

        ModoMedida.REPS -> ultimasReps?.let {
            ProximaCarga(
                pesoKg = null,
                reps = it + 1,
                segundos = null,
                dica = "Tente uma repetição a mais que da última vez.",
            )
        }

        ModoMedida.TEMPO -> ultimosSegundos?.let {
            ProximaCarga(
                pesoKg = null,
                reps = null,
                segundos = it + 5,
                dica = "Aguente 5 segundos a mais que da última vez.",
            )
        }
    }
}
