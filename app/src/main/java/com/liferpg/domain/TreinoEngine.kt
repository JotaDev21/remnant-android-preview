package com.liferpg.domain

import com.liferpg.data.Preferencias
import kotlin.math.roundToInt
import kotlin.random.Random

/** How hard the user trains, which scales both set count and exercise selection. */
enum class NivelExperiencia(
    val titulo: String,
    val multSeries: Float,
    val exerciciosPorTreino: Int,
) {
    INICIANTE("Iniciante", 0.8f, 4),
    INTERMEDIARIO("Intermediário", 1.0f, 5),
    AVANCADO("Avançado", 1.2f, 6);

    companion object {
        /** Parses a persisted name, falling back to [INICIANTE] for unknown values. */
        fun seguro(nome: String?): NivelExperiencia =
            entries.firstOrNull { it.name == nome } ?: INICIANTE
    }
}

/** Training goal, which fixes the rep range, base set count and rest interval. */
enum class Objetivo(
    val titulo: String,
    val emoji: String,
    val repMin: Int,
    val repMax: Int,
    val seriesBase: Int,
    val descansoSeg: Int,
) {
    FORCA("Força", "🏋️", 3, 6, 4, 180),
    HIPERTROFIA("Hipertrofia", "💪", 8, 12, 4, 90),
    RESISTENCIA("Resistência", "🔥", 15, 20, 3, 45),
    GERAL("Condicionamento geral", "⚡", 6, 12, 3, 75);

    companion object {
        /** Parses a persisted name, falling back to [GERAL] for unknown values. */
        fun seguro(nome: String?): Objetivo =
            entries.firstOrNull { it.name == nome } ?: GERAL
    }
}

/** One prescribed exercise inside a suggested workout. */
data class ItemTreino(
    val exercicio: Exercicio,
    val series: Int,
    val repMin: Int,
    val repMax: Int,
    val descansoSeg: Int,
    val motivo: String,
) {
    /** Reps as shown to the user; timed exercises are rendered as seconds (3s per rep). */
    val repsRotulo: String
        get() = if (exercicio.modo == ModoMedida.TEMPO) {
            "${repMin * 3}–${repMax * 3}s"
        } else {
            "$repMin–$repMax"
        }
}

/** A full workout suggestion for one day. */
data class TreinoSugerido(
    val titulo: String,
    val subtitulo: String,
    val itens: List<ItemTreino>,
    val musculosAlvo: List<Musculo>,
) {
    val volumeTotalSeries: Int get() = itens.sumOf { it.series }

    companion object {
        /** Returned when nothing in the catalogue matches the user's constraints. */
        val VAZIO = TreinoSugerido("Treino do dia", "", emptyList(), emptyList())
    }
}

/**
 * Builds the daily workout: picks the muscles with the weakest ratings that were
 * not trained yesterday, then the best-fitting exercise for each.
 */
object TreinoEngine {

    /** Starting preferences for a brand-new profile, sized off the user's body weight. */
    fun preferenciasPadrao(pesoCorporalKg: Float): Preferencias = Preferencias(
        objetivo = "GERAL",
        experiencia = "INICIANTE",
        localPreferido = "AMBOS",
        diasPorSemana = 3,
        descansoPadraoSeg = 90,
        metaProteinaG = (pesoCorporalKg.coerceAtLeast(40f) * 1.8f).roundToInt(),
        rotinaAtiva = null,
        lembretesAtivos = true,
    )

    private fun disponivel(ex: Exercicio, local: LocalTreino): Boolean = when (local) {
        LocalTreino.CASA -> ex.local == LocalTreino.CASA || ex.local == LocalTreino.AMBOS
        LocalTreino.ACADEMIA -> ex.local == LocalTreino.ACADEMIA || ex.local == LocalTreino.AMBOS
        LocalTreino.AMBOS -> true
    }

