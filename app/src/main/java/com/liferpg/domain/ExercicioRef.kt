package com.liferpg.domain

/** Lightweight pointer to an exercise, used where the full [Exercicio] is overkill. */
data class ExercicioRef(
    val nome: String,
    val categoria: String,
    val imagem: String?,
)
