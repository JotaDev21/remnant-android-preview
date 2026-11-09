package com.liferpg.notif

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.liferpg.MainActivity
import com.liferpg.R
import java.util.Calendar
import com.liferpg.Graph
import com.liferpg.domain.TrainingStreak
import com.liferpg.domain.StreakDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Every notification and alarm the app owns: the rest-timer chime and the daily
 * "go train" reminder. All posting is best-effort — a revoked POST_NOTIFICATIONS or a
 * denied exact-alarm permission degrades instead of crashing the session.
 */
object Notificacoes {

    private const val CANAL_DESCANSO = "descanso"
    private const val NOTIF_ID_DESCANSO = 1001
    private const val REQ_ALARME = 2001

    private const val CANAL_LEMBRETE = "lembrete"
    private const val NOTIF_ID_LEMBRETE = 1002
    private const val REQ_LEMBRETE = 2002

    private const val CANAL_ARENA = "arena_convites"
    private const val NOTIF_ID_ARENA = 1003
    private const val REQ_ARENA = 2003
    private const val CANAL_CONVERSAS = "circle_conversations"

    fun criarCanais(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            CANAL_CONVERSAS,"Conversas dos Círculos",NotificationManager.IMPORTANCE_DEFAULT).apply { description="Comentários nos treinos que você compartilhou" })
        val canal = NotificationChannel(
            CANAL_DESCANSO,
            "Descanso entre séries",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa quando o descanso termina"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)

        val canalLembrete = NotificationChannel(
            CANAL_LEMBRETE,
            "Lembretes de treino",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Lembra você de treinar no seu horário"
        }
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(canalLembrete)

        val canalArena = NotificationChannel(
            CANAL_ARENA,
            "Convites da Arena",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Pedidos de amizade e convites para duelos" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canalArena)
    }

    fun temPermissao(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun mostrarComentarioCirculo(ctx:Context,uid:String,notice:com.liferpg.data.sync.CircleCommentNotice):Boolean {
        if(!temPermissao(ctx)) return false
        criarCanais(ctx)
        val target=com.liferpg.data.sync.CircleConversationTarget(notice.circle,notice.post)
        // Data URI, not a hash-only request code, makes every workout's PendingIntent distinct.
        val intent=Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setData(android.net.Uri.parse("remnant-internal://conversation/${notice.circle}/${notice.post}"))
            .putExtra("open_social_section",target.route).putExtra("social_account",uid)
        val pending=PendingIntent.getActivity(ctx,2100,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(ctx,CANAL_CONVERSAS).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nova conversa no seu treino").setContentText("${notice.nickname} comentou em ${notice.title}")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(pending).build()
        return try { NotificationManagerCompat.from(ctx).notify("conversation/$uid/${notice.post}",1010,notification); true }
        catch(_:SecurityException) { false }
        catch(_:RuntimeException) { false }
    }

    fun limparConversas(ctx:Context) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.notification.channelId==CANAL_CONVERSAS }.forEach { NotificationManagerCompat.from(ctx).cancel(it.tag,it.id) } }
    }
    fun limparConversa(ctx:Context,uid:String,post:String) {
        runCatching { NotificationManagerCompat.from(ctx).cancel("conversation/$uid/$post",1010) }
    }

    // ---------------------------------------------------------------- descanso

    fun mostrarDescansoConcluido(ctx: Context) {
        if (!temPermissao(ctx)) return

        val abrir = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx,
            0,
            abrir,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(ctx, CANAL_DESCANSO)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💪 Descanso concluído!")
            .setContentText("Hora da próxima série, guerreiro.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        postar(ctx, NOTIF_ID_DESCANSO, notif)
    }

    /**
     * Rest-timer alarm without any exact-alarm permission: a short delivery window is enough for a
     * "rest is over" nudge, and the in-app countdown remains the source of truth while the screen is on.
     */
    fun agendarFimDescanso(ctx: Context, fimEpochMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmePI(ctx)
        runCatching {
            am.setWindow(AlarmManager.RTC_WAKEUP, fimEpochMs, JANELA_DESCANSO_MS, pi)
        }.onFailure {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fimEpochMs, pi) }
        }
    }

    /** Tolerated delivery window for the rest alarm (the OS batches inexact alarms). */
    private const val JANELA_DESCANSO_MS = 10_000L

    fun cancelarDescanso(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(alarmePI(ctx)) }
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_DESCANSO) }
    }

    private fun alarmePI(ctx: Context): PendingIntent {
        val intent = Intent(ctx, DescansoReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx,
            REQ_ALARME,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    // ---------------------------------------------------------------- lembrete

    fun mostrarLembrete(ctx: Context, streak: TrainingStreak? = null) {
        if (!temPermissao(ctx)) return

        val abrir = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx,
            1,
            abrir,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(ctx, CANAL_LEMBRETE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (streak?.day == StreakDay.PENDING) "Proteja seus ${streak.count} dias de sequência" else "Seu próximo dia começa com uma série")
            .setContentText(if (streak?.day == StreakDay.PENDING) "Registre uma série válida até meia-noite. Um exercício em casa também conta." else "Uma série válida inicia sua sequência. Seu recorde continua guardado.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        postar(ctx, NOTIF_ID_LEMBRETE, notif)
    }

    fun limparLembrete(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_LEMBRETE)
    }

    /** Called only when a newly discovered incoming invitation has not been notified before. */
    fun mostrarConvitesArena(ctx: Context, amizades: Int, duelos: Int, pactos: Int = 0, circulos: Int = 0): Boolean {
        if (!temPermissao(ctx)) return false
        val total = amizades + duelos + pactos + circulos
        if (total == 0) return false
        val abrir = Intent(ctx, MainActivity::class.java)
            .putExtra("open_arena", true)
            .putExtra("open_social_section", com.liferpg.domain.SocialInbox(amizades,circulos,pactos,duelos).destination.route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, REQ_ARENA + com.liferpg.domain.SocialInbox(amizades,circulos,pactos,duelos).destination.ordinal, abrir,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val resumo = listOfNotNull(
            amizades.takeIf { it > 0 }?.let { "$it pedido${if (it == 1) "" else "s"} de amizade" },
            duelos.takeIf { it > 0 }?.let { "$it convite${if (it == 1) "" else "s"} para duelo" },
            pactos.takeIf { it > 0 }?.let { "$it convite${if (it == 1) "" else "s"} para pacto · enfrente um chefe com aliados" },
            circulos.takeIf { it > 0 }?.let { "$it convite${if (it == 1) "" else "s"} para círculo de treino" },
        ).joinToString(" · ")
        val notif = NotificationCompat.Builder(ctx, CANAL_ARENA)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (total == 1) "Novo convite no REMNANT" else "$total convites no REMNANT")
            .setContentText(resumo)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$resumo. Toque para responder em Amigos."))
            .setNumber(total)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        return postar(ctx, if (circulos > 0) NOTIF_ID_ARENA + 2 else if (pactos > 0) NOTIF_ID_ARENA + 1 else NOTIF_ID_ARENA, notif)
    }

    fun limparConvitesArena(ctx: Context, incluirPactos: Boolean = true) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_ARENA) }
        if (incluirPactos) runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_ARENA + 1) }
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_ARENA + 2) }
    }

    @SuppressLint("MissingPermission")
    private fun postar(ctx: Context, id: Int, notification: android.app.Notification): Boolean {
        if (!temPermissao(ctx)) return false
        return try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
            true
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and the platform call.
            false
        }
    }

    /** Fires daily at [hora]:00 local time, starting today if that hour is still ahead. */
    fun agendarLembreteDiario(ctx: Context, hora: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val agora = Calendar.getInstance()
        val alvo = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hora.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alvo.timeInMillis <= agora.timeInMillis) alvo.add(Calendar.DAY_OF_YEAR, 1)

        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                alvo.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                lembretePI(ctx),
            )
        }
    }

    fun cancelarLembrete(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(lembretePI(ctx)) }
    }

    private fun lembretePI(ctx: Context): PendingIntent {
        val intent = Intent(ctx, LembreteReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx,
            REQ_LEMBRETE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

class DescansoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        Notificacoes.mostrarDescansoConcluido(context)
}

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    Graph.provide(context.applicationContext)
                    if (Graph.repository.preferenciasAtuais()?.lembretesAtivos != true) return@withTimeout
                    val profile = Graph.repository.perfil.first() ?: return@withTimeout
                    val day = LocalDate.now().toEpochDay()
                    val streak = TrainingStreak.of(profile.streak, profile.melhorStreak, profile.ultimoDiaTreino, day)
                    // Never warn about a lost day when the player's valid set already secured it.
                    if (streak.day != StreakDay.SECURED) Notificacoes.mostrarLembrete(context, streak)
                }
            } catch (_: Exception) {
                // Database/permission failure should not fabricate a streak warning.
            } finally { pending.finish() }
        }
    }
}

/** Android drops alarms on reboot; restore only the player's saved reminder preference. */
class LembreteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    Graph.provide(context.applicationContext)
                    val preferences = Graph.repository.preferenciasAtuais() ?: return@withTimeout
                    if (preferences.lembretesAtivos) Notificacoes.agendarLembreteDiario(context,
                        com.liferpg.domain.HorarioTreino.seguro(preferences.horarioTreino).hora)
                }
            } catch (_: Exception) {
                // The next normal app launch also restores the saved schedule.
            } finally { pending.finish() }
        }
    }
}
