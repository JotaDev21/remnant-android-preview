package com.liferpg.domain

import com.liferpg.data.CardioSession
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class CardioActivity(val title: String, val subtitle: String, val distance: Boolean, val pace: Boolean = false) {
    RUN("Corrida", "Rua, parque ou pista", true, true),
    WALK("Caminhada", "Cada passo conta", true, true),
    TREADMILL("Esteira", "Corrida ou caminhada indoor", true, true),
    BIKE("Bicicleta", "Ao ar livre ou ergométrica", true),
    ELLIPTICAL("Elíptico", "Ritmo contínuo na máquina", true),
    STAIRS("Escada", "Máquina ou escadaria", false),
    ROW("Remo", "Ergômetro ou remo indoor", true),
    AEROBIC("Aeróbico", "Aula, dança ou circuito", false),
    ROPE("Pular corda", "Sessão por tempo", false),
    OTHER("Outro cardio", "Seu movimento, seu ritmo", false);
}

enum class CardioGoal { FREE, TIME, DISTANCE }
enum class CardioEffort(val title: String) { EASY("Leve"), MODERATE("Moderado"), HARD("Intenso") }

/** Monotonic time includes sleep; wall time is used only for the journal date. */
data class CardioClock(val elapsed: Long, val boot: Int, val wall: Long, val device: String = "")

data class CardioSummary(val minutes: Int, val distanceKm: Double, val sessions: Int, val days: Int, val dailyMinutes: List<Int>)

object CardioEngine {
    const val MAX_MS = 6 * 60 * 60 * 1000L
    const val STREAK_SECONDS = 5 * 60
    const val DAILY_XP_CAP = 120

    fun qualifyingDays(rows: List<CardioSession>): Set<Long> = rows.filter { it.status == "FINISHED" && it.accumulatedMs >= STREAK_SECONDS * 1000L }.map { it.epochDay }.toSet()

    fun start(activity: CardioActivity, goal: CardioGoal, target: Double, clock: CardioClock): CardioSession {
        require(goal != CardioGoal.DISTANCE || activity.distance) { "Essa atividade usa metas por tempo." }
        require(target.isFinite())
        require(when (goal) { CardioGoal.FREE -> target == 0.0; CardioGoal.TIME -> target in 1.0..360.0; CardioGoal.DISTANCE -> target in 0.1..200.0 }) { "Confira sua meta." }
        return CardioSession(UUID.randomUUID().toString(), activity.name, goal.name, target, clock.wall, 0L,
            clock.elapsed, clock.boot, "RUNNING", null, "", "", "TIMER", 0L, 0L, 0, clock.device)
    }

    fun elapsed(row: CardioSession, clock: CardioClock): Long = (row.accumulatedMs +
        if (row.status == "RUNNING" && row.boot == clock.boot && row.device == clock.device && clock.elapsed >= row.anchorMs)
            clock.elapsed - row.anchorMs else 0L).coerceIn(0L, MAX_MS)

    fun checkpoint(row: CardioSession, clock: CardioClock): CardioSession {
        val uninterrupted = row.boot == clock.boot && row.device == clock.device && clock.elapsed >= row.anchorMs
        return row.copy(accumulatedMs = elapsed(row, clock), anchorMs = clock.elapsed, boot = clock.boot, device = clock.device,
            status = if (row.status == "RUNNING" && !uninterrupted) "PAUSED" else row.status)
    }

    fun pause(row: CardioSession, clock: CardioClock) = checkpoint(row, clock).copy(status = "PAUSED")
    fun resume(row: CardioSession, clock: CardioClock) = checkpoint(row, clock).copy(status = "RUNNING")

    fun reward(seconds: Int, earnedToday: Int): Int = if (seconds < STREAK_SECONDS) 0
        else minOf((seconds / 60) * 2, (DAILY_XP_CAP - earnedToday).coerceAtLeast(0))

    fun validate(row: CardioSession) {
        require(runCatching { UUID.fromString(row.id).toString() == row.id }.getOrDefault(false))
        val activity = CardioActivity.valueOf(row.activity)
        val goal = CardioGoal.valueOf(row.goal)
        require(row.target.isFinite() && when (goal) { CardioGoal.FREE -> row.target == 0.0; CardioGoal.TIME -> row.target in 1.0..360.0; CardioGoal.DISTANCE -> activity.distance && row.target in 0.1..200.0 })
        require(row.startedAt > 0L && row.accumulatedMs in 0..MAX_MS && row.anchorMs >= 0L)
        require(row.distanceKm == null || (activity.distance && row.distanceKm.isFinite() && row.distanceKm in 0.01..200.0)) { "Informe uma distância entre 0,01 e 200 km, ou deixe em branco." }
        require(row.effort.isEmpty() || row.effort in CardioEffort.entries.map { it.name })
        require(row.note.length <= 400 && row.source in listOf("TIMER", "MANUAL", "GPS"))
        require(row.source != "GPS" || activity in listOf(CardioActivity.RUN, CardioActivity.WALK, CardioActivity.BIKE))
        require(row.steps == null || row.steps in 0L..200_000L)
        require(row.device.length <= 128)
        require(row.status in listOf("RUNNING", "PAUSED", "FINISHED"))
        require(row.xpGained in 0..DAILY_XP_CAP)
        if (row.status == "FINISHED") require(row.accumulatedMs >= 60_000 && row.endedAt >= row.startedAt && row.epochDay in 0..100_000)
    }

    fun pace(seconds: Int, km: Double?): String? = km?.takeIf { it.isFinite() && it > 0 && seconds > 0 }?.let {
        val perKm = (seconds / it).roundToInt()
        "${perKm / 60}:${(perKm % 60).toString().padStart(2, '0')}"
    }
    fun speed(seconds: Int, km: Double?): String? = km?.takeIf { it.isFinite() && it > 0 && seconds > 0 }?.let {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f", it * 3600 / seconds)
    }
    fun km(value: Double) = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)
    fun time(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (seconds < 3600) "%02d:%02d".format(seconds / 60, seconds % 60)
            else "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    fun week(rows: List<CardioSession>, today: Long): CardioSummary {
        val monday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        val selected = rows.filter { it.status == "FINISHED" && it.epochDay in monday..today }
        return CardioSummary((selected.sumOf { it.accumulatedMs } / 60_000).toInt(), selected.sumOf { it.distanceKm ?: 0.0 },
            selected.size, selected.map { it.epochDay }.distinct().size,
            (0L..6L).map { day -> (selected.filter { it.epochDay == monday + day }.sumOf { it.accumulatedMs } / 60_000).toInt() })
    }
}
