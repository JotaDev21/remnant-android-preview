package com.liferpg.domain

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The rank a muscle currently holds, plus the exercise that earned it. */
data class PatenteMusculo(
    val patente: Patente,
    val progresso: Float,
    val exercicioChave: Exercicio?,
    val scoreChave: Float,
)

/**
 * Scoring core: turns logged sets into a normalised score, the score into a rank
 * and a 0..15000 rating, and the ratings into the overall competitive standing.
 */
object RankEngine {

    /** Extra XP fraction when the exercise matches the character class. */
    const val BONUS_CLASSE = 0.2f
    const val XP_BASE_SERIE = 10
    const val XP_BONUS_RECORDE = 50
    const val XP_BONUS_SUBIU_PATENTE = 150

    /** Multipliers that extend the 7 shipped benchmarks into the 7 epic ranks. */
    private val MULT_EPICO = floatArrayOf(0.7f, 1.5f, 2.5f, 3.8f, 5.4f, 7.4f, 10f)

    /** Power-score → percentile curve, as a pair of parallel tables. */
    private val PCT_RATING = floatArrayOf(
        0f, 1000f, 2000f, 3000f, 4000f, 5000f, 6000f, 7000f,
        8000f, 9000f, 10000f, 11000f, 12000f, 13000f, 14000f, 15000f,
    )
    private val PCT_TOPO = floatArrayOf(
        100f, 90f, 81f, 70f, 59f, 48f, 38f, 30f,
        22f, 16f, 11f, 7f, 4f, 2f, 0.7f, 0.1f,
    )

    /** Epley one-rep-max estimate. A single rep is already the max. */
    fun e1rm(pesoKg: Float, reps: Int): Float = when {
        reps <= 0 || pesoKg <= 0f -> 0f
        reps == 1 -> pesoKg
        else -> pesoKg * (1f + reps / 30f)
    }

    /**
     * Normalises one set to a comparable number: load work is expressed as a
     * multiple of body weight, the other modes score their raw count.
     */
    fun scoreSerie(
        modo: ModoMedida,
        pesoCorporalKg: Float,
        pesoKg: Float?,
        reps: Int?,
        segundos: Int?,
    ): Float = when (modo) {
        ModoMedida.CARGA -> {
            val corpo = if (pesoCorporalKg > 0f) pesoCorporalKg else 1f
            e1rm(pesoKg ?: 0f, reps ?: 0) / corpo
        }

        ModoMedida.REPS -> (reps ?: 0).toFloat()
        ModoMedida.TEMPO -> (segundos ?: 0).toFloat()
    }

    /**
     * The 7 benchmarks shipped per exercise cover BRONZE..LENDARIO. The 7 epic
     * ranks are extrapolated from the last step, floored at 8% of the top value
     * so a flat table still produces a rising ladder.
     */
    fun limiaresCompletos(benchmarks: List<Float>): List<Float> {
        if (benchmarks.size != 7) return benchmarks
        val topo = benchmarks[6]
        val passo = (benchmarks[6] - benchmarks[5]).coerceAtLeast(0.08f * topo)
        return benchmarks + MULT_EPICO.map { topo + passo * it }
    }

    /** Highest rank whose threshold [score] clears. Unranked without benchmarks. */
    fun patenteDoScore(score: Float, benchmarks: List<Float>?): Patente {
        if (benchmarks == null || benchmarks.size != 7 || score <= 0f) return Patente.SEM_PATENTE
        val limiares = limiaresCompletos(benchmarks)
        var atual = Patente.SEM_PATENTE
        for (p in Patente.ranqueadas) {
            if (score < limiares[p.benchmarkIndex]) break
            atual = p
        }
        return atual
    }

    /** Progress from the current rank's floor to the next rank's floor, 0..1. */
    fun progressoNaPatente(score: Float, benchmarks: List<Float>?): Float {
        if (benchmarks == null || benchmarks.size != 7) return 0f
        val atual = patenteDoScore(score, benchmarks)
        if (atual == Patente.IMORTAL) return 1f
        val limiares = limiaresCompletos(benchmarks)
        val piso = if (atual == Patente.SEM_PATENTE) 0f else limiares[atual.benchmarkIndex]
        val teto = limiares[atual.benchmarkIndex + 1]
        if (teto <= piso) return 0f
        return ((score - piso) / (teto - piso)).coerceIn(0f, 1f)
    }

