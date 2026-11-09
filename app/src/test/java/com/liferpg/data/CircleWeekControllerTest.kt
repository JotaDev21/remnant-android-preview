package com.liferpg.data

import com.liferpg.data.sync.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CircleWeekControllerTest {
    private val monday=LocalDate.of(2026,10,12)
    private fun account(uid:String)=Sessao("test","test",Long.MAX_VALUE,uid,null,false)
    private fun week(circle:String="c",start:LocalDate=monday)=CircleWeek(circle,start,monday.minusWeeks(2),monday,monday,
        "UTC",listOf(CircleWeekMember("a","jota",3,emptySet(),0,0,0,0)))
    @Test fun switchedWeekHasNoOldCountsAndFailedRequestCanReturnToCurrentWeek()=runTest {
        val account=MutableStateFlow<Sessao?>(account("a")); var fail=true
        val service=object:SyncService by LocalSyncService() {
            override suspend fun placarCirculo(id:String,week:String?)=if(week!=null && fail) Result.failure(CircleRequestFailure("Offline",true))
                else Result.success(week(id,week?.let(LocalDate::parse)?:monday))
        }
        val controller=CircleWeekController(backgroundScope,account,service); runCurrent()
        controller.open("c"); runCurrent(); assertNotNull(controller.state.value.content)
        controller.select(monday.minusWeeks(1)); assertNull(controller.state.value.content); runCurrent()
        assertEquals("Offline",controller.state.value.error); assertNull(controller.state.value.content)
        controller.select(null); runCurrent(); assertEquals(monday,controller.state.value.content?.start)
        fail=false; controller.select(monday.minusWeeks(1)); runCurrent(); assertEquals(monday.minusWeeks(1),controller.state.value.content?.start)
    }
    @Test fun lateUncancellableResponseCannotPopulateAChangedAccount()=runTest {
        val account=MutableStateFlow<Sessao?>(account("a")); val response=CompletableDeferred<Result<CircleWeek>>()
        val service=object:SyncService by LocalSyncService() {
            override suspend fun placarCirculo(id:String,week:String?)=withContext(NonCancellable) { response.await() }
        }
        val controller=CircleWeekController(backgroundScope,account,service); runCurrent(); controller.open("c"); runCurrent()
        account.value=account("b"); runCurrent(); response.complete(Result.success(week())); runCurrent()
        assertEquals("b",controller.state.value.accountId); assertNull(controller.state.value.circle); assertNull(controller.state.value.content)
    }
    @Test fun forbiddenRefreshClearsPreviouslyVisibleMembers()=runTest {
        val account=MutableStateFlow<Sessao?>(account("a")); var forbidden=false
        val service=object:SyncService by LocalSyncService() {
            override suspend fun placarCirculo(id:String,week:String?)=if(forbidden) Result.failure(CircleRequestFailure("Acesso encerrado",false,true)) else Result.success(week())
        }
        val controller=CircleWeekController(backgroundScope,account,service); runCurrent(); controller.open("c"); runCurrent()
        assertNotNull(controller.state.value.content); forbidden=true; controller.refresh(); runCurrent()
        assertNull(controller.state.value.content); assertEquals("Acesso encerrado",controller.state.value.error)
    }
    @Test fun serverCannotChangeTheSelectedCircleOrWeekAndFutureSelectionDoesNothing()=runTest {
        val account=MutableStateFlow<Sessao?>(account("a")); var wrong=false; var calls=0
        val service=object:SyncService by LocalSyncService() {
            override suspend fun placarCirculo(id:String,week:String?):Result<CircleWeek> { calls++; return Result.success(week(if(wrong) "other" else id)) }
        }
        val controller=CircleWeekController(backgroundScope,account,service); runCurrent(); controller.open("c"); runCurrent()
        controller.select(monday.plusWeeks(1)); runCurrent(); assertEquals(1,calls)
        wrong=true; controller.select(monday.minusWeeks(1)); runCurrent()
        assertNull(controller.state.value.content); assertTrue(controller.state.value.error!!.contains("outro período"))
    }
}
