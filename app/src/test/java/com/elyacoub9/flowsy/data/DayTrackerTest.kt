package com.elyacoub9.flowsy.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayTrackerTest {

    @Test
    fun refresh_picksUpTheNewDayAfterMidnight() {
        val zone = ZoneId.of("Africa/Casablanca")
        var clock = Clock.fixed(Instant.parse("2026-10-31T22:59:00Z"), zone) // 23:59 local
        val tracker = DayTracker { LocalDate.now(clock) }
        assertEquals(LocalDate.of(2026, 10, 31), tracker.day.value)

        clock = Clock.fixed(Instant.parse("2026-10-31T23:01:00Z"), zone) // 00:01 local
        tracker.refresh() // called on every resume
        assertEquals(LocalDate.of(2026, 11, 1), tracker.day.value)
    }
}
