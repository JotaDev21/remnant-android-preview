package com.liferpg.domain

/**
 * A food resolved from the barcode scanner or the nutrition search.
 * Macros are per 100 g. [marca] and [barcode] are absent for manual entries.
 */
data class AlimentoEncontrado(
    val nome: String,
    val marca: String?,
    val proteinaG: Float,
    val calorias: Float,
    val carboG: Float,
    val gorduraG: Float,
    val barcode: String?,
    val id: String = "",
    val fonte: String = "Registro manual",
    val fibraG: Float? = null,
    val imagemUrl: String? = null,
    val dadosAusentes: Int = 0,
    val grupo: String? = null,
)
