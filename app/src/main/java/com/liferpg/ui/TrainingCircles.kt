package com.liferpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.CircleDelivery
import com.liferpg.data.sync.*
import com.liferpg.domain.EstadoSessao
import com.liferpg.ui.theme.*
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal object CircleUiTokens {
    val gap=12.dp; val smallGap=8.dp; val line=1.dp; val rankWidth=36.dp
    val body=14.sp; val caption=12.sp; val heading=20.sp; val number=24.sp
}

@Composable internal fun CircleArenaContent(vm: MainViewModel, inspect: (String) -> Unit,train:()->Unit={}) {
    val observedState by vm.circles.state.collectAsStateWithLifecycle()
    val deliveries by vm.circles.deliveries.collectAsStateWithLifecycle()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val state=if(observedState.accountId==account?.userId) observedState else com.liferpg.data.CircleState()
    val friends by vm.amizadesArena.collectAsStateWithLifecycle()
    val prefs by vm.prefsEfetivas.collectAsStateWithLifecycle()
    var create by rememberSaveable(account?.userId) { mutableStateOf(false) }
    var inviting by rememberSaveable(account?.userId) { mutableStateOf(false) }
    var leaving by rememberSaveable(account?.userId) { mutableStateOf(false) }
    val conversation by vm.circles.conversation.state.collectAsStateWithLifecycle()
    val reactionActions by vm.circles.conversation.reactions.collectAsStateWithLifecycle()
    val weeklyBoard by vm.circles.weekBoard.state.collectAsStateWithLifecycle()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(Unit) { onDispose { vm.circles.conversation.close(); vm.circles.weekBoard.close() } }
    LaunchedEffect(conversation.target,lifecycle) {
        if(conversation.target!=null) lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while(true) { kotlinx.coroutines.delay(15_000); vm.circles.conversation.refresh(quiet=true) }
        }
    }
    LaunchedEffect(conversation.target,state.busy,state.circles) {
        conversation.target?.circle?.let { id -> if(!state.busy && state.selected!=id && state.circles.any { it.id==id && it.status=="accepted" }) vm.circles.select(id) }
    }
    var inviteGoal by rememberSaveable(account?.userId) { mutableIntStateOf(prefs.diasPorSemana.coerceIn(1,7)) }
    val selected=state.circles.firstOrNull { it.id==state.selected && it.status=="accepted" }
    val allowed=friends.filter { it.status=="accepted" }.map { it.jogadorId }.toSet()
    LaunchedEffect(Unit) { vm.circles.load(); vm.carregarSocial(silencioso=true) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Seu círculo.",fontSize=CircleUiTokens.heading,fontWeight=FontWeight.SemiBold)
            Text("Treinos reais. Presença compartilhada.",color=TextoSecundario,fontSize=CircleUiTokens.caption) }
        TextButton(onClick=vm.circles::load,enabled=!state.busy) { Text("Atualizar") }
    }
    AnimatedVisibility(state.error!=null) { Text(state.error.orEmpty(),color=TextoSecundario,fontSize=CircleUiTokens.body) }
    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=TextoPrimario)
    state.circles.filter { it.status=="pending" }.forEach { invitation -> key(invitation.id) { Panel {
        Eyebrow("CONVITE PRIVADO")
        Text(invitation.name,fontSize=CircleUiTokens.heading,fontWeight=FontWeight.SemiBold)
        Text("Ao aceitar, você verá os treinos compartilhados aqui. Seus registros só aparecem se você compartilhar.",fontSize=CircleUiTokens.body,color=TextoSecundario)
        CircleGoalPicker(inviteGoal) { inviteGoal=it }
        Row { TextButton(onClick={ vm.circles.respond(invitation.id,true,inviteGoal) },enabled=!state.busy) { Text("Aceitar convite") }
            TextButton(onClick={ vm.circles.respond(invitation.id,false,inviteGoal) },enabled=!state.busy) { Text("Recusar") } }
    } } }
    if(state.circles.none { it.status=="accepted" }) {
        CircleActivityInvitation(false,!state.busy) { create=true }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(CircleUiTokens.smallGap)) {
            state.circles.filter { it.status=="accepted" }.forEach { c -> Choice(c.name,c.id==state.selected) { vm.circles.select(c.id) } }
            TextButton(onClick={ create=true },enabled=!state.busy) { Text("Novo círculo") }
        }
    }
    selected?.let { circle ->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Eyebrow("CÍRCULO PRIVADO")
                Text(circle.name,fontSize=CircleUiTokens.heading,fontWeight=FontWeight.SemiBold) }
            if(circle.owner==account?.userId) TextButton(onClick={ inviting=true; vm.carregarSocial(silencioso=true) },enabled=!state.busy) { Text("Convidar") }
        }
        if(state.cached) Text("Dados guardados no aparelho. O placar precisa de conexão para atualizar.",fontSize=CircleUiTokens.caption,color=TextoSecundario)
        CircleBoard(circle,account?.userId.orEmpty(),allowed,{ vm.circles.weekBoard.open(circle.id) },inspect)
        val pending=deliveries.filter { it.userId==account?.userId && it.circleId==circle.id && it.status in listOf("pending","failed") }
        pending.forEach { row -> CircleDeliveryLine(row,{ vm.circles.retry(row.id) },{ vm.circles.withdraw(row.circleId,row.sessionId) {} }) }
        Section("Atividade compartilhada")
        if(state.posts.isEmpty() && !state.busy) CircleActivityInvitation(true,true,train)
        state.posts.forEach { post -> key(post.id) { CircleWorkoutStory(post,reactionActions[post.id],
            { vm.circles.conversation.react(post,it) },{ vm.circles.conversation.open(circle.id,post.id) }) } }
        if(state.hasMore) TextButton(onClick=vm.circles::more,enabled=!state.busy) { Text("Ver treinos anteriores") }
        TextButton(onClick={ leaving=true },enabled=!state.busy) { Text(if(circle.owner==account?.userId) "Encerrar círculo…" else "Sair do círculo…",color=TextoSecundario) }
    }
    if(create) CircleCreateDialog(state.creating,state.error,prefs.diasPorSemana,{ create=false },canSubmit=!state.busy) { name,goal -> vm.circles.create(name,goal) { create=false } }
    if(inviting && selected!=null) AlertDialog(onDismissRequest={ inviting=false },title={ Text("Convidar aliado") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(CircleUiTokens.smallGap)) {
            Text("A entrada depende do aceite. Nenhum histórico privado é liberado pelo convite.",color=TextoSecundario)
            state.error?.let { Text(it) }
            val options=friends.filter { it.status=="accepted" && selected.members.none { m -> m.uid==it.jogadorId } }
            if(options.isEmpty()) Text("Todos os seus amigos já estão convidados. Você pode encontrar novas conexões em Amigos.")
            options.forEach { f -> TextButton(onClick={ vm.circles.invite(selected.id,f.jogadorId) { inviting=false } },enabled=!state.busy) { Text(f.apelido) } }
        }
    },confirmButton={ TextButton(onClick={ inviting=false }) { Text("Fechar") } })
    if(leaving && selected!=null) AlertDialog(onDismissRequest={ leaving=false },title={ Text(if(selected.owner==account?.userId) "Encerrar este círculo?" else "Sair deste círculo?") },
        text={ Column { Text(if(selected.owner==account?.userId) "O círculo, os resumos e as conversas serão removidos para todos. Os treinos pessoais permanecem guardados." else "Você perde acesso. Seus resumos, comentários e reações neste círculo são removidos. Seu treino pessoal permanece guardado."); state.error?.let { Text(it) } } },
        confirmButton={ TextButton(onClick={ vm.circles.leave(selected.id) { leaving=false } },enabled=!state.busy) { Text("Confirmar") } },
        dismissButton={ TextButton(onClick={ leaving=false }) { Text("Voltar") } })
    if(conversation.target!=null && conversation.accountId==account?.userId) {
        val post=conversation.content?.post
        CircleConversationSheet(conversation,post?.let { reactionActions[it.id] },account?.userId.orEmpty(),
            vm.circles.conversation::close,{ vm.circles.conversation.refresh() },{ vm.circles.conversation.refresh(more=true) },
            { kind -> post?.let { vm.circles.conversation.react(it,kind) } },vm.circles.conversation::draft,
            vm.circles.conversation::send,vm.circles.conversation::delete,
            if(post?.uid==account?.userId) { { vm.circles.withdraw(conversation.target!!.circle,post!!.sessionId) { vm.circles.conversation.close() } } } else null,
            vm.circles.conversation::acknowledgeVisible,state.busy,state.error)
    }
    if(weeklyBoard.circle!=null && weeklyBoard.accountId==account?.userId) {
        val circle=state.circles.firstOrNull { it.id==weeklyBoard.circle && it.status=="accepted" }
        if(circle!=null) CircleWeekSheet(weeklyBoard,circle.name,account?.userId.orEmpty(),allowed,
            vm.circles.weekBoard::close,vm.circles.weekBoard::refresh,vm.circles.weekBoard::select) { target ->
                vm.circles.weekBoard.close(); inspect(target)
            }
    }
}

