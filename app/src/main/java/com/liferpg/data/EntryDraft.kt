package com.liferpg.data

import android.content.Context
import com.liferpg.domain.*

data class EntryAnswers(
    val step: Int = 0, val nome: String = "", val peso: String = "", val altura: String = "",
    val idade: String = "", val objetivo: String = Objetivo.HIPERTROFIA.name,
    val experiencia: String = NivelExperiencia.INICIANTE.name, val local: String = LocalTreino.ACADEMIA.name,
    val foco: String = "", val dias: Set<Int> = setOf(1, 3, 5),
) {
    val pesoKg get() = peso.replace(',', '.').toFloatOrNull()
    val alturaCm get() = altura.toIntOrNull()
    val idadeAnos get() = idade.toIntOrNull()
    val musculo get() = Musculo.entries.firstOrNull { it.name == foco }
    fun valida(step: Int): Boolean = when (step) {
        0 -> nome.trim().length in 2..24 && nome.none { it.isISOControl() || it == '@' }
        1 -> pesoKg?.let { it.isFinite() && it in 30f..350f } == true &&
            alturaCm?.let { it in 100..250 } == true && idadeAnos?.let { it in 13..100 } == true
        2 -> Objetivo.entries.any { it.name == objetivo } && NivelExperiencia.entries.any { it.name == experiencia }
        3 -> dias.size in 1..6 && dias.all { it in 1..7 } && LocalTreino.entries.any { it.name == local } &&
            (foco.isBlank() || musculo != null)
        else -> true
    }
    fun completa() = (0..3).all(::valida)
}

/** Process-death-safe answers only. Passwords and auth tokens never enter this draft. */
class EntryDraft(context: Context) {
    private val prefs = context.getSharedPreferences("entry_answers_v1", Context.MODE_PRIVATE)
    fun load(): EntryAnswers = EntryAnswers(
        step = prefs.getInt("step", 0).coerceIn(0, 5), nome = prefs.getString("nome", "").orEmpty(),
        peso = prefs.getString("peso", "").orEmpty(), altura = prefs.getString("altura", "").orEmpty(),
        idade = prefs.getString("idade", "").orEmpty(), objetivo = prefs.getString("objetivo", "HIPERTROFIA").orEmpty(),
        experiencia = prefs.getString("experiencia", "INICIANTE").orEmpty(), local = prefs.getString("local", "ACADEMIA").orEmpty(),
        foco = prefs.getString("foco", "").orEmpty(), dias = prefs.getStringSet("dias", setOf("1", "3", "5"))
            .orEmpty().mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..7 } }.toSet())
    fun save(a: EntryAnswers) { prefs.edit().putInt("step", a.step).putString("nome", a.nome)
        .putString("peso", a.peso).putString("altura", a.altura).putString("idade", a.idade)
        .putString("objetivo", a.objetivo).putString("experiencia", a.experiencia).putString("local", a.local)
        .putString("foco", a.foco).putStringSet("dias", a.dias.map { it.toString() }.toSet()).apply() }
    fun clear() { prefs.edit().clear().apply() }
}
