package com.elyacoub9.flowsy.domain.month

import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/** First and last day of a calendar month. Months are never stored; always derived from dates. */
data class MonthRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        fun of(month: YearMonth) = MonthRange(month.atDay(1), month.atEndOfMonth())
    }
}

fun LocalDate.yearMonth(): YearMonth = YearMonth.from(this)

data class MonthTotals(
    val income: Long,
    val spent: Long,
) {
    /** income − spent; negative means over. */
    val left: Long get() = Math.subtractExact(income, spent)
    val isOver: Boolean get() = left < 0
    /** Positive amount shown as "Over by X"; 0 when not over. */
    val overBy: Long get() = if (isOver) Math.negateExact(left) else 0
}

/** Sums use exact arithmetic: an overflow throws instead of giving a wrong total. */
object MonthCalculator {

    fun totals(month: YearMonth, income: List<Income>, expenses: List<Expense>): MonthTotals {
        val range = MonthRange.of(month)
        return MonthTotals(
            income = income.filter { it.date in range }.sumExact { it.amount },
            spent = expenses.filter { it.date in range }.sumExact { it.amount },
        )
    }

    fun spentByCategory(month: YearMonth, expenses: List<Expense>): Map<Long, Long> {
        val range = MonthRange.of(month)
        return expenses.filter { it.date in range }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumExact { it.amount } }
    }

    fun spentRatio(totals: MonthTotals): SpentRatio = when {
        totals.income == 0L && totals.spent == 0L -> SpentRatio.Empty
        totals.income == 0L -> SpentRatio.NoIncome
        else -> {
            val percent = floorPercent(totals.spent, totals.income)
            SpentRatio.Ratio(
                percent = percent,
                barPercent = percent.coerceAtMost(100),
                // From the amounts: 100.4 % shows "100 %" but is still over.
                over = totals.spent > totals.income,
            )
        }
    }

    /**
     * Spending per category in the month, largest first (ties: [Category.sortOrder], then id).
     * Percentages of spent are whole numbers that always add up to 100 (largest remainder method).
     * Archived categories are included. Empty when nothing was spent.
     */
    fun categoryBreakdown(month: YearMonth, expenses: List<Expense>, categories: List<Category>): List<CategorySlice> {
        val totals = spentByCategory(month, expenses)
        val spent = totals.values.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
        if (spent == 0L) return emptyList()
        val byId = categories.associateBy { it.id }
        val ordered = totals.entries
            .mapNotNull { (id, amount) -> byId[id]?.let { it to amount } }
            .sortedWith(
                compareByDescending<Pair<Category, Long>> { it.second }
                    .thenBy { it.first.sortOrder }
                    .thenBy { it.first.id }
            )
        val scaled = ordered.map { Math.multiplyExact(it.second, 100L) }
        val floors = scaled.map { (it / spent).toInt() }.toMutableList()
        // Hand out the missing points to the largest remainders (ties: the larger amount, already first).
        val missing = 100 - floors.sum()
        ordered.indices
            .sortedByDescending { scaled[it] % spent }
            .take(missing)
            .forEach { floors[it] += 1 }
        return ordered.mapIndexed { i, (category, amount) -> CategorySlice(category, amount, floors[i]) }
    }

    /** Income and spent per day, for the day headers. Days without records are absent. */
    fun dayTotals(income: List<Income>, expenses: List<Expense>): Map<LocalDate, DayTotal> {
        val incomeByDay = income.groupBy { it.date }.mapValues { (_, list) -> list.sumExact { it.amount } }
        val spentByDay = expenses.groupBy { it.date }.mapValues { (_, list) -> list.sumExact { it.amount } }
        return (incomeByDay.keys + spentByDay.keys).associateWith {
            DayTotal(income = incomeByDay[it] ?: 0, spent = spentByDay[it] ?: 0)
        }
    }

    /** part / whole as a whole percent, rounded down (54.52 % → 54 %). */
    private fun floorPercent(part: Long, whole: Long): Int =
        (BigInteger.valueOf(part) * BigInteger.valueOf(100) / BigInteger.valueOf(whole))
            .min(BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            .toInt()

    private inline fun <T> List<T>.sumExact(amount: (T) -> Long): Long =
        fold(0L) { sum, item -> Math.addExact(sum, amount(item)) }
}

/** The "X % of income spent" bar. */
sealed interface SpentRatio {
    /** No income and no expenses: no bar, no chart. */
    data object Empty : SpentRatio

    /** Spent without income: "No income this month". */
    data object NoIncome : SpentRatio

    /**
     * [percent] is the real value shown in the text (rounded down, can exceed 100); [barPercent] is
     * capped at 100 for the bar; [over] is true when spent > income.
     */
    data class Ratio(val percent: Int, val barPercent: Int, val over: Boolean) : SpentRatio
}

data class CategorySlice(val category: Category, val amount: Long, val percent: Int)

data class DayTotal(val income: Long, val spent: Long)
