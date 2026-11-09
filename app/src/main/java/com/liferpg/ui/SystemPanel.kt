package com.liferpg.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.CameraBossProgress
import com.liferpg.data.MissaoDiaria
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

/** Native tokens: match the app's monochrome palette and default sans typography. */
internal object SystemTokens {
    val bg=Fundo; val surface=Superficie; val raised=SuperficieAlta; val ink=TextoPrimario
    val muted=TextoSecundario; val border=Borda
    val inset=22.dp; val gap=16.dp; val small=8.dp; val compact=4.dp; val touch=48.dp
    val corner=24.dp; val rowCorner=16.dp; val stroke=1.dp; val heroGlyph=108.dp; val bossHeight=252.dp
    val caption=12.sp; val body=14.sp; val heading=24.sp; val title=34.sp; val metric=40.sp
    const val settle=380; const val reveal=220
}

internal data class SystemBoard(
    val day: Long,
    val daily: List<MissaoDiaria>,
    val weekly: List<MissaoDiaria> = emptyList(),
    val history: List<MissaoDiaria> = emptyList(),
    val canAdapt: Boolean = false,
    val activeSession: String? = null,
    val planName: String = "Seu treino",
    val trainedDays: Set<Long> = emptySet(),
    val bosses: Map<CameraBoss,CameraBossProgress> = emptyMap(),
)

