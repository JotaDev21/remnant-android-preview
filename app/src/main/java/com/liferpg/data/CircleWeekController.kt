package com.liferpg.data

import com.liferpg.data.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class CircleWeekState(val accountId: String? = null, val circle: String? = null,
    val requested: LocalDate? = null, val content: CircleWeek? = null, val loading: Boolean = false,
    val error: String? = null)

/** Read-only; account, circle and week are pinned for every response. No feed-page inference. */
class CircleWeekController(private val scope: CoroutineScope, private val account: StateFlow<Sessao?>,
    private val service: SyncService) {
    private fun uid() = account.value?.takeUnless { it.anonimo }?.userId
    private val mutable = MutableStateFlow(CircleWeekState(accountId = uid()))
    val state = mutable.asStateFlow()
    private var fetch: Job? = null
    private var generation = 0

    init { scope.launch { account.map { it?.takeUnless(Sessao::anonimo)?.userId }.distinctUntilChanged().collect { actor ->
        if (mutable.value.accountId != actor) { fetch?.cancel(); generation++; mutable.value = CircleWeekState(accountId = actor) }
    } } }

    fun open(circle: String) {
        val actor = uid() ?: return
        fetch?.cancel(); generation++
        mutable.value = CircleWeekState(accountId = actor, circle = circle)
        refresh()
    }
    fun close() { fetch?.cancel(); generation++; mutable.value = CircleWeekState(accountId = uid()) }
    fun select(week: LocalDate?) {
        val old = mutable.value
        if (old.circle == null) return
        val content = old.content
        if (week != null && (content == null || week < content.first || week > content.current || week.dayOfWeek != java.time.DayOfWeek.MONDAY)) return
        fetch?.cancel(); generation++
        mutable.value = old.copy(requested = week, content = null, loading = false, error = null)
        refresh()
    }
    fun refresh() {
        val actor = uid() ?: return
        val old = mutable.value; val circle = old.circle ?: return
        if (old.loading || old.accountId != actor) return
        val stamp = generation
        mutable.value = old.copy(loading = true, error = null)
        fetch = scope.launch {
            try {
                val result = service.placarCirculo(circle, old.requested?.toString()).getOrThrow()
                check(result.circleId == circle && (old.requested == null || old.requested == result.start)) { "O servidor retornou outro período. Tente atualizar." }
                if (uid() == actor && stamp == generation && mutable.value.circle == circle)
                    mutable.value = mutable.value.copy(content = result, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (uid() == actor && stamp == generation && mutable.value.circle == circle)
                    mutable.value = mutable.value.copy(loading = false, error = e.message ?: "Não consegui carregar o placar.",
                        content = if ((e as? CircleRequestFailure)?.accessDenied == true) null else mutable.value.content)
            }
        }
    }
}
