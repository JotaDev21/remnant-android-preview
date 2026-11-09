package com.liferpg.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.liferpg.data.*
import com.liferpg.data.sync.*
import com.liferpg.ui.theme.*
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*
import kotlinx.coroutines.flow.distinctUntilChanged

internal object ConversationTokens {
    val gap=16.dp; val compact=8.dp; val tiny=4.dp; val inset=20.dp; val line=1.dp
    val touch=48.dp; val avatar=36.dp; val icon=22.dp; val radius=20.dp
    val title=24.sp; val metric=40.sp; val body=14.sp; val caption=12.sp
    const val revealMs=260; const val responseMs=380; const val countMs=180
}
internal data class WorkoutHighlight(val exercise:String,val kg:Float?,val reps:Int?,val seconds:Int?,val pr:Boolean)
internal data class WorkoutStory(val title:String,val sets:Int,val exercises:Int,val prs:Int,val minutes:Int,val highlight:WorkoutHighlight?)
internal fun workoutStory(post:CirclePost):WorkoutStory {
    val r=JSONObject(post.receipt); val exercises=r.getJSONArray("exercises"); var total=0; var prs=0
    val marks=mutableListOf<WorkoutHighlight>()
    for(i in 0 until exercises.length()) {
        val e=exercises.getJSONObject(i); val sets=e.getJSONArray("sets"); total+=sets.length()
        for(j in 0 until sets.length()) { val s=sets.getJSONObject(j); val pr=s.optBoolean("pr"); if(pr) prs++
            marks+=WorkoutHighlight(e.getString("name"),if(s.has("kg")) s.getDouble("kg").toFloat() else null,
                if(s.has("reps")) s.getInt("reps") else null,if(s.has("seconds")) s.getInt("seconds") else null,pr)
        }
    }
    val highlight=marks.filter { it.pr }.ifEmpty { marks }.maxWithOrNull(compareBy<WorkoutHighlight> { it.kg?:0f }.thenBy { it.reps?:it.seconds?:0 })
    return WorkoutStory(r.getString("title"),total,exercises.length(),prs,(r.getInt("duration_seconds")/60).coerceAtLeast(0),highlight)
}
private fun conversationTime(value:String)=runCatching { OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM · HH:mm")) }.getOrDefault("")

/** A workout is a story with an actual performance, rather than a generic text card. */
@Composable internal fun CircleWorkoutStory(post:CirclePost,action:CircleReactionAction?,react:(CircleReaction)->Unit,open:()->Unit) {
    val story=remember(post.receipt) { workoutStory(post) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(ConversationTokens.revealMs)),verticalArrangement=Arrangement.spacedBy(ConversationTokens.gap)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
            Box(Modifier.size(ConversationTokens.avatar).clip(CircleShape).background(SuperficieAlta),contentAlignment=Alignment.Center) {
                Text(post.nickname.take(1).uppercase(),fontWeight=FontWeight.Bold,color=TextoPrimario)
            }
            Column(Modifier.weight(1f)) {
                Text(post.nickname,fontSize=ConversationTokens.body,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(conversationTime(JSONObject(post.receipt).getString("ended_at")),color=TextoSecundario,fontSize=ConversationTokens.caption)
            }
            if(story.prs>0) Icon(Icons.Outlined.EmojiEvents,"${story.prs} recordes neste treino",tint=TextoPrimario)
        }
        Column(Modifier.fillMaxWidth().clickable(onClickLabel="Ver séries de ${story.title}",onClick=open),verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
            Text(story.title,fontSize=ConversationTokens.title,fontWeight=FontWeight.SemiBold)
            WorkoutPerformance(story)
            Text("${story.sets} séries · ${story.exercises} exercícios · ${story.minutes} min",fontSize=ConversationTokens.caption,color=TextoSecundario)
        }
        CircleReactions(post.social,action,react)
        TextButton(onClick=open,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.ChatBubbleOutline,null,Modifier.size(ConversationTokens.icon))
            Spacer(Modifier.width(ConversationTokens.compact))
            Text(if(post.social.comments==0) "Abrir conversa" else "${post.social.comments} comentário${if(post.social.comments==1) "" else "s"}",Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.size(ConversationTokens.icon))
        }
        HorizontalDivider(color=Borda,thickness=ConversationTokens.line)
    }
}
@Composable private fun WorkoutPerformance(story:WorkoutStory) {
    story.highlight?.let { mark ->
        Column(Modifier.fillMaxWidth().border(ConversationTokens.line,Borda,RoundedCornerShape(ConversationTokens.radius))
            .padding(ConversationTokens.gap),verticalArrangement=Arrangement.spacedBy(ConversationTokens.tiny)) {
            Text(if(mark.pr) "RECORDE DESTA SESSÃO" else "SÉRIE EM DESTAQUE",fontSize=ConversationTokens.caption,color=TextoSecundario)
            Text(mark.exercise,fontSize=ConversationTokens.body,fontWeight=FontWeight.Medium)
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
                val kg=mark.kg?.takeIf { it>0f }
                Text(if(kg!=null) Format.peso(kg) else (mark.reps?:mark.seconds?:0).toString(),fontSize=ConversationTokens.metric,fontWeight=FontWeight.SemiBold)
                Column(Modifier.padding(bottom=ConversationTokens.compact)) {
                    Text(if(kg!=null) "kg" else if(mark.reps!=null) "repetições" else "segundos",fontSize=ConversationTokens.body,color=TextoSecundario)
                    if(kg!=null) Text(if(mark.reps!=null) "${mark.reps} repetições" else "${mark.seconds} segundos",fontSize=ConversationTokens.caption,color=TextoSecundario)
                }
            }
        }
    }
}
@Composable internal fun CircleReactions(social:CircleSocial,action:CircleReactionAction?,react:(CircleReaction)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
            CircleReaction.entries.forEach { kind ->
                val selected=if(action?.pending==true) action.desired==kind else social.mine==kind
                ReactionControl(kind,social.count(kind),selected,action?.pending!=true,Modifier.weight(1f)) { react(kind) }
            }
        }
        AnimatedVisibility(action?.pending==true || action?.error!=null,enter=fadeIn(),exit=fadeOut()) {
            Text(action?.error?:"Confirmando reação…",fontSize=ConversationTokens.caption,color=TextoSecundario,
                modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        }
    }
}
@Composable private fun ReactionControl(kind:CircleReaction,count:Int,selected:Boolean,enabled:Boolean,modifier:Modifier,click:()->Unit) {
    val interaction=remember { MutableInteractionSource() }; var taps by remember { mutableIntStateOf(0) }
    val impact=remember { Animatable(0f) }; val haptics=LocalHapticFeedback.current
    LaunchedEffect(taps) { if(taps>0) { impact.snapTo(1f); impact.animateTo(0f,tween(ConversationTokens.responseMs,easing=LinearOutSlowInEasing)) } }
    // Inverting both colours through grey erases the label midway. State changes immediately;
    // the icon, particles, press layer and count carry motion while contrast stays intact.
    val tint=if(selected) Fundo else TextoPrimario
    val background=if(selected) TextoPrimario else Superficie
    Row(modifier.heightIn(min=ConversationTokens.touch).pressMotion(interaction).clip(RoundedCornerShape(ConversationTokens.radius))
        .background(background).clickable(enabled=enabled,interactionSource=interaction,indication=LocalIndication.current,role=Role.Button) {
            taps++; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); click()
        }.semantics { contentDescription="${kind.title}, $count reações"; stateDescription=if(selected) "Sua reação" else "Não selecionada" }
        .padding(horizontal=ConversationTokens.compact,vertical=ConversationTokens.compact),
        horizontalArrangement=Arrangement.spacedBy(ConversationTokens.tiny),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(ConversationTokens.icon),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val p=1f-impact.value; if(impact.value>0f) repeat(6) { i ->
                    val angle=i*PI.toFloat()/3; val distance=size.minDimension*(.45f+p*.3f)
                    drawCircle(tint.copy(alpha=impact.value),radius=size.minDimension*.04f,
                        center=Offset(center.x+cos(angle)*distance,center.y+sin(angle)*distance))
                }
            }
            Icon(when(kind) { CircleReaction.RESPECT->Icons.Outlined.FitnessCenter; CircleReaction.FIRE->Icons.Outlined.Whatshot; CircleReaction.RECORD->Icons.Outlined.EmojiEvents },
                null,Modifier.size(ConversationTokens.icon).graphicsLayer { scaleX=1f+sin(impact.value*PI.toFloat())*.2f; scaleY=scaleX; rotationZ=sin(impact.value*PI.toFloat())*-10f },tint=tint)
        }
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(kind.title,fontSize=ConversationTokens.caption,color=tint,fontWeight=FontWeight.Medium,maxLines=1)
            AnimatedContent(count,transitionSpec={ (slideInVertically(tween(ConversationTokens.countMs)) { it/2 }+fadeIn()) togetherWith
                (slideOutVertically(tween(ConversationTokens.countMs)) { -it/2 }+fadeOut()) },label="reactionCount") {
                Text(it.toString(),fontSize=ConversationTokens.caption,color=tint)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CircleConversationSheet(state:CircleConversationState,action:CircleReactionAction?,uid:String,
    dismiss:()->Unit,refresh:()->Unit,more:()->Unit,react:(CircleReaction)->Unit,draft:(String)->Unit,send:()->Unit,delete:(CircleComment)->Unit,withdraw:(()->Unit)?=null,
    visible:(List<String>)->Unit={},withdrawBusy:Boolean=false,withdrawError:String?=null) {
    val target=state.target?:return
    var tab by rememberSaveable(target.post) { mutableIntStateOf(0) }
    var deleting by remember(target.post) { mutableStateOf<CircleComment?>(null) }
    var withdrawing by rememberSaveable(target.post) { mutableStateOf(false) }
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=sheet,containerColor=Fundo,contentColor=TextoPrimario,
        dragHandle={ BottomSheetDefaults.DragHandle(color=Borda) },shape=RoundedCornerShape(topStart=ConversationTokens.radius,topEnd=ConversationTokens.radius)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal=ConversationTokens.inset),verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CONVERSA DO TREINO",color=TextoSecundario,fontSize=ConversationTokens.caption)
                        Text(state.content?.post?.nickname?:"Círculo privado",fontSize=ConversationTokens.body,fontWeight=FontWeight.Medium)
                    }
                    IconButton(onClick=refresh,enabled=!state.loading && !state.sending) { Icon(Icons.Outlined.Refresh,"Atualizar conversa") }
                    IconButton(onClick=dismiss) { Icon(Icons.Outlined.Close,"Fechar conversa") }
                }
                val content=state.content
                if(content==null) {
                    if(state.loading) ConversationSkeleton()
                    else Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.gap)) {
                        Icon(Icons.Outlined.CloudOff,null)
                        Text(state.error?:"A conversa está indisponível.",fontSize=ConversationTokens.body)
                        TextButton(onClick=refresh) { Text("Tentar novamente") }
                    }
                } else {
                    Row(horizontalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
                        Choice("Conversa",tab==0) { tab=0 }; Choice("Séries",tab==1) { tab=1 }
                    }
                    AnimatedContent(tab,modifier=Modifier.weight(1f),transitionSpec={ (fadeIn(tween(ConversationTokens.revealMs))+slideInHorizontally { it/18 }) togetherWith fadeOut(tween(ConversationTokens.countMs)) },label="workoutConversationTab") { mode ->
                        val scroll=key(mode) { androidx.compose.foundation.lazy.rememberLazyListState() }
                        val onVisible by rememberUpdatedState(visible)
                        LaunchedEffect(scroll,mode,content.comments) {
                            if(mode==0) snapshotFlow { scroll.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.filter { id -> content.comments.any { it.id==id } } }
                                .distinctUntilChanged().collect { onVisible(it) }
                        }
                        LazyColumn(Modifier.fillMaxSize(),state=scroll,verticalArrangement=Arrangement.spacedBy(ConversationTokens.gap),contentPadding=PaddingValues(bottom=ConversationTokens.gap)) {
                            item(key="workout") {
                                Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
                                    Text(workoutStory(content.post).title,fontSize=ConversationTokens.title,fontWeight=FontWeight.SemiBold)
                                    if(mode==0) {
                                        WorkoutPerformance(workoutStory(content.post))
                                        CircleReactions(content.post.social,action,react)
                                    }
                                }
                            }
                            if(mode==0) {
                                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    Text("${content.post.social.comments} comentário${if(content.post.social.comments==1) "" else "s"}",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                                    if(state.loading) CircularProgressIndicator(Modifier.size(ConversationTokens.icon),color=TextoPrimario,strokeWidth=ConversationTokens.line)
                                } }
                                if(content.comments.isEmpty()) item { Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
                                    Icon(Icons.Outlined.Forum,null,tint=TextoSecundario)
                                    Text("O treino abriu a conversa. Seja o primeiro a dar força.",fontSize=ConversationTokens.body,color=TextoSecundario)
                                } }
                                items(content.comments,key={ it.id }) { comment ->
                                    Column(Modifier.animateItem(),verticalArrangement=Arrangement.spacedBy(ConversationTokens.tiny)) {
                                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(if(comment.uid==uid) "${comment.nickname} · você" else comment.nickname,fontSize=ConversationTokens.body,fontWeight=FontWeight.SemiBold)
                                                Text(conversationTime(comment.createdAt),color=TextoSecundario,fontSize=ConversationTokens.caption)
                                            }
                                            if(comment.uid==uid) IconButton(onClick={ deleting=comment },enabled=state.deleting==null && !state.sending && !state.loading) { Icon(Icons.Outlined.DeleteOutline,"Excluir meu comentário") }
                                        }
                                        Text(comment.body,fontSize=ConversationTokens.body)
                                        HorizontalDivider(Modifier.padding(top=ConversationTokens.compact),color=Borda)
                                    }
                                }
                                if(state.hasMore) item { TextButton(onClick=more,enabled=!state.loading) { Text("Comentários anteriores") } }
                            } else {
                                val rows=JSONObject(content.post.receipt).getJSONArray("exercises")
                                items(rows.length()) { i -> WorkoutSets(rows.getJSONObject(i)) }
                                item { Text("Cargas e repetições declaradas pelo participante.",color=TextoSecundario,fontSize=ConversationTokens.caption) }
                                if(withdraw!=null) item { TextButton(onClick={ withdrawing=true },enabled=!withdrawBusy) { Text("Retirar meu treino do círculo") } }
                            }
                        }
                    }
                    state.error?.let { Text(it,fontSize=ConversationTokens.caption,color=TextoSecundario,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
                    AnimatedVisibility(state.confirmation!=null) { Text(state.confirmation.orEmpty(),fontSize=ConversationTokens.caption,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
                    if(tab==0) ConversationComposer(state.draft,state.sending,state.loading || state.deleting!=null,draft,send)
                }
            }
        }
    }
    deleting?.let { comment -> AlertDialog(onDismissRequest={ deleting=null },title={ Text("Excluir seu comentário?") },
        text={ Text("Ele deixará de aparecer nesta conversa.") },confirmButton={ TextButton(onClick={ deleting=null; delete(comment) }) { Text("Excluir") } },dismissButton={ TextButton(onClick={ deleting=null }) { Text("Voltar") } }) }
    if(withdrawing && withdraw!=null) AlertDialog(onDismissRequest={ withdrawing=false },title={ Text("Retirar este compartilhamento?") },
        text={ Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
            Text("O treino e a conversa deixarão de aparecer no círculo. Seu histórico pessoal permanece. Este resumo não poderá ser republicado.")
            withdrawError?.let { Text(it,color=TextoSecundario) }
        } },confirmButton={ TextButton(onClick=withdraw,enabled=!withdrawBusy) { Text(if(withdrawBusy) "Retirando…" else "Retirar") } },dismissButton={ TextButton(onClick={ withdrawing=false }) { Text("Voltar") } })
}
@Composable private fun WorkoutSets(e:JSONObject) {
    Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
        Text(e.getString("name"),fontWeight=FontWeight.SemiBold,fontSize=ConversationTokens.body)
        val sets=e.getJSONArray("sets")
        for(j in 0 until sets.length()) {
            val s=sets.getJSONObject(j)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${j+1}",Modifier.width(ConversationTokens.avatar),color=TextoSecundario)
                Text(listOfNotNull(if(s.has("kg")) "${Format.peso(s.getDouble("kg").toFloat())} kg" else null,
                    if(s.has("reps")) "${s.getInt("reps")} rep" else null,if(s.has("seconds")) "${s.getInt("seconds")} s" else null).joinToString(" × "),Modifier.weight(1f),fontSize=ConversationTokens.body)
                if(s.optBoolean("pr")) Icon(Icons.Outlined.EmojiEvents,"Recorde",Modifier.size(ConversationTokens.icon))
            }
        }
        HorizontalDivider(color=Borda)
    }
}
@Composable internal fun ConversationComposer(value:String,sending:Boolean,blocked:Boolean,change:(String)->Unit,send:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom=ConversationTokens.gap),verticalArrangement=Arrangement.spacedBy(ConversationTokens.tiny)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
            OutlinedTextField(value,change,Modifier.weight(1f),placeholder={ Text("Dê força. Pergunte. Celebre.",fontSize=ConversationTokens.body) },
                label={ Text("Seu comentário") },maxLines=4,shape=RoundedCornerShape(ConversationTokens.radius))
            FilledIconButton(onClick=send,enabled=value.trim().isNotEmpty() && !sending && !blocked,modifier=Modifier.size(ConversationTokens.touch)) {
                if(sending) CircularProgressIndicator(Modifier.size(ConversationTokens.icon),strokeWidth=ConversationTokens.line)
                else Icon(Icons.AutoMirrored.Outlined.Send,"Publicar comentário")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(if(sending) "Confirmando envio…" else "Só membros deste círculo",Modifier.weight(1f),fontSize=ConversationTokens.caption,color=TextoSecundario)
            if(value.length>640) Text("${value.length}/800",fontSize=ConversationTokens.caption,color=TextoSecundario)
        }
    }
}
@Composable private fun ConversationSkeleton() {
    Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.gap)) {
        Text("Abrindo conversa…",fontSize=ConversationTokens.caption,color=TextoSecundario)
        repeat(3) { Box(Modifier.fillMaxWidth(if(it==1) .7f else 1f).height(ConversationTokens.touch)
            .clip(RoundedCornerShape(ConversationTokens.radius)).background(Superficie)) }
    }
}
@Composable internal fun CircleConversationNotices(notices:List<CircleCommentNotice>,open:(CircleCommentNotice)->Unit) {
    if(notices.isEmpty()) return
    Column(verticalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ConversationTokens.compact)) {
            Icon(Icons.Outlined.MarkChatUnread,null,tint=TextoPrimario)
            Text("Conversas esperando você",fontWeight=FontWeight.SemiBold,fontSize=ConversationTokens.body)
        }
        notices.groupBy { it.post }.values.take(3).forEach { rows -> val notice=rows.first()
            TextButton(onClick={ open(notice) },modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(notice.title,fontSize=ConversationTokens.body,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("${notice.nickname}${if(rows.size>1) " e mais ${rows.size-1}" else ""} comentou",fontSize=ConversationTokens.caption,color=TextoSecundario)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
            }
        }
        if(notices.map { it.post }.distinct().size>3) Text("Outras conversas estão na atividade dos seus Círculos.",fontSize=ConversationTokens.caption,color=TextoSecundario)
    }
}

