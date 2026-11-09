package com.liferpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.SerieRegistro
import com.liferpg.data.TreinoSessao
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object HistoryTokens {
    val inset=22.dp; val gap=16.dp; val small=8.dp; val compact=4.dp; val touch=48.dp
    val corner=16.dp; val icon=20.dp; val badge=40.dp; val stroke=1.dp
    val caption=12.sp; val body=14.sp; val heading=20.sp; val metric=30.sp
    const val settle=240
}

@Composable internal fun WorkoutHistory(vm: MainViewModel, back: ()->Unit, open: (String)->Unit,
    exerciseId: String?=null, sessionId: Long?=null) {
    val archive by vm.arquivoTreinos.collectAsStateWithLifecycle()
    val day by vm.hoje.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val session by vm.sessao.collectAsStateWithLifecycle()
    WorkoutHistoryContent(archive,day,back,open,{ s -> vm.repetirSessao(s) { open(RemnantRoute.SESSION) } },
        exerciseId?.let { id -> catalog.firstOrNull { it.id==id }?.nome ?: id }.orEmpty(),sessionId,
        session?.takeUnless { it.concluida }?.nome)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun WorkoutHistoryContent(archive: WorkoutArchive?, today: Long, back: ()->Unit,
    open: (String)->Unit, repeat: (TreinoSessao)->Unit, initialQuery: String="", initialSession: Long?=null,
    activeSession: String?=null) {
    var tab by rememberSaveable { mutableIntStateOf(if(initialQuery.isNotBlank()) 1 else 0) }
    var period by rememberSaveable { mutableStateOf(ArchivePeriod.ALL) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selectedId by rememberSaveable { mutableStateOf(initialSession) }
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val list=rememberLazyListState()
    val focus=LocalFocusManager.current
    LaunchedEffect(tab,period,query) { list.scrollToItem(0) }
    val sessions=remember(archive,today,period,query) { archive?.let { WorkoutArchiveEngine.sessions(it,today,period,query) }.orEmpty() }
    val days=remember(archive,today,period,query) { archive?.let { WorkoutArchiveEngine.days(it,today,period,query) }.orEmpty() }
    Box(Modifier.fillMaxSize().background(Fundo)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),state=list,
            contentPadding=PaddingValues(start=HistoryTokens.inset,end=HistoryTokens.inset,top=HistoryTokens.gap,bottom=HistoryTokens.inset*2),
            verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) {
            item(key="header") { SystemHeader("Histórico", "Treinos que ficam.", back,showSeal=false) }
            item(key="search") {
                OutlinedTextField(query,{ query=it },Modifier.fillMaxWidth().semantics { contentDescription="Buscar treino, exercício ou músculo" },singleLine=true,
                    placeholder={Text("Treino, exercício ou músculo",fontSize=HistoryTokens.caption)},
                    leadingIcon={Icon(Icons.Outlined.Search,null)},
                    trailingIcon=if(query.isNotEmpty()) { { IconButton(onClick={query=""}) { Icon(Icons.Outlined.Close,"Limpar busca") } } } else null,
                    shape=RoundedCornerShape(HistoryTokens.corner))
            }
            stickyHeader(key="filters") {
                Column(Modifier.fillMaxWidth().background(Fundo).padding(vertical=HistoryTokens.compact),verticalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
                    HistoryTabs(listOf("Sessões","Séries"),tab) { tab=it }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
                        ArchivePeriod.entries.forEach { p ->
                            Box(Modifier.weight(1f).heightIn(min=HistoryTokens.touch).clip(RoundedCornerShape(HistoryTokens.corner))
                                .border(HistoryTokens.stroke,if(p==period) TextoPrimario else Borda,RoundedCornerShape(HistoryTokens.corner))
                                .selectable(p==period,role=Role.RadioButton,onClick={period=p}),contentAlignment=Alignment.Center) {
                                Text(p.label,color=if(p==period) TextoPrimario else TextoSecundario,fontSize=HistoryTokens.caption)
                            }
                        }
                    }
                }
            }
            if(archive==null) item(key="loading") {
                Column(verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap),modifier=Modifier.semantics { contentDescription="Carregando histórico" }) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(),color=TextoPrimario,trackColor=Borda)
                    Text("Organizando seus registros…",fontSize=HistoryTokens.body,color=TextoSecundario)
                }
            } else {
                if((tab==0 && sessions.isNotEmpty()) || (tab==1 && days.isNotEmpty())) item(key="summary") {
                    val count=if(tab==0) sessions.size else days.size
                    HistorySummary(count,if(tab==0) { if(count==1) "sessão" else "sessões" } else { if(count==1) "dia registrado" else "dias registrados" },
                        if(tab==0) sessions.sumOf { it.session.series } else days.sumOf { it.sets },
                        if(tab==0) "Sessões finalizadas. Toque para rever ou repetir." else "Todas as séries, inclusive fora de uma sessão.")
                }
                if((tab==0 && sessions.isEmpty()) || (tab==1 && days.isEmpty())) item(key="empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical=HistoryTokens.gap),verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) {
                        Icon(Icons.Outlined.History,null,Modifier.size(HistoryTokens.badge),tint=TextoSecundario)
                        Text(if(query.isNotBlank() || period!=ArchivePeriod.ALL) "Nada neste filtro." else if(tab==0) "Seu próximo treino começa este arquivo." else "Sua primeira série fica guardada aqui.",fontSize=HistoryTokens.heading,fontWeight=FontWeight.Medium)
                        Text(if(query.isNotBlank() || period!=ArchivePeriod.ALL) "Tente outro nome ou amplie o período." else if(tab==0) "Finalize uma sessão para ver duração, exercícios e suas marcas." else "Os registros do Arsenal e da câmera também aparecem aqui.",fontSize=HistoryTokens.body,color=TextoSecundario)
                        if(query.isNotBlank() || period!=ArchivePeriod.ALL) TextButton(onClick={query="";period=ArchivePeriod.ALL}) { Text("Limpar filtros") }
                        else SystemAction(if(tab==0) "Abrir meu treino" else "Abrir Arsenal") { open(if(tab==0) RemnantRoute.PLAN else RemnantRoute.tab(2)) }
                    }
                }
                if(tab==0) sessions.groupBy { monthKey(it.session.epochDay) }.forEach { (month,rows) ->
                    item(key="month:$month") { HistoryMonth(rows.first().session.epochDay) }
                    items(rows,key={"session:${it.session.id}"}) { row -> HistorySessionRow(row.session) { focus.clearFocus();selectedId=row.session.id } }
                } else days.groupBy { monthKey(it.day) }.forEach { (month,rows) ->
                    item(key="month:$month") { HistoryMonth(rows.first().day) }
                    items(rows,key={"day:${it.day}"}) { day ->
                        HistoryEntry(date=historyDate(day.day,today),title=day.exercises.joinToString(" · ") { it.name },
                            detail="${historyCount(day.sets,"série")} · ${historyCount(day.exercises.size,"exercício")}",tag=null) { focus.clearFocus();selectedDay=day.day }
                    }
                }
            }
        }
        archive?.sessions?.firstOrNull { it.session.id==selectedId }?.let { selected ->
            HistorySessionSheet(selected,activeSession,{selectedId=null},open) { repeat(selected.session) }
        }
        days.firstOrNull { it.day==selectedDay }?.let { selected ->
            HistoryDaySheet(selected,today,{selectedDay=null},open)
        }
    }
}

