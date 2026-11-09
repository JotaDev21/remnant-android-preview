package com.liferpg.domain

enum class CategoriaConquista(val titulo: String, val emoji: String) {
    FORCA("Força", "🛡️"),
    VOLUME("Volume", "🏋️"),
    EXPLORACAO("Exploração", "🧭"),
    MAESTRIA("Maestria", "🎯"),
    CONSTANCIA("Constância", "🔥"),
    NIVEL("Níveis", "⭐"),
    COMPETITIVO("Competitivo", "⚡"),
    MISSOES("Missões", "📜"),
    TEMPORADA("Temporadas", "🏆"),
    EQUILIBRIO("Equilíbrio", "⚖️"),
    NUTRICAO("Nutrição", "🍗"),
    SEGREDO("Segredos", "🌙"),
}

/**
 * Everything an achievement can ask about the player, gathered once per evaluation so the
 * thousands of [Condicao] checks never touch the database.
 */
data class EstadoConquistas(
    val totalSeries: Long = 0L,
    val streakMax: Int = 0,
    val nivel: Int = 1,
    val powerScore: Float = 0f,
    val percentilTopo: Int = 100,
    val patenteGeral: Patente = Patente.SEM_PATENTE,
    val divisaoGeral: Divisao? = null,
    val patentePorMusculo: Map<Musculo, Patente> = emptyMap(),
    val volumePorMusculo: Map<Musculo, Int> = emptyMap(),
    val patentePorExercicio: Map<String, Patente> = emptyMap(),
    val exerciciosRegistrados: Set<String> = emptySet(),
    val coberturaMusculo: Map<Musculo, Boolean> = emptyMap(),
    val missoesConcluidas: Int = 0,
    val temporadasEncerradas: Int = 0,
    val picoPatente: Patente = Patente.SEM_PATENTE,
    val metaProteinaHoje: Boolean = false,
) {
    /** Overall rank flattened to a single number so ranks and divisions compare in one step. */
    fun rankGeralNum(): Int = patenteGeral.ordinal * 4 + (divisaoGeral?.ordinal ?: 0)
}

/** A single unlock predicate. Every achievement carries exactly one. */
sealed interface Condicao {
    fun cumprida(e: EstadoConquistas): Boolean

    data class TotalSeriesMin(val n: Long) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.totalSeries >= n
    }

    data class StreakMin(val dias: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.streakMax >= dias
    }

    data class NivelMin(val n: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.nivel >= n
    }

    data class PowerScoreMin(val n: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.powerScore >= n.toFloat()
    }

    /** Top percentile is inverted: smaller is better, and an unranked player has no score. */
    data class PercentilMax(val topo: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.powerScore > 0f && e.percentilTopo <= topo
    }

    data class PatenteMusculoMin(val musculo: Musculo, val patente: Patente) : Condicao {
        override fun cumprida(e: EstadoConquistas) =
            (e.patentePorMusculo[musculo] ?: Patente.SEM_PATENTE) >= patente
    }

    data class VolumeMusculoMin(val musculo: Musculo, val n: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = (e.volumePorMusculo[musculo] ?: 0) >= n
    }

    data class PrimeiraVezExercicio(val exId: String) : Condicao {
        override fun cumprida(e: EstadoConquistas) = exId in e.exerciciosRegistrados
    }

    data class PatenteExercicioMin(val exId: String, val patente: Patente) : Condicao {
        override fun cumprida(e: EstadoConquistas) =
            (e.patentePorExercicio[exId] ?: Patente.SEM_PATENTE) >= patente
    }

    data class CoberturaMusculo(val musculo: Musculo) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.coberturaMusculo[musculo] == true
    }

    data class DivisaoGeralMin(val patente: Patente, val divisao: Divisao) : Condicao {
        private val alvo = patente.ordinal * 4 + divisao.ordinal
        override fun cumprida(e: EstadoConquistas) = e.rankGeralNum() >= alvo
    }

    data class MissoesMin(val n: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.missoesConcluidas >= n
    }

    data class TemporadasMin(val n: Int) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.temporadasEncerradas >= n
    }

    data class PicoTemporadaMin(val patente: Patente) : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.picoPatente >= patente
    }

    /** No weak link: every muscle has to clear the rank. */
    data class EquilibrioMin(val patente: Patente) : Condicao {
        override fun cumprida(e: EstadoConquistas) =
            Musculo.entries.all { (e.patentePorMusculo[it] ?: Patente.SEM_PATENTE) >= patente }
    }

    data object ProteinaMetaDia : Condicao {
        override fun cumprida(e: EstadoConquistas) = e.metaProteinaHoje
    }
}

