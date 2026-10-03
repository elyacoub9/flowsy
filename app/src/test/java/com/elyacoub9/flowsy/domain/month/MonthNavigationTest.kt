package com.elyacoub9.flowsy.domain.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthNavigationTest {

    private val zone = ZoneId.of("Africa/Casablanca")
    private val clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), zone)
    private val today = LocalDate.now(clock)
    private val october = YearMonth.of(2026, 10)

    @Test
    fun earliestRecordBeforeInstall_startsAtRecordMonth() {
        val bounds = MonthNavigation.bounds(LocalDate.of(2026, 3, 31), installDate = LocalDate.of(2026, 9, 2), today = today)
        assertEquals(MonthBounds(YearMonth.of(2026, 3), october), bounds)
    }

    @Test
    fun installBeforeEarliestRecord_orNoRecord_startsAtInstallMonth() {
        val install = LocalDate.of(2026, 8, 20)
        assertEquals(YearMonth.of(2026, 8), MonthNavigation.bounds(LocalDate.of(2026, 9, 1), install, today).first)
        assertEquals(YearMonth.of(2026, 8), MonthNavigation.bounds(null, install, today).first)
    }

    @Test
    fun neverStartsAfterCurrentMonth() {
        val bounds = MonthNavigation.bounds(null, installDate = LocalDate.of(2027, 1, 1), today = today)
        assertEquals(MonthBounds(october, october), bounds)
    }

    @Test
    fun forwardDisabledOnCurrentMonth_backDisabledOnFirst_coerce() {
        val bounds = MonthBounds(YearMonth.of(2026, 8), october)
        assertFalse(bounds.canGoForward(october))
        assertTrue(bounds.canGoBack(october))
        assertTrue(bounds.canGoForward(YearMonth.of(2026, 8)))
        assertFalse(bounds.canGoBack(YearMonth.of(2026, 8)))
        assertEquals(october, bounds.coerce(YearMonth.of(2027, 2)))
        assertEquals(YearMonth.of(2026, 8), bounds.coerce(YearMonth.of(2025, 1)))
    }

    @Test
    fun monthOpenedFromYear_beforeFirstRecord_isIncluded() {
        val bounds = MonthBounds(october, october) // records and install only in October
        val september = YearMonth.of(2026, 9)
        val extended = bounds.including(september)
        assertEquals(MonthBounds(september, october), extended)
        assertEquals(september, extended.coerce(september))
        assertTrue(extended.canGoForward(september))
        assertEquals(bounds, bounds.including(null))
        assertEquals(bounds, bounds.including(october))
        assertEquals(bounds, bounds.including(YearMonth.of(2027, 1))) // never past the current month
    }

    @Test
    fun currentMonthMovesAtMidnight() {
        val beforeMidnight = LocalDate.now(Clock.fixed(Instant.parse("2026-10-31T22:59:00Z"), zone)) // 23:59 local
        val afterMidnight = LocalDate.now(Clock.fixed(Instant.parse("2026-10-31T23:01:00Z"), zone)) // 00:01 local
        val install = LocalDate.of(2026, 9, 1)
        assertEquals(october, MonthNavigation.bounds(null, install, beforeMidnight).last)
        assertEquals(YearMonth.of(2026, 11), MonthNavigation.bounds(null, install, afterMidnight).last)
    }

    @Test
    fun defaultRecordDate_todayInCurrentMonth_elseFirstOfMonth() {
        assertEquals(today, MonthNavigation.defaultRecordDate(october, today))
        assertEquals(LocalDate.of(2026, 9, 1), MonthNavigation.defaultRecordDate(YearMonth.of(2026, 9), today))
        assertEquals(LocalDate.of(2025, 2, 1), MonthNavigation.defaultRecordDate(YearMonth.of(2025, 2), today))
    }
}
