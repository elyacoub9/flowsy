package com.elyacoub9.flowsy.domain.year

import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.month.MonthTotals
import com.elyacoub9.flowsy.domain.month.SpentRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** APP_SPEC "Validation test cases → Year 2026". */
class YearCalculatorTest {

    private val today = LocalDate.now(Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Africa/Casablanca")))
    private fun oct(d: Int) = LocalDate.of(2026, 10, d)
    private fun sep(d: Int) = LocalDate.of(2026, 9, d)

    private val income = listOf(
        Income(1, "Salary", 1_200_000, sep(1), null, 0),
        Income(2, "Salary", 1_235_000, oct(1), null, 0),
        Income(3, "Refund", 20_000, oct(10), null, 0),
        Income(4, "Old", 99_999, LocalDate.of(2025, 12, 31), null, 0), // other year: ignored
    )

    private fun expense(amount: Long, date: LocalDate) = Expense(0, amount, 1, date, null, 0)

    private val expenses = listOf(
        expense(950_000, sep(15)),
        expense(450_000, oct(1)), expense(34_250, oct(3)), expense(82_040, oct(5)),
        expense(15_000, oct(8)), expense(38_960, oct(10)), expense(64_000, oct(12)),
    )

    private val summary = YearCalculator.summary(2026, income, expenses, today)

    @Test
    fun yearTotals_spec() {
        assertEquals(2_455_000L, summary.totals.income)
        assertEquals(1_634_250L, summary.totals.spent)
        assertEquals(820_750L, summary.totals.left)
    }

    @Test
    fun perMonth_septemberAndOctober() {
        val september = summary.months[8]
        assertEquals(YearMonth.of(2026, 9), september.month)
        assertEquals(MonthTotals(income = 1_200_000, spent = 950_000), september.totals)
        assertEquals(250_000L, september.totals.left)
        assertEquals(SpentRatio.Ratio(percent = 79, barPercent = 79, over = false), september.ratio)

        val october = summary.months[9]
        assertEquals(MonthTotals(income = 1_255_000, spent = 684_250), october.totals)
        assertEquals(570_750L, october.totals.left)
        assertEquals(SpentRatio.Ratio(percent = 54, barPercent = 54, over = false), october.ratio)
    }

    @Test
    fun monthStates() {
        assertEquals(12, summary.months.size)
        assertEquals(MonthStatus.NO_RECORDS, summary.months[0].status) // January: past, nothing
        assertEquals(MonthStatus.RECORDS, summary.months[8].status)
        assertEquals(MonthStatus.RECORDS, summary.months[9].status)
        assertEquals(MonthStatus.FUTURE, summary.months[10].status)
        assertEquals(MonthStatus.FUTURE, summary.months[11].status)
        assertEquals(listOf(YearMonth.of(2026, 10)), summary.months.filter { it.isCurrent }.map { it.month })
        assertEquals(SpentRatio.Empty, summary.months[0].ratio)
    }

    @Test
    fun overBudgetMonth() {
        val over = YearCalculator.summary(2026, income, expenses + expense(600_000, oct(11)), today).months[9]
        assertTrue(over.totals.isOver)
        assertEquals(29_250L, over.totals.overBy)
        assertEquals(SpentRatio.Ratio(percent = 102, barPercent = 100, over = true), over.ratio)
    }

    @Test
    fun pastYear_noFutureNoCurrent() {
        val past = YearCalculator.summary(2025, income, expenses, today)
        assertTrue(past.months.none { it.status == MonthStatus.FUTURE || it.isCurrent })
        assertEquals(MonthStatus.RECORDS, past.months[11].status)
        assertEquals(99_999L, past.totals.income)
    }

    // Chart

    @Test
    fun chart_scaledToLargestValue() {
        val bars = YearChart.bars(summary)!!
        assertEquals(12, bars.size)
        assertEquals(1f, bars[9].income, 0.0001f) // October income 12,550.00 is the largest
        assertEquals(1_200_000f / 1_255_000f, bars[8].income, 0.0001f)
        assertEquals(950_000f / 1_255_000f, bars[8].spent, 0.0001f)
        assertEquals(MonthBars(0f, 0f), bars[0])
    }

    @Test
    fun chart_allZero_null() {
        assertNull(YearChart.bars(YearCalculator.summary(2026, emptyList(), emptyList(), today)))
    }

    @Test
    fun chart_defaultSelection() {
        assertEquals(YearMonth.of(2026, 10), YearChart.defaultSelection(summary))
        assertEquals(YearMonth.of(2025, 12), YearChart.defaultSelection(YearCalculator.summary(2025, income, expenses, today)))
        assertNull(YearChart.defaultSelection(YearCalculator.summary(2024, income, expenses, today)))
    }

    // Navigation

    @Test
    fun bounds_earliestRecordOrInstallYear_toCurrentYear() {
        val bounds = YearNavigation.bounds(LocalDate.of(2024, 5, 2), LocalDate.of(2026, 9, 1), today)
        assertEquals(YearBounds(2024, 2026), bounds)
        assertFalse(bounds.canGoForward(2026))
        assertTrue(bounds.canGoBack(2026))
        assertFalse(bounds.canGoBack(2024))
        assertEquals(2026, bounds.coerce(2030))
        assertEquals(YearBounds(2026, 2026), YearNavigation.bounds(null, LocalDate.of(2026, 9, 1), today))
    }
}
