package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.data.sync.*
import com.liferpg.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CircleOutboxTest {
    private lateinit var db: AppDatabase
    private lateinit var queue: CircleOutbox
    private val session="be5bf14a-e8d4-4f2d-a886-3e2c3dd0efb4"
    private val exercise=Exercicio("bench","Supino",Musculo.PEITO,emptyList(),"Barra",LocalTreino.ACADEMIA,
        ModoMedida.CARGA,1f,"",emptyList(),"",null,emptyList(),"")
    private fun workout() = EstadoSessao("Peito",listOf(ItemSessao(ItemTreino(exercise,3,8,12,90,""),
        listOf(SerieFeita(80f,10,null,true))),ItemSessao(ItemTreino(exercise.copy(id="fly"),3,8,12,90,""))),
        iniciadaEm=1000,sessionUuid=session)
    @Before fun before() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).allowMainThreadQueries().build()
        queue=CircleOutbox(db)
    }
    @After fun after() = db.close()
    private suspend fun seed() {
        db.dao().salvarSessao(TreinoSessao(epochDay=20000,nome="Peito",xpGanho=20,series=1,exercicios=1,recordes=1,
            duracaoSeg=600,encerradaEm=601000,sessionUuid=session,receiptJson=CircleCodec.receipt(workout(),601000)))
    }
    @Test fun receiptContainsActualSetsAndPreservesIdentityAcrossResume() {
        val state=workout(); val restored=state.toActiveSnapshot().resolve(listOf(exercise,exercise.copy(id="fly")))!!
        assertEquals(session,restored.sessionUuid)
        val json=JSONObject(CircleCodec.receipt(restored,601000))
        assertEquals(setOf("title","ended_at","duration_seconds","exercises"),json.keys().asSequence().toSet())
        val rows=json.getJSONArray("exercises"); assertEquals(1,rows.length())
        val set=rows.getJSONObject(0).getJSONArray("sets").getJSONObject(0)
        assertEquals(80.0,set.getDouble("kg"),.001); assertEquals(10,set.getInt("reps")); assertTrue(set.getBoolean("pr"))
        assertFalse(set.has("seconds"))
    }
    @Test fun repeatedFinalizationAndSharingCreateOneSessionAndOneIntent() = runTest {
        seed(); seed(); assertEquals(1,db.dao().totalSessoes().first())
        queue.share("a","circle",session); queue.share("a","circle",session)
        assertEquals(1,db.dao().pendingCircleDeliveries("a").size)
        assertTrue(db.dao().pendingCircleDeliveries("b").isEmpty())
    }
    @Test fun privateFieldsAndMalformedRestoredReceiptCannotReachTheQueue() = runTest {
        val valid=CircleCodec.receipt(workout(),601000)
        assertThrows(IllegalArgumentException::class.java) { CircleCodec.validateReceipt(JSONObject(valid).put("body_photo","private-file").toString()) }
        db.dao().salvarSessao(TreinoSessao(epochDay=20000,nome="Peito",xpGanho=20,series=1,exercicios=1,recordes=0,duracaoSeg=10,
            encerradaEm=1000,sessionUuid=session,receiptJson="not-json"))
        assertTrue(runCatching { queue.share("a","circle",session) }.isFailure)
        assertTrue(db.dao().pendingCircleDeliveries("a").isEmpty())
    }
    @Test fun offlineIntentSurvivesNewProcessorAndRetriesWithoutDuplicates() = runTest {
        seed(); queue.share("a","circle",session); var calls=0
        val service=object: SyncService by LocalSyncService() {
            override suspend fun enviarReciboCirculo(id:String,session:String,receipt:String,actor:String):Result<Unit> {
                calls++; return if(calls==1) Result.failure(CircleRequestFailure("offline",true)) else Result.success(Unit)
            }
        }
        assertTrue(queue.drain("a",{ "a" },service)); assertEquals("pending",db.dao().circleDeliveries("a").first().single().status)
        assertFalse(CircleOutbox(db).drain("a",{ "a" },service)); assertEquals("sent",db.dao().circleDeliveries("a").first().single().status)
        queue.drain("a",{ "a" },service); assertEquals(2,calls)
    }
    @Test fun changedAccountNeverSendsAnotherPlayersIntent() = runTest {
        seed(); queue.share("a","circle",session); var calls=0
        val service=object: SyncService by LocalSyncService() {
            override suspend fun enviarReciboCirculo(id:String,session:String,receipt:String,actor:String):Result<Unit> { calls++; return Result.success(Unit) }
        }
        queue.drain("a",{ "b" },service); assertEquals(0,calls)
        assertEquals("pending",db.dao().circleDeliveries("a").first().single().status)
    }
    @Test fun withdrawalDuringPublishCannotBeOverwrittenByLateConfirmation() = runTest {
        seed(); queue.share("a","circle",session)
        val service=object: SyncService by LocalSyncService() {
            override suspend fun enviarReciboCirculo(id:String,session:String,receipt:String,actor:String):Result<Unit> {
                queue.withdraw(actor,id,session); return Result.success(Unit)
            }
            override suspend fun retirarReciboCirculo(id:String,session:String,actor:String) = Result.success(Unit)
        }
        assertTrue(queue.drain("a",{ "a" },service))
        val row=db.dao().circleDeliveries("a").first().single(); assertEquals("DELETE",row.action); assertEquals("pending",row.status)
        assertFalse(queue.drain("a",{ "a" },service)); assertEquals("withdrawn",db.dao().circleDeliveries("a").first().single().status)
    }
    @Test fun permanentErrorsRequireExplicitRetryAndPortableBackupNeverSharesAgain() = runTest {
        seed(); queue.share("a","circle",session)
        val service=object: SyncService by LocalSyncService() {
            override suspend fun enviarReciboCirculo(id:String,session:String,receipt:String,actor:String) = Result.failure<Unit>(CircleRequestFailure("membership",false))
        }
        assertFalse(queue.drain("a",{ "a" },service)); assertEquals("failed",db.dao().circleDeliveries("a").first().single().status)
        db.dao().retryCircleDelivery(CircleOutbox.key("a","circle",session),"b")
        assertTrue(db.dao().pendingCircleDeliveries("a").isEmpty())
        db.dao().retryCircleDelivery(CircleOutbox.key("a","circle",session),"a")
        assertEquals(1,db.dao().pendingCircleDeliveries("a").size)
        val repo=AppRepository(db,CatalogoRepo(ApplicationProvider.getApplicationContext()))
        val backup=repo.exportarBackupJson(); assertFalse(backup.contains("circle_delivery")); assertFalse(backup.contains("pending"))
        repo.restaurarBackupJson(backup)
        assertEquals(session,db.dao().sessaoPorUuid(session)!!.sessionUuid)
        assertTrue(db.dao().circleDeliveries("a").first().isEmpty())
    }
    @Test fun equivalentFractionsTieAndPendingMembersNeverEnterBoard() {
        val circle=TrainingCircle("c","a","Grupo","UTC","accepted","2026-10-05",listOf(
            CircleMember("b","B","accepted",6,2),CircleMember("a","A","accepted",3,1),CircleMember("x","X","pending",1,7)))
        assertEquals(listOf("a","b"),circle.board.map { it.uid })
        assertEquals(listOf(1,1),circle.board.map(circle::position))
        val restored=CircleCodec.circles(CircleCodec.cache(listOf(circle))).single()
        assertEquals(circle,restored)
    }
}
