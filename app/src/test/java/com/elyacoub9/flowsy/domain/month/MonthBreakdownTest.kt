package com.elyacoub9.flowsy.domain.month

import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Spent ratio, category breakdown and day totals. The spec October case is in SpecMoneyValidationTest. */
class MonthBreakdownTest {

    private val october = YearMonth.of(2026, 10)
    private fun day(d: Int) = LocalDate.of(2026, 10, d)

    private fun category(id: Long, sortOrder: Int = id.toInt(), archived: Boolean = false) =
        Category(id, "C$id", null, "other", "red", archived, sortOrder)

    private fun expense(amount: Long, categoryId: Long, date: LocalDate = day(5)) = Expense(0, amount, categoryId, date, null, 0)

    // Spent ratio

    @Test
    fun ratio_over100_realPercentShown_barCapped() {
        assertEquals(
            SpentRatio.Ratio(percent = 112, barPercent = 100, over = true),
            MonthCalculator.spentRatio(MonthTotals(income = 100_000, spent = 112_000)),
        )
    }

    @Test
    fun ratio_100_4_shows100_overFromAmounts() {
        assertEquals(
            SpentRatio.Ratio(percent = 100, barPercent = 100, over = true),
            MonthCalculator.spentRatio(MonthTotals(income = 100_000, spent = 100_400)),
        )
        assertEquals(
            SpentRatio.Ratio(percent = 100, barPercent = 100, over = false),
            MonthCalculator.spentRatio(MonthTotals(income = 100_000, spent = 100_000)),
        )
    }

    @Test
    fun ratio_incomeButNothingSpent_zeroBar() {
        assertEquals(SpentRatio.Ratio(percent = 0, barPercent = 0, over = false), MonthCalculator.spentRatio(MonthTotals(50_000, 0)))
    }

    @Test
    fun ratio_noIncome_andEmpty() {
        assertEquals(SpentRatio.NoIncome, MonthCalculator.spentRatio(MonthTotals(income = 0, spent = 1)))
        assertEquals(SpentRatio.Empty, MonthCalculator.spentRatio(MonthTotals(income = 0, spent = 0)))
    }

    // Category breakdown

    @Test
    fun breakdown_percentagesAlwaysAddUpTo100() {
        // Three equal thirds: 33.33 each → 34 / 33 / 33.
        val categories = listOf(category(1), category(2), category(3))
        val slices = MonthCalculator.categoryBreakdown(october, listOf(expense(100, 1), expense(100, 2), expense(100, 3)), categories)
        assertEquals(listOf(34, 33, 33), slices.map { it.percent })
        assertEquals(listOf(1L, 2L, 3L), slices.map { it.category.id })

        val uneven = MonthCalculator.categoryBreakdown(
            october,
            listOf(expense(1, 1), expense(1, 2), expense(998, 3)),
            categories,
        )
        assertEquals(100, uneven.sumOf { it.percent })
        assertEquals(3L, uneven.first().category.id)
    }

    @Test
    fun breakdown_tiesBySortOrder_archivedIncluded_otherMonthsIgnored() {
        val categories = listOf(category(1, sortOrder = 5), category(2, sortOrder = 1, archived = true))
        val slices = MonthCalculator.categoryBreakdown(
            october,
            listOf(expense(500, 1), expense(500, 2), expense(9_999, 1, LocalDate.of(2026, 11, 1))),
            categories,
        )
        assertEquals(listOf(2L, 1L), slices.map { it.category.id })
        assertTrue(slices.first().category.archived)
        assertEquals(listOf(500L, 500L), slices.map { it.amount })
        assertEquals(listOf(50, 50), slices.map { it.percent })
    }

    @Test
    fun breakdown_nothingSpent_empty() {
        assertTrue(MonthCalculator.categoryBreakdown(october, emptyList(), listOf(category(1))).isEmpty())
    }

    // Day totals

    @Test
    fun dayTotals_incomeAndSpentPerDay() {
        val income = listOf(Income(1, "Salary", 1_235_000, day(1), null, 0), Income(2, "Refund", 20_000, day(10), null, 0))
        val expenses = listOf(expense(450_000, 1, day(1)), expense(34_250, 1, day(1)), expense(15_000, 2, day(8)))
        assertEquals(
            mapOf(
                day(1) to DayTotal(income = 1_235_000, spent = 484_250),
                day(8) to DayTotal(income = 0, spent = 15_000),
                day(10) to DayTotal(income = 20_000, spent = 0),
            ),
            MonthCalculator.dayTotals(income, expenses),
        )
    }

    // Donut

    @Test
    fun donut_tinySlicesGetMinimumSweep_totalStays360() {
        val sweeps = DonutGeometry.sweeps(listOf(484_250, 146_040, 38_960, 15_000, 10))
        assertEquals(360f, sweeps.sum(), 0.01f)
        assertTrue(sweeps.all { it >= DonutGeometry.MIN_SWEEP - 0.001f })
        assertEquals(DonutGeometry.MIN_SWEEP, sweeps.last(), 0.001f)
        // The large slices keep their proportions to each other.
        assertEquals(484_250.0 / 146_040.0, (sweeps[0] / sweeps[1]).toDouble(), 0.001)
    }

    @Test
    fun donut_singleSlice_fullCircle_manySlices_equal() {
        assertEquals(listOf(360f), DonutGeometry.sweeps(listOf(42)))
        val many = DonutGeometry.sweeps(List(80) { 1L + it })
        assertEquals(360f, many.sum(), 0.01f)
        assertTrue(many.all { it == 4.5f })
        assertTrue(DonutGeometry.sweeps(emptyList()).isEmpty())
    }
}
