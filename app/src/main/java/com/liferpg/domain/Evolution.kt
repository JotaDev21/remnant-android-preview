package com.liferpg.domain

import com.liferpg.data.EvolutionEntry
import com.liferpg.data.SerieRegistro
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.floor

enum class SetEffort(val label: String) { LEVE("Leve"), CERTO("Na medida"), PESADO("Muito difícil") }

data class EquipmentSetup(val label: String = "", val step: Float = 0f, val maximum: Float = 0f) {
    fun validate() {
        require(label.length <= 60 && step.isFinite() && step in 0f..100f)
        require(maximum.isFinite() && maximum in 0f..3000f)
    }
    fun next(weight: Float): Float? {
        if (step <= 0f) return null
        val next = (floor((weight + .001f) / step) + 1f) * step
        return next.takeIf { it > weight && (maximum == 0f || it <= maximum) }
    }
}

data class TrainingTarget(val previous: List<SerieRegistro>, val kg: Float?, val reps: Int?, val seconds: Int?, val reason: String) {
    val hasReference get() = previous.isNotEmpty()
}

object AdaptiveProgression {
    /** Never uses invalid, future or a different machine's sets as a personal reference. */
    fun target(item: ItemTreino, history: List<SerieRegistro>, equipment: EquipmentSetup,
        beforeTimestamp: Long, today: Long, current: List<SerieRegistro> = emptyList()): TrainingTarget {
        val usable = history.filter { it.exercicioId == item.exercicio.id && it.valido && it.timestamp < beforeTimestamp &&
            it.epochDay <= today && it.equipamento == equipment.label }
        val day = usable.maxOfOrNull { it.epochDay }
        val previous = usable.filter { it.epochDay == day }.sortedBy { it.timestamp }.takeLast(item.series)
        fun suggestion(kg: Float?, reps: Int?, seconds: Int?, reason: String) = TrainingTarget(previous, kg, reps, seconds, reason)
        val last = previous.lastOrNull() ?: return suggestion(null, null, null,
            "Primeiro registro neste equipamento. Escolha uma carga confortável e registre o resultado real para formar sua referência.")
        val difficult = (previous + current.filter { it.valido && it.exercicioId == item.exercicio.id && it.equipamento == equipment.label }).any { it.esforco == SetEffort.PESADO.name }
        val returning = today - (day ?: today) >= 14
        val guidance = when {
            returning -> "Você voltou após uma pausa. Use a marca anterior como referência, ajuste a carga e retome sem obrigação de bater recorde."
            difficult -> "Você marcou uma série como muito difícil. Mantenha ou ajuste a carga; hoje não há aumento automático."
            else -> null
        }
        if (guidance != null) return suggestion(last.pesoKg, last.reps, last.segundos, guidance)
        return when (item.exercicio.modo) {
            ModoMedida.CARGA -> {
                val kg = last.pesoKg ?: return suggestion(null, null, null, "Ainda falta uma carga registrada.")
                val minimum = previous.mapNotNull { it.reps }.minOrNull() ?: item.repMin
                val complete = previous.size >= item.series && previous.all { it.pesoKg == kg && (it.reps ?: 0) >= item.repMax }
                val next = equipment.next(kg)
                when {
                    complete && next != null && (next - kg) / kg.coerceAtLeast(1f) <= .15f ->
                        suggestion(next, item.repMin, null, "Você atingiu o teto nas ${item.series} séries. O próximo incremento disponível é uma opção; confirme que a execução continua confortável.")
                    complete -> suggestion(kg, item.repMax, null, if (equipment.step <= 0f)
                        "Você completou a faixa nas séries planejadas. Defina os pesos disponíveis antes de sugerir uma carga maior."
                        else "Mantenha esta carga. O próximo peso está indisponível ou representa um salto grande; avance no seu ritmo.")
                    minimum < item.repMin -> suggestion(kg, minimum.coerceAtLeast(1), null,
                        "Sua última sessão ficou abaixo da faixa. Ajuste a carga se necessário e consolide a execução antes de aumentar.")
                    else -> suggestion(kg, (minimum + 1).coerceAtMost(item.repMax), null,
                        "Mantenha a carga e tente acrescentar uma repetição à série mais curta da sessão anterior. A meta é uma referência, não uma obrigação.")
                }
            }
            ModoMedida.REPS -> suggestion(null, (previous.mapNotNull { it.reps }.minOrNull() ?: 0) + 1, null,
                "Se a execução estiver confortável, tente uma repetição a mais na sua próxima série.")
            ModoMedida.TEMPO -> suggestion(null, null, (last.segundos ?: 0) + 5,
                "Seu último tempo é a referência. Um avanço de cinco segundos é opcional; mantenha a execução.")
        }
    }
}