@Composable internal fun CircleBoard(circle: TrainingCircle, uid: String, friends: Set<String>, openBoard: (() -> Unit)? = null, inspect: (String) -> Unit) {
    var expanded by rememberSaveable(circle.id) { mutableStateOf(false) }
    val board=circle.board
    val visible=if(expanded || board.size<=3) board else (board.take(3)+board.filter { it.uid==uid }).distinctBy { it.uid }
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(CircleUiTokens.gap)) {
        Eyebrow("CONSTÂNCIA / META SEMANAL")
        Text("Desde ${java.time.LocalDate.parse(circle.weekStart).format(DateTimeFormatter.ofPattern("dd/MM"))} · Fuso: ${circle.timezone.substringAfterLast('/').replace('_',' ')}",fontSize=CircleUiTokens.caption,color=TextoSecundario)
        Text("Um dia conta uma vez. Cada pessoa cumpre a própria meta; 100% é o teto. Registros declarados pelos participantes.",fontSize=CircleUiTokens.caption,color=TextoSecundario)
        visible.forEach { m ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(CircleUiTokens.gap)) {
                Text("${circle.position(m)}",Modifier.width(CircleUiTokens.rankWidth),fontSize=CircleUiTokens.number,fontWeight=FontWeight.SemiBold)
                Column(Modifier.weight(1f)) {
                    Text(if(m.uid==uid) "${m.nickname} · você" else m.nickname,fontWeight=FontWeight.Medium,
                        modifier=Modifier.clickable(enabled=m.uid in friends) { inspect(m.uid) })
                    Text("${m.days}/${m.goal} dias · ${(m.progress*100).toInt()}%",fontSize=CircleUiTokens.caption,color=TextoSecundario)
                    Meter(m.progress)
                }
            }
        }
        if(openBoard!=null) TextButton(onClick=openBoard) { Text("Placar completo e semanas anteriores") }
        else if(board.size>3) TextButton(onClick={ expanded=!expanded }) { Text(if(expanded) "Resumir placar" else "Ver os ${board.size} participantes") }
    }
}