@Composable internal fun SystemPanel(vm: MainViewModel, open: (String)->Unit, back: ()->Unit) {
    val day by vm.hoje.collectAsStateWithLifecycle()
    val daily by vm.missoesHoje.collectAsStateWithLifecycle()
    val weekly by vm.missoesSemana.collectAsStateWithLifecycle()
    val history by vm.historicoMissoes.collectAsStateWithLifecycle()
    val adapt by vm.podeAdaptarMissoes.collectAsStateWithLifecycle()
    val plan by vm.treinoHoje.collectAsStateWithLifecycle()
    val session by vm.sessao.collectAsStateWithLifecycle()
    val days by vm.diasTreinadosRecentes.collectAsStateWithLifecycle()
    val bosses by vm.cameraBosses.collectAsStateWithLifecycle()
    SystemContent(SystemBoard(day,daily,weekly,history,adapt,session?.takeUnless { it.concluida }?.nome,
        plan.titulo.replace("💪", "").trim(),days,bosses),open,vm::adaptarMissoes,back)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable internal fun SystemContent(board: SystemBoard, open: (String)->Unit, adapt: (SystemMode)->Unit, back: ()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var showModes by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected=(board.daily+board.weekly+board.history).firstOrNull { it.id==selectedId }
    val mode=MissaoEngine.mode(board.daily)
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }
    Box(Modifier.fillMaxSize().background(SystemTokens.bg)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),state=listState,
            contentPadding=PaddingValues(start=SystemTokens.inset,end=SystemTokens.inset,top=SystemTokens.gap,bottom=SystemTokens.inset*2),
            verticalArrangement=Arrangement.spacedBy(SystemTokens.inset)) {
            item(key="header") { SystemHeader("SYSTEM", "Missões e desafios", back) }
            stickyHeader(key="tabs") {
                Box(Modifier.fillMaxWidth().background(SystemTokens.bg).padding(vertical=SystemTokens.compact)) {
                    SystemTabs(listOf("Ordens","Provações","Registro"),page) { page=it }
                }
            }
            when(page) {
                0 -> {
                    item(key="hero") { OrderHero(board.daily) }
                    if(board.daily.isNotEmpty()) item(key="mode") {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                SystemLabel("ORDENS DO DIA")
                                Text(mode.titulo,color=SystemTokens.ink,fontSize=SystemTokens.body,fontWeight=FontWeight.SemiBold)
                            }
                            TextButton(onClick={showModes=true}) { Text("Adaptar ordens",color=SystemTokens.ink,fontSize=SystemTokens.caption) }
                        }
                        if(!board.canAdapt && board.daily.isNotEmpty())
                            Text("Modalidade mantida enquanto as ordens estão em andamento.",color=SystemTokens.muted,fontSize=SystemTokens.caption)
                    }
                    if(board.daily.isEmpty()) item(key="loading") { OrdersLoading() }
                    items(board.daily,key={"daily-${it.id}"}) { mission ->
                        MissionOrder(mission,board.daily.indexOf(mission)+1) { selectedId=mission.id }
                    }
                    item(key="plan") {
                        SystemLink(if(board.activeSession!=null) "Seu treino está em andamento" else "O treino de hoje",
                            board.activeSession ?: board.planName,Icons.Outlined.FitnessCenter) {
                            open(if(board.activeSession!=null) RemnantRoute.SESSION else RemnantRoute.PLAN)
                        }
                    }
                    board.weekly.firstOrNull()?.let { weekly -> item(key="week") {
                        WeeklyContract(weekly,board.day,board.trainedDays) { selectedId=weekly.id }
                    } }
                    item(key="rules") { Text("Ordens diárias renovam à meia-noite. O contrato semanal encerra no domingo. As recompensas recebidas continuam suas.",
                        color=SystemTokens.muted,fontSize=SystemTokens.caption,lineHeight=18.sp) }
                }
                1 -> {
                    item(key="challenge-intro") {
                        Text("Conquistas que\nexigem presença.",color=SystemTokens.ink,fontSize=SystemTokens.title,lineHeight=38.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(SystemTokens.small))
                        Text("Seu corpo enfrenta a provação. Seus aliados ampliam a jornada.",color=SystemTokens.muted,fontSize=SystemTokens.body,lineHeight=21.sp)
                    }
                    item(key="solo-label") { SystemLabel("SOLO / CÂMERA") }
                    items(CameraBoss.entries,key={it.name}) { boss ->
                        SystemBossCard(boss,board.bosses[boss]) { open(RemnantRoute.camera(boss.exerciseId)) }
                    }
                    item(key="trial-rules") {
                        Row(horizontalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
                            Icon(Icons.Outlined.Shield,null,tint=SystemTokens.muted,modifier=Modifier.size(18.dp))
                            Text("Movimentos completos, uma tentativa. 15 segundos sem completar uma repetição encerram a tentativa. A câmera analisa no aparelho e não grava vídeo.",
                                color=SystemTokens.muted,fontSize=SystemTokens.caption,lineHeight=18.sp,modifier=Modifier.weight(1f))
                        }
                    }
                    item(key="coop-label") { SystemLabel("JORNADA COMPARTILHADA") }
                    item(key="coop") {
                        CommunityPath("01","Pactos","Um chefe em grupo. Cada aliado contribui com a própria meta.",Icons.Outlined.Groups) { open(RemnantRoute.PACTS) }
                        CommunityPath("02","Duelos","Dispute a evolução conquistada depois do aceite.",Icons.Outlined.Bolt) { open(RemnantRoute.DUELS) }
                        CommunityPath("03","Sua jornada","Capítulos e metas de constância ao longo das semanas.",Icons.Outlined.Explore) { open(RemnantRoute.JOURNEY) }
                    }
                }
                else -> {
                    item(key="archive-title") {
                        Text("O que você\nconquistou fica.",color=SystemTokens.ink,fontSize=SystemTokens.title,lineHeight=38.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(SystemTokens.small))
                        val earned=board.history.filter { it.concluida }.sumOf { it.xpRecompensa }
                        Text("Últimos 90 dias · $earned XP recebidos em ordens",color=SystemTokens.muted,fontSize=SystemTokens.body)
                    }
                    item(key="archive-filters") { SystemTabs(listOf("Todas","Cumpridas","Encerradas"),filter) { filter=it } }
                    val entries=board.history.filter {
                        when(filter) { 1->it.concluida;2->!it.concluida && periodEnded(it,board.day);else->true }
                    }.groupBy { it.epochDay }
                    if(entries.isEmpty()) item(key="archive-empty") {
                        EmptySystem(if(filter==2) "Nenhuma ordem encerrada" else "Sua história começa aqui",
                            if(filter==2) "As ordens em andamento continuam na primeira aba." else "Quando cumprir suas ordens, o registro guarda objetivos e recompensas reais.")
                        SystemAction("Ver ordens de hoje") { page=0 }
                    }
                    entries.forEach { (day,orders) ->
                        item(key="date-$day") { SystemLabel(if(day==board.day) "HOJE" else date(day).uppercase()) }
                        items(orders,key={"archive-${it.id}"}) { m -> ArchiveOrder(m,board.day) { selectedId=m.id } }
                    }
                }
            }
        }
    }
    if(showModes) ModalBottomSheet(onDismissRequest={showModes=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=SystemTokens.surface,contentColor=SystemTokens.ink) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(SystemTokens.inset),verticalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
            Text("Como será seu dia?",fontSize=SystemTokens.heading,fontWeight=FontWeight.Bold)
            Text(if(board.canAdapt) "Escolha antes de começar. Seu contrato semanal continua o mesmo." else "As ordens já começaram. Escolha outra modalidade no próximo dia para manter o progresso de hoje.",color=SystemTokens.muted,fontSize=SystemTokens.body)
            SystemMode.entries.forEach { choice ->
                val icon=when(choice) { SystemMode.GYM->Icons.Outlined.FitnessCenter;SystemMode.HOME->Icons.Outlined.Home;SystemMode.CARDIO->Icons.AutoMirrored.Outlined.DirectionsRun }
                SystemLink(choice.titulo,choice.descricao,icon,enabled=board.canAdapt) {
                    adapt(choice);showModes=false
                }
            }
        }
    }
    selected?.let { mission -> MissionDetail(mission,board,mode,{selectedId=null}) { route -> selectedId=null;open(route) } }
}

