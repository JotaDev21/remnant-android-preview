package com.liferpg.data

import android.content.Context
import com.liferpg.data.sync.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId

data class CircleState(val circles: List<TrainingCircle> = emptyList(), val selected: String? = null, val cached: Boolean = false, val accountId: String? = null,
    val posts: List<CirclePost> = emptyList(), val busy: Boolean = false, val error: String? = null, val hasMore: Boolean = false,
    val notices:List<CircleCommentNotice> = emptyList(), val creating: Boolean = false)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class,kotlinx.coroutines.FlowPreview::class)
class CircleController(private val scope: CoroutineScope, private val context: Context,
    private val auth: SupabaseAuth, private val service: SyncService, private val db: AppDatabase, private val outbox: CircleOutbox) {
    private val _state = MutableStateFlow(CircleState())
    private val cache=context.getSharedPreferences("circle_roster",Context.MODE_PRIVATE)
    private var reloadRequested=false
    private val reactionRevisions=mutableMapOf<String,Long>()
    private var reactionRevision=0L
    val state = _state.asStateFlow()
    val weekBoard = CircleWeekController(scope, auth.sessao, service)
    val conversation=CircleConversationController(scope,auth.sessao,service,CircleCommentDrafts(context),
        changed={ post,social -> reactionRevisions[post]=++reactionRevision
            _state.value=_state.value.copy(posts=_state.value.posts.map { if(it.id==post) it.copy(social=social) else it }) },
        read={ ids ->
            val old=_state.value.notices; val remaining=old.filterNot { it.id in ids }
            _state.value=_state.value.copy(notices=remaining)
            old.filter { it.id in ids }.map { it.post }.distinct().filter { post -> remaining.none { it.post==post } }
                .forEach { post -> uid()?.let { com.liferpg.notif.Notificacoes.limparConversa(context,it,post) } }
        })
    val deliveries = auth.sessao.flatMapLatest { account -> db.dao().circleDeliveries(account?.userId.orEmpty()) }
        .stateIn(scope,SharingStarted.WhileSubscribed(5000),emptyList())
    init {
        scope.launch { auth.sessao.map { it?.takeUnless { s -> s.anonimo }?.userId }.distinctUntilChanged().collect { uid ->
            reloadRequested=false
            reactionRevisions.clear()
            val remembered=uid?.let { cache.getString(it,null) }?.let { runCatching { CircleCodec.circles(it) }.getOrNull() }.orEmpty()
            _state.value = CircleState(circles=remembered,selected=remembered.firstOrNull { it.status=="accepted" }?.id,cached=remembered.isNotEmpty(),accountId=uid)
            if (uid != null) { CircleOutbox.schedule(context,uid); load() }
        } }
        scope.launch { deliveries.map { rows -> rows.map { it.id to it.status } }.distinctUntilChanged().debounce(800).collect { load() } }
    }
    private fun uid() = auth.sessao.value?.takeUnless { it.anonimo }?.userId
    private fun operation(creating: Boolean = false, block: suspend (String) -> Unit) {
        val actor = uid() ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy=true,creating=creating,error=null)
        scope.launch {
            try { block(actor) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (uid()==actor) _state.value=_state.value.copy(error=e.message ?: "Não foi possível atualizar os círculos.") }
            finally { if (uid()==actor) {
                _state.value=_state.value.copy(busy=false,creating=false)
                if(reloadRequested) { reloadRequested=false; load() }
            } }
        }
    }
    private suspend fun refresh(actor: String) {
        val rows = service.circulos().getOrThrow()
        if (uid()!=actor) return
        cache.edit().putString(actor,CircleCodec.cache(rows)).apply()
        val selection = rows.firstOrNull { it.id==_state.value.selected && it.status=="accepted" }?.id
            ?: rows.firstOrNull { it.status=="accepted" }?.id
        if (weekBoard.state.value.circle != null && rows.none { it.id == weekBoard.state.value.circle && it.status == "accepted" }) weekBoard.close()
        val sameSelection=selection==_state.value.selected
        _state.value=_state.value.copy(circles=rows,selected=selection,cached=false,
            posts=if(sameSelection) _state.value.posts else emptyList(),hasMore=sameSelection && _state.value.hasMore)
        com.liferpg.notif.ArenaConvites.avisarCirculos(context,actor,rows)
        service.avisosCirculo().getOrNull()?.let { notices -> if(uid()==actor) {
            _state.value=_state.value.copy(notices=notices)
            com.liferpg.notif.ArenaConvites.avisarComentarios(context,actor,notices)
        } }
        if (selection != null) {
            val before=HashMap(reactionRevisions)
            val posts=service.recibosCirculo(selection).getOrThrow()
            if (uid()==actor && _state.value.selected==selection) {
                val resolved=posts.map { p ->
                    val current=_state.value.posts.firstOrNull { it.id==p.id }
                    if(current!=null && before[p.id]!=reactionRevisions[p.id]) p.copy(social=current.social.copy(comments=p.social.comments)) else p
                }
                _state.value=_state.value.copy(posts=resolved,hasMore=posts.size==20)
                reactionRevisions.keys.retainAll((resolved.map { it.id }+conversation.state.value.content?.post?.id).filterNotNull().toSet())
            }
        }
    }
    fun load() { if(_state.value.busy) reloadRequested=true else operation { refresh(it) } }
    fun select(id: String) = operation { actor ->
        if (_state.value.circles.none { it.id==id && it.status=="accepted" }) return@operation
        _state.value=_state.value.copy(selected=id,posts=emptyList(),hasMore=false)
        val posts=service.recibosCirculo(id).getOrThrow()
        if (uid()==actor) _state.value=_state.value.copy(posts=posts,hasMore=posts.size==20)
    }
    fun more() = operation { actor ->
        val selected=_state.value.selected ?: return@operation
        val cursor=_state.value.posts.lastOrNull() ?: return@operation
        val rows=service.recibosCirculo(selected,cursor).getOrThrow()
        if (uid()==actor) _state.value=_state.value.copy(posts=(_state.value.posts+rows).distinctBy { it.id },hasMore=rows.size==20)
    }
    fun create(name: String, goal: Int, done: () -> Unit) = operation(creating=true) { actor ->
        service.criarCirculo(name.trim(),goal,ZoneId.systemDefault().id).getOrThrow()
        if(uid()==actor) { done(); refresh(actor) }
    }
    fun invite(id: String, target: String, done: () -> Unit) = operation { actor ->
        service.convidarCirculo(id,target).getOrThrow()
        if(uid()==actor) { done(); refresh(actor) }
    }
    fun respond(id: String, accept: Boolean, goal: Int) = operation { actor ->
        service.responderCirculo(id,accept,goal).getOrThrow(); if(uid()==actor) refresh(actor)
    }
    fun leave(id: String, done: () -> Unit) = operation { actor ->
        service.sairCirculo(id).getOrThrow(); if(uid()==actor) { done(); refresh(actor) }
    }
    fun share(circle: String, session: String, done: () -> Unit) = operation { actor ->
        require(_state.value.circles.any { it.id==circle && it.status=="accepted" }) { "Aceite o convite antes de compartilhar." }
        outbox.share(actor,circle,session)
        if (uid()==actor) { CircleOutbox.schedule(context,actor); done() }
    }
    fun withdraw(circle: String, session: String, done: () -> Unit) = operation { actor ->
        outbox.withdraw(actor,circle,session)
        if (uid()==actor) { CircleOutbox.schedule(context,actor); done() }
    }
    fun retry(id: String) = operation { actor -> db.dao().retryCircleDelivery(id,actor); CircleOutbox.schedule(context,actor) }
}
