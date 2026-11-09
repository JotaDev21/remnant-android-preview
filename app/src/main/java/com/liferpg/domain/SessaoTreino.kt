package com.liferpg.domain

/** One logged set. Which field is filled depends on the exercise's [ModoMedida]. */
data class SerieFeita(
    val pesoKg: Float?,
    val reps: Int?,
    val segundos: Int?,
    val recorde: Boolean,
)

/** A prescribed exercise plus the sets already logged against it. */
data class ItemSessao(
    val item: ItemTreino,
    val feitas: List<SerieFeita> = emptyList(),
    val conclusaoManual: Boolean = false,
) {
    val concluido: Boolean get() = conclusaoManual || feitas.size >= item.series
}

/** A rank-up that happened during the session, surfaced on the finish screen. */
data class PatenteSubida(
    val musculo: Musculo,
    val patente: Patente,
)

/** The live state of a workout in progress. */
data class EstadoSessao(
    val nome: String,
    val itens: List<ItemSessao>,
    val indiceAtual: Int = 0,
    val iniciadaEm: Long,
    val xpGanho: Int = 0,
    val recordes: List<String> = emptyList(),
    val patentesSubidas: List<PatenteSubida> = emptyList(),
    val concluida: Boolean = false,
    val sessionUuid: String = java.util.UUID.randomUUID().toString(),
) {
    val itemAtual: ItemSessao? get() = itens.getOrNull(indiceAtual)

    val totalExercicios: Int get() = itens.size

    val seriesFeitas: Int get() = itens.sumOf { it.feitas.size }

    val seriesPlanejadas: Int get() = itens.sumOf { it.item.series }

    val exerciciosConcluidos: Int get() = itens.count { it.concluido }

    val progressoExercicios: Float
        get() = if (totalExercicios == 0) 0f else exerciciosConcluidos / totalExercicios.toFloat()

    val progresso: Float
        get() = if (seriesPlanejadas == 0) 0f else (seriesFeitas / seriesPlanejadas.toFloat()).coerceIn(0f, 1f)

    val ehUltimo: Boolean get() = indiceAtual >= itens.size - 1
}