@Composable internal fun CircleDeliveryLine(row: CircleDelivery, retry: () -> Unit, withdraw: () -> Unit) {
    Column(Modifier.animateContentSize()) {
        Text(runCatching { JSONObject(row.receipt).getString("title") }.getOrDefault("Resumo de treino"),fontWeight=FontWeight.SemiBold,fontSize=CircleUiTokens.body)
        Text(when { row.status=="failed" -> "Ação não concluída"; row.action=="DELETE" -> "Retirada aguardando envio"; else -> "Resumo aguardando envio" },fontWeight=FontWeight.Medium,fontSize=CircleUiTokens.body)
        Text(row.error ?: "Guardado no aparelho. A conexão vai confirmar a ação.",color=TextoSecundario,fontSize=CircleUiTokens.caption)
        Row { if(row.status=="failed") TextButton(onClick=retry) { Text("Tentar novamente") }
            if(row.action=="PUBLISH") TextButton(onClick=withdraw) { Text("Retirar compartilhamento") } }
    }
}

@Composable internal fun CirclePostRow(post: CirclePost, open: () -> Unit) {
    val r=remember(post.receipt) { JSONObject(post.receipt) }
    val exercises=r.getJSONArray("exercises")
    val sets=(0 until exercises.length()).sumOf { exercises.getJSONObject(it).getJSONArray("sets").length() }
    val prs=(0 until exercises.length()).sumOf { i -> val ss=exercises.getJSONObject(i).getJSONArray("sets"); (0 until ss.length()).count { ss.getJSONObject(it).optBoolean("pr") } }
    Panel(Modifier.clickable(onClick=open)) {
        Text(post.nickname,fontWeight=FontWeight.Medium,fontSize=CircleUiTokens.body)
        Text(r.getString("title"),fontSize=CircleUiTokens.heading,fontWeight=FontWeight.SemiBold)
        Text("$sets série${if(sets==1) "" else "s"} · ${exercises.length()} exercício${if(exercises.length()==1) "" else "s"}${if(prs>0) " · $prs PR${if(prs==1) "" else "s"}" else ""}",fontSize=CircleUiTokens.body,color=TextoSecundario)
        Text(OffsetDateTime.parse(r.getString("ended_at")).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))+" · Ver treino ›",fontSize=CircleUiTokens.caption,color=TextoSecundario)
    }
}

