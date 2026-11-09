package com.liferpg.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.liferpg.data.sync.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CircleConversationControllerTest {
    @Test fun lateBackgroundFetchDoesNotUndoAReactionAlreadyConfirmed()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val delayed=CompletableDeferred<Result<CircleConversation>>(); var reads=0
        val remote=object:SyncService by service() {
            override suspend fun conversaCirculo(circle:String,post:String,before:CircleComment?):Result<CircleConversation> {
                reads++; return if(reads==1) Result.success(CircleConversation(this@CircleConversationControllerTest.post,emptyList())) else delayed.await()
            }
            override suspend fun reagirCirculo(post:String,kind:CircleReaction?)=Result.success(CircleSocial(fire=1,mine=kind))
        }
        val controller=CircleConversationController(backgroundScope,account,remote,drafts())
        runCurrent(); controller.open("circle","p"); runCurrent(); controller.refresh(quiet=true); runCurrent()
        controller.react(post,CircleReaction.FIRE); runCurrent(); assertEquals(CircleReaction.FIRE,controller.state.value.content?.post?.social?.mine)
        delayed.complete(Result.success(CircleConversation(post.copy(social=CircleSocial(comments=1)),listOf(comment)))); runCurrent()
        assertEquals(CircleReaction.FIRE,controller.state.value.content?.post?.social?.mine)
        assertEquals(1,controller.state.value.content?.post?.social?.fire); assertEquals(1,controller.state.value.content?.post?.social?.comments)
    }
    private fun session(uid:String)=Sessao("test","test",Long.MAX_VALUE,uid,null,false)
    private fun drafts():CircleCommentDrafts {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("circle_comment_drafts",Context.MODE_PRIVATE).edit().clear().commit()
        return CircleCommentDrafts(context)
    }
    private val post=CirclePost("p","s","owner","Aliado","2026-10-08T17:00:00Z","{}")
    private val comment=CircleComment("id","owner","Aliado","Muito bom!","2026-10-08T17:00:00Z")
    private fun service()=object:SyncService by LocalSyncService() {
        override suspend fun conversaCirculo(circle:String,post:String,before:CircleComment?)=Result.success(CircleConversation(this@CircleConversationControllerTest.post,emptyList()))
    }
    @Test fun lateResponseCannotPopulateAnotherAccountOrReopenedSheet()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val response=CompletableDeferred<Result<CircleConversation>>()
        val remote=object:SyncService by service() { override suspend fun conversaCirculo(circle:String,post:String,before:CircleComment?)=response.await() }
        val controller=CircleConversationController(backgroundScope,account,remote,drafts())
        runCurrent(); controller.open("circle","p"); runCurrent(); controller.draft("rascunho da conta a")
        account.value=session("b"); runCurrent(); response.complete(Result.success(CircleConversation(post,listOf(comment)))); runCurrent()
        assertNull(controller.state.value.target); assertNull(controller.state.value.content); assertEquals("",controller.state.value.draft)
        controller.open("circle","p"); runCurrent(); assertEquals("",controller.state.value.draft)
        account.value=session("a"); runCurrent(); controller.open("circle","p"); runCurrent()
        assertEquals("rascunho da conta a",controller.state.value.draft)
    }
    @Test fun unknownDeliveryRetriesWithSamePersistentIdAndNoAutomaticSending()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val store=drafts(); val ids=mutableListOf<String>(); var succeed=false
        val remote=object:SyncService by service() {
            override suspend fun comentarCirculo(post:String,id:String,body:String):Result<Unit> {
                ids+=id; return if(succeed) Result.success(Unit) else Result.failure(CircleRequestFailure("Sem conexão",true))
            }
        }
        val first=CircleConversationController(backgroundScope,account,remote,store)
        runCurrent(); first.open("circle","p"); runCurrent(); first.draft("  Marca incrível! 💪  "); first.send(); first.send(); runCurrent()
        assertEquals(1,ids.size); assertEquals("  Marca incrível! 💪  ",first.state.value.draft)
        first.close(); val second=CircleConversationController(backgroundScope,account,remote,store)
        runCurrent(); second.open("circle","p"); runCurrent(); assertEquals(1,ids.size)
        succeed=true; second.send(); runCurrent()
        assertEquals(2,ids.size); assertEquals(ids[0],ids[1]); assertEquals("",second.state.value.draft)
        assertEquals("Comentário publicado",second.state.value.confirmation)
    }
    @Test fun successfulSendDoesNotEraseNewTextTypedWhileRequestWasRunning()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val response=CompletableDeferred<Result<Unit>>()
        val remote=object:SyncService by service() { override suspend fun comentarCirculo(post:String,id:String,body:String)=response.await() }
        val store=drafts(); val controller=CircleConversationController(backgroundScope,account,remote,store)
        runCurrent(); controller.open("circle","p"); runCurrent(); controller.draft("Primeiro"); controller.send(); runCurrent()
        controller.draft("Segundo comentário"); response.complete(Result.success(Unit)); runCurrent()
        assertEquals("Segundo comentário",controller.state.value.draft); assertEquals("Segundo comentário",store.text("a","p"))
    }
    @Test fun onlyDeliveredVisibleIdsAreAcknowledgedAndAccessRevocationClearsContent()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val marked=mutableListOf<List<String>>(); var forbidden=false
        val remote=object:SyncService by service() {
            override suspend fun conversaCirculo(circle:String,post:String,before:CircleComment?)=if(forbidden) Result.failure(CircleRequestFailure("Acesso encerrado",false,true))
                else Result.success(CircleConversation(this@CircleConversationControllerTest.post,listOf(comment)))
            override suspend fun lerComentariosCirculo(post:String,ids:List<String>):Result<Unit> { marked+=ids; return Result.success(Unit) }
        }
        val controller=CircleConversationController(backgroundScope,account,remote,drafts())
        runCurrent(); controller.open("circle","p"); runCurrent(); assertTrue(marked.isEmpty())
        controller.acknowledgeVisible(listOf("unknown")); runCurrent(); assertTrue(marked.isEmpty())
        controller.acknowledgeVisible(listOf("id","unknown","id")); runCurrent(); assertEquals(listOf(listOf("id")),marked)
        forbidden=true; controller.refresh(); runCurrent(); assertNull(controller.state.value.content)
    }
    @Test fun failedReactionKeepsConfirmedCountAndAllowsExplicitRetryThenUndo()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val changes=mutableListOf<CircleSocial>(); var failure=true; val kinds=mutableListOf<CircleReaction?>()
        val remote=object:SyncService by service() { override suspend fun reagirCirculo(post:String,kind:CircleReaction?):Result<CircleSocial> {
            kinds+=kind; return if(failure) Result.failure(CircleRequestFailure("offline",true)) else Result.success(CircleSocial(respect=if(kind==null) 0 else 1,mine=kind))
        } }
        val controller=CircleConversationController(backgroundScope,account,remote,drafts(),changed={ _,social -> changes+=social })
        runCurrent(); controller.react(post,CircleReaction.RESPECT); controller.react(post,CircleReaction.RESPECT); runCurrent()
        assertEquals(1,kinds.size); assertTrue(changes.isEmpty()); assertNotNull(controller.reactions.value["p"]?.error)
        failure=false; controller.react(post,CircleReaction.RESPECT); runCurrent(); assertEquals(1,changes.last().respect)
        controller.react(post.copy(social=changes.last()),CircleReaction.RESPECT); runCurrent(); assertNull(kinds.last()); assertEquals(0,changes.last().respect)
    }
    @Test fun changingBodyAfterFailedSubmissionUsesNewIdAndForeignDeleteNeverCallsServer()=runTest {
        val account=MutableStateFlow<Sessao?>(session("a")); val ids=mutableListOf<String>(); var deletes=0
        val remote=object:SyncService by service() {
            override suspend fun comentarCirculo(post:String,id:String,body:String):Result<Unit> { ids+=id; return Result.failure(CircleRequestFailure("offline",true)) }
            override suspend fun excluirComentarioCirculo(id:String):Result<Unit> { deletes++; return Result.success(Unit) }
        }
        val controller=CircleConversationController(backgroundScope,account,remote,drafts())
        runCurrent(); controller.open("circle","p"); runCurrent(); controller.draft("Original"); controller.send(); runCurrent()
        controller.draft("Corrigido"); controller.send(); runCurrent(); assertNotEquals(ids[0],ids[1])
        controller.delete(comment); runCurrent(); assertEquals(0,deletes)
    }
}
