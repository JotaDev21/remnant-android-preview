package com.liferpg.domain

import com.liferpg.data.SerieRegistro
import com.liferpg.data.TreinoSessao
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ArchivePeriod(val label: String) { WEEK("Semana"), MONTH("30 dias"), ALL("Tudo") }

data class ArchivedSet(val kg: Float?, val reps: Int?, val seconds: Int?, val pr: Boolean = false,
    val valid: Boolean = true, val equipment: String = "")
data class ArchivedExercise(val id: String, val name: String, val muscle: String, val sets: List<ArchivedSet>)
data class ArchivedWorkout(val session: TreinoSessao, val exercises: List<ArchivedExercise>, val exact: Boolean)
data class ArchiveDay(val day: Long, val exercises: List<ArchivedExercise>) {
    val sets get() = exercises.sumOf { it.sets.size }
}
data class WorkoutArchive(val sessions: List<ArchivedWorkout>, val days: List<ArchiveDay>)

/** Read-only projections. Sessions use their own receipts; a date never assigns sets to a session. */
object WorkoutArchiveEngine {
    fun days(rows: List<SerieRegistro>, catalog: List<Exercicio>): List<ArchiveDay> {
        val names=catalog.associateBy { it.id }
        return rows.groupBy { it.epochDay }.toSortedMap(reverseOrder()).map { (day,sets) ->
            ArchiveDay(day,sets.sortedWith(compareBy<SerieRegistro> { it.timestamp }.thenBy { it.id })
                .groupBy { it.exercicioId }.map { (id,records) ->
                    ArchivedExercise(id,names[id]?.nome ?: id,names[id]?.musculo?.nomeExibicao
                        ?: Musculo.entries.firstOrNull { it.name==records.first().musculo }?.nomeExibicao ?: records.first().musculo,
                        records.map { ArchivedSet(it.pesoKg,it.reps,it.segundos,valid=it.valido,equipment=it.equipamento) })
                })
        }
    }
    fun contains(day: Long, today: Long, period: ArchivePeriod): Boolean {
        val start=when(period) {
            ArchivePeriod.WEEK -> LocalDate.ofEpochDay(today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
            ArchivePeriod.MONTH -> today-29
            ArchivePeriod.ALL -> Long.MIN_VALUE
        }
        return day in start..today
    }
    fun matches(text: String, query: String): Boolean = clean(query).split(' ').filter { it.isNotBlank() }.all { it in clean(text) }
    fun sessions(archive: WorkoutArchive, today: Long, period: ArchivePeriod, query: String): List<ArchivedWorkout> =
        archive.sessions.filter { contains(it.session.epochDay,today,period) &&
            matches(it.session.nome+" "+it.exercises.joinToString(" ") { e -> e.name+" "+e.muscle },query) }
            .sortedWith(compareByDescending<ArchivedWorkout> { it.session.encerradaEm }.thenByDescending { it.session.id })
    fun days(archive: WorkoutArchive, today: Long, period: ArchivePeriod, query: String): List<ArchiveDay> =
        archive.days.filter { contains(it.day,today,period) }.mapNotNull { day ->
            val exercises=day.exercises.filter { matches(it.name+" "+it.muscle,query) }
            if(exercises.isEmpty()) null else day.copy(exercises=exercises)
        }
    private fun clean(value: String) = Normalizer.normalize(value.lowercase(java.util.Locale.ROOT),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").trim()
}
