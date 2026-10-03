package com.elyacoub9.flowsy.domain.date

import java.time.LocalDate

/** Allowed record dates, inclusive. */
data class RecordDateRange(val min: LocalDate, val max: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(min) && !date.isAfter(max)
}

/**
 * Dates of income, expenses and pot movements: any past date and today, never the future.
 * Used by every date picker and every repository.
 */
object RecordDateRule {
    /** Earliest date the pickers offer (the Material date picker's first year). */
    val EARLIEST: LocalDate = LocalDate.of(1900, 1, 1)

    fun range(today: LocalDate): RecordDateRange = RecordDateRange(EARLIEST, today)

    fun isAllowed(date: LocalDate, today: LocalDate): Boolean = date in range(today)
}
