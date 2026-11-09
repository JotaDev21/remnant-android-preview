package com.liferpg.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.liferpg.Graph
import com.liferpg.data.sync.CircleRequestFailure
import com.liferpg.data.sync.SyncService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Explicit sharing only. Portable backups never contain delivery intentions. */
class CircleOutbox(private val db: AppDatabase) {
    private val dao get() = db.dao()
    private val lock = Mutex()
    suspend fun share(uid: String, circle: String, session: String) = db.withTransaction {
        val saved = requireNotNull(dao.sessaoPorUuid(session)) { "O resumo ainda está sendo salvo. Tente novamente." }
        require(saved.series > 0 && saved.receiptJson.isNotBlank()) { "Este treino não tem um resumo compartilhável." }
        com.liferpg.data.sync.CircleCodec.validateReceipt(saved.receiptJson)
        dao.insertCircleDelivery(CircleDelivery(key(uid,circle,session),uid,circle,session,saved.receiptJson,"PUBLISH","pending",null,System.currentTimeMillis()))
    }
    suspend fun withdraw(uid: String, circle: String, session: String) = db.withTransaction {
        val id = key(uid,circle,session)
        dao.insertCircleDelivery(CircleDelivery(id,uid,circle,session,"","DELETE","pending",null,System.currentTimeMillis()))
        dao.withdrawCircleDelivery(id,uid)
    }
    suspend fun drain(uid: String, currentUid: () -> String?, service: SyncService): Boolean = lock.withLock {
        var retry = false
        for (row in dao.pendingCircleDeliveries(uid)) {
            if (currentUid() != uid) return@withLock false
            val result = if (row.action == "DELETE") service.retirarReciboCirculo(row.circleId,row.sessionId,uid)
                else service.enviarReciboCirculo(row.circleId,row.sessionId,row.receipt,uid)
            if (currentUid() != uid) return@withLock false
            result.onSuccess { dao.markCircleDelivery(row.id,row.action,if (row.action=="DELETE") "withdrawn" else "sent",null) }
                .onFailure { error ->
                    val transient = (error as? CircleRequestFailure)?.retryable == true || error is java.io.IOException
                    retry = retry || transient
                    dao.markCircleDelivery(row.id,row.action,if (transient) "pending" else "failed",error.message?.take(240) ?: "Envio não confirmado.")
                }
        }
        retry || dao.pendingCircleDeliveries(uid).isNotEmpty()
    }
    companion object {
        fun key(uid: String, circle: String, session: String) = "$uid/$circle/$session"
        fun schedule(ctx: Context, uid: String, immediate: Boolean = true) {
            val manager = WorkManager.getInstance(ctx)
            val data = workDataOf("uid" to uid)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            manager.enqueueUniquePeriodicWork("circles-periodic-$uid",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CircleDeliveryWorker>(15,TimeUnit.MINUTES).setInputData(data).setConstraints(constraints).build())
            if (immediate) manager.enqueueUniqueWork("circles-send-$uid",ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<CircleDeliveryWorker>().setInputData(data).setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
        }
    }
}

class CircleDeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        Graph.provide(applicationContext)
        val uid = inputData.getString("uid") ?: return Result.success()
        if (Graph.auth.sessao.value?.userId != uid) return Result.success()
        return try {
            if (Graph.circleOutbox.drain(uid,{ Graph.auth.sessao.value?.takeUnless { it.anonimo }?.userId },Graph.sync)) Result.retry() else Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
