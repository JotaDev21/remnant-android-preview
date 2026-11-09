package com.liferpg.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Immutable snapshot of the winning set and the previous score winner, read in one transaction. */
data class MarcaPessoal(val pesoKg: Float?, val reps: Int?, val segundos: Int?, val score: Float)
data class RecordePessoal(
    val exercicioId: String, val exercicioNome: String, val modo: ModoMedida,
    val anterior: MarcaPessoal?, val atual: MarcaPessoal,
)

data class ApresentacaoRecorde(
    val nome: String, val de: Float, val para: Float, val unidade: String,
    val detalhe: String, val comparacao: String, val ganho: String, val criterio: String,
)

/** The main number is always the actual measurement. Estimated strength is labelled separately. */
fun RecordePessoal.apresentacao(): ApresentacaoRecorde {
    fun number(n: Float) = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))).format(n)
    fun value(m: MarcaPessoal) = when (modo) {
        ModoMedida.CARGA -> m.pesoKg ?: 0f
        ModoMedida.REPS -> (m.reps ?: 0).toFloat()
        ModoMedida.TEMPO -> (m.segundos ?: 0).toFloat()
    }
    fun label(m: MarcaPessoal) = when (modo) {
        ModoMedida.CARGA -> "${number(value(m))} kg × ${m.reps ?: 0} reps"
        ModoMedida.REPS -> "${number(value(m))} reps"
        ModoMedida.TEMPO -> "${number(value(m))} s"
    }
    val from = anterior?.let(::value) ?: value(atual)
    val to = value(atual)
    val gain = when {
        anterior == null -> "PRIMEIRA MARCA"
        to > from -> "+${number(to - from)} " + when (modo) {
            ModoMedida.CARGA -> "KG"
            ModoMedida.REPS -> "REPS"
            ModoMedida.TEMPO -> "S"
        }
        modo == ModoMedida.CARGA && to == from && (atual.reps ?: 0) > (anterior.reps ?: 0) ->
            "+${(atual.reps ?: 0) - (anterior.reps ?: 0)} REPS"
        else -> "+${number(((atual.score / anterior.score) - 1f) * 100f)}% FORÇA ESTIMADA"
    }
    return ApresentacaoRecorde(
        exercicioNome, from, to,
        when (modo) { ModoMedida.CARGA -> "KG"; ModoMedida.REPS -> "REPETIÇÕES"; ModoMedida.TEMPO -> "SEGUNDOS" },
        if (modo == ModoMedida.CARGA) "${atual.reps ?: 0} repetições" else "Marca validada",
        anterior?.let { "${label(it)} → ${label(atual)}" } ?: label(atual), gain,
        if (modo == ModoMedida.CARGA) "PR de força estimada · carga + repetições" else "Recorde de ${if (modo == ModoMedida.REPS) "repetições" else "tempo"}",
    )
}