    /** Shared aggregation for legacy callers and the competitive profile. */
    fun patenteDoMusculo(exerciciosDoMusculo: List<Exercicio>, melhorScorePorExercicio: Map<String, Float>): PatenteMusculo {
        val r = ratingMusculo(exerciciosDoMusculo, melhorScorePorExercicio)
        return PatenteMusculo(r.patente, r.progressoPatente, r.exercicioChave, r.scoreChave)
    }

    /** The median rank across muscles, so one strong lift can't carry the profile. */
    fun patenteGeral(patentes: Collection<Patente>): Patente {
        if (patentes.isEmpty()) return Patente.SEM_PATENTE
        val ordenado = patentes.sortedBy { it.ordinal }
        return ordenado[ordenado.size / 2]
    }

    /** XP uses measured performance plus explicit milestones. Past logged XP is never recalculated. */
    fun xpDaSerie(
        exercicio: Exercicio,
        classe: ClassePersonagem,
        novoRecorde: Boolean,
        subiuPatente: Boolean,
        score: Float,
    ): Int {
        return Experience.calcular(exercicio, classe, score, novoRecorde, subiuPatente).total
    }

    /** Inverse of [xpTotalParaNivel]: each level costs 100 XP more than the last. */
    fun nivelDoXp(xpTotal: Long): Int {
        if (xpTotal <= 0) return 1
        val nivel = floor((sqrt(xpTotal * 200.0 + 2500.0) + 50.0) / 100.0).toInt()
        return nivel.coerceAtLeast(1)
    }

    /** Cumulative XP required to reach [nivel]: 0, 100, 300, 600, 1000, … */
    fun xpTotalParaNivel(nivel: Int): Long {
        if (nivel <= 1) return 0L
        return (nivel - 1).toLong() * 100 * nivel / 2
    }

    /** Progress inside the current level, 0..1. */
    fun progressoNoNivel(xpTotal: Long): Float {
        val nivel = nivelDoXp(xpTotal)
        val piso = xpTotalParaNivel(nivel)
        val teto = xpTotalParaNivel(nivel + 1)
        if (teto <= piso) return 0f
        return ((xpTotal - piso) / (teto - piso).toFloat()).coerceIn(0f, 1f)
    }

    fun tituloDoNivel(nivel: Int): String = when (nivel) {
        in 1..4 -> "Novato"
        in 5..9 -> "Aprendiz"
        in 10..14 -> "Adepto"
        in 15..24 -> "Campeão"
        in 25..49 -> "Lenda"
        else -> "Mítico"
    }

    /** Extends the streak on consecutive days; same day keeps it, a gap resets it. */
    fun novoStreak(streakAtual: Int, ultimoDiaTreino: Long, hoje: Long): Int = when {
        ultimoDiaTreino >= hoje -> streakAtual.coerceAtLeast(1)
        ultimoDiaTreino == hoje - 1 -> streakAtual + 1
        else -> 1
    }

    /** A stored streak only still counts if the last workout was today or yesterday. */
    fun streakVigente(streak: Int, ultimoDiaTreino: Long, hoje: Long): Int =
        if (ultimoDiaTreino <= 0 || hoje - ultimoDiaTreino > 1) 0 else streak

    /**
     * Continuous 0..[RATING_MAX] rating: each of the 14 thresholds is worth
     * [RATING_POR_PATENTE], interpolated inside the band. Past the top threshold
     * the last band's slope is extended, capped just below the ceiling.
     */
    fun ratingDoScore(score: Float, benchmarks: List<Float>?): Float {
        if (benchmarks == null || benchmarks.size != 7 || score <= 0f) return 0f
        val limiares = limiaresCompletos(benchmarks)
        val ult = limiares.size - 1

        if (score < limiares[0]) return score / limiares[0] * RATING_POR_PATENTE

        for (i in 0 until ult) {
            if (score < limiares[i + 1]) {
                val frac = (score - limiares[i]) / (limiares[i + 1] - limiares[i])
                return (i + 1) * RATING_POR_PATENTE + RATING_POR_PATENTE * frac
            }
        }

        val slope = if (limiares[ult] > limiares[ult - 1]) {
            RATING_POR_PATENTE / (limiares[ult] - limiares[ult - 1])
        } else {
            0f
        }
        return ((ult + 1) * RATING_POR_PATENTE + (score - limiares[ult]) * slope)
            .coerceAtMost(RATING_MAX - 1f)
    }

    fun patenteDeRating(rating: Float): Patente =
        Patente.entries[(rating / RATING_POR_PATENTE).toInt().coerceIn(0, Patente.entries.size - 1)]

