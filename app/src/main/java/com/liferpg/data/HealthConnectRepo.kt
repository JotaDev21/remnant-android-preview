package com.liferpg.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import kotlinx.coroutines.CancellationException
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only bridge to Health Connect. Every call is best-effort: a missing provider, a
 * revoked permission or a throwing SDK degrades to a neutral value instead of crashing.
 */
class HealthConnectRepo(context: Context) {

    private val ctx = context.applicationContext

    private val client by lazy { HealthConnectClient.getOrCreate(ctx) }

    val permissoes: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )
    val permissaoPassos = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    suspend fun stepsSnapshot(): DailySteps = DailyStepsReader.read(object : DailyStepsSource {
        override val available get() = disponivel
        override suspend fun permitted() = client.permissionController.getGrantedPermissions().containsAll(permissaoPassos)
        override suspend fun aggregate(start: Instant, end: Instant) = client.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL]
    })

    val disponivel: Boolean
        get() = runCatching {
            HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)

    suspend fun temAlgumaPermissao(): Boolean = runCatching {
        client.permissionController.getGrantedPermissions().any { it in permissoes }
    }.getOrDefault(false)

    /** Most recent body weight logged in the last 180 days, in kilograms. */
    suspend fun ultimoPesoKg(): Float? = runCatching {
        val fim = Instant.now()
        val ini = fim.minus(Duration.ofDays(180))
        client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(ini, fim))
        ).records.maxByOrNull { it.time }?.weight?.inKilograms?.toFloat()
    }.getOrNull()

    /** Steps accumulated since local midnight. */
    suspend fun passosHoje(): Long = runCatching {
        val zona = ZoneId.systemDefault()
        val ini = LocalDate.now().atStartOfDay(zona).toInstant()
        client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(ini, Instant.now())))[StepsRecord.COUNT_TOTAL] ?: 0L
    }.getOrDefault(0L)
}

data class DailySteps(val status: String = "LOADING", val count: Long? = null, val day: LocalDate = LocalDate.now(), val updatedAt: Long = 0)