@Composable internal fun SystemHeader(title: String, subtitle: String, back: ()->Unit, showSeal: Boolean=true) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
        IconButton(back,Modifier.size(SystemTokens.touch).clip(CircleShape).border(SystemTokens.stroke,SystemTokens.border,CircleShape)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Voltar",tint=SystemTokens.ink)
        }
        Column(Modifier.weight(1f).padding(start=SystemTokens.small)) {
            Text(title,color=SystemTokens.ink,fontSize=SystemTokens.heading,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
            Text(subtitle,color=SystemTokens.muted,fontSize=SystemTokens.caption)
        }
        if(showSeal) Icon(Icons.Outlined.AutoAwesome,null,tint=SystemTokens.ink,modifier=Modifier.size(22.dp))
    }
}

@Composable private fun SystemTabs(labels: List<String>, selected: Int, choose: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(SystemTokens.rowCorner)).background(SystemTokens.surface).padding(SystemTokens.compact),horizontalArrangement=Arrangement.spacedBy(SystemTokens.compact)) {
        labels.forEachIndexed { index,label ->
            val active=index==selected
            Box(Modifier.weight(1f).heightIn(min=SystemTokens.touch).clip(RoundedCornerShape(12.dp)).background(if(active) SystemTokens.ink else SystemTokens.surface)
                .selectable(active,role=Role.Tab,onClick={choose(index)}),contentAlignment=Alignment.Center) {
                Text(label,color=if(active) SystemTokens.bg else SystemTokens.muted,fontSize=SystemTokens.caption,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun OrderHero(missions: List<MissaoDiaria>) {
    val done=missions.count { it.concluida }
    val all=missions.isNotEmpty() && done==missions.size
    val fraction=if(missions.isEmpty()) 0f else missions.sumOf { (it.progresso.toDouble()/it.alvoQtd.coerceAtLeast(1)).coerceIn(0.0,1.0) }.toFloat()/missions.size
    val progress by animateFloatAsState(fraction,tween(SystemTokens.settle),label="ordersOverall")
    Column(Modifier.fillMaxWidth().padding(vertical=SystemTokens.small),verticalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
            Column(Modifier.weight(1f)) {
                SystemLabel(if(all) "DIA CONQUISTADO" else "SUA PRÓXIMA CONQUISTA")
                Spacer(Modifier.height(SystemTokens.small))
                AnimatedContent(all,transitionSpec={fadeIn(tween(SystemTokens.reveal)) togetherWith fadeOut(tween(SystemTokens.reveal))},label="ordersHeadline") { complete ->
                    Text(if(complete) "Ordens\ncumpridas." else "A evolução\né uma escolha.",color=SystemTokens.ink,fontSize=SystemTokens.title,lineHeight=38.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                }
            }
            SystemSeal(progress,all,Modifier.size(SystemTokens.heroGlyph))
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
            Text("${done.toString().padStart(2,'0')}",color=SystemTokens.ink,fontSize=SystemTokens.metric,fontWeight=FontWeight.Bold)
            Text(" / ${missions.size.takeIf { it>0 } ?: 3} ordens",color=SystemTokens.muted,fontSize=SystemTokens.body,modifier=Modifier.weight(1f).padding(bottom=6.dp))
            AnimatedContent(missions.filter { it.concluida }.sumOf { it.xpRecompensa },label="ordersReward") { xp ->
                Text("$xp XP recebidos",color=SystemTokens.ink,fontSize=SystemTokens.caption,modifier=Modifier.padding(bottom=6.dp))
            }
        }
        LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(3.dp),color=SystemTokens.ink,trackColor=SystemTokens.border)
    }
}

@Composable internal fun SystemSeal(progress: Float, complete: Boolean, modifier: Modifier) {
    val clock=LocalMotion.current
    Canvas(modifier.semantics { contentDescription=if(complete) "Selo de ordens cumpridas" else "Progresso das ordens" }) {
        val radius=size.minDimension*.39f
        val pulse=clock.pulse.value
        drawCircle(SystemTokens.border,radius,style=Stroke(1.dp.toPx()))
        rotate(clock.orbit.value) {
            drawArc(SystemTokens.ink.copy(alpha=.2f),-90f,82f,false,topLeft=Offset(center.x-radius-6.dp.toPx(),center.y-radius-6.dp.toPx()),
                size=androidx.compose.ui.geometry.Size((radius+6.dp.toPx())*2,(radius+6.dp.toPx())*2),style=Stroke(1.dp.toPx()))
            drawArc(SystemTokens.ink.copy(alpha=.3f),90f,42f,false,topLeft=Offset(center.x-radius-6.dp.toPx(),center.y-radius-6.dp.toPx()),
                size=androidx.compose.ui.geometry.Size((radius+6.dp.toPx())*2,(radius+6.dp.toPx())*2),style=Stroke(1.dp.toPx()))
        }
        drawCircle(SystemTokens.ink.copy(alpha=.035f+pulse*.035f),radius*.76f)
        drawArc(SystemTokens.ink,-90f,360f*progress,false,topLeft=Offset(center.x-radius,center.y-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
        val path=Path().apply {
            moveTo(center.x,center.y-radius*.54f);lineTo(center.x+radius*.21f,center.y-radius*.13f)
            lineTo(center.x+radius*.50f,center.y);lineTo(center.x+radius*.21f,center.y+radius*.13f)
            lineTo(center.x,center.y+radius*.54f);lineTo(center.x-radius*.21f,center.y+radius*.13f)
            lineTo(center.x-radius*.50f,center.y);lineTo(center.x-radius*.21f,center.y-radius*.13f);close()
        }
        drawPath(path,SystemTokens.ink,style=if(complete) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.5.dp.toPx()))
        drawCircle(if(complete) SystemTokens.bg else SystemTokens.ink,2.dp.toPx())
    }
}

@Composable private fun MissionOrder(m: MissaoDiaria, index: Int, inspect: ()->Unit) {
    val progress by animateFloatAsState((m.progresso.toFloat()/m.alvoQtd.coerceAtLeast(1)).coerceIn(0f,1f),tween(SystemTokens.settle),label="order-${m.id}")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(SystemTokens.rowCorner)).clickable(onClick=inspect).padding(vertical=SystemTokens.small),
        horizontalArrangement=Arrangement.spacedBy(SystemTokens.gap),verticalAlignment=Alignment.Top) {
        Box(Modifier.padding(top=SystemTokens.compact).size(36.dp).clip(CircleShape)
            .background(if(m.concluida) SystemTokens.ink else SystemTokens.surface).border(SystemTokens.stroke,SystemTokens.border,CircleShape),contentAlignment=Alignment.Center) {
            AnimatedContent(m.concluida,label="orderSeal") { done ->
                if(done) Icon(Icons.Outlined.Check,null,tint=SystemTokens.bg,modifier=Modifier.size(18.dp))
                else Text(index.toString().padStart(2,'0'),color=SystemTokens.ink,fontSize=SystemTokens.caption,fontWeight=FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
                Text(m.descricao,Modifier.weight(1f),color=SystemTokens.ink,fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold)
                Icon(Icons.Outlined.ChevronRight,"Detalhes de ${m.descricao}",tint=SystemTokens.muted,modifier=Modifier.size(18.dp))
            }
            Text(MissaoEngine.unidade(m)+" · "+if(m.concluida) "${m.xpRecompensa} XP recebidos" else "+${m.xpRecompensa} XP",color=SystemTokens.muted,fontSize=SystemTokens.caption)
            LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(2.dp),color=SystemTokens.ink,trackColor=SystemTokens.border)
            Text(if(m.concluida) "Cumprida" else "Em andamento · ver objetivo",color=SystemTokens.muted,fontSize=SystemTokens.caption)
        }
    }
}

@Composable private fun WeeklyContract(m: MissaoDiaria, today: Long, days: Set<Long>, inspect: ()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(SystemTokens.corner)).background(SystemTokens.surface)
        .border(SystemTokens.stroke,SystemTokens.border,RoundedCornerShape(SystemTokens.corner)).clickable(onClick=inspect).padding(SystemTokens.inset),
        verticalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            SystemLabel("CONTRATO SEMANAL")
            Text(if(m.concluida) "Recebido" else "+${m.xpRecompensa} XP",color=SystemTokens.ink,fontSize=SystemTokens.caption)
        }
        Text("Consistência\nforja evolução.",color=SystemTokens.ink,fontSize=SystemTokens.heading,lineHeight=29.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            repeat(7) { index ->
                val day=m.epochDay+index;val done=day in days;val current=day==today
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
                    Text(listOf("S","T","Q","Q","S","S","D")[index],color=SystemTokens.muted,fontSize=SystemTokens.caption)
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if(done) SystemTokens.ink else SystemTokens.raised)
                        .border(SystemTokens.stroke,if(current) SystemTokens.ink else SystemTokens.border,CircleShape)
                        .semantics { contentDescription="${date(day)}: ${if(done) "atividade registrada" else "sem atividade registrada"}" },contentAlignment=Alignment.Center) {
                        if(done) Icon(Icons.Outlined.Check,null,tint=SystemTokens.bg,modifier=Modifier.size(16.dp))
                        else if(current) Box(Modifier.size(4.dp).clip(CircleShape).background(SystemTokens.ink))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(MissaoEngine.unidade(m),color=SystemTokens.ink,fontSize=SystemTokens.body,fontWeight=FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Detalhes do contrato semanal",tint=SystemTokens.ink,modifier=Modifier.size(18.dp))
        }
    }
}

