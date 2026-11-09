package com.liferpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.data.CircleWeekState
import com.liferpg.data.sync.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object CircleWeekTokens {
    val inset=20.dp; val gap=16.dp; val small=8.dp; val tiny=4.dp
    val touch=48.dp; val round=20.dp; val ring=76.dp; val stroke=4.dp; val position=38.dp
    val day=28.dp; val icon=20.dp; val caption=12.sp; val body=14.sp; val title=24.sp; val metric=32.sp
    const val settle=260
}
private val circleDate=DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CircleWeekSheet(state: CircleWeekState, name: String, uid: String,
    friends: Set<String>, dismiss: () -> Unit, refresh: () -> Unit,
    select: (LocalDate?) -> Unit, inspect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Fundo,contentColor=TextoPrimario,
        shape=RoundedCornerShape(topStart=CircleWeekTokens.round,topEnd=CircleWeekTokens.round),
        dragHandle={ BottomSheetDefaults.DragHandle(color=Borda) }) {
        CircleWeekContent(state,name,uid,friends,dismiss,refresh,select,inspect,Modifier.fillMaxHeight(.94f))
    }
}

@Composable internal fun CircleWeekContent(state: CircleWeekState, name: String, uid: String,
    friends: Set<String>, dismiss: () -> Unit, refresh: () -> Unit,
    select: (LocalDate?) -> Unit, inspect: (String) -> Unit, modifier: Modifier=Modifier) {
    val week=state.content
    Column(modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=CircleWeekTokens.inset),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("PLACAR DO CÍRCULO")
                Text(name,fontSize=CircleWeekTokens.title,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=refresh,enabled=!state.loading) { Icon(Icons.Outlined.Refresh,"Atualizar placar") }
            IconButton(onClick=dismiss) { Icon(Icons.Outlined.Close,"Fechar placar") }
        }
        CircleWeekNavigator(week,state.requested,state.loading,select)
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=TextoPrimario)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(CircleWeekTokens.inset),
            verticalArrangement=Arrangement.spacedBy(CircleWeekTokens.gap)) {
            state.error?.let { error -> item(key="error") {
                Column(verticalArrangement=Arrangement.spacedBy(CircleWeekTokens.small)) {
                    Text(error,fontSize=CircleWeekTokens.body,color=TextoSecundario)
                    if(week!=null) Text("Exibindo a última consulta deste período.",fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                    TextButton(onClick=refresh,enabled=!state.loading) { Text("Tentar novamente") }
                }
            } }
            if(week!=null) {
                week.members.firstOrNull { it.uid==uid }?.let { mine -> item(key="mine") { CircleMyWeek(week,mine) } }
                item(key="group") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("O círculo em movimento",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=CircleWeekTokens.body)
                        Text("${week.goalsMet}/${week.members.size} metas",fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                    }
                }
                items(week.board,key={ it.uid }) { member ->
                    CircleWeekMemberRow(week,member,uid,member.uid in friends) { inspect(member.uid) }
                }
                item(key="rules") {
                    Text("Dias de segunda a domingo · ${week.timezone.substringAfterLast('/').replace('_',' ')}. " +
                        "Só treinos compartilhados contam. Um dia vale uma vez; a classificação usa o percentual da meta, limitado a 100%. Empates dividem a posição.",
                        fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                    Spacer(Modifier.height(CircleWeekTokens.small))
                    Text("Histórico dos últimos 12 períodos, desde a criação do círculo. Retiradas e saídas atualizam os resultados; este arquivo não congela vencedores.",
                        fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                }
            } else if(state.loading) item(key="loading") {
                Text("Consultando os treinos compartilhados…",fontSize=CircleWeekTokens.body,color=TextoSecundario)
            }
        }
    }
}

@Composable private fun CircleWeekNavigator(week: CircleWeek?, requested: LocalDate?, loading: Boolean, select:(LocalDate?)->Unit) {
    val start=week?.start?:requested
    Column(Modifier.fillMaxWidth().padding(horizontal=CircleWeekTokens.inset)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={ week?.let { select(it.start.minusWeeks(1)) } },
                enabled=!loading && week!=null && week.start>week.first) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,"Semana anterior")
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(start?.let { "${it.format(circleDate)} — ${it.plusDays(6).format(circleDate)}" }?:"Esta semana",
                    fontSize=CircleWeekTokens.body,fontWeight=FontWeight.SemiBold)
                Text(if(week?.isCurrent==true) "EM ANDAMENTO" else if(start!=null) "HISTÓRICO SEMANAL" else "PRESENÇA COMPARTILHADA",
                    fontSize=CircleWeekTokens.caption,color=TextoSecundario)
            }
            IconButton(onClick={ week?.let { select(if(it.start.plusWeeks(1)==it.current) null else it.start.plusWeeks(1)) } },
                enabled=!loading && week!=null && week.start<week.current) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,"Semana seguinte")
            }
        }
        if(requested!=null) TextButton(onClick={ select(null) },enabled=!loading,modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar à semana atual") }
    }
}