data class PersonalCampaign(val id: String, val name: String, val startDay: Long, val createdAt: Long,
    val weeks: Int, val weeklyGoal: Int, val baseline: Map<String, Float>, val archived: Boolean = false, val closedAt: Long? = null) {
    val endDay get() = startDay + weeks * 7 - 1
}
data class CampaignChapter(val index: Int, val firstDay: Long, val lastDay: Long, val days: Int, val goal: Int, val closed: Boolean) {
    val complete get() = days >= goal
    val progress get() = (days.toFloat() / goal).coerceIn(0f, 1f)
}
data class CampaignView(val campaign: PersonalCampaign, val chapters: List<CampaignChapter>, val improvements: List<String>, val finished: Boolean) {
    val victories get() = chapters.count { it.complete }
    val conquered get() = finished && !campaign.archived && victories == campaign.weeks
}
data class WeeklyReview(val firstDay: Long, val trainedDays: Int, val goal: Int, val sets: Int,
    val improvements: List<Pair<String, Float>>, val message: String) {
    val progress get() = (trainedDays.toFloat() / goal).coerceIn(0f, 1f)
}

object EvolutionEngine {
    fun start(name: String, today: Long, now: Long, weeks: Int, goal: Int, history: List<SerieRegistro>): PersonalCampaign {
        require(name.isNotBlank() && name.length <= 60 && weeks in 4..8 && goal in 1..7)
        return PersonalCampaign(UUID.randomUUID().toString(), name.trim(), today, now, weeks, goal,
            history.filter { it.valido && it.timestamp < now && it.epochDay <= today }.groupBy { referenceKey(it) }.mapValues { (_, sets) -> sets.maxOf { performance(it) } })
    }
    fun campaign(c: PersonalCampaign, history: List<SerieRegistro>, today: Long, cardio: List<com.liferpg.data.CardioSession> = emptyList()): CampaignView {
        val eligible = history.filter { it.valido && it.timestamp >= c.createdAt && it.timestamp <= (c.closedAt ?: Long.MAX_VALUE) && it.epochDay in c.startDay..minOf(today, c.endDay) }
        val movementDays = cardio.filter { it.status == "FINISHED" && it.startedAt >= c.createdAt && it.endedAt <= (c.closedAt ?: Long.MAX_VALUE) &&
            it.accumulatedMs >= CardioEngine.STREAK_SECONDS * 1000L && it.epochDay in c.startDay..minOf(today, c.endDay) }.map { it.epochDay }
        val chapters = (0 until c.weeks).map { index ->
            val start = c.startDay + index * 7
            CampaignChapter(index, start, start + 6, (eligible.filter { it.epochDay in start..start+6 }.map { it.epochDay } + movementDays.filter { it in start..start+6 }).distinct().size,
                c.weeklyGoal, today > start + 6)
        }
        val improvements = eligible.groupBy { referenceKey(it) }.filter { (id, sets) ->
            c.baseline[id]?.let { base -> base > 0f && sets.maxOf { performance(it) } > base * 1.005f } ?: false
        }.values.map { it.first().exercicioId }.distinct().sorted()
        return CampaignView(c, chapters, improvements, today > c.endDay)
    }
    /** A calendar week, independent of the daily consecutive-day streak. Rest is never a fake set. */
    fun week(history: List<SerieRegistro>, today: Long, goal: Int, movementDays: Set<Long> = emptySet()): WeeklyReview {
        val start = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        val valid = history.filter { it.valido && it.epochDay <= today }
        val current = valid.filter { it.epochDay in start..today }
        val improved = current.groupBy { it.exercicioId }.mapNotNull { (id, sets) ->
            val label = sets.maxBy { it.timestamp }.equipamento
            val reference = valid.filter { it.exercicioId == id && it.epochDay < start && it.equipamento == label }
            val previousDay = reference.maxOfOrNull { it.epochDay } ?: return@mapNotNull null
            val base = reference.filter { it.epochDay == previousDay }.maxOf { performance(it) }
            val best = sets.filter { it.equipamento == label }.maxOf { performance(it) }
            if (base > 0 && best > base * 1.005f) id to ((best / base - 1) * 100) else null
        }.sortedByDescending { it.second }
        val days = (current.map { it.epochDay } + movementDays.filter { it in start..today }).distinct().size
        val target = goal.coerceIn(1, 7)
        val message = when {
            current.isEmpty() && days == 0 -> "Seu próximo treino abre a semana. Seu histórico e suas conquistas continuam aqui."
            improved.isNotEmpty() -> "Você evoluiu em ${improved.size} exercício${if (improved.size == 1) "" else "s"}. Repetições e tempo também contam como progresso."
            days >= target -> "Compromisso semanal cumprido. Você pode seguir o descanso previsto no seu planejamento."
            else -> "$days de $target dias registrados nesta semana. Confira o próximo treino e siga sua agenda."
        }
        return WeeklyReview(start, days, target, current.size, improved, message)
    }
    fun performance(s: SerieRegistro): Float = when {
        s.pesoKg != null && s.reps != null -> RankEngine.e1rm(s.pesoKg, s.reps)
        s.reps != null -> s.reps.toFloat()
        else -> (s.segundos ?: 0).toFloat()
    }
    private fun referenceKey(s: SerieRegistro) = s.exercicioId + "\u001f" + s.equipamento
}

