package com.liferpg.data

import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

internal interface DailyStepsSource {
    val available: Boolean
    suspend fun permitted(): Boolean
    suspend fun aggregate(start: Instant, end: Instant): Long?
}

internal object DailyStepsReader {
    suspend fun read(source: DailyStepsSource, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): DailySteps {
        val day = now.atZone(zone).toLocalDate()
        return try {
            if (!source.available) DailySteps("UNAVAILABLE", day = day)
            else if (!source.permitted()) DailySteps("PERMISSION", day = day)
            else {
                val count = source.aggregate(day.atStartOfDay(zone).toInstant(), now)
                require(count == null || count >= 0)
                DailySteps(if (count == null) "EMPTY" else "READY", count, day, now.toEpochMilli())
            }
        } catch (e: CancellationException) { throw e }
        catch (_: SecurityException) { DailySteps("PERMISSION", day = day) }
        catch (_: Exception) { DailySteps("ERROR", day = day) }
    }
}
