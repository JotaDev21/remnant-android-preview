package com.liferpg.data

import android.content.Context
import com.liferpg.data.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/** Local drafts are never sent automatically or included in portable backups. */
class CircleCommentDrafts(context:Context) {
    private val prefs=context.getSharedPreferences("circle_comment_drafts",Context.MODE_PRIVATE)
    private fun key(uid:String,post:String)="$uid/$post"
    fun text(uid:String,post:String)=prefs.getString(key(uid,post),"").orEmpty()
    fun save(uid:String,post:String,text:String) { prefs.edit().putString(key(uid,post),text.take(800)).apply() }
    fun submission(uid:String,post:String,body:String):String {
        val key=key(uid,post)
        if(prefs.getString("$key/body",null)==body) prefs.getString("$key/id",null)?.let { return it }
        val id=UUID.randomUUID().toString()
        if(!prefs.edit().putString("$key/id",id).putString("$key/body",body).commit()) {
            prefs.edit().remove("$key/id").remove("$key/body").apply()
            error("Não consegui guardar o comentário neste aparelho. Tente novamente.")
        }
        return id
    }
    fun confirmed(uid:String,post:String,body:String) {
        val key=key(uid,post)
        val editor=prefs.edit().remove("$key/id").remove("$key/body")
        if(text(uid,post).trim()==body) editor.remove(key)
        editor.apply()
    }
}
data class CircleConversationState(val accountId:String?=null,val target:CircleConversationTarget?=null,
    val content:CircleConversation?=null,val loading:Boolean=false,val sending:Boolean=false,val deleting:String?=null,
    val draft:String="",val error:String?=null,val hasMore:Boolean=false,val confirmation:String?=null)
data class CircleReactionAction(val pending:Boolean=false,val desired:CircleReaction?=null,val error:String?=null)

