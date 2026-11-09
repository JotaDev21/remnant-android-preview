package com.liferpg.domain

/** Allowlist shared by AI proposals and local companion shortcuts. No arbitrary routes or code. */
internal data class MentorAction(
    val kind: String, val target: String = "", val seconds: Int = 90,
    val ids: List<String> = emptyList(), val name: String = "Treino do mentor",
) {
    fun valid(exerciseIds: Set<String>): Boolean = when (kind) {
        "navigate" -> target in setOf("tab:0", "tab:1", "tab:2", "tab:3", "tab:4", "tab:5", "amigos", "circulos", "duelos", "pactos", "plano", "sessao", "nutricao", "prato", "peso", "trofeus", "titulos", "config", "sistema", "fotos", "rotinas", "celebracoes", "arena", "health", "wger", "alimentos", "scanner", "receitas", "ferramentas", "privacidade") ||
            (target.startsWith("ex:") && target.removePrefix("ex:") in exerciseIds) ||
            (target.startsWith("camera:") && target.removePrefix("camera:") in exerciseIds) ||
            (target.startsWith("patente:") && Musculo.entries.any { it.name == target.removePrefix("patente:") })
        "rest" -> seconds in 15..600
        "cancel_rest", "start_suggested" -> true
        "start_workout" -> ids.size in 1..8 && ids.distinct().size == ids.size && ids.all { it in exerciseIds } && name.isNotBlank() && name.length <= 80
        else -> false
    }
}