object EvolutionCodec {
    fun equipment(row: EvolutionEntry?): EquipmentSetup = row?.let {
        val o = JSONObject(it.payload)
        EquipmentSetup(o.optString("label"), o.optDouble("step", 0.0).toFloat(), o.optDouble("maximum", 0.0).toFloat()).also { value -> value.validate() }
    } ?: EquipmentSetup()
    fun encodeEquipment(id: String, setup: EquipmentSetup): EvolutionEntry {
        setup.validate(); require(id.isNotBlank() && id.length <= 160)
        return EvolutionEntry("equipment:$id", JSONObject().put("label", setup.label.trim()).put("step", setup.step.toDouble()).put("maximum", setup.maximum.toDouble()).toString())
    }
    fun encodeCampaign(c: PersonalCampaign): EvolutionEntry = EvolutionEntry("campaign:${c.id}", JSONObject().apply {
        put("id", c.id); put("name", c.name); put("startDay", c.startDay); put("createdAt", c.createdAt)
        put("weeks", c.weeks); put("weeklyGoal", c.weeklyGoal); put("archived", c.archived)
        put("closedAt", c.closedAt ?: JSONObject.NULL)
        put("baseline", JSONObject(c.baseline.mapValues { it.value.toDouble() }))
    }.toString())
    fun campaign(row: EvolutionEntry): PersonalCampaign {
        val o = JSONObject(row.payload); val base = o.getJSONObject("baseline")
        val c = PersonalCampaign(o.getString("id"), o.getString("name"), o.getLong("startDay"), o.getLong("createdAt"),
            o.getInt("weeks"), o.getInt("weeklyGoal"), base.keys().asSequence().associateWith { base.getDouble(it).toFloat() }, o.optBoolean("archived"), if (o.isNull("closedAt")) null else o.getLong("closedAt"))
        require(row.id == "campaign:${c.id}" && runCatching { UUID.fromString(c.id) }.isSuccess && c.name.isNotBlank() && c.name.length <= 60)
        require(c.weeks in 4..8 && c.weeklyGoal in 1..7 && c.startDay in 0..100_000 && c.createdAt > 0 && c.baseline.size <= 2000)
        require(c.closedAt == null || c.closedAt >= c.createdAt)
        require(c.baseline.all { it.key.length in 1..224 && it.value.isFinite() && it.value >= 0 })
        return c
    }
    fun validate(row: EvolutionEntry) {
        require(row.payload.length <= 100_000)
        when {
            row.id.startsWith("campaign:") -> campaign(row)
            row.id.startsWith("equipment:") -> { require(row.id.length <= 170); equipment(row) }
            else -> error("Registro de evolução desconhecido.")
        }
    }
}
