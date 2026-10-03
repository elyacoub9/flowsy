package com.elyacoub9.flowsy.domain.date

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecordDateRuleTest {

    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun today_accepted_future_refused() {
        assertTrue(RecordDateRule.isAllowed(today, today))
        assertFalse(RecordDateRule.isAllowed(today.plusDays(1), today))
    }

    @Test
    fun anyPastDate_accepted() {
        assertTrue(RecordDateRule.isAllowed(today.minusDays(1), today))
        assertTrue(RecordDateRule.isAllowed(LocalDate.of(2001, 5, 17), today))
        assertTrue(RecordDateRule.isAllowed(RecordDateRule.EARLIEST, today))
    }
}
