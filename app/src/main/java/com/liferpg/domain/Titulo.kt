package com.liferpg.domain

data class TituloDef(
    val id: String,
    val texto: String,
    val descricao: String,
    val condicao: Condicao,
)

/**
 * Equippable titles shown next to the player name. Deliberately overlaps the trophy milestones —
 * a trophy sits on the shelf, a title is what everyone else sees.
 */
object TituloEngine {

    val LISTA = listOf(
        TituloDef("desperto", "O Desperto", "Alcance o nível 5", Condicao.NivelMin(5)),
        TituloDef("veterano", "O Veterano", "Alcance o nível 25", Condicao.NivelMin(25)),
        TituloDef("heroi", "O Herói", "Alcance o nível 50", Condicao.NivelMin(50)),
        TituloDef("indestrutivel", "O Indestrutível", "100 dias de streak", Condicao.StreakMin(100)),
        TituloDef("eterno", "O Eterno", "365 dias de streak", Condicao.StreakMin(365)),
        TituloDef("colosso", "O Colosso", "5.000 séries no total", Condicao.TotalSeriesMin(5000L)),
        TituloDef(
            "forjado_ouro", "Forjado em Ouro", "Todos os músculos em Ouro+",
            Condicao.EquilibrioMin(Patente.OURO),
        ),
        TituloDef("soberano", "O Soberano", "Power Score de 8.000", Condicao.PowerScoreMin(8000)),
        TituloDef(
            "elite_mundial", "Elite Mundial", "Entre no Top 1% do ranking",
            Condicao.PercentilMax(1),
        ),
        TituloDef(
            "campeao", "O Campeão", "Pico de temporada Lendário",
            Condicao.PicoTemporadaMin(Patente.LENDARIO),
        ),
        TituloDef("lenda_viva", "Lenda Viva", "Alcance o nível 100", Condicao.NivelMin(100)),
        TituloDef(
            "o_imortal", "O Imortal", "Todos os músculos em Imortal",
            Condicao.EquilibrioMin(Patente.IMORTAL),
        ),
    )

    /** Every title the state already unlocks, in catalogue order. */
    fun ganhos(estado: EstadoConquistas): List<TituloDef> =
        LISTA.filter { it.condicao.cumprida(estado) }

    /** Resolves the equipped title id stored in the profile. */
    fun porId(id: String?): TituloDef? {
        if (id == null) return null
        return LISTA.firstOrNull { it.id == id }
    }
}
