package com.liferpg.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.ui.theme.*
import kotlin.math.*

internal fun mentorLocalCue(vm: MainViewModel, screen: String): String {
    val s = vm.sessao.value
    val rest = vm.descansoRestante.value
    return when {
        rest != null && rest > 0 -> "Recuperando energia · ${rest}s de descanso"
        s != null && !s.concluida -> "${s.seriesFeitas}/${s.seriesPlanejadas} séries · ${s.itemAtual?.item?.exercicio?.nome ?: s.nome}"
        screen.startsWith(RemnantRoute.EXERCISE_PREFIX) -> "Vamos entender este movimento e sua próxima série."
        screen == RemnantRoute.NUTRITION -> "${vm.nutricaoHoje.value.calorias.toInt()} kcal registradas hoje. Vamos olhar seu combustível."
        screen == RemnantRoute.tab(1) || screen.startsWith(RemnantRoute.MUSCLE_RANK_PREFIX) -> "Sua força tem história. Vamos olhar o próximo avanço."
        else -> vm.revisaoSemanal.value.message
    }
}

internal fun mentorSnapshot(vm: MainViewModel, screen: String): String = buildString {
    val p = vm.prefsEfetivas.value
    val s = vm.sessao.value
    appendLine("Tela atual: $screen; momento: ${java.time.LocalDateTime.now()}")
    appendLine("Objetivo=${p.objetivo}; experiência=${p.experiencia}; local=${p.localPreferido}; dias/semana=${p.diasPorSemana}")
    appendLine("Semana: ${vm.metasSemana.value.treinos}/${vm.metasSemana.value.metaTreinos} treinos; ${vm.metasSemana.value.series}/${vm.metasSemana.value.metaSeries} séries")
    appendLine("Revisão local desta semana: ${vm.revisaoSemanal.value.message}")
    vm.jornadas.value.firstOrNull { !it.finished && !it.campaign.archived }?.let {
        appendLine("Campanha pessoal: ${it.campaign.name}; ${it.victories}/${it.campaign.weeks} capítulos conquistados; meta=${it.campaign.weeklyGoal} dias/semana. Descanso não cria séries ou XP.")
    }
    s?.itemAtual?.item?.exercicio?.let { ex -> appendLine("Próximo passo calculado localmente: ${vm.alvoDo(ex).reason}") }
    appendLine("Patente muscular: média dos pontos normalizados dos exercícios registrados; exercícios sem registro ficam fora. Cada exercício tem limiares próprios.")
    appendLine("Patentes: " + vm.resumoCompetitivo.value.ratings.entries.joinToString { "${it.key.name}=${it.value.rotulo}, poder=${it.value.rating.toInt()}" })
    if(screen.startsWith(RemnantRoute.MUSCLE_RANK_PREFIX)) {
        val muscle=com.liferpg.domain.Musculo.entries.find { it.name==screen.removePrefix(RemnantRoute.MUSCLE_RANK_PREFIX) }
        val candidates=vm.exercicios.value.filter { it.musculo==muscle && (vm.melhoresScores.value[it.id] ?: 0f)>0f && it.benchmarks?.size==7 }
        val today=vm.hoje.value
        val decay=vm.ultimoTreinoPorMusculo.value[muscle]?.let { com.liferpg.domain.RankEngine.fatorDecay((today-it).coerceAtLeast(0)) } ?: 1f
        appendLine("Exercícios registrados do músculo em foco (${candidates.size}; listados até 20): " + candidates.take(20).joinToString { ex ->
            val r=com.liferpg.domain.RankEngine.ratingMusculo(listOf(ex),vm.melhoresScores.value,decay)
            "${ex.nome}: ${r.rotulo}, ${r.rating.toInt()} pontos"
        })
    }
    appendLine("Sessão: " + if (s == null || s.concluida) "nenhuma ativa" else "${s.nome}; ${s.seriesFeitas}/${s.seriesPlanejadas} séries; exercício atual=${s.itemAtual?.item?.exercicio?.nome}; XP=${s.xpGanho}")
    appendLine("Descanso restante=${vm.descansoRestante.value ?: 0}s")
    appendLine("Plano do dia: " + vm.treinoSugerido.value.itens.joinToString { "id=${it.exercicio.id}, ${it.exercicio.nome}, ${it.series} séries, ${it.repsRotulo}, descanso=${it.descansoSeg}s" })
    val food = vm.nutricaoHoje.value
    val targets = vm.metasNutricao.value
    appendLine("Diário alimentar registrado, não consumo total presumido: ${food.calorias.toInt()}/${targets.calorias} kcal; proteína ${food.proteinaG.toInt()}/${targets.proteinaG}g; carboidratos ${food.carboG.toInt()}/${targets.carboG}g; gorduras ${food.gorduraG.toInt()}/${targets.gorduraG}g. Meta zero significa sem referência definida. A Visão do REM na rota prato analisa uma foto escolhida, mas as porções precisam de revisão.")
    if (screen.startsWith(RemnantRoute.EXERCISE_PREFIX)) vm.exercicios.value.find { RemnantRoute.exercise(it.id) == screen }?.let {
        appendLine("Exercício em foco: ${it.id}, ${it.nome}, modo=${it.modo}; instruções=${it.instrucoes.joinToString().take(1000)}; melhor score=${vm.melhoresScores.value[it.id]}")
    }
}