@Composable private fun HistoryTabs(labels: List<String>, selected: Int, choose: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(HistoryTokens.corner)).background(Superficie).padding(HistoryTokens.compact),horizontalArrangement=Arrangement.spacedBy(HistoryTokens.compact)) {
        labels.forEachIndexed { i,label ->
            Box(Modifier.weight(1f).heightIn(min=HistoryTokens.touch).clip(RoundedCornerShape(HistoryTokens.corner)).background(if(i==selected) TextoPrimario else Superficie)
                .selectable(i==selected,role=Role.Tab,onClick={choose(i)}),contentAlignment=Alignment.Center) {
                Text(label,fontSize=HistoryTokens.body,fontWeight=FontWeight.Medium,color=if(i==selected) Fundo else TextoSecundario)
            }
        }
    }
}

@Composable private fun HistorySummary(count: Int, label: String, sets: Int, help: String) {
    Column(verticalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) {
            Column(Modifier.weight(1f)) { Text("$count",fontSize=HistoryTokens.metric,fontWeight=FontWeight.SemiBold); Text(label,color=TextoSecundario,fontSize=HistoryTokens.caption) }
            Column(Modifier.weight(1f)) { Text("$sets",fontSize=HistoryTokens.metric,fontWeight=FontWeight.SemiBold); Text(if(sets==1) "série registrada" else "séries registradas",color=TextoSecundario,fontSize=HistoryTokens.caption) }
        }
        Text(help,color=TextoSecundario,fontSize=HistoryTokens.caption)
    }
}

