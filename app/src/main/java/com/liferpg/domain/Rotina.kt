package com.liferpg.domain

import com.squareup.moshi.JsonClass

/** One line of a user-built routine, stored as JSON in `RotinaCustom.itensJson`. */
@JsonClass(generateAdapter = true)
data class ItemRotinaDef(
    val exercicioId: String,
    val series: Int,
    val repMin: Int,
    val repMax: Int,
    val descansoSeg: Int,
)

/** JSON envelope for the persisted routine lines. */
@JsonClass(generateAdapter = true)
data class RotinaItens(
    val itens: List<ItemRotinaDef>,
    /** ISO weekdays: Monday=1 through Sunday=7. Empty keeps legacy routines unscheduled. */
    val diasSemana: List<Int> = emptyList(),
)

/** A stored routine after its [ItemRotinaDef] ids have been resolved against the catalogue. */
data class RotinaResolvida(
    val id: Long,
    val nome: String,
    val itens: List<ItemTreino>,
    val diasSemana: Set<Int> = emptySet(),
) {
    val volumeTotalSeries: Int get() = itens.sumOf { it.series }
}

fun rotinaDoDia(rotinas: List<RotinaResolvida>, epochDay: Long): RotinaResolvida? {
    val weekday = java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek.value
    return rotinas.firstOrNull { weekday in it.diasSemana }
}