internal fun mentorReaction(r: com.liferpg.data.ResultadoSerie): String = when {
    r.tipoRegistro == "MISSAO" && r.nivelNovo > r.nivelAnterior -> "Nível ${r.nivelNovo}. Suas ordens cumpridas também fazem parte da evolução."
    r.tipoRegistro == "MISSAO" -> "Ordem cumprida. +${r.xpGanho} XP recebidos pelo seu progresso."
    r.tipoRegistro == "CARDIO" && r.nivelNovo > r.nivelAnterior -> "Nível ${r.nivelNovo}. Seu cardio também faz parte dessa evolução."
    r.tipoRegistro == "CARDIO" -> "Cardio concluído. Seu movimento garantiu a sequência de hoje. +${r.xpGanho} XP."
    r.patenteNova > r.patenteAnterior -> "${r.patenteNova.titulo}. Você acaba de subir de patente."
    r.novoRecorde -> "Novo recorde registrado. +${r.xpGanho} XP. Respira e recupera."
    r.nivelNovo > r.nivelAnterior -> "Nível ${r.nivelNovo}. Essa série fez a diferença."
    r.xpGanho > 0 -> "Série confirmada. +${r.xpGanho} XP. Uma a menos, evolução a mais."
    else -> "Série registrada. Confira os critérios de XP no exercício."
}

@Composable internal fun MentorOrb(size: Dp = 44.dp, thinking: Boolean = false) {
    val clock = LocalMotion.current
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val phase = clock.orbit.value * PI.toFloat()/180f
        val color = Color(0xFF8BF4EE)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.25f), Color.Transparent)), r)
        // Projected orbital cages: depth changes continuously as each axis turns.
        repeat(3) { axis ->
            var last: Offset? = null
            repeat(81) { i ->
                val a = i * 2f * PI.toFloat()/80f
                val v = Vertex3(cos(a)*.75f,sin(a)*.75f,0f)
                    .rotate(phase*(if(thinking) 3f else 1f)+axis*1.1f, .6f+axis*.7f)
                val pt = center + Offset(v.x*r,v.y*r)
                last?.let { drawLine(color.copy(alpha=(.4f+v.z*.5f).coerceIn(.12f,.9f)),it,pt,1.5.dp.toPx()) }
                last = pt
            }
        }
        repeat(12) { i ->
            val a = i*PI.toFloat()/6f - phase
            val inner = center+Offset(cos(a),sin(a))*r*.86f
            val outer = center+Offset(cos(a),sin(a))*r*.94f
            drawLine(color.copy(alpha=.5f),inner,outer,1.dp.toPx())
        }
        val core = r*(.17f+clock.pulse.value*.05f)
        drawCircle(Brush.radialGradient(listOf(Color.White,color,Color.Transparent),center,core*2.5f),core*2.5f)
        drawCircle(Color.White,core*.48f)
    }
}

@Composable internal fun MentorDock(vm: MainViewModel, mentor: MentorViewModel, quiet: Boolean = false, open: () -> Unit) {
    // Keep the data subscriptions alive across every screen, including the mentor itself.
    val session by vm.sessao.collectAsStateWithLifecycle()
    val rest by vm.descansoRestante.collectAsStateWithLifecycle()
    val week by vm.metasSemana.collectAsStateWithLifecycle()
    val food by vm.nutricaoHoje.collectAsStateWithLifecycle()
    val ranks by vm.resumoCompetitivo.collectAsStateWithLifecycle()
    val plan by vm.treinoSugerido.collectAsStateWithLifecycle()
    val prefs by vm.prefsEfetivas.collectAsStateWithLifecycle()
    val targets by vm.metasNutricao.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val scores by vm.melhoresScores.collectAsStateWithLifecycle()
    val review by vm.revisaoSemanal.collectAsStateWithLifecycle()
    // Read observed state here so the cue tracks updates without any network request.
    val cue = remember(session, rest, week, food, ranks, plan, prefs, targets, catalog, scores, review, mentor.sourceScreen) { mentorLocalCue(vm, mentor.sourceScreen) }
    val active = session?.concluida == false
    if (quiet) {
        Row(Modifier.fillMaxWidth().background(HearthInk).clickable(onClick = open)
            .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(8.dp)) {
                drawCircle(if (mentor.busy) HearthIvory else HearthBrass)
            }
            Text("REM", color = HearthBrass, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 1.sp)
            Text(mentor.reaction ?: cue, color = HearthMuted, fontSize = 11.sp, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("↗", color = HearthBrass, fontSize = 18.sp)
        }
        return
    }
    val accent = Color(0xFF8BF4EE)
    MotionScene(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF10252C), Fundo)))
            .padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = open), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MentorOrb(52.dp, mentor.busy || mentor.reaction != null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow(when { mentor.busy -> "REM / ANALISANDO"; mentor.reaction != null -> "REM / COM VOCÊ"; rest != null && rest!! > 0 -> "REM / RECUPERAÇÃO"; active -> "REM / TREINO AO VIVO"; else -> "REM / ACOMPANHANDO" }, accent)
                    androidx.compose.animation.AnimatedContent(mentor.reaction ?: cue, label = "mentorReaction") { message ->
                        Text(message, fontSize = 12.sp, color = TextoPrimario, maxLines = 2)
                    }
                }
                Text("↗", color = accent, fontSize = 24.sp)
            }
        }
    }
}