@Composable internal fun HistorySessionRow(session: TreinoSessao, click: ()->Unit) {
    HistoryEntry(historyDate(session.epochDay),session.nome,
        "${historyCount(session.series,"série")} · ${historyCount(session.exercicios,"exercício")} · ${Format.tempo(session.duracaoSeg)}",
        session.recordes.takeIf { it>0 }?.let { "$it PR${if(it>1) "s" else ""}" },click)
}

@Composable private fun HistoryEntry(date: String, title: String, detail: String, tag: String?, click: ()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HistoryTokens.corner)).clickable(role=Role.Button,onClick=click)
        .padding(vertical=HistoryTokens.gap),verticalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(date,color=TextoSecundario,fontSize=HistoryTokens.caption,modifier=Modifier.weight(1f))
            if(tag!=null) Text(tag,color=TextoPrimario,fontSize=HistoryTokens.caption,modifier=Modifier.padding(end=HistoryTokens.small))
            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(HistoryTokens.icon),tint=TextoSecundario)
        }
        Text(title,fontSize=HistoryTokens.heading,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(detail,color=TextoSecundario,fontSize=HistoryTokens.caption)
        HorizontalDivider(color=Borda)
    }
}

@Composable private fun HistoryMonth(day: Long) { Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("MMMM yyyy",historyLocale)).replaceFirstChar { it.titlecase(historyLocale) },fontSize=HistoryTokens.body,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=HistoryTokens.small)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HistorySessionSheet(workout: ArchivedWorkout, active: String?, close: ()->Unit,
    open: (String)->Unit, repeat: ()->Unit) {
    val session=workout.session
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Fundo) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=HistoryTokens.inset).padding(bottom=HistoryTokens.gap)) {
            LazyColumn(Modifier.weight(1f,fill=false),verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap),contentPadding=PaddingValues(bottom=HistoryTokens.gap)) {
                item {
                    Text(historyDate(session.epochDay),color=TextoSecundario,fontSize=HistoryTokens.caption)
                    Text(session.nome,fontSize=HistoryTokens.heading,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=HistoryTokens.small))
                }
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) {
                    HistoryMetric(Format.tempo(session.duracaoSeg),"duração",Modifier.weight(1f))
                    HistoryMetric("+${session.xpGanho}","XP recebido",Modifier.weight(1f))
                    HistoryMetric("${session.recordes}","PRs",Modifier.weight(1f))
                } }
                item { Text("${historyCount(session.series,"série")} registrada${if(session.series==1) "" else "s"} · ${historyCount(session.exercicios,"exercício")} concluíd${if(session.exercicios==1) "o" else "os"}",color=TextoSecundario,fontSize=HistoryTokens.caption); HorizontalDivider(Modifier.padding(top=HistoryTokens.gap),color=Borda) }
                if(!workout.exact) item {
                    Text("Este treino antigo tem apenas o resumo. As séries continuam no registro do dia; não foram associadas a esta sessão por suposição.",fontSize=HistoryTokens.body,color=TextoSecundario)
                }
                items(workout.exercises,key={it.id}) { exercise -> HistoryExercise(exercise,open) }
                if(active!=null) item { Text("Você tem uma sessão em andamento: $active. Finalize ou retome antes de repetir outra.",fontSize=HistoryTokens.caption,color=TextoSecundario) }
            }
            if(active==null) SystemAction("Repetir treino",action=repeat)
            else SystemAction("Retomar sessão atual") { close();open(RemnantRoute.SESSION) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryDaySheet(day: ArchiveDay, today: Long, close: ()->Unit, open: (String)->Unit) {
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Fundo) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(),contentPadding=PaddingValues(HistoryTokens.inset),verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) {
            item { Text(historyDate(day.day,today),fontSize=HistoryTokens.heading,fontWeight=FontWeight.SemiBold)
                Text("${historyCount(day.sets,"série")} · ${historyCount(day.exercises.size,"exercício")}",fontSize=HistoryTokens.caption,color=TextoSecundario) }
            items(day.exercises,key={it.id}) { HistoryExercise(it,open) }
            item { Text("Registros fora do ranking ficam identificados. Abrir este histórico não altera marcas ou XP.",fontSize=HistoryTokens.caption,color=TextoSecundario) }
        }
    }
}

@Composable private fun HistoryMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(HistoryTokens.compact)) {
        Text(value,fontSize=HistoryTokens.heading,fontWeight=FontWeight.SemiBold)
        Text(label,fontSize=HistoryTokens.caption,color=TextoSecundario)
    }
}

