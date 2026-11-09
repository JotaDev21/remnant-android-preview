package com.liferpg.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DailyStepsReaderTest {
    private class Source(override val available: Boolean = true, val permission: Boolean = true, val total: Long? = 6000L, val failure: Exception? = null) : DailyStepsSource {
        var calls = 0; var start: Instant? = null; var end: Instant? = null
        override suspend fun permitted() = permission
        override suspend fun aggregate(start: Instant, end: Instant): Long? { calls++; this.start = start; this.end = end; failure?.let { throw it }; return total }
    }
    @Test fun oneConsolidatedAggregateUsesLocalMidnightAndRetainsRealZero() = runTest {
        val now = Instant.parse("2026-10-08T15:00:00Z"); val source = Source(total = 0)
        val snapshot = DailyStepsReader.read(source, now, ZoneId.of("America/Sao_Paulo"))
        assertEquals("READY", snapshot.status); assertEquals(0L, snapshot.count); assertEquals(1, source.calls)
        assertEquals(Instant.parse("2026-10-08T03:00:00Z"), source.start); assertEquals(now, source.end)
    }
    @Test fun deniedUnavailableEmptyAndFailingSourcesAreDistinctAndNeverBecomeFakeZeroSteps() = runTest {
        val unavailable = Source(available = false); assertEquals("UNAVAILABLE", DailyStepsReader.read(unavailable).status); assertEquals(0, unavailable.calls)
        val denied = Source(permission = false); assertEquals("PERMISSION", DailyStepsReader.read(denied).status); assertEquals(0, denied.calls)
        val empty = DailyStepsReader.read(Source(total = null)); assertEquals("EMPTY", empty.status); assertNull(empty.count)
        val failed = DailyStepsReader.read(Source(failure = IllegalStateException())); assertEquals("ERROR", failed.status); assertNull(failed.count)
        assertEquals("PERMISSION", DailyStepsReader.read(Source(failure = SecurityException())).status)
    }
    @Test fun cancellationPropagatesAndDayBoundaryResetsTheQueryWindow() = runTest {
        try { DailyStepsReader.read(Source(failure = CancellationException())); fail() } catch (_: CancellationException) { }
        val source = Source(); val snapshot = DailyStepsReader.read(source, Instant.parse("2026-10-09T03:01:00Z"), ZoneId.of("America/Sao_Paulo"))
        assertEquals("2026-10-09", snapshot.day.toString()); assertEquals(Instant.parse("2026-10-09T03:00:00Z"), source.start)
    }
}
