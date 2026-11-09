package com.liferpg.domain

import androidx.compose.ui.graphics.Color

/** Trophy metals, ordered by prestige. Each carries its base colour and its highlight. */
enum class MaterialTrofeu(val nome: String, val cor: Color, val corBrilho: Color) {
    BRONZE("Bronze", Color(0xFF9C6B3F), Color(0xFFE0A864)),
    PRATA("Prata", Color(0xFF8E9AA8), Color(0xFFEDF2F8)),
    OURO("Ouro", Color(0xFFC9961E), Color(0xFFFFE08A)),
    PLATINA("Platina", Color(0xFF3FA89A), Color(0xFFB8F0E6)),
    DIAMANTE("Diamante", Color(0xFF3E9BD4), Color(0xFFCDEBFF)),
    ESMERALDA("Esmeralda", Color(0xFF1F9E6B), Color(0xFF7BF3C0)),
    RUBI("Rubi", Color(0xFFC02743), Color(0xFFFF8FA3)),
    OBSIDIANA("Obsidiana", Color(0xFF2A2438), Color(0xFF9A6BE6)),
    LENDARIO("Lendário", Color(0xFFB23BE0), Color(0xFFF6C12B)),
}

data class TrofeuDef(
    val id: String,
    val nome: String,
    val descricao: String,
    val material: MaterialTrofeu,
    val condicao: Condicao,
)

/**
 * The eighteen milestones that earn a trophy — hand-picked, unlike the generated achievement
 * catalogue, so each one stays rare enough to display on the shelf.
 */
object TrofeuEngine {

    val LISTA = listOf(
        TrofeuDef(
            "nivel_5", "Despertar do Guerreiro", "Alcance o nível 5",
            MaterialTrofeu.BRONZE, Condicao.NivelMin(5),
        ),
        TrofeuDef(
            "nivel_25", "Veterano de Ferro", "Alcance o nível 25",
            MaterialTrofeu.PRATA, Condicao.NivelMin(25),
        ),
        TrofeuDef(
            "nivel_50", "Herói Consagrado", "Alcance o nível 50",
            MaterialTrofeu.OURO, Condicao.NivelMin(50),
        ),
        TrofeuDef(
            "nivel_100", "Lenda Viva", "Alcance o nível 100",
            MaterialTrofeu.LENDARIO, Condicao.NivelMin(100),
        ),
        TrofeuDef(
            "streak_30", "Chama Constante", "30 dias seguidos de treino",
            MaterialTrofeu.PRATA, Condicao.StreakMin(30),
        ),
        TrofeuDef(
            "streak_100", "Disciplina de Aço", "100 dias seguidos de treino",
            MaterialTrofeu.OURO, Condicao.StreakMin(100),
        ),
        TrofeuDef(
            "streak_365", "Ano de Glória", "365 dias seguidos de treino",
            MaterialTrofeu.LENDARIO, Condicao.StreakMin(365),
        ),
        TrofeuDef(
            "vol_1000", "Operário do Ferro", "1.000 séries no total",
            MaterialTrofeu.PRATA, Condicao.TotalSeriesMin(1000L),
        ),
        TrofeuDef(
            "vol_5000", "Colosso do Volume", "5.000 séries no total",
            MaterialTrofeu.ESMERALDA, Condicao.TotalSeriesMin(5000L),
        ),
        TrofeuDef(
            "fisico_bronze", "Físico de Bronze", "Todos os músculos em Bronze+",
            MaterialTrofeu.BRONZE, Condicao.EquilibrioMin(Patente.BRONZE),
        ),
        TrofeuDef(
            "fisico_ouro", "Físico de Ouro", "Todos os músculos em Ouro+",
            MaterialTrofeu.OURO, Condicao.EquilibrioMin(Patente.OURO),
        ),
        TrofeuDef(
            "fisico_lendario", "Físico Lendário", "Todos os músculos em Lendário+",
            MaterialTrofeu.DIAMANTE, Condicao.EquilibrioMin(Patente.LENDARIO),
        ),
        TrofeuDef(
            "fisico_imortal", "Físico Imortal", "Todos os músculos em Imortal",
            MaterialTrofeu.OBSIDIANA, Condicao.EquilibrioMin(Patente.IMORTAL),
        ),
        TrofeuDef(
            "power_8000", "Ascensão Soberana", "Power Score de 8.000",
            MaterialTrofeu.PLATINA, Condicao.PowerScoreMin(8000),
        ),
        TrofeuDef(
            "power_13000", "Poder Imortal", "Power Score de 13.000",
            MaterialTrofeu.RUBI, Condicao.PowerScoreMin(13000),
        ),
        TrofeuDef(
            "top_1", "Elite Mundial", "Entre no Top 1% do ranking",
            MaterialTrofeu.DIAMANTE, Condicao.PercentilMax(1),
        ),
        TrofeuDef(
            "temp_campeao", "Campeão de Temporada", "Encerre uma temporada com pico Lendário",
            MaterialTrofeu.OURO, Condicao.PicoTemporadaMin(Patente.LENDARIO),
        ),
        TrofeuDef(
            "missoes_200", "Mestre das Missões", "Conclua 200 missões diárias",
            MaterialTrofeu.PRATA, Condicao.MissoesMin(200),
        ),
    )

    /** Ids of every trophy the state already earns. */
    fun ganhos(estado: EstadoConquistas): Set<String> =
        LISTA.asSequence()
            .filter { it.condicao.cumprida(estado) }
            .map { it.id }
            .toSet()
}
