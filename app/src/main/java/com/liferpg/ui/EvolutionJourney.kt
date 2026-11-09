package com.liferpg.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable internal fun JourneyPresence(vm: MainViewModel, open: (String) -> Unit) {
    val journeys by vm.jornadas.collectAsStateWithLifecycle()
    val week by vm.revisaoSemanal.collectAsStateWithLifecycle()
    val active = journeys.firstOrNull { !it.campaign.archived && !it.finished }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open(RemnantRoute.JOURNEY) }
        .border(1.dp, Borda, RoundedCornerShape(14.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Route, null, tint = TextoPrimario, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HearthLabel(if (active == null) "SUA PRÓXIMA JORNADA" else "CAMPANHA PESSOAL")
                Text(active?.campaign?.name ?: "Caminho da Forja", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            }
            Icon(Icons.Outlined.ChevronRight, "Abrir campanha e revisão", tint = TextoSecundario)
        }
        if (active != null) ChapterMarks(active)
        Text(if (active == null) "Transforme seu planejamento em capítulos. Seus registros continuam sendo a base."
            else "${active.victories}/${active.campaign.weeks} capítulos conquistados · ${active.improvements.size} exercícios em evolução",
            color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
        HorizontalDivider(color = Borda)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Compromisso semanal", color = TextoSecundario, fontSize = 12.sp)
            AnimatedLabel("${week.trainedDays}/${week.goal}", fontSize = 18.sp)
        }
        HearthProgress(week.progress)
    }
}