    /**
     * [hoje] is an epoch day; it seeds the shuffle so the same day always yields the
     * same workout. Muscles trained within the last day are pushed to the back by a
     * large penalty rather than excluded, so a small catalogue still produces a workout.
     */
    fun gerar(
        prefs: Preferencias,
        catalogoPorMusculo: Map<Musculo, List<Exercicio>>,
        ratings: Map<Musculo, RatingMusculo>,
        ultimoTreinoPorMusculo: Map<Musculo, Long>,
        hoje: Long,
    ): TreinoSugerido {
        val obj = Objetivo.seguro(prefs.objetivo)
        val exp = NivelExperiencia.seguro(prefs.experiencia)
        val local = runCatching { LocalTreino.valueOf(prefs.localPreferido) }
            .getOrDefault(LocalTreino.AMBOS)
        val rng = Random(hoje)

        val porMusculoDisp = catalogoPorMusculo
            .mapValues { (_, exercicios) -> exercicios.filter { disponivel(it, local) } }
            .filterValues { it.isNotEmpty() }
        if (porMusculoDisp.isEmpty()) return TreinoSugerido.VAZIO

        val musculosEvitados = CondicaoSaude.musculosEvitados(prefs.condicoes)
        val evitarCargaAlta = CondicaoSaude.evitarCargaAlta(prefs.condicoes)

        val disponiveis = porMusculoDisp.keys
            .filterNot { it in musculosEvitados }
            .ifEmpty { porMusculoDisp.keys.toList() }

        val ordenado = disponiveis.sortedBy { musculo ->
            val rating = ratings[musculo]?.rating ?: 0f
            val diasDesde = ultimoTreinoPorMusculo[musculo]?.let { hoje - it } ?: 999L
            rating + (if (diasDesde <= 1) 2500f else 0f) + rng.nextInt(120)
        }
        val nAlvo = minOf(4, ordenado.size)
        val alvo = ordenado.take(nAlvo)

        val total = exp.exerciciosPorTreino
        val itens = mutableListOf<ItemTreino>()
        val usados = mutableSetOf<String>()
        val series = (obj.seriesBase * exp.multSeries).roundToInt().coerceIn(2, 6)
        val descanso = if (prefs.descansoPadraoSeg > 0) prefs.descansoPadraoSeg else obj.descansoSeg

        var passo = 0
        val maxPassos = total * 4
        while (itens.size < total && passo < maxPassos) {
            val m = alvo[passo % alvo.size]
            val ex = melhorCandidato(porMusculoDisp.getValue(m), usados, exp, evitarCargaAlta)
            if (ex != null) {
                usados += ex.id
                itens += ItemTreino(
                    exercicio = ex,
                    series = series,
                    repMin = obj.repMin,
                    repMax = obj.repMax,
                    descansoSeg = descanso,
                    motivo = if (m == alvo.first()) "Ponto mais fraco" else "Equilíbrio",
                )
            }
            passo++
        }

        return TreinoSugerido(
            titulo = "${obj.emoji} Treino do dia",
            subtitulo = "Foco: " + alvo.joinToString(" · ") { it.nomeExibicao },
            itens = itens,
            musculosAlvo = alvo,
        )
    }

    /**
     * Ranks the unused candidates: bodyweight first when high load must be avoided,
     * then compound over isolation, then by difficulty — easiest first for beginners,
     * hardest first otherwise.
     */
    private fun melhorCandidato(
        candidatos: List<Exercicio>,
        usados: Set<String>,
        exp: NivelExperiencia,
        evitarCargaAlta: Boolean,
    ): Exercicio? {
        val disp = candidatos.filterNot { it.id in usados }
        if (disp.isEmpty()) return null

        val porDificuldade: Comparator<Exercicio> =
            if (exp == NivelExperiencia.INICIANTE) {
                compareBy { it.dificuldade }
            } else {
                compareByDescending { it.dificuldade }
            }

        val porCarga: Comparator<Exercicio> =
            if (evitarCargaAlta) {
                compareByDescending { it.modo != ModoMedida.CARGA }
            } else {
                compareByDescending { 0 }
            }

        return disp.sortedWith(
            porCarga
                .thenByDescending { it.mecanica == "COMPOSTO" }
                .then(porDificuldade)
        ).first()
    }
}
