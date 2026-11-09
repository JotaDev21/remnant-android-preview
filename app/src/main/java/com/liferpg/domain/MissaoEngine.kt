package com.liferpg.domain

import com.liferpg.data.CardioSession
import com.liferpg.data.MissaoDiaria
import com.liferpg.data.SerieRegistro
import java.time.LocalDate

enum class TipoMissao { TREINAR_MUSCULO, TOTAL_SERIES, BATER_RECORDE }
enum class SystemMode(val titulo: String, val descricao: String) {
    GYM("Academia", "Séries válidas do seu treino."),
    HOME("Em casa", "Flexões e agachamentos registrados, com ou sem câmera."),
    CARDIO("Cardio", "Tempo ativo de atividades concluídas."),
}
data class MissionFacts(val day: Long, val sets: List<SerieRegistro>, val cardio: List<CardioSession>, val waterRecords: Int)
data class MissionAdvance(val mission: MissaoDiaria, val awarded: Boolean)

/** Orders use the persistent legacy ledger. No XP is granted by a UI action. */
object MissaoEngine {
    const val MISSOES_POR_DIA = 3
    const val WEEK = "SYS_WEEK_ACTIVITY"
    fun inicioSemana(day: Long) = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
    fun semanal(m: MissaoDiaria) = m.tipo == WEEK
    fun mode(missions: List<MissaoDiaria>): SystemMode = when {
        missions.any { it.tipo == "SYS_HOME_REPS" } -> SystemMode.HOME
        missions.any { it.tipo == "SYS_CARDIO_MIN" } -> SystemMode.CARDIO
        else -> SystemMode.GYM
    }
    fun gerarMissoes(epochDay: Long, mode: SystemMode = SystemMode.GYM): List<MissaoDiaria> {
        fun order(type: String, title: String, target: Int, xp: Int) =
            MissaoDiaria(epochDay=epochDay,tipo=type,descricao=title,alvoMusculo=null,alvoQtd=target,xpRecompensa=xp)
        return listOf(
            order("SYS_START", "Abra sua jornada do dia", 1, 30),
            when (mode) {
                SystemMode.GYM -> order("SYS_GYM_SETS", "Construa seu treino", 6, 60)
                SystemMode.HOME -> order("SYS_HOME_REPS", "Seu corpo é a arma", 24, 60)
                SystemMode.CARDIO -> order("SYS_CARDIO_MIN", "Encontre seu ritmo", 15, 60)
            },
            order("SYS_CARE", "Cuide da recuperação", 1, 20),
        )
    }
    fun gerarSemana(day: Long, goal: Int) = MissaoDiaria(epochDay=inicioSemana(day),tipo=WEEK,
        descricao="Complete sua semana de atividade",alvoMusculo=null,alvoQtd=goal.coerceIn(1,7),xpRecompensa=100)
    fun avancar(m: MissaoDiaria, facts: MissionFacts, newPr: Boolean = false): MissionAdvance {
        if (m.concluida || m.alvoQtd <= 0 || (if (semanal(m)) inicioSemana(facts.day)!=m.epochDay else facts.day!=m.epochDay))
            return MissionAdvance(m,false)
        val today = facts.sets.filter { it.valido && it.epochDay == facts.day }
        val cardio = facts.cardio.filter { it.status=="FINISHED" && it.epochDay==facts.day }
        val progress = when(m.tipo) {
            "SYS_START" -> if(today.isNotEmpty() || cardio.any { it.accumulatedMs>=CardioEngine.STREAK_SECONDS*1000L }) 1 else 0
            "SYS_GYM_SETS", "TOTAL_SERIES" -> today.size
            "SYS_HOME_REPS" -> today.filter { it.exercicioId in CameraBoss.entries.map(CameraBoss::exerciseId) }
                .sumOf { (it.reps ?: 0).coerceIn(0,500) }
            "SYS_CARDIO_MIN" -> (cardio.sumOf { it.accumulatedMs.coerceAtLeast(0) }/60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            "SYS_CARE" -> if(facts.waterRecords>0) 1 else 0
            WEEK -> (facts.sets.filter { it.valido && it.epochDay in m.epochDay..facts.day }.map { it.epochDay } +
                facts.cardio.filter { it.status=="FINISHED" && it.epochDay in m.epochDay..facts.day && it.accumulatedMs>=CardioEngine.STREAK_SECONDS*1000L }.map { it.epochDay }).distinct().size
            "TREINAR_MUSCULO" -> today.count { it.musculo==m.alvoMusculo }
            "BATER_RECORDE" -> m.progresso + if(newPr) 1 else 0
            else -> m.progresso
        }
        val capped=maxOf(m.progresso,progress).coerceAtMost(m.alvoQtd)
        val done=capped>=m.alvoQtd
        return MissionAdvance(m.copy(progresso=capped,concluida=done),done)
    }
    fun instrucao(m: MissaoDiaria): String = when(m.tipo) {
        "SYS_START" -> "Registre uma série válida ou conclua pelo menos 5 minutos de cardio. Academia e treino em casa contam."
        "SYS_GYM_SETS" -> "Registre ${m.alvoQtd} séries válidas no treino de hoje. Você escolhe os exercícios e as cargas."
        "SYS_HOME_REPS" -> "Some ${m.alvoQtd} flexões ou agachamentos registrados hoje. A câmera pode validar os movimentos; o registro manual também conta."
        "SYS_CARDIO_MIN" -> "Some ${m.alvoQtd} minutos ativos em sessões concluídas de corrida, caminhada ou outro cardio. Pausas e cronômetros ainda abertos não contam."
        "SYS_CARE" -> "Registre no diário a água que você já bebeu. Vale uma vez por dia, sem impor uma meta extra de consumo."
        WEEK -> "Tenha atividade registrada em ${m.alvoQtd} dias distintos nesta semana. Uma série válida ou cardio concluído de 5 minutos conta; várias atividades no mesmo dia continuam valendo um dia."
        else -> m.descricao
    }
    fun unidade(m: MissaoDiaria) = when(m.tipo) {
        "SYS_START", "SYS_CARE", "BATER_RECORDE" -> "${m.progresso.coerceAtMost(m.alvoQtd)} / ${m.alvoQtd}"
        "SYS_HOME_REPS" -> "${m.progresso.coerceAtMost(m.alvoQtd)} / ${m.alvoQtd} reps"
        "SYS_CARDIO_MIN" -> "${m.progresso.coerceAtMost(m.alvoQtd)} / ${m.alvoQtd} min"
        WEEK -> "${m.progresso.coerceAtMost(m.alvoQtd)} / ${m.alvoQtd} dias"
        else -> "${m.progresso.coerceAtMost(m.alvoQtd)} / ${m.alvoQtd} séries"
    }
}