data class ConquistaDef(
    val id: String,
    val titulo: String,
    val descricao: String,
    val emoji: String,
    val categoria: CategoriaConquista,
    val condicao: Condicao,
)

/**
 * Builds the whole achievement catalogue from a handful of threshold ladders — roughly 900
 * entries, generated rather than hand-written so a new muscle or rank extends it for free.
 */
object ConquistaEngine {

    private val VOLUME_MUSC = listOf(25, 50, 100, 250, 500, 1000, 2000)
    private val VOLUME_TOTAL = listOf(1L, 10L, 50L, 100L, 250L, 500L, 1000L, 2500L, 5000L, 10000L)
    private val STREAK = listOf(3, 7, 14, 30, 60, 100, 150, 200, 300, 365)
    private val NIVEL = listOf(3, 5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80, 90, 100)
    private val POWER = (500..14000 step 500).toList()
    private val PERCENTIL = listOf(50, 25, 10, 5, 2, 1)
    private val MISSOES = listOf(1, 10, 25, 50, 100, 200, 365, 500, 1000)

    /** Per-exercise mastery only starts at Prata — Bronze is the entry rank, not an achievement. */
    private val MAESTRIA_TIERS =
        listOf(Patente.PRATA, Patente.OURO, Patente.MESTRE, Patente.LENDARIO, Patente.TITA, Patente.IMORTAL)

    private val RANQUEAVEIS = Patente.ranqueadas

