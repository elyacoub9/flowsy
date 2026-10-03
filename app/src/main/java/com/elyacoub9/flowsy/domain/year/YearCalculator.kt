package com.elyacoub9.flowsy.domain.year

import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.month.MonthCalculator
import com.elyacoub9.flowsy.domain.month.MonthTotals
import com.elyacoub9.flowsy.domain.month.SpentRatio
import com.elyacoub9.flowsy.domain.month.yearMonth
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.YearMonth

enum class MonthStatus {
    /** After the current month: dimmed, no amounts. */
    FUTURE,

    /** Past or current month without any income or expense: "No records", not 0.00. */
    NO_RECORDS,

    RECORDS,
}

data class MonthSummary(
    val month: YearMonth,
    val status: MonthStatus,
    val isCurrent: Boolean,
    val totals: MonthTotals,
    val ratio: SpentRatio,
)

data class YearSummary(
    val year: Int,
    /** January to December. */
    val months: List<MonthSummary>,
    /** Sum of the 12 months. */
    val totals: MonthTotals,
)

object YearCalculator {

    /** [income] and [expenses] may contain other years; only [year] is counted. */
    fun summary(year: Int, income: List<Income>, expenses: List<Expense>, today: LocalDate): YearSummary {
        val current = today.yearMonth()
        val incomeByMonth = income.groupBy { it.date.yearMonth() }
        val expensesByMonth = expenses.groupBy { it.date.yearMonth() }
        val months = (1..12).map { m ->
            val month = YearMonth.of(year, m)
            val monthIncome = incomeByMonth[month].orEmpty()
            val monthExpenses = expensesByMonth[month].orEmpty()
            val totals = MonthCalculator.totals(month, monthIncome, monthExpenses)
            MonthSummary(
                month = month,
                status = when {
                    month > current -> MonthStatus.FUTURE
                    monthIncome.isEmpty() && monthExpenses.isEmpty() -> MonthStatus.NO_RECORDS
                    else -> MonthStatus.RECORDS
                },
                isCurrent = month == current,
                totals = totals,
                ratio = MonthCalculator.spentRatio(totals),
            )
        }
        val totals = months.fold(MonthTotals(0, 0)) { sum, m ->
            MonthTotals(Math.addExact(sum.income, m.totals.income), Math.addExact(sum.spent, m.totals.spent))
        }
        return YearSummary(year, months, totals)
    }
}

/** Bar heights for one month, 0..1 of the year's largest single value. */
data class MonthBars(val income: Float, val spent: Float)

object YearChart {

    /** One entry per month, scaled to the largest income or spent of the year; null when every value is 0. */
    fun bars(summary: YearSummary): List<MonthBars>? {
        val max = summary.months.maxOf { maxOf(it.totals.income, it.totals.spent) }
        if (max == 0L) return null
        return summary.months.map { MonthBars(fraction(it.totals.income, max), fraction(it.totals.spent, max)) }
    }

    /** Month whose values are labelled first: the current month, else the last month with records, else none. */
    fun defaultSelection(summary: YearSummary): YearMonth? =
        summary.months.firstOrNull { it.isCurrent }?.month
            ?: summary.months.lastOrNull { it.status == MonthStatus.RECORDS }?.month

    private fun fraction(value: Long, max: Long): Float =
        BigDecimal.valueOf(value).divide(BigDecimal.valueOf(max), MathContext.DECIMAL64).toFloat()
}