@Composable internal fun CircleActivityInvitation(hasCircle:Boolean,enabled:Boolean,action:()->Unit) {
    val reveal=remember { Animatable(0f) }
    LaunchedEffect(hasCircle) { reveal.snapTo(0f); reveal.animateTo(1f,tween(420,easing=FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(ConversationTokens.gap)) {
        Row(Modifier.fillMaxWidth().height(ConversationTokens.touch),verticalAlignment=Alignment.CenterVertically) {
            val icons=listOf(Icons.Outlined.FitnessCenter,Icons.Outlined.Groups,Icons.Outlined.ChatBubbleOutline)
            icons.forEachIndexed { index,icon ->
                Box(Modifier.size(ConversationTokens.touch).clip(CircleShape).background(Superficie),contentAlignment=Alignment.Center) {
                    Icon(icon,null,Modifier.size(ConversationTokens.icon).graphicsLayer { alpha=reveal.value; translationY=(1f-reveal.value)*ConversationTokens.compact.toPx() },tint=TextoPrimario)
                }
                if(index<2) Canvas(Modifier.weight(1f).height(ConversationTokens.touch)) {
                    drawLine(Borda,Offset(0f,center.y),Offset(size.width,center.y),strokeWidth=ConversationTokens.line.toPx())
                    drawLine(TextoPrimario,Offset(0f,center.y),Offset(size.width*reveal.value,center.y),strokeWidth=ConversationTokens.line.toPx())
                }
            }
        }
        Text(if(hasCircle) "Seu próximo treino abre a conversa." else "A força tem companhia.",fontSize=ConversationTokens.title,fontWeight=FontWeight.SemiBold)
        Text(if(hasCircle) "Conclua uma sessão e compartilhe o resumo aqui. Seus amigos poderão ver as séries, dar força e conversar sobre suas marcas."
            else "Reúna seus aliados num Círculo. Compartilhe os treinos realizados, celebre os recordes e acompanhe a constância de vocês.",fontSize=ConversationTokens.body,color=TextoSecundario)
        Button(onClick=action,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=ConversationTokens.touch),shape=RoundedCornerShape(ConversationTokens.radius)) {
            Text(if(hasCircle) "Ir para meus treinos" else "Criar Círculo privado",Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.size(ConversationTokens.icon))
        }
        Text("Você escolhe o que compartilhar. Fotos do shape permanecem fora do Círculo.",fontSize=ConversationTokens.caption,color=TextoSecundario)
    }
}
