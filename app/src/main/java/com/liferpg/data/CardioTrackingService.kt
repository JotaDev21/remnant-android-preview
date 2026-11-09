package com.liferpg.data

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.liferpg.Graph
import com.liferpg.MainActivity
import com.liferpg.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

data class CardioTrackingStatus(val id: String? = null, val signal: String = "STOPPED", val heartbeat: Long = 0, val stepSignal: String = "WAITING")
object CardioTrackingRuntime {
    private val state = MutableStateFlow(CardioTrackingStatus())
    val status = state.asStateFlow()
    fun update(value: CardioTrackingStatus) { state.value = value }
}

class CardioTracking(private val context: Context) {
    fun available(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
    fun start(id: String) {
        check(available()) { "Ative o GPS e autorize localização precisa para registrar seu percurso." }
        ContextCompat.startForegroundService(context, Intent(context, CardioTrackingService::class.java).putExtra("session", id))
    }
    fun stop() { context.stopService(Intent(context, CardioTrackingService::class.java)) }
    fun running(id: String): Boolean = CardioTrackingRuntime.status.value.let { it.id == id && it.signal != "STOPPED" }
}

/** User starts this from a visible screen. Never starts location at boot or asks for all-time access. */
class CardioTrackingService : Service(), LocationListener, SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var locations: LocationManager
    private lateinit var sensors: SensorManager
    private var id: String? = null
    private var activity = "WALK"
    private var counter = SessionStepCounter()
    private var subscribed = false
    private var lastGoodFix = 0L
    private var lastFixDelivery = 0L
    private var trackingStartedAt = 0L
    private var collection: Job? = null
    private var heartbeat: Job? = null
    private var accountWatch: Job? = null
    private var stepSignal = "WAITING"
    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java); sensors = getSystemService(SensorManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("cardio_tracking", "Cardio em andamento", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getStringExtra("session")
        if (requested == null) { stopSelf(); return START_NOT_STICKY }
        if (intent.getBooleanExtra("pause", false)) {
            scope.launch { Graph.repository.updateCardio(requested, cardioClock(this@CardioTrackingService), true); stopSelf() }
            return START_NOT_STICKY
        }
        val owner = Graph.auth.sessao.value?.takeIf { !it.anonimo }?.userId
        if (owner == null) { stopSelf(); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this, 730, notification("Buscando sinal GPS…"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        if (id == requested) return START_NOT_STICKY
        unsubscribe(); collection?.cancel(); heartbeat?.cancel(); id = requested; counter = SessionStepCounter()
        lastGoodFix = 0L; lastFixDelivery = 0L; stepSignal = "WAITING"
        trackingStartedAt = SystemClock.elapsedRealtime()
        accountWatch?.cancel()
        accountWatch = scope.launch {
            Graph.auth.sessao.collect { account ->
                if (account?.userId != owner || account.anonimo) {
                    Graph.repository.updateCardio(requested, cardioClock(this@CardioTrackingService), true); stopSelf()
                }
            }
        }
        CardioTrackingRuntime.update(CardioTrackingStatus(requested, "ACQUIRING", SystemClock.elapsedRealtime()))
        collection = scope.launch {
            try {
                var initialized = false
                Graph.repository.activeCardio.collect { row ->
                    if (row?.id != requested || row.status != "RUNNING" || row.source != "GPS") { stopSelf(); return@collect }
                    activity = row.activity
                    val clock = cardioClock(this@CardioTrackingService)
                    if (row.boot != clock.boot || row.device != clock.device) {
                        Graph.repository.freezeInterruptedCardio(requested, clock); stopSelf(); return@collect
                    }
                    if (!initialized) {
                        initialized = true
                        val valid = Graph.repository.recordCardioTracking(requested, cardioClock(this@CardioTrackingService)) {
                            it.copy(segment = it.segment + 1)
                        }
                        if (!valid) { stopSelf(); return@collect }
                        subscribe()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { interrupt("Não consegui continuar o percurso. Retome pelo app.") }
        }
        heartbeat = scope.launch {
            while (isActive) {
                delay(15_000)
                try {
                    Graph.repository.updateCardio(requested, cardioClock(this@CardioTrackingService))
                    val signal = if (lastGoodFix > 0 && SystemClock.elapsedRealtime() - lastGoodFix <= 30_000) "TRACKING" else "WEAK"
                    CardioTrackingRuntime.update(CardioTrackingStatus(requested, signal, SystemClock.elapsedRealtime(), stepSignal))
                    notifySafely(730, notification(if (signal == "TRACKING") "Percurso sendo registrado" else "Sinal fraco · aguardando posição precisa"))
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { interrupt("O registro foi interrompido. Seu percurso foi mantido."); break }
            }
        }
        return START_NOT_STICKY
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun subscribe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || !locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
            error("Localização precisa indisponível")
        locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000L, 0f, this, Looper.getMainLooper())
        subscribed = true
        val permitted = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSignal = when { activity == "BIKE" -> "NOT_APPLICABLE"; !permitted -> "PERMISSION"; sensor == null -> "UNAVAILABLE"
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) -> "READY"; else -> "UNAVAILABLE" }
        CardioTrackingRuntime.status.value.takeIf { it.id == id }?.let { CardioTrackingRuntime.update(it.copy(stepSignal = stepSignal)) }
    }
    private fun unsubscribe() {
        // Permission can be revoked while the session is running.
        try { if (subscribed) locations.removeUpdates(this) } catch (_: SecurityException) { }
        sensors.unregisterListener(this); subscribed = false
    }
    override fun onLocationChanged(location: Location) {
        val session = id ?: return
        val elapsed = location.elapsedRealtimeNanos / 1_000_000
        if (elapsed < trackingStartedAt) return
        if (elapsed - lastFixDelivery < 4000 && lastFixDelivery != 0L) return
        lastFixDelivery = elapsed
        scope.launch {
            try {
                val valid = Graph.repository.recordCardioTracking(session, cardioClock(this@CardioTrackingService)) { state ->
                    val result = CardioRouteEngine.accept(state, CardioFix(location.latitude, location.longitude, location.accuracy, elapsed, state.segment), SystemClock.elapsedRealtime(), activity)
                    if (location.hasAccuracy() && location.accuracy <= 25 && SystemClock.elapsedRealtime() - elapsed in 0..15_000) lastGoodFix = elapsed
                    if (location.hasAccuracy()) result else state
                }
                if (!valid) stopSelf()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { interrupt("Não consegui salvar o percurso. A sessão foi pausada.") }
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        val session = id ?: return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER || event.values.isEmpty() || !event.values[0].isFinite()) return
        if (event.timestamp / 1_000_000 < trackingStartedAt) return
        val delta = counter.sample(event.values[0].toLong())
        scope.launch {
            try { Graph.repository.recordCardioTracking(session, cardioClock(this@CardioTrackingService)) { it.copy(steps = ((it.steps ?: 0) + delta).coerceAtMost(200_000)) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { interrupt("Não consegui salvar os passos. A sessão foi pausada.") }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onProviderDisabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) scope.launch { interrupt("GPS desativado. Retome a sessão para continuar.") } }
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Legacy Android callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    private suspend fun interrupt(message: String) {
        id?.let { runCatching { Graph.repository.updateCardio(it, cardioClock(this), true) } }
        notifySafely(731, notification(message, false))
        stopSelf()
    }
    private fun notifySafely(number: Int, value: Notification) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try { getSystemService(NotificationManager::class.java).notify(number, value) } catch (_: SecurityException) { }
    }
    private fun notification(message: String, ongoing: Boolean = true): Notification {
        val content = PendingIntent.getActivity(this, 730, Intent(this, MainActivity::class.java).putExtra("open_cardio", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, "cardio_tracking").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("REMNANT · ${runCatching { CardioActivity.valueOf(activity).title }.getOrDefault("Cardio")}")
            .setContentText(message).setContentIntent(content).setOngoing(ongoing).setAutoCancel(!ongoing).setOnlyAlertOnce(true).setSilent(true)
        id?.takeIf { ongoing }?.let { session ->
            val pause = PendingIntent.getService(this, 731, Intent(this, CardioTrackingService::class.java).putExtra("session", session).putExtra("pause", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pause)
        }
        return builder.build()
    }
    override fun onDestroy() {
        unsubscribe(); collection?.cancel(); heartbeat?.cancel(); accountWatch?.cancel(); scope.cancel()
        if (CardioTrackingRuntime.status.value.id == id) CardioTrackingRuntime.update(CardioTrackingStatus())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
