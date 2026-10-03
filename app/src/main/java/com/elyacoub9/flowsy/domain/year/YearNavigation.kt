package com.elyacoub9.flowsy.domain.year

import com.elyacoub9.flowsy.domain.month.MonthNavigation
import java.time.LocalDate

/** The years the switcher can show, inclusive. */
data class YearBounds(val first: Int, val last: Int) {
    fun canGoBack(year: Int): Boolean = year > first
    fun canGoForward(year: Int): Boolean = year < last
    fun coerce(year: Int): Int = year.coerceIn(first, last)
}

object YearNavigation {
    /** Same sources as the month switcher: earliest record or install date, up to the current year. */
    fun bounds(earliestRecord: LocalDate?, installDate: LocalDate, today: LocalDate): YearBounds {
        val months = MonthNavigation.bounds(earliestRecord, installDate, today)
        return YearBounds(months.first.year, months.last.year)
    }
}
