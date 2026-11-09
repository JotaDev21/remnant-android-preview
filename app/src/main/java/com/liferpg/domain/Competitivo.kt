package com.liferpg.domain

import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Ceiling of the per-muscle rating; also the top of the power score. */
const val RATING_MAX = 15000.0f

/** How much rating one full rank is worth. */
const val RATING_POR_PATENTE = 1000.0f

/** Sub-rank inside a [Patente], IV (lowest) through I (highest). */
enum class Divisao(val romano: String) {
    IV("IV"),
    III("III"),
    II("II"),
    I("I");

    companion object {
        /** [offsetNaPatente] is the rating above the rank floor, i.e. 0..[RATING_POR_PATENTE). */
        fun doOffset(offsetNaPatente: Float): Divisao =
            entries[(offsetNaPatente / 250f).toInt().coerceIn(0, 3)]
    }
}

/** Player ascension earned through XP levels. Power-based lookup remains for legacy arena rows. */
enum class EstagioFirmamento(
    val titulo: String,
    val min: Float,
    val nivelMinimo: Int,
    val cor: Color,
    val corBrilho: Color,
    val drawable: String,
) {
    BRASA("Brasa", 0f, 1, Color(0xFF897768), Color(0xFFB69977), "astro_brasa"),
    FAISCA("Faísca", 1300f, 3, Color(0xFFE0742B), Color(0xFFFFB066), "astro_faisca"),
    CHAMA("Chama", 2600f, 5, Color(0xFFE0452B), Color(0xFFFF7A4D), "astro_chama"),
    ESTELAR("Estelar", 4000f, 8, Color(0xFFE6B422), Color(0xFFFFE08A), "astro_estelar"),
    NEBULOSA("Nebulosa", 5400f, 11, Color(0xFF2FB58A), Color(0xFF6BE3BD), "astro_nebulosa"),
    CONSTELACAO("Constelação", 6800f, 15, Color(0xFF22D3EE), Color(0xFF9BF0E1), "astro_constelacao"),
    PULSAR("Pulsar", 8200f, 20, Color(0xFF5EC8F8), Color(0xFFB9E8FF), "astro_pulsar"),
    NOVA("Nova", 9600f, 26, Color(0xFF8B5CF6), Color(0xFFC4A6FF), "astro_nova"),
    QUASAR("Quasar", 11000f, 33, Color(0xFFB24BF3), Color(0xFFF6C12B), "astro_quasar"),
    SUPERNOVA("Supernova", 12300f, 41, Color(0xFFBFE3FF), Color(0xFFFFFFFF), "astro_supernova"),
    GALAXIA("Galáxia", 12800f, 50, Color(0xFFF6C12B), Color(0xFFFFFFFF), "astro_galaxia"),
    FIRMAMENTO("Firmamento", 14000f, 60, Color(0xFFFFFFFF), Color(0xFF22D3EE), "astro_firmamento");

    /** Progress towards the next stage, 0..1. The last stage is always full. */
    fun progresso(powerScore: Float): Float {
        val prox = entries.getOrNull(ordinal + 1) ?: return 1f
        if (prox.min <= min) return 1f
        return ((powerScore - min) / (prox.min - min)).coerceIn(0f, 1f)
    }

    companion object {
        fun de(powerScore: Float): EstagioFirmamento = entries.last { powerScore >= it.min }
        fun deNivel(nivel: Int): EstagioFirmamento = entries.last { nivel >= it.nivelMinimo }
    }
}

/** How much each muscle contributes to the overall power score. Sums to 1. */
object PesosMusculo {
    val MAPA: Map<Musculo, Float> = mapOf(
        Musculo.PEITO to 0.13f,
        Musculo.COSTAS to 0.15f,
        Musculo.QUADRICEPS to 0.15f,
        Musculo.POSTERIOR to 0.10f,
        Musculo.GLUTEOS to 0.08f,
        Musculo.OMBROS to 0.10f,
        Musculo.ABDOMEN to 0.07f,
        Musculo.BICEPS to 0.06f,
        Musculo.TRICEPS to 0.06f,
        Musculo.PANTURRILHA to 0.06f,
        Musculo.ANTEBRACO to 0.04f,
    )

    fun peso(m: Musculo): Float = MAPA[m] ?: (1f / Musculo.entries.size)
}

/** The competitive standing of a single muscle. */
data class RatingMusculo(
    val patente: Patente,
    val divisao: Divisao?,
    val lp: Int,
    val rating: Float,
    val progressoPatente: Float,
    val exercicioChave: Exercicio?,
    val scoreChave: Float,
) {
    /** e.g. "OURO II"; the unranked tier has no division. */
    val rotulo: String
        get() = if (divisao == null) {
            patente.titulo.uppercase(Locale.ROOT)
        } else {
            patente.titulo.uppercase(Locale.ROOT) + " " + divisao.romano
        }
}

/** The whole competitive profile: overall standing plus the per-muscle breakdown. */
data class ResumoCompetitivo(
    val powerScore: Float,
    val patente: Patente,
    val divisao: Divisao?,
    val lp: Int,
    val progressoPatente: Float,
    val percentilTopo: Int,
    val ratings: Map<Musculo, RatingMusculo>,
    val temporada: Int,
    val diasRestantesTemporada: Int,
) {
    val rotulo: String
        get() = if (divisao == null) {
            patente.titulo.uppercase(Locale.ROOT)
        } else {
            patente.titulo.uppercase(Locale.ROOT) + " " + divisao.romano
        }

    fun musculoMaisFraco(): Musculo? = ratings.minByOrNull { it.value.rating }?.key

    fun musculoMaisForte(): Musculo? = ratings.maxByOrNull { it.value.rating }?.key

    companion object {
        val VAZIO = ResumoCompetitivo(
            powerScore = 0f,
            patente = Patente.SEM_PATENTE,
            divisao = null,
            lp = 0,
            progressoPatente = 0f,
            percentilTopo = 100,
            ratings = emptyMap(),
            temporada = 1,
            diasRestantesTemporada = 90,
        )
    }
}

/** Ranked seasons: fixed 90-day windows counted from a hard-coded epoch day. */
object Temporadas {
    const val DURACAO_DIAS = 90

    /** Epoch day of the very first season start. */
    const val INICIO_EPOCHDAY = 20454L

    fun numeroDaTemporada(hoje: Long): Int =
        if (hoje < INICIO_EPOCHDAY) 1 else 1 + ((hoje - INICIO_EPOCHDAY) / DURACAO_DIAS).toInt()

    fun inicioDaTemporada(numero: Int): Long =
        INICIO_EPOCHDAY + (numero - 1).toLong() * DURACAO_DIAS

    fun fimDaTemporada(numero: Int): Long = inicioDaTemporada(numero) + DURACAO_DIAS - 1

    fun diasRestantes(hoje: Long): Int =
        (fimDaTemporada(numeroDaTemporada(hoje)) - hoje).toInt().coerceAtLeast(0)
}
