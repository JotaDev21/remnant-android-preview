package com.liferpg.domain

/** Weekly targets and how far along the user is. Progress is clamped to 0..1. */
data class MetaSemana(
    val treinos: Int,
    val metaTreinos: Int,
    val series: Int,
    val metaSeries: Int,
) {
    val progressoTreinos: Float
        get() = if (metaTreinos <= 0) 0f else (treinos.toFloat() / metaTreinos).coerceIn(0f, 1f)

    val progressoSeries: Float
        get() = if (metaSeries <= 0) 0f else (series.toFloat() / metaSeries).coerceIn(0f, 1f)

    val completa: Boolean get() = treinos >= metaTreinos && metaTreinos > 0
}
