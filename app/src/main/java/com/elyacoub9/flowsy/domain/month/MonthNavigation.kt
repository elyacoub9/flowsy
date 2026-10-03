package com.elyacoub9.flowsy.domain.month

import java.time.LocalDate
import java.time.YearMonth

/** The months the switcher can show, inclusive. */
data class MonthBounds(val first: YearMonth, val last: YearMonth) {
    fun canGoBack(month: YearMonth): Boolean = month > first
    fun canGoForward(month: YearMonth): Boolean = month < last
    fun coerce(month: YearMonth): YearMonth = month.coerceIn(first, last)

    /** Also allows [month] (e.g. opened from a Year card) when it is before [first]; never past [last]. */
    fun including(month: YearMonth?): MonthBounds =
        if (month != null && month < first) copy(first = minOf(month, last)) else this
}

object MonthNavigation {

    /**
     * From the earliest month with a record (or the install month, if earlier or there is no record)
     * up to the current month. Never starts after the current month, even if the phone date went back.
     */
    fun bounds(earliestRecord: LocalDate?, installDate: LocalDate, today: LocalDate): MonthBounds {
        val current = today.yearMonth()
        val first = listOfNotNull(earliestRecord?.yearMonth(), installDate.yearMonth(), current).min()
        return MonthBounds(first, current)
    }

    /**
     * Date proposed for a new record: today in the current month, otherwise the 1st of the viewed
     * month. The saved date is still checked by RecordDateRule.
     */
    fun defaultRecordDate(viewed: YearMonth, today: LocalDate): LocalDate =
        if (viewed < today.yearMonth()) viewed.atDay(1) else today
}