@Composable internal fun CirclePostDialog(post: CirclePost, mine: Boolean, busy: Boolean, dismiss: () -> Unit, withdraw: () -> Unit) {
    val r=remember(post.receipt) { JSONObject(post.receipt) }
    AlertDialog(onDismissRequest=dismiss,title={ Text(r.getString("title")) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(CircleUiTokens.gap)) {
            Text("Compartilhado por ${post.nickname}. Cargas e repetições declaradas.",color=TextoSecundario)
            val exercises=r.getJSONArray("exercises")
            for(i in 0 until exercises.length()) {
                val e=exercises.getJSONObject(i); Text(e.getString("name"),fontWeight=FontWeight.SemiBold)
                val sets=e.getJSONArray("sets")
                for(j in 0 until sets.length()) {
                    val s=sets.getJSONObject(j)
                    Text("${j+1}. "+listOfNotNull(if(s.has("kg")) "${Format.peso(s.getDouble("kg").toFloat())} kg" else null,
                        if(s.has("reps")) "${s.getInt("reps")} rep" else null,if(s.has("seconds")) "${s.getInt("seconds")} s" else null).joinToString(" × ")+if(s.optBoolean("pr")) " · PR" else "",fontSize=CircleUiTokens.body)
                }
            }
        }
    },confirmButton={ TextButton(onClick=dismiss) { Text("Fechar") } },dismissButton={ if(mine) TextButton(onClick=withdraw,enabled=!busy) { Text("Retirar do círculo") } })
}

@Composable private fun CircleGoalPicker(goal: Int, change: (Int) -> Unit) {
    Text("Sua meta fixa: $goal dia${if(goal==1) "" else "s"} por semana",fontSize=CircleUiTokens.body)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(CircleUiTokens.smallGap)) {
        (1..7).forEach { Choice("$it",goal==it) { change(it) } }
    }
}

@Composable internal fun SessionCircleShare(vm: MainViewModel, session: EstadoSessao) {
    val observedState by vm.circles.state.collectAsStateWithLifecycle()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val state=if(observedState.accountId==account?.userId) observedState else com.liferpg.data.CircleState()
    val deliveries by vm.circles.deliveries.collectAsStateWithLifecycle()
    var showing by rememberSaveable(session.sessionUuid,account?.userId) { mutableStateOf(false) }
    var chosen by rememberSaveable(session.sessionUuid,account?.userId) { mutableStateOf<String?>(null) }
    val rows=deliveries.filter { it.userId==account?.userId && it.sessionId==session.sessionUuid }
    val saving by vm.sessionSaving.collectAsStateWithLifecycle()
    val shareable by vm.sessionShareable.collectAsStateWithLifecycle()
    if(session.seriesFeitas>0 && shareable) {
        Action("Compartilhar resumo com um círculo",!state.busy && !saving) { vm.circles.load(); showing=true }
        Text("Só exercícios, séries e PRs deste treino. Sem fotos do shape ou medidas pessoais.",color=TextoSecundario,fontSize=CircleUiTokens.caption)
        rows.forEach { r -> Text(when(r.status) { "sent" -> "Resumo publicado no círculo."; "withdrawn" -> "Compartilhamento retirado."; "failed" -> r.error ?: "Envio não concluído."; else -> if(r.action=="DELETE") "Retirada aguardando envio." else "Resumo guardado, aguardando envio." },color=TextoSecundario,fontSize=CircleUiTokens.body) }
    }
    if(showing) AlertDialog(onDismissRequest={ showing=false },title={ Text("Escolha quem vai ver") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(CircleUiTokens.gap)) {
            Text("${session.nome} · ${session.seriesFeitas} séries. Apenas os membros aceitos do círculo escolhido verão o resumo.")
            val options=state.circles.filter { it.status=="accepted" }
            if(options.isEmpty()) Text("Crie ou aceite um círculo em Amigos → Coop antes de compartilhar.",color=TextoSecundario)
            options.forEach { c -> Row(Modifier.fillMaxWidth().clickable { chosen=c.id },verticalAlignment=Alignment.CenterVertically) {
                RadioButton(chosen==c.id,{ chosen=c.id }); Text(c.name,Modifier.weight(1f))
            } }
            state.error?.let { Text(it) }
            Text("Você pode retirar o resumo depois. Compartilhar não muda XP, sequência ou patente.",fontSize=CircleUiTokens.caption,color=TextoSecundario)
        }
    },confirmButton={ TextButton(onClick={ chosen?.let { vm.circles.share(it,session.sessionUuid) { showing=false } } },enabled=!state.busy && state.circles.any { it.id==chosen && it.status=="accepted" } && rows.none { it.circleId==chosen }) { Text("Compartilhar resumo") } },dismissButton={ TextButton(onClick={ showing=false }) { Text("Agora não") } })
}
