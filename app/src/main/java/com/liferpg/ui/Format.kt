package com.liferpg.ui

import com.liferpg.domain.ModoMedida
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {

    fun score(modo: ModoMedida, score: Float, pesoCorporalKg: Float?): String = when (modo) {
        ModoMedida.CARGA -> {
            val base = String.format(Locale.US, "%.2fx PC", score)
            if (pesoCorporalKg != null && pesoCorporalKg > 0f) {
                "$base (≈ ${(pesoCorporalKg * score).roundToInt()}kg)"
            } else {
                base
            }
        }
        ModoMedida.REPS -> "${score.roundToInt()} reps"
        ModoMedida.TEMPO -> tempo(score.roundToInt())
    }

    fun tempo(segundos: Int): String = when {
        segundos < 60 -> "${segundos}s"
        segundos % 60 == 0 -> "${segundos / 60}m"
        else -> "${segundos / 60}m ${segundos % 60}s"
    }

    fun peso(valor: Float): String {
        val inteiro = valor.roundToInt()
        if (abs(valor - inteiro) < 0.05f) return inteiro.toString()
        return String.format(Locale.US, "%.1f", valor)
    }
}
