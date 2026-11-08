package com.example.data.remote.trakt

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = android.app.Application::class)
class TraktDatesTest {
    @Test fun roundTripsMillisAndSecondsOnTheMinimumAndroidSdk() {
        for (time in listOf(0L, 1_000L, 1_700_000_000_123L)) assertEquals(time, TraktDates.parse(TraktDates.format(time)))
    }
    @Test fun acceptsTraktFractionsAndTimezoneOffsets() {
        assertEquals(1_123L, TraktDates.parse("1970-01-01T00:00:01.123456Z"))
        assertEquals(1_000L, TraktDates.parse("1970-01-01T05:00:01+05:00"))
    }
    @Test fun invalidOrIncompleteDatesCannotAdvanceTheSyncWatermark() {
        listOf(null, "invalid", "2026-02-30T00:00:00Z", "2026-11-08T00:00:00Zgarbage", "2026-11-08").forEach { assertNull(TraktDates.parse(it)) }
    }
    @Test fun localizedEpisodeDateKeepsItsCalendarDay() {
        assertEquals("Nov 8, 2026", TraktDates.displayDate("2026-11-08", java.util.Locale.US))
        assertNull(TraktDates.displayDate("2026-02-30"))
    }
    @Test fun pkceChallengeUsesTheRfcVectorOnAndroidSeven() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val auth = TraktAuthManager(context, { FakeTraktApiService() }, FakeTraktCredentialStore())
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", auth.generateCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
}