@Composable internal fun SystemBossCard(boss: CameraBoss, progress: CameraBossProgress?, enter: ()->Unit) {
    val rank=Patente.entries[(progress?.trialRank ?: 1).coerceIn(1,14)]
    val enemy=CameraTrials.enemy(boss,rank)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(SystemTokens.corner)).background(SystemTokens.surface)
        .border(SystemTokens.stroke,SystemTokens.border,RoundedCornerShape(SystemTokens.corner))) {
        Box(Modifier.fillMaxWidth().heightIn(min=SystemTokens.bossHeight)) {
            Image(painterResource(trialArt(rank)),null,Modifier.align(Alignment.CenterEnd).fillMaxWidth(.50f).height(SystemTokens.bossHeight),
                contentScale=ContentScale.Fit,colorFilter=ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(SystemTokens.surface,SystemTokens.surface.copy(alpha=.8f),Color.Transparent))))
            Column(Modifier.fillMaxWidth(.69f).padding(SystemTokens.inset),verticalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
                SystemLabel(if(boss==CameraBoss.ASH_WARDEN) "FLEXÕES / ${rank.titulo.uppercase()}" else "AGACHAMENTOS / ${rank.titulo.uppercase()}")
                Text(enemy.name,color=SystemTokens.ink,fontSize=SystemTokens.heading,lineHeight=29.sp,fontWeight=FontWeight.Bold)
                Text("${enemy.hp}",color=SystemTokens.ink,fontSize=SystemTokens.metric,fontWeight=FontWeight.Bold)
                Text("repetições numa tentativa",color=SystemTokens.muted,fontSize=SystemTokens.caption)
                Text("${progress?.wins ?: 0} vitórias registradas",color=SystemTokens.muted,fontSize=SystemTokens.caption)
            }
        }
        SystemAction("Enfrentar ${if(boss==CameraBoss.ASH_WARDEN) "com flexões" else "com agachamentos"}",Modifier.padding(start=SystemTokens.inset,end=SystemTokens.inset,bottom=SystemTokens.inset),enter)
    }
}