@Composable internal fun HistoryExercise(exercise: ArchivedExercise, open: (String)->Unit) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
        Row(Modifier.fillMaxWidth().heightIn(min=HistoryTokens.touch).clip(RoundedCornerShape(HistoryTokens.corner))
            .clickable(role=Role.Button,onClick={expanded=!expanded}).semantics { stateDescription=if(expanded) "Séries expandidas" else "Séries recolhidas" },
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name,fontSize=HistoryTokens.body,fontWeight=FontWeight.Medium)
                Text("${historyCount(exercise.sets.size,"série")} · ${exercise.muscle}",color=TextoSecundario,fontSize=HistoryTokens.caption)
            }
            Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,tint=TextoSecundario)
        }
        if(!expanded) exercise.sets.lastOrNull()?.let {
            Text("Última · ${historySetLabel(it)}${if(!it.valid) " · fora do ranking" else ""}",fontSize=HistoryTokens.caption,color=TextoSecundario)
        }
        AnimatedVisibility(expanded,enter=fadeIn(tween(HistoryTokens.settle))+expandVertically(tween(HistoryTokens.settle)),
            exit=fadeOut(tween(HistoryTokens.settle))+shrinkVertically(tween(HistoryTokens.settle))) {
            Column(verticalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
                exercise.sets.forEachIndexed { index,set ->
                    Row(Modifier.fillMaxWidth().padding(vertical=HistoryTokens.compact),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(HistoryTokens.small)) {
                        Text((index+1).toString().padStart(2,'0'),fontSize=HistoryTokens.caption,color=TextoSecundario)
                        Column(Modifier.weight(1f)) {
                            Text(historySetLabel(set),fontSize=HistoryTokens.body)
                            if(set.equipment.isNotBlank()) Text(set.equipment,fontSize=HistoryTokens.caption,color=TextoSecundario)
                            if(!set.valid) Text("Fora do ranking",fontSize=HistoryTokens.caption,color=TextoSecundario)
                        }
                        if(set.pr) Text("PR",fontSize=HistoryTokens.caption,fontWeight=FontWeight.SemiBold)
                    }
                }
                TextButton(onClick={open(RemnantRoute.exercise(exercise.id))},modifier=Modifier.heightIn(min=HistoryTokens.touch)) { Text("Ver exercício") }
            }
        }
        HorizontalDivider(color=Borda)
    }
}

/** Shared compact day log: one row per exercise, rather than repeating its image for every set. */
@Composable internal fun TodayTrainingLog(rows: List<SerieRegistro>, catalog: List<Exercicio>, open: (String)->Unit) {
    val exercises=remember(rows,catalog) { WorkoutArchiveEngine.days(rows,catalog).flatMap { it.exercises } }
    Column(verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) { exercises.forEach { HistoryExercise(it,open) } }
}

@Composable internal fun ExerciseHistoryLog(rows: List<SerieRegistro>, ex: Exercicio, open: (String)->Unit) {
    val days=remember(rows,ex.id) { WorkoutArchiveEngine.days(rows,listOf(ex)) }
    Column(verticalArrangement=Arrangement.spacedBy(HistoryTokens.gap)) {
        if(days.isEmpty()) Text("Nenhum registro ainda. Sua primeira marca aparecerá aqui.",color=TextoSecundario,fontSize=HistoryTokens.body)
        days.take(3).forEach { day ->
            Text(historyDate(day.day),color=TextoSecundario,fontSize=HistoryTokens.caption)
            HistoryExercise(day.exercises.first(),open)
        }
        if(days.isNotEmpty()) TextButton(onClick={open(RemnantRoute.exerciseHistory(ex.id))}) { Text("Histórico completo") }
    }
}

internal fun historySetLabel(set: ArchivedSet): String = listOfNotNull(
    set.kg?.let { "${Format.peso(it)} kg" },set.reps?.let { "$it reps" },set.seconds?.let { Format.tempo(it) }).joinToString(" × ")
private val historyLocale=Locale.forLanguageTag("pt-BR")
private fun historyDate(day: Long, today: Long?=null): String = when(day) {
    today -> "Hoje"
    today?.minus(1) -> "Ontem"
    else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd MMM yyyy",historyLocale))
}
private fun monthKey(day: Long) = LocalDate.ofEpochDay(day).withDayOfMonth(1).toEpochDay()
private fun historyCount(count: Int, singular: String) = "$count $singular${if(count==1) "" else "s"}"
