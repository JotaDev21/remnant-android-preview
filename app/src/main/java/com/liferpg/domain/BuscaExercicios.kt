package com.liferpg.domain

import java.text.Normalizer
import java.util.Locale

/** Search the entire arsenal by exercise, equipment, muscle or common gym aliases. */
object BuscaExercicios {
    private val aliases = mapOf(
        "pulley" to listOf("polia", "puxada"),
        "aparelho" to listOf("maquina", "articulada"),
        "maquina" to listOf("articulada", "smith"),
        "skullcrusher" to listOf("testa"),
        "preacher" to listOf("scott"),
        "overhead" to listOf("frances"),
    )

    fun normalizar(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    fun corresponde(ex: Exercicio, query: String): Boolean {
        val terms = normalizar(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        val name = normalizar(ex.nome)
        val equipment = normalizar(ex.equipamento)
        val index = "$name $equipment ${normalizar(ex.musculo.nomeExibicao)} ${normalizar(ex.id)}"
        return terms.all { term ->
            term in index || aliases[term]?.any { it in index } == true
        }
    }
}