@Composable private fun CommunityPath(index: String, title: String, description: String, icon: ImageVector, open: ()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(SystemTokens.rowCorner)).clickable(onClick=open).padding(vertical=SystemTokens.gap),verticalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
            Icon(icon,null,tint=SystemTokens.ink,modifier=Modifier.size(28.dp))
            Text(title,color=SystemTokens.ink,fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            Text(index,color=SystemTokens.muted,fontSize=SystemTokens.caption)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,tint=SystemTokens.ink,modifier=Modifier.size(18.dp))
        }
        Text(description,color=SystemTokens.muted,fontSize=SystemTokens.body,lineHeight=21.sp,modifier=Modifier.padding(start=44.dp))
        HorizontalDivider(color=SystemTokens.border,modifier=Modifier.padding(top=SystemTokens.gap))
    }
}

@Composable private fun ArchiveOrder(m: MissaoDiaria, today: Long, inspect: ()->Unit) {
    SystemLink(m.descricao,
        (if(m.concluida) "Cumprida · ${m.xpRecompensa} XP recebidos" else if(periodEnded(m,today)) "Prazo encerrado · ${MissaoEngine.unidade(m)}" else "Em andamento · ${MissaoEngine.unidade(m)}")+
            if(MissaoEngine.semanal(m)) " · semanal" else "",
        if(m.concluida) Icons.Outlined.Verified else Icons.Outlined.Schedule,onClick=inspect)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MissionDetail(m: MissaoDiaria, board: SystemBoard, mode: SystemMode, close: ()->Unit, open: (String)->Unit) {
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=SystemTokens.surface,contentColor=SystemTokens.ink) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(SystemTokens.inset),verticalArrangement=Arrangement.spacedBy(SystemTokens.inset)) {
            SystemLabel(if(MissaoEngine.semanal(m)) "CONTRATO SEMANAL" else "ORDEM DO SYSTEM")
            Text(m.descricao,fontSize=SystemTokens.heading,lineHeight=30.sp,fontWeight=FontWeight.Bold)
            Text(MissaoEngine.instrucao(m),color=SystemTokens.muted,fontSize=SystemTokens.body,lineHeight=22.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(MissaoEngine.unidade(m),fontSize=SystemTokens.body,fontWeight=FontWeight.SemiBold)
                Text("${m.xpRecompensa} XP",fontSize=SystemTokens.body,fontWeight=FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress={(m.progresso.toFloat()/m.alvoQtd.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp),color=SystemTokens.ink,trackColor=SystemTokens.border)
            Text(when {m.concluida->"Recompensa recebida automaticamente. Seu registro de conquista está guardado.";periodEnded(m,board.day)->"Essa ordem encerrou. As ordens atuais continuam disponíveis na primeira aba.";else->"O progresso acompanha os registros válidos. A recompensa entra automaticamente ao concluir o objetivo."},color=SystemTokens.muted,fontSize=SystemTokens.caption,lineHeight=19.sp)
            if(!m.concluida && !periodEnded(m,board.day)) {
                val route=when(m.tipo) {
                    "SYS_CARE"->RemnantRoute.NUTRITION
                    "SYS_CARDIO_MIN"->RemnantRoute.CARDIO
                    "SYS_HOME_REPS"->RemnantRoute.BOSS_HUNT
                    "TREINAR_MUSCULO"->m.alvoMusculo?.let(RemnantRoute::muscleRank) ?: RemnantRoute.PLAN
                    else->when { mode==SystemMode.CARDIO && !MissaoEngine.semanal(m)->RemnantRoute.CARDIO;mode==SystemMode.HOME && !MissaoEngine.semanal(m)->RemnantRoute.BOSS_HUNT;board.activeSession!=null->RemnantRoute.SESSION;else->RemnantRoute.PLAN }
                }
                SystemAction(when(route) {RemnantRoute.NUTRITION->"Abrir diário de nutrição";RemnantRoute.CARDIO->"Abrir cardio";RemnantRoute.BOSS_HUNT->"Escolher provação";RemnantRoute.SESSION->"Retomar meu treino";else->"Ver meu treino"}) { open(route) }
            }
        }
    }
}

