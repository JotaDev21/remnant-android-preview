package com.liferpg.domain

import com.liferpg.data.SerieRegistro

data class MuscleAscension(val day: Long, val rank: Patente)
data class MuscleMemory(val firstDay: Long?, val trainedDays: Int, val sets: Int, val exercises: Int, val milestones: List<MuscleAscension>)

object MuscleChronicle {
    /** Historical milestones use precisely the existing average-of-registered-exercises formula. */
    fun build(muscle: Musculo, catalog: List<Exercicio>, history: List<SerieRegistro>): MuscleMemory {
        val exercises = catalog.filter { it.musculo == muscle }
        val ids = exercises.map { it.id }.toSet()
        val rows = history.filter { it.valido && it.exercicioId in ids }.sortedWith(compareBy<SerieRegistro> { it.epochDay }.thenBy { it.timestamp })
        val scores = mutableMapOf<String, Float>(); val milestones = mutableListOf<MuscleAscension>()
        var peak = Patente.SEM_PATENTE
        for ((day, sets) in rows.groupBy { it.epochDay }) {
            sets.forEach { s -> scores[s.exercicioId] = maxOf(scores[s.exercicioId] ?: 0f, s.score) }
            val current = RankEngine.ratingMusculo(exercises, scores).patente
            if (current > peak) { milestones += MuscleAscension(day, current); peak = current }
        }
        return MuscleMemory(rows.firstOrNull()?.epochDay, rows.map { it.epochDay }.distinct().size, rows.size, scores.size, milestones)
    }
}
