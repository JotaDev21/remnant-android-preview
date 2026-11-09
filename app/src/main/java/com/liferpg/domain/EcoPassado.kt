package com.liferpg.domain

/**
 * "Echo of the past": compares today's effort against the same exercise
 * [dias] days ago, so the UI can show whether the user beat their old self.
 */
data class EcoPassado(
    val atual: Float,
    val passado: Float,
    val dias: Int,
) {
    val temEco: Boolean get() = passado > 0f && dias > 0
    val delta: Float get() = atual - passado
    val superou: Boolean get() = delta >= 0f
}
