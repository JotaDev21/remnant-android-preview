package com.liferpg.notif

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.liferpg.Graph
import com.liferpg.data.sync.AmizadeArena
import com.liferpg.data.sync.DesafioArena
import java.util.concurrent.TimeUnit

/** A local notification is emitted once per invitation, even when foreground and worker overlap. */
object ArenaConvites {
    @Synchronized fun avisarComentarios(ctx:Context,uid:String,rows:List<com.liferpg.data.sync.CircleCommentNotice>) {
        val prefs=ctx.getSharedPreferences("arena_social_alerts",Context.MODE_PRIVATE)
        if(prefs.getString("comment_notification_owner",null)!=uid) {
            Notificacoes.limparConversas(ctx)
            prefs.edit().putString("comment_notification_owner",uid).apply()
        }
        val key="comments_$uid"; val known=prefs.getStringSet(key,emptySet()).orEmpty(); val delivered=mutableSetOf<String>()
        rows.filter { it.id !in known }.groupBy { it.post }.values.forEach { notices ->
            if(Notificacoes.mostrarComentarioCirculo(ctx,uid,notices.first())) delivered.addAll(notices.map { it.id })
        }
        prefs.edit().putStringSet(key,(known+delivered).toList().takeLast(300).toSet()).apply()
    }
    @Synchronized fun avisarCirculos(ctx: Context, uid: String, rows: List<com.liferpg.data.sync.TrainingCircle>) {
        val prefs=ctx.getSharedPreferences("circle_invites_$uid",Context.MODE_PRIVATE)
        val seen=prefs.getStringSet("seen",emptySet()).orEmpty()
        val incoming=rows.filter { it.status=="pending" }.map { it.id }.toSet()
        val fresh=incoming-seen
        val notified=fresh.isNotEmpty() && Notificacoes.mostrarConvitesArena(ctx,0,0,circulos=fresh.size)
        prefs.edit().putStringSet("seen",if(notified) incoming else seen.intersect(incoming)).apply()
    }
    private const val WORK_NAME = "arena-convites"

    @Synchronized fun avisarPactos(ctx: Context, uid: String, rows: List<com.liferpg.data.sync.TrainingPact>) {
        val incoming = rows.filter { it.status == "forming" && it.members.any { m -> m.uid == uid && m.status == "pending" } }
        val prefs = ctx.getSharedPreferences("arena_social_alerts", Context.MODE_PRIVATE)
        val key = "pacts_$uid"
        val known = prefs.getStringSet(key, emptySet()).orEmpty()
        val ids = incoming.map { it.id }
        if (ids.any { it !in known } && Notificacoes.mostrarConvitesArena(ctx, 0, 0, incoming.size))
            prefs.edit().putStringSet(key, (known + ids).toList().takeLast(200).toSet()).apply()
    }

    fun acompanhar(ctx: Context, signedIn: Boolean) {
        val work = WorkManager.getInstance(ctx)
        if (!signedIn) {
            work.cancelUniqueWork(WORK_NAME)
            Notificacoes.limparConvitesArena(ctx)
            Notificacoes.limparConversas(ctx)
            return
        }
        val request = PeriodicWorkRequestBuilder<ArenaConvitesWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    @Synchronized
    fun avisar(ctx: Context, uid: String, amizades: List<AmizadeArena>, desafios: List<DesafioArena>) {
        val recebidos = amizades.filter { it.status == "pending" && !it.enviadaPorMim }
        val duelos = desafios.filter { it.status == "pending" && !it.euDesafiei }
        if (recebidos.isEmpty() && duelos.isEmpty()) {
            Notificacoes.limparConvitesArena(ctx, incluirPactos = false)
            return
        }
        val prefs = ctx.getSharedPreferences("arena_social_alerts", Context.MODE_PRIVATE)
        val key = "notified_$uid"
        val anteriores = prefs.getStringSet(key, emptySet()).orEmpty()
        val novos = recebidos.map { "friend_${it.id}" } + duelos.map { "duel_${it.id}" }
        if (novos.none { it !in anteriores }) return
        if (Notificacoes.mostrarConvitesArena(ctx, recebidos.size, duelos.size)) {
            prefs.edit().putStringSet(key, (anteriores + novos).toList().takeLast(200).toSet()).apply()
        }
    }
}

class ArenaConvitesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val uid = Graph.auth.sessao.value?.userId ?: return Result.success()
        if (!Graph.sync.habilitado || !Notificacoes.temPermissao(applicationContext)) return Result.success()
        val amizades = Graph.sync.amizades().getOrNull() ?: return Result.retry()
        val desafios = Graph.sync.desafios().getOrNull() ?: return Result.retry()
        val pacts = Graph.sync.pactos().getOrNull()
        // An account can change while the requests are in flight.
        if (Graph.auth.sessao.value?.userId == uid) ArenaConvites.avisar(applicationContext, uid, amizades, desafios)
        if (Graph.auth.sessao.value?.userId == uid && pacts != null) ArenaConvites.avisarPactos(applicationContext, uid, pacts)
        val circles=Graph.sync.circulos().getOrNull()
        if (Graph.auth.sessao.value?.userId==uid && circles!=null) ArenaConvites.avisarCirculos(applicationContext,uid,circles)
        val comments=Graph.sync.avisosCirculo().getOrNull()
        if(Graph.auth.sessao.value?.userId==uid && comments!=null) ArenaConvites.avisarComentarios(applicationContext,uid,comments)
        return Result.success()
    }
}
