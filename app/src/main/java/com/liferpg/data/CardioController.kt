package com.liferpg.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.liferpg.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CardioController(private val scope: CoroutineScope, private val repo: AppRepository,
    private val clock: () -> CardioClock, private val tracking: CardioTracking? = null, private val rewarded: suspend (ResultadoSerie) -> Unit = {}) {
    private val _active = MutableStateFlow<CardioSession?>(null)
    val active = _active.asStateFlow()
    val history = repo.cardioHistory.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableStateFlow<CardioSession?>(null)
    val saved = _saved.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    init {
        scope.launch {
            // Load from Room before offering a new session; recover reboot as paused.
            while (true) {
                try {
                    repo.activeCardio.first()?.let {
                        if (it.source == "GPS" && tracking?.running(it.id) != true) repo.freezeInterruptedCardio(it.id, clock())
                        else repo.updateCardio(it.id, clock())
                    }
                    repo.activeCardio.collect { if (!_ready.value) _error.value = null; _active.value = it; _ready.value = true }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { _ready.value = false; _error.value = "Não consegui abrir seu cardio. Tentando novamente…"; delay(3000) }
            }
        }
        scope.launch {
            while (true) {
                delay(15_000)
                active.value?.takeIf { it.status == "RUNNING" }?.let { row ->
                    try {
                        if (row.source == "GPS" && tracking?.running(row.id) != true) repo.freezeInterruptedCardio(row.id, clock())
                        else repo.updateCardio(row.id, clock())
                    }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { _error.value = "Não consegui atualizar o cronômetro. Sua sessão foi mantida." }
                }
            }
        }
    }
    fun now() = clock()
    fun clearError() { _error.value = null }
    fun dismissSaved() { _saved.value = null }
    private fun act(action: suspend () -> Unit) {
        if (!_ready.value || !_busy.compareAndSet(false, true)) return
        _error.value = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "Não consegui salvar. Sua sessão foi mantida." }
            finally { _busy.value = false }
        }
    }
    fun start(activity: CardioActivity, goal: CardioGoal, target: Double) = act { repo.startCardio(activity, goal, target, clock()) }
    fun startGps(activity: CardioActivity, goal: CardioGoal, target: Double) = act {
        check(tracking?.available() == true) { "Ative o GPS e autorize localização precisa." }
        repo.startCardio(activity, goal, target, clock(), true)
        repo.activeCardio.first()?.let { launchTracking(it.id) }
    }
    private suspend fun launchTracking(id: String) {
        try { checkNotNull(tracking).start(id) }
        catch (e: Exception) { repo.updateCardio(id, clock(), true); throw e }
    }
    fun pause() { active.value?.let { row -> act { repo.updateCardio(row.id, clock(), true); if (row.source == "GPS") tracking?.stop() } } }
    fun resume() { active.value?.let { row -> act {
        if (row.source == "GPS") check(tracking?.available() == true) { "Ative o GPS e autorize localização precisa para retomar." }
        repo.updateCardio(row.id, clock(), false)
        if (row.source == "GPS") launchTracking(row.id)
    } } }
    fun discard() { active.value?.let { row -> act { repo.discardCardio(row.id); if (row.source == "GPS") tracking?.stop() } } }
    fun finish(id: String, distance: Double?, effort: String, note: String, manual: CardioSession? = null) = act {
        val result = repo.finishCardio(id, clock(), distance, effort, note, manual)
        if (active.value?.source == "GPS") tracking?.stop()
        _saved.value = repo.cardioHistory.first().firstOrNull { it.id == id }
        if (result != null) rewarded(result)
    }
}

fun cardioClock(context: Context): CardioClock = CardioClock(SystemClock.elapsedRealtime(),
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0), System.currentTimeMillis(),
    java.security.MessageDigest.getInstance("SHA-256").digest(
        (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unavailable").toByteArray())
        .joinToString("") { "%02x".format(it) })