@Composable private fun CircleMyWeek(week: CircleWeek, mine: CircleWeekMember) {
    val progress by animateFloatAsState(mine.progress,tween(CircleWeekTokens.settle),label="Compromisso da semana")
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(CircleWeekTokens.gap)) {
        Box(Modifier.size(CircleWeekTokens.ring).semantics { contentDescription="${(mine.progress*100).toInt()} por cento da sua meta semanal" },contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width=CircleWeekTokens.stroke.toPx()
                drawCircle(Borda,style=Stroke(width),radius=(size.minDimension-width)/2)
                drawArc(TextoPrimario,-90f,360f*progress,false,topLeft=androidx.compose.ui.geometry.Offset(width/2,width/2),
                    size=androidx.compose.ui.geometry.Size(size.width-width,size.height-width),style=Stroke(width,cap=StrokeCap.Round))
            }
            Text("${(mine.progress*100).toInt()}%",fontSize=CircleWeekTokens.title,fontWeight=FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(CircleWeekTokens.tiny)) {
            Text("SEU COMPROMISSO",fontSize=CircleWeekTokens.caption,color=TextoSecundario)
            Text("${mine.days}/${mine.goal} dias",fontSize=CircleWeekTokens.metric,fontWeight=FontWeight.SemiBold)
            Text(when {
                mine.remaining==0 -> "Meta alcançada. Presença que inspira."
                !week.isCurrent -> "${mine.remaining} dia${if(mine.remaining==1) "" else "s"} abaixo da meta neste período."
                else -> "Falta${if(mine.remaining==1) "" else "m"} ${mine.remaining} dia${if(mine.remaining==1) "" else "s"} para sua meta."
            },fontSize=CircleWeekTokens.body,color=TextoSecundario)
        }
    }
    Spacer(Modifier.height(CircleWeekTokens.gap))
    CircleWeekCalendar(week,mine)
    if(week.members.all { it.days==0 }) {
        Spacer(Modifier.height(CircleWeekTokens.small))
        Text(if(week.isCurrent) "O placar começa com o primeiro treino compartilhado." else "Nenhum treino compartilhado neste período.",
            fontSize=CircleWeekTokens.body,color=TextoSecundario)
    }
}

@Composable internal fun CircleWeekCalendar(week:CircleWeek,member:CircleWeekMember) {
    val labels=listOf("S","T","Q","Q","S","S","D")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(CircleWeekTokens.tiny)) {
        (0..6).forEach { offset ->
            val day=week.start.plusDays(offset.toLong()); val active=day in member.dates
            Column(Modifier.weight(1f).semantics(mergeDescendants=true) {
                contentDescription="${member.nickname}, ${day.format(circleDate)}: ${if(active) "treino compartilhado" else if(day>week.today) "dia futuro" else "sem treino compartilhado"}"
            },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(CircleWeekTokens.tiny)) {
                Text(labels[offset],fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                Box(Modifier.size(CircleWeekTokens.day).clip(CircleShape).background(if(active) TextoPrimario else SuperficieAlta),contentAlignment=Alignment.Center) {
                    if(active) Icon(Icons.Outlined.Check,null,Modifier.size(CircleWeekTokens.icon),tint=Fundo)
                    else Text("${day.dayOfMonth}",fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                }
            }
        }
    }
}

@Composable private fun CircleWeekMemberRow(week:CircleWeek,member:CircleWeekMember,uid:String,isFriend:Boolean,inspect:()->Unit) {
    var expanded by rememberSaveable(week.circleId,week.start.toString(),member.uid) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(CircleWeekTokens.settle))) {
        Row(Modifier.fillMaxWidth().heightIn(min=CircleWeekTokens.touch).clickable { expanded=!expanded }
            .semantics { stateDescription=if(expanded) "Detalhes abertos" else "Detalhes fechados" },
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(CircleWeekTokens.small)) {
            Text("${week.position(member)}",Modifier.width(CircleWeekTokens.position),fontSize=CircleWeekTokens.title,fontWeight=FontWeight.SemiBold)
            Column(Modifier.weight(1f)) {
                Text(member.nickname+if(member.uid==uid) " · você" else "",fontSize=CircleWeekTokens.body,fontWeight=FontWeight.SemiBold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${member.days}/${member.goal} dias · ${(member.progress*100).toInt()}%",fontSize=CircleWeekTokens.caption,color=TextoSecundario)
                Spacer(Modifier.height(CircleWeekTokens.tiny)); Meter(member.progress)
            }
            Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,Modifier.size(CircleWeekTokens.icon),tint=TextoSecundario)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top=CircleWeekTokens.small,bottom=CircleWeekTokens.gap),verticalArrangement=Arrangement.spacedBy(CircleWeekTokens.small)) {
                if(member.uid!=uid) CircleWeekCalendar(week,member)
                Text("${member.workouts} treino${if(member.workouts==1) "" else "s"} · ${member.sets} séries · ${member.prs} PRs · ${member.seconds/60} min",
                    fontSize=CircleWeekTokens.body,color=TextoSecundario)
                if(isFriend && member.uid!=uid) TextButton(onClick=inspect) { Text("Ver perfil de ${member.nickname}",maxLines=2,overflow=TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(CircleWeekTokens.small)); HorizontalDivider(color=Borda)
    }
}
