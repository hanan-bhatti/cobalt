package com.example.media.recommendation

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ScreeningScheduleTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private val utc = ZoneId.of("UTC")
    @Test fun morningWindowNeverSendsAtNight() {
        assertTrue(ScreeningSchedule.inWindow(ms("2026-11-12T09:00:00Z"), 9, 0, true, utc))
        assertFalse(ScreeningSchedule.inWindow(ms("2026-11-12T12:00:00Z"), 9, 0, true, utc))
        assertFalse(ScreeningSchedule.inWindow(ms("2026-11-12T08:59:59Z"), 9, 0, true, utc))
        assertEquals(ms("2026-11-13T09:00:00Z"), ScreeningSchedule.next(ms("2026-11-12T22:00:00Z"), 9, 0, true, utc))
    }
    @Test fun startupCanCatchTodayButWorkerSchedulesTomorrow() {
        val now = ms("2026-11-12T09:10:00Z")
        assertEquals(now, ScreeningSchedule.next(now, 9, 0, true, utc))
        assertEquals(ms("2026-11-13T09:00:00Z"), ScreeningSchedule.next(now, 9, 0, true, utc, false))
    }
    @Test fun anEarlyWorkerStillSchedulesNextDate() {
        assertEquals(ms("2026-11-13T09:00:00Z"), ScreeningSchedule.next(ms("2026-11-12T08:00:00Z"), 9, 0, true, utc, false))
    }
    @Test fun weekendsSkipped() {
        assertEquals(ms("2026-11-16T09:00:00Z"), ScreeningSchedule.next(ms("2026-11-13T18:00:00Z"), 9, 0, false, utc))
        assertFalse(ScreeningSchedule.inWindow(ms("2026-11-14T09:00:00Z"), 9, 0, false, utc))
    }
    @Test fun springAndFallDstStayAtNineLocal() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(ms("2026-03-08T13:00:00Z"), ScreeningSchedule.next(ms("2026-03-07T16:00:00Z"), 9, 0, true, zone, false))
        assertEquals(ms("2026-11-01T14:00:00Z"), ScreeningSchedule.next(ms("2026-10-31T16:00:00Z"), 9, 0, true, zone, false))
    }
}