    fun gerar(exercicios: List<Exercicio>): List<ConquistaDef> {
        val out = ArrayList<ConquistaDef>(900)

        for (n in VOLUME_TOTAL) {
            out += ConquistaDef(
                "vol_total_$n", "Volume: $n séries", "Registre $n séries no total",
                "🏋️", CategoriaConquista.VOLUME, Condicao.TotalSeriesMin(n),
            )
        }
        for (n in STREAK) {
            out += ConquistaDef(
                "streak_$n", "Constância: $n dias", "Treine $n dias seguidos",
                "🔥", CategoriaConquista.CONSTANCIA, Condicao.StreakMin(n),
            )
        }
        for (n in NIVEL) {
            out += ConquistaDef(
                "nivel_$n", "${RankEngine.tituloDoNivel(n)} (nível $n)", "Alcance o nível $n",
                "⭐", CategoriaConquista.NIVEL, Condicao.NivelMin(n),
            )
        }
        for (n in POWER) {
            out += ConquistaDef(
                "power_$n", "Power Score $n", "Atinja $n de Power Score",
                "⚡", CategoriaConquista.COMPETITIVO, Condicao.PowerScoreMin(n),
            )
        }
        for (n in PERCENTIL) {
            out += ConquistaDef(
                "top_$n", "Top $n%", "Entre no top $n% do ranking",
                "📊", CategoriaConquista.COMPETITIVO, Condicao.PercentilMax(n),
            )
        }
        for (n in MISSOES) {
            out += ConquistaDef(
                "missoes_$n", "Missões: $n", "Conclua $n missões diárias",
                "📜", CategoriaConquista.MISSOES, Condicao.MissoesMin(n),
            )
        }

        for (musculo in Musculo.entries) {
            for (patente in RANQUEAVEIS) {
                out += ConquistaDef(
                    "musc_${musculo.name}_${patente.name}",
                    "${musculo.emoji} ${musculo.nomeExibicao} ${patente.titulo}",
                    "Alcance ${patente.titulo} em ${musculo.nomeExibicao}",
                    musculo.emoji, CategoriaConquista.FORCA,
                    Condicao.PatenteMusculoMin(musculo, patente),
                )
            }
            for (n in VOLUME_MUSC) {
                out += ConquistaDef(
                    "volm_${musculo.name}_$n",
                    "${musculo.emoji} ${musculo.nomeExibicao}: $n séries",
                    "Registre $n séries de ${musculo.nomeExibicao}",
                    musculo.emoji, CategoriaConquista.VOLUME,
                    Condicao.VolumeMusculoMin(musculo, n),
                )
            }
            out += ConquistaDef(
                "explorador_${musculo.name}",
                "Arsenal de ${musculo.nomeExibicao}",
                "Estreie todos os exercícios de ${musculo.nomeExibicao}",
                "🧭", CategoriaConquista.EXPLORACAO, Condicao.CoberturaMusculo(musculo),
            )
        }

        for (patente in RANQUEAVEIS) {
            for (divisao in Divisao.entries) {
                out += ConquistaDef(
                    "div_${patente.name}_${divisao.name}",
                    "${patente.titulo} ${divisao.romano}",
                    "Alcance ${patente.titulo} ${divisao.romano} no ranking geral",
                    "⚡", CategoriaConquista.COMPETITIVO,
                    Condicao.DivisaoGeralMin(patente, divisao),
                )
            }
            out += ConquistaDef(
                "equilibrio_${patente.name}",
                "Físico ${patente.titulo}",
                "Tenha TODOS os 11 músculos em ${patente.titulo}+",
                "⚖️", CategoriaConquista.EQUILIBRIO, Condicao.EquilibrioMin(patente),
            )
            out += ConquistaDef(
                "pico_temp_${patente.name}",
                "Pico ${patente.titulo}",
                "Encerre uma temporada com pico ${patente.titulo}",
                "🏆", CategoriaConquista.TEMPORADA, Condicao.PicoTemporadaMin(patente),
            )
        }

        for (n in listOf(1, 3, 5, 10)) {
            out += ConquistaDef(
                "temp_enc_$n",
                "Veterano de $n Temporada" + (if (n > 1) "s" else ""),
                "Participe de $n temporadas",
                "🏆", CategoriaConquista.TEMPORADA, Condicao.TemporadasMin(n),
            )
        }

        out += ConquistaDef(
            "proteina_meta", "Combustível de Herói", "Bata sua meta de proteína em um dia",
            "🍗", CategoriaConquista.NUTRICAO, Condicao.ProteinaMetaDia,
        )

        for (ex in exercicios) {
            out += ConquistaDef(
                "estreia_${ex.id}", "Estreia: ${ex.nome}",
                "Registre sua primeira série de ${ex.nome}",
                "🧭", CategoriaConquista.EXPLORACAO, Condicao.PrimeiraVezExercicio(ex.id),
            )
            // Mastery ranks need a benchmark table to compare against.
            if (ex.benchmarks != null) {
                for (patente in MAESTRIA_TIERS) {
                    out += ConquistaDef(
                        "mae_${ex.id}_${patente.name}",
                        "${ex.nome}: ${patente.titulo}",
                        "Alcance ${patente.titulo} em ${ex.nome}",
                        "🎯", CategoriaConquista.MAESTRIA,
                        Condicao.PatenteExercicioMin(ex.id, patente),
                    )
                }
            }
        }

        return out
    }

    /** Ids of every achievement the state already satisfies. */
    fun avaliar(defs: List<ConquistaDef>, estado: EstadoConquistas): List<String> =
        defs.asSequence()
            .filter { it.condicao.cumprida(estado) }
            .map { it.id }
            .toList()
}
