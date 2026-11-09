package com.liferpg.domain

import com.liferpg.data.sync.RecordeAmigo

object IdentidadePerfil {
    val molduras = linkedMapOf("CINZAS" to "Cinzas", "OURO" to "Graça", "VIOLETA" to "Crepúsculo", "RUBRO" to "Sangue", "GEADA" to "Geada", "ESMERALDA" to "Bosque")
    val focos = linkedMapOf("EQUILIBRIO" to "Equilíbrio", "FORCA" to "Força", "HIPERTROFIA" to "Hipertrofia", "RESISTENCIA" to "Resistência", "CONSISTENCIA" to "Consistência").apply {
        Musculo.entries.forEach { put(it.name, it.nomeExibicao) }
    }
    val selos = linkedMapOf("ESPADA" to "Espada", "LOBO" to "Lobo", "COROA" to "Coroa", "CHAMA" to "Chama", "ESCUDO" to "Escudo", "LUA" to "Lua")
}

enum class LiderancaExercicio { VOCE, ALIADO, EMPATE, SEM_COMPARACAO }
data class ConfrontoExercicio(val id: String, val meu: RecordeAmigo?, val aliado: RecordeAmigo?) {
    val referencia: RecordeAmigo get() = aliado ?: requireNotNull(meu)
    val lideranca: LiderancaExercicio get() {
        val a = meu ?: return LiderancaExercicio.SEM_COMPARACAO
        val b = aliado ?: return LiderancaExercicio.SEM_COMPARACAO
        if (a.modo != b.modo || (a.modo == "CARGA" && (a.pesoKg == null || b.pesoKg == null))) return LiderancaExercicio.SEM_COMPARACAO
        val difference = when (a.modo) {
            "CARGA" -> a.pesoKg!! - b.pesoKg!!
            "TEMPO" -> (a.segundos - b.segundos).toFloat()
            else -> (a.reps - b.reps).toFloat()
        }
        return when { difference > .001f -> LiderancaExercicio.VOCE; difference < -.001f -> LiderancaExercicio.ALIADO; else -> LiderancaExercicio.EMPATE }
    }
}

object CompeticaoAmigos {
    /** Exact catalogue ids only: different leg presses and unilateral loads are never merged. */
    fun comparar(meus: List<RecordeAmigo>, aliado: List<RecordeAmigo>): List<ConfrontoExercicio> {
        val a = meus.associateBy { it.exercicioId }; val b = aliado.associateBy { it.exercicioId }
        return (a.keys + b.keys).map { ConfrontoExercicio(it, a[it], b[it]) }
            .sortedWith(compareBy<ConfrontoExercicio> { it.lideranca == LiderancaExercicio.SEM_COMPARACAO }.thenBy { it.referencia.musculo }.thenBy { it.referencia.nome })
    }

    fun itensImportaveis(rotina: com.liferpg.data.sync.RotinaAmigo, catalogo: List<Exercicio>): List<ItemRotinaDef> {
        val ids = catalogo.map { it.id }.toSet()
        return rotina.itens.filter { it.exercicioId in ids && it.series in 1..10 && it.repMin in 1..100 && it.repMax in it.repMin..100 && it.descansoSeg in 15..600 }.distinctBy { it.exercicioId }
    }
}