@Composable internal fun ChapterMarks(view: CampaignView) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        view.chapters.forEachIndexed { index, chapter ->
            if (index > 0) Box(Modifier.weight(1f).height(1.dp).background(if (view.chapters[index-1].complete) TextoPrimario else Borda))
            Canvas(Modifier.size(24.dp)) {
                drawCircle(if (chapter.complete) TextoPrimario else Borda, radius = size.minDimension / 2, style = Stroke(1.dp.toPx()))
                if (chapter.complete) drawCircle(TextoPrimario, radius = size.minDimension * .23f)
                else if (chapter.days > 0) drawArc(TextoPrimario, -90f, 360f * chapter.progress, false, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable internal fun EvolutionJourney(vm: MainViewModel, open: (String) -> Unit) {
    val journeys by vm.jornadas.collectAsStateWithLifecycle()
    val archive by vm.arquivoSemanal.collectAsStateWithLifecycle()
    val settings by vm.prefsEfetivas.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val today by vm.hoje.collectAsStateWithLifecycle()
    val active = journeys.firstOrNull { !it.campaign.archived && !it.finished }
    var create by rememberSaveable { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    Page("EVOLUÇÃO / SUA JORNADA", active?.campaign?.name ?: "Um treino.\nUm próximo passo.") {
        if (active == null) {
            Text("Escolha um compromisso e acompanhe cada capítulo com seus treinos reais. Seu planejamento permanece editável.", color = TextoSecundario, fontSize = 14.sp, lineHeight = 21.sp)
            Action("Começar uma campanha") { create = true }
        } else {
            ChapterMarks(active)
            Text("${active.campaign.weeks} semanas · ${active.campaign.weeklyGoal} dias por capítulo · ${journeyDate(active.campaign.startDay)} a ${journeyDate(active.campaign.endDay)}",
                color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
            active.chapters.forEach { chapter ->
                val current = today in chapter.firstDay..chapter.lastDay
                Row(Modifier.fillMaxWidth().animateContentSize().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(if (chapter.complete) TextoPrimario else Superficie)
                        .border(1.dp, if (current) TextoPrimario else Borda, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        if (chapter.complete) Icon(Icons.Outlined.Check, "Capítulo conquistado", tint = Fundo)
                        else Text("${chapter.index + 1}", color = if (current) TextoPrimario else TextoSecundario, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(listOf("Cinzas", "Primeira chama", "A forja", "Ascensão", "O juramento", "A conquista", "Além da névoa", "Legado")[chapter.index], fontWeight = FontWeight.Medium)
                        Text("${journeyDate(chapter.firstDay)} — ${journeyDate(chapter.lastDay)}", color = TextoSecundario, fontSize = 11.sp)
                        if (current) HearthProgress(chapter.progress)
                    }
                    Text(if (chapter.complete) "Conquistado" else if (chapter.closed) "${chapter.days}/${chapter.goal} · encerrado" else "${chapter.days}/${chapter.goal}",
                        color = if (chapter.complete || current) TextoPrimario else TextoSecundario, fontSize = 11.sp)
                }
                HorizontalDivider(color = Borda)
            }
            Text("Cada dia com série válida conta uma vez. Registros anteriores ao início ficam como referência; marcar um exercício sem registrar uma série não avança a campanha.", color = TextoSecundario, fontSize = 11.sp, lineHeight = 17.sp)
            Action("Abrir meu treino") { open(RemnantRoute.PLAN) }
            TextButton(onClick = { finish = true }) { Text("Encerrar campanha", color = TextoSecundario) }
        }
        Section("REM / retrospectiva")
        WeeklyArchivePanel(archive, catalog)
        Section("Seu compromisso")
        Text("A sequência diária continua separada. O descanso previsto faz parte do planejamento, sem criar séries ou XP.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 19.sp)
        TextButton(onClick = { open(RemnantRoute.ROUTINES) }) { Text("Revisar minha agenda") }
        if (journeys.any { it.finished || it.campaign.archived }) {
            Section("Memória das jornadas")
            journeys.filter { it.finished || it.campaign.archived }.forEach { past ->
                Panel {
                    Eyebrow(if (past.conquered) "LEGADO CONQUISTADO" else if (past.campaign.archived) "CAMPANHA ENCERRADA" else "CAMPANHA FINALIZADA")
                    Text(past.campaign.name, fontWeight = FontWeight.SemiBold)
                    Text("${past.victories}/${past.campaign.weeks} capítulos · ${past.improvements.size} exercícios melhorados", color = TextoSecundario, fontSize = 12.sp)
                    ChapterMarks(past)
                }
            }
        }
    }
    if (create) CampaignSetup(settings.diasPorSemana, { create = false }) { name, weeks, goal ->
        vm.iniciarJornada(name, weeks, goal); create = false
    }
    if (finish && active != null) AlertDialog(onDismissRequest = { finish = false }, title = { Text("Encerrar esta campanha?") },
        text = { Text("Os treinos e capítulos já conquistados ficam preservados. Uma nova campanha começa com outra referência.") },
        confirmButton = { TextButton(onClick = { vm.arquivarJornada(active.campaign); finish = false }) { Text("Encerrar") } },
        dismissButton = { TextButton(onClick = { finish = false }) { Text("Continuar jornada") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CampaignSetup(defaultGoal: Int, close: () -> Unit, save: (String, Int, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Caminho da Forja") }
    var weeks by rememberSaveable { mutableIntStateOf(6) }
    var goal by rememberSaveable { mutableIntStateOf(defaultGoal.coerceIn(1, 7)) }
    ModalBottomSheet(onDismissRequest = close, containerColor = Fundo) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Eyebrow("UM COMPROMISSO SEU")
            Text("Forje sua jornada.", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome da campanha") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Duração", color = TextoSecundario, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(4, 6, 8).forEach { n -> Choice("$n semanas", n == weeks) { weeks = n } } }
            Text("Dias de treino por semana", color = TextoSecundario, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..7).forEach { n -> Choice("$n", n == goal) { goal = n } } }
            Text("A meta fica definida para esta campanha. Os exercícios continuam sendo os do seu planejamento, que você pode editar.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
            Action("Iniciar jornada", name.isNotBlank()) { save(name, weeks, goal) }
        }
    }
}

@Composable internal fun ProgressionReference(vm: MainViewModel, ex: Exercicio, use: (TrainingTarget) -> Unit) {
    val history by vm.historicoValido.collectAsStateWithLifecycle()
    val entries by vm.evolutionEntries.collectAsStateWithLifecycle()
    val session by vm.sessao.collectAsStateWithLifecycle()
    val target = remember(history, entries, session, ex.id) { vm.alvoDo(ex) }
    var equipment by rememberSaveable(ex.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("REM / PRÓXIMO PASSO")
            Spacer(Modifier.weight(1f))
            if (ex.modo == ModoMedida.CARGA) TextButton(onClick = { equipment = true }) { Text("Equipamento", fontSize = 11.sp) }
        }
        if (target.previous.isNotEmpty()) {
            Text("Anterior · " + target.previous.joinToString(" / ") { row ->
                if (row.pesoKg != null) "${Format.peso(row.pesoKg)} × ${row.reps}" else if (row.reps != null) "${row.reps} reps" else "${row.segundos}s"
            }, fontSize = 12.sp, color = TextoSecundario)
            Text(listOfNotNull(target.kg?.let { "${Format.peso(it)} kg" }, target.reps?.let { "$it reps" }, target.seconds?.let { "${it}s" }).joinToString(" × "),
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { use(target) }) { Text("Usar como referência") }
        }
        Text(target.reason, fontSize = 12.sp, color = TextoSecundario, lineHeight = 19.sp)
        HorizontalDivider(color = Borda)
    }
    if (equipment) EquipmentSheet(vm.equipamentoDo(ex.id), { equipment = false }) { setup -> vm.salvarEquipamento(ex.id, setup); equipment = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EquipmentSheet(current: EquipmentSetup, close: () -> Unit, save: (EquipmentSetup) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current.label) }
    var step by rememberSaveable { mutableStateOf(current.step.takeIf { it > 0 }?.let { Format.peso(it) } ?: "") }
    var max by rememberSaveable { mutableStateOf(current.maximum.takeIf { it > 0 }?.let { Format.peso(it) } ?: "") }
    val setup = EquipmentSetup(name.trim(), step.replace(',', '.').toFloatOrNull() ?: 0f, max.replace(',', '.').toFloatOrNull() ?: 0f)
    val valid = runCatching { setup.validate() }.isSuccess && (step.isBlank() || step.replace(',', '.').toFloatOrNull() != null) && (max.isBlank() || max.replace(',', '.').toFloatOrNull() != null)
    ModalBottomSheet(onDismissRequest = close, containerColor = Fundo) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Seu equipamento", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(name, { name = it.take(60) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Academia / máquina · opcional") })
            NumberField("Incremento disponível / kg", step) { step = it }
            NumberField("Carga máxima disponível / kg · opcional", max) { max = it }
            Text("Use a carga como você a registra: total da barra ou por halter. Uma máquina diferente começa outra referência; seus PRs antigos permanecem no histórico.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
            Action("Salvar equipamento", valid) { save(setup) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ExerciseReplacement(vm: MainViewModel, current: Exercicio, close: () -> Unit) {
    val alternatives = vm.alternativasPara(current)
    ModalBottomSheet(onDismissRequest = close, containerColor = Fundo) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Máquina ocupada?", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
            item { Text("Alternativas do mesmo grupo muscular. Revise a execução e ajuste a carga para o novo exercício.", color = TextoSecundario, fontSize = 13.sp) }
            if (alternatives.isEmpty()) item { Text("Sem alternativa disponível no catálogo para este treino.", color = TextoSecundario) }
            alternatives.forEach { alternative -> item(key = alternative.id) { ExerciseRow(alternative) { vm.substituirExercicio(alternative); close() } } }
        }
    }
}

private fun journeyDate(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM"))