    /** A rating split into the parts the UI shows: rank, division, LP and bar. */
    private data class Componentes(
        val patente: Patente,
        val divisao: Divisao?,
        val lp: Int,
        val progresso: Float,
    )

    private fun componentesDeRating(rating: Float): Componentes {
        val patente = patenteDeRating(rating)
        val offset = rating - patente.ordinal * RATING_POR_PATENTE
        val divisao = if (patente == Patente.SEM_PATENTE) null else Divisao.doOffset(offset)
        val lp = if (patente == Patente.SEM_PATENTE) {
            0
        } else {
            ((offset % 250f) / 250f * 100f).roundToInt().coerceIn(0, 100)
        }
        val progresso = (offset / RATING_POR_PATENTE).coerceIn(0f, 1f)
        return Componentes(patente, divisao, lp, progresso)
    }

    /** Two weeks of grace, then 5% decay per further week, floored at 75%. */
    fun fatorDecay(diasSemTreinarMusculo: Long): Float {
        if (diasSemTreinarMusculo <= 14) return 1f
        val semanas = (diasSemTreinarMusculo - 14) / 7f
        return (1f - 0.05f * semanas).coerceAtLeast(0.75f)
    }

    /** Every recorded exercise contributes once, using its own normalised benchmarks. */
    fun ratingMusculo(
        exerciciosDoMusculo: List<Exercicio>,
        melhorScorePorExercicio: Map<String, Float>,
        fatorDecay: Float = 1f,
    ): RatingMusculo {
        val entries = exerciciosDoMusculo.distinctBy { it.id }.mapNotNull { ex ->
            val score = melhorScorePorExercicio[ex.id]
            if (ex.benchmarks?.size != 7 || score == null || !score.isFinite() || score <= 0f) null
            else ex to (score * fatorDecay.coerceIn(0f, 1f))
        }
        val average = if (entries.isEmpty()) 0f else entries.map { (ex,score) -> ratingDoScore(score,ex.benchmarks) }.average().toFloat()
        val reference = entries.maxByOrNull { (ex,score) -> ratingDoScore(score,ex.benchmarks) }
        val c = componentesDeRating(average)
        return RatingMusculo(c.patente,c.divisao,c.lp,average,c.progresso,reference?.first,reference?.second ?: 0f)
    }

    /** Weighted average of the per-muscle ratings; missing muscles count as 0. */
    fun powerScore(ratings: Map<Musculo, RatingMusculo>): Float {
        var soma = 0f
        for (m in Musculo.entries) {
            soma += PesosMusculo.peso(m) * (ratings[m]?.rating ?: 0f)
        }
        return soma
    }

    /** "Top X%" estimate, interpolated over a hard-coded distribution. */
    fun percentilLocal(powerScore: Float): Int {
        val ps = powerScore.coerceIn(0f, RATING_MAX)
        for (i in 0 until PCT_RATING.size - 1) {
            if (ps <= PCT_RATING[i + 1]) {
                val frac = (ps - PCT_RATING[i]) / (PCT_RATING[i + 1] - PCT_RATING[i])
                return (PCT_TOPO[i] + (PCT_TOPO[i + 1] - PCT_TOPO[i]) * frac).roundToInt()
                    .coerceIn(1, 100)
            }
        }
        return 1
    }

    /** The whole competitive profile in one pass over the muscle list. */
    fun resumoCompetitivo(
        catalogoPorMusculo: Map<Musculo, List<Exercicio>>,
        melhorScorePorExercicio: Map<String, Float>,
        ultimoTreinoPorMusculo: Map<Musculo, Long>,
        hoje: Long,
    ): ResumoCompetitivo {
        val ratings = Musculo.entries.associateWith { musculo ->
            val decay = ultimoTreinoPorMusculo[musculo]
                ?.let { fatorDecay((hoje - it).coerceAtLeast(0L)) }
                ?: 1f
            ratingMusculo(catalogoPorMusculo[musculo].orEmpty(), melhorScorePorExercicio, decay)
        }
        val ps = powerScore(ratings)
        val c = componentesDeRating(ps)
        return ResumoCompetitivo(
            powerScore = ps,
            patente = c.patente,
            divisao = c.divisao,
            lp = c.lp,
            progressoPatente = c.progresso,
            percentilTopo = percentilLocal(ps),
            ratings = ratings,
            temporada = Temporadas.numeroDaTemporada(hoje),
            diasRestantesTemporada = Temporadas.diasRestantes(hoje),
        )
    }
}
