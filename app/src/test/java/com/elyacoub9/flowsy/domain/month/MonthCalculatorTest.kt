package com.elyacoub9.flowsy.domain.month

import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** APP_SPEC "Validation test cases → Month boundaries". */
class MonthCalculatorTest {

    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private fun expense(amount: Long, date: LocalDate) = Expense(0, amount, 1, date, null, 0)
    private fun income(amount: Long, date: LocalDate) = Income(0, "Salary", amount, date, null, 0)

    @Test
    fun lastDayBelongsToItsMonth_firstDayToTheNext() {
        val oct31 = LocalDate.of(2026, 10, 31)
        val nov1 = LocalDate.of(2026, 11, 1)
        assertEquals(october, oct31.yearMonth())
        assertEquals(november, nov1.yearMonth())

        val expenses = listOf(expense(100, oct31), expense(7, nov1))
        assertEquals(100L, MonthCalculator.totals(october, emptyList(), expenses).spent)
        assertEquals(7L, MonthCalculator.totals(november, emptyList(), expenses).spent)
    }

    @Test
    fun februaryLength() {
        assertEquals(LocalDate.of(2028, 2, 29), MonthRange.of(YearMonth.of(2028, 2)).end)
        assertEquals(LocalDate.of(2026, 2, 28), MonthRange.of(YearMonth.of(2026, 2)).end)
        assertTrue(LocalDate.of(2028, 2, 29) in MonthRange.of(YearMonth.of(2028, 2)))
        assertFalse(LocalDate.of(2026, 3, 1) in MonthRange.of(YearMonth.of(2026, 2)))
    }

    @Test
    fun recordsOutsideTheMonth_ignored() {
        val totals = MonthCalculator.totals(
            october,
            listOf(income(1_000, LocalDate.of(2026, 9, 30)), income(500, LocalDate.of(2026, 10, 1))),
            listOf(expense(200, LocalDate.of(2026, 11, 1)), expense(50, LocalDate.of(2026, 10, 15))),
        )
        assertEquals(MonthTotals(income = 500, spent = 50), totals)
        assertEquals(450L, totals.left)
    }

    @Test
    fun emptyMonth_leftZero_notOver() {
        val totals = MonthCalculator.totals(october, emptyList(), emptyList())
        assertEquals(0L, totals.left)
        assertFalse(totals.isOver)
        assertEquals(0L, totals.overBy)
    }

    @Test(expected = ArithmeticException::class)
    fun overflow_throwsInsteadOfWrapping() {
        val day = LocalDate.of(2026, 10, 1)
        MonthCalculator.totals(october, emptyList(), listOf(expense(Long.MAX_VALUE, day), expense(1, day)))
    }
}