@Composable internal fun SystemLabel(text: String) { Text(text,color=SystemTokens.muted,fontSize=SystemTokens.caption,fontWeight=FontWeight.Medium,letterSpacing=.8.sp) }
@Composable internal fun SystemAction(label: String, modifier: Modifier=Modifier, action: ()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .982f else 1f,spring(.9f,650f),label="systemPress")
    val haptic=LocalHapticFeedback.current
    Row(modifier.fillMaxWidth().graphicsLayer { scaleX=scale;scaleY=scale }.heightIn(min=SystemTokens.touch)
        .clip(RoundedCornerShape(SystemTokens.rowCorner)).background(SystemTokens.ink)
        .clickable(interactionSource=interaction,indication=null,role=Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);action() }
        .padding(horizontal=SystemTokens.gap,vertical=SystemTokens.small),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SystemTokens.small)) {
        Text(label,color=SystemTokens.bg,fontSize=SystemTokens.body,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,tint=SystemTokens.bg,modifier=Modifier.size(18.dp))
    }
}
@Composable private fun SystemLink(title: String, subtitle: String, icon: ImageVector, enabled: Boolean=true, onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=SystemTokens.touch).clip(RoundedCornerShape(SystemTokens.rowCorner)).clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(vertical=SystemTokens.small),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
        Icon(icon,null,tint=SystemTokens.ink,modifier=Modifier.size(24.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(SystemTokens.compact)) {
            Text(title,color=SystemTokens.ink,fontSize=SystemTokens.body,fontWeight=FontWeight.SemiBold)
            Text(subtitle,color=SystemTokens.muted,fontSize=SystemTokens.caption,lineHeight=18.sp)
        }
        Icon(Icons.Outlined.ChevronRight,null,tint=SystemTokens.muted,modifier=Modifier.size(18.dp))
    }
}
@Composable private fun EmptySystem(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical=SystemTokens.inset),verticalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
        Icon(Icons.Outlined.History,null,tint=SystemTokens.muted,modifier=Modifier.size(40.dp))
        Text(title,color=SystemTokens.ink,fontSize=SystemTokens.heading,fontWeight=FontWeight.Bold)
        Text(description,color=SystemTokens.muted,fontSize=SystemTokens.body,lineHeight=22.sp)
    }
}
@Composable private fun OrdersLoading() {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(SystemTokens.gap)) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(SystemTokens.rowCorner)).background(SystemTokens.surface)) }
        Text("Preparando suas ordens…",color=SystemTokens.muted,fontSize=SystemTokens.caption)
    }
}
private fun periodEnded(m: MissaoDiaria, today: Long) = today > m.epochDay + if(MissaoEngine.semanal(m)) 6 else 0
private fun date(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM",Locale.forLanguageTag("pt-BR")))