/** Requests pin their account and target. Slow responses never populate another conversation. */
class CircleConversationController(private val scope:CoroutineScope,private val account:StateFlow<Sessao?>,
    private val service:SyncService,private val drafts:CircleCommentDrafts,
    private val changed:(String,CircleSocial)->Unit={ _,_ -> },private val read:(List<String>)->Unit={}) {
    private val mutable=MutableStateFlow(CircleConversationState(accountId=account.value?.takeUnless { it.anonimo }?.userId))
    val state=mutable.asStateFlow()
    private val reactionState=MutableStateFlow<Map<String,CircleReactionAction>>(emptyMap())
    val reactions=reactionState.asStateFlow()
    private var fetch:Job?=null
    private var generation=0
    private var reactionGeneration=0
    private var socialRevision=0
    private var acknowledging=false
    init { scope.launch { account.map { it?.takeUnless(Sessao::anonimo)?.userId }.distinctUntilChanged().collect { uid ->
        if(mutable.value.accountId==uid) return@collect
        fetch?.cancel(); generation++; reactionGeneration++; acknowledging=false
        mutable.value=CircleConversationState(accountId=uid); reactionState.value=emptyMap()
    } } }
    private fun uid()=account.value?.takeUnless { it.anonimo }?.userId
    private fun same(actor:String,target:CircleConversationTarget,stamp:Int)=uid()==actor && mutable.value.target==target && generation==stamp
    fun open(circle:String,post:String) {
        val actor=uid()?:return
        fetch?.cancel(); generation++
        val target=CircleConversationTarget(circle,post)
        mutable.value=CircleConversationState(accountId=actor,target=target,draft=drafts.text(actor,post))
        refresh()
    }
    fun close() { fetch?.cancel(); generation++; mutable.value=CircleConversationState(accountId=uid()) }
    fun draft(value:String) {
        val actor=uid()?:return; val target=mutable.value.target?:return
        if(value.length>800) return
        drafts.save(actor,target.post,value)
        mutable.value=mutable.value.copy(draft=value,confirmation=null)
    }
    fun refresh(more:Boolean=false,quiet:Boolean=false) {
        val actor=uid()?:return; val old=mutable.value; val target=old.target?:return
        if(old.loading || old.sending || old.deleting!=null || (quiet && old.content?.comments.orEmpty().size>30)) return
        val before=if(more) old.content?.comments?.lastOrNull()?:return else null
        val stamp=generation
        val initialSocialRevision=socialRevision
        mutable.value=old.copy(loading=true,error=if(quiet) old.error else null)
        fetch=scope.launch {
            try {
                var result=service.conversaCirculo(target.circle,target.post,before).getOrThrow()
                if(same(actor,target,stamp)) {
                    if(socialRevision!=initialSocialRevision && mutable.value.content?.post?.id==result.post.id) {
                        val confirmed=mutable.value.content!!.post.social.copy(comments=result.post.social.comments)
                        result=result.copy(post=result.post.copy(social=confirmed))
                    }
                    val rows=if(more) (old.content!!.comments+result.comments).distinctBy { it.id } else result.comments
                    mutable.value=mutable.value.copy(content=result.copy(comments=rows),loading=false,hasMore=result.comments.size==30,error=null)
                    changed(target.post,result.post.social)
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { if(same(actor,target,stamp)) mutable.value=mutable.value.copy(loading=false,error=e.message?:"Não consegui abrir a conversa.",
                content=if((e as? CircleRequestFailure)?.accessDenied==true) null else mutable.value.content) }
        }
    }
    fun acknowledgeVisible(visibleIds:List<String>) {
        val actor=uid()?:return; val snapshot=mutable.value; val target=snapshot.target?:return
        val loaded=snapshot.content?.comments?.map { it.id }.orEmpty().toSet()
        val ids=visibleIds.filter { it in loaded }.distinct().take(30); if(ids.isEmpty() || acknowledging) return
        val stamp=generation; acknowledging=true
        scope.launch { try { service.lerComentariosCirculo(target.post,ids).getOrThrow()
            if(same(actor,target,stamp)) read(ids)
        } catch(e:CancellationException) { throw e } catch(_:Exception) { /* Unread notices remain visible until confirmed. */ }
        finally { if(generation==stamp) acknowledging=false } }
    }
    fun send() {
        val actor=uid()?:return; val snapshot=mutable.value; val target=snapshot.target?:return
        val body=snapshot.draft.trim()
        if(body.isEmpty() || body.length>800 || snapshot.sending || snapshot.loading || snapshot.deleting!=null || snapshot.content==null) return
        val id=runCatching { drafts.submission(actor,target.post,body) }.getOrElse {
            mutable.value=snapshot.copy(error=it.message); return
        }; val stamp=generation
        mutable.value=snapshot.copy(sending=true,error=null,confirmation=null)
        scope.launch {
            try {
                service.comentarCirculo(target.post,id,body).getOrThrow()
                // Confirmation belongs to the original account even if the sheet was closed.
                drafts.confirmed(actor,target.post,body)
                if(same(actor,target,stamp)) {
                    mutable.value=mutable.value.copy(sending=false,draft=drafts.text(actor,target.post),confirmation="Comentário publicado")
                    refresh()
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { if(same(actor,target,stamp)) mutable.value=mutable.value.copy(sending=false,error=e.message?:"Envio não confirmado. Seu rascunho permanece aqui.") }
        }
    }
    fun delete(comment:CircleComment) {
        val actor=uid()?:return; val snapshot=mutable.value; val target=snapshot.target?:return
        if(comment.uid!=actor || snapshot.sending || snapshot.loading || snapshot.deleting!=null) return
        val stamp=generation
        mutable.value=snapshot.copy(deleting=comment.id,error=null)
        scope.launch { try {
            service.excluirComentarioCirculo(comment.id).getOrThrow()
            if(same(actor,target,stamp)) { mutable.value=mutable.value.copy(deleting=null,confirmation="Comentário excluído"); refresh() }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { if(same(actor,target,stamp)) mutable.value=mutable.value.copy(deleting=null,error=e.message?:"Exclusão não confirmada.") } }
    }
    fun react(post:CirclePost,kind:CircleReaction) {
        val actor=uid()?:return
        if(reactionState.value[post.id]?.pending==true) return
        val desired=kind.takeUnless { it==post.social.mine }; val stamp=reactionGeneration
        reactionState.value=reactionState.value+(post.id to CircleReactionAction(true,desired))
        scope.launch { try {
            val social=service.reagirCirculo(post.id,desired).getOrThrow()
            if(uid()==actor && reactionGeneration==stamp) {
                socialRevision++
                changed(post.id,social)
                if(mutable.value.content?.post?.id==post.id) mutable.value=mutable.value.copy(content=mutable.value.content!!.let { it.copy(post=it.post.copy(social=social)) })
                reactionState.value=reactionState.value-post.id
            }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { if(uid()==actor && reactionGeneration==stamp) reactionState.value=reactionState.value+(post.id to CircleReactionAction(error=e.message?:"Reação não confirmada. Tente novamente.")) } }
    }
}
