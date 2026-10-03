package com.elyacoub9.flowsy.ui.year

import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.year.YearBounds
import java.time.LocalDate

/** Sample data for Compose previews only: the spec's Year 2026 case and variations. */
internal object PreviewData {
    private val today = LocalDate.of(2026, 10, 12)
    private val bounds = YearBounds(2025, 2026)

    private fun income(amount: Long, date: LocalDate) = Income(0, "Salary", amount, date, null, 0)
    private fun expense(amount: Long, date: LocalDate) = Expense(0, amount, 1, date, null, 0)

    private val income2026 = listOf(
        income(1_200_000, LocalDate.of(2026, 9, 1)),
        income(1_235_000, LocalDate.of(2026, 10, 1)),
        income(20_000, LocalDate.of(2026, 10, 10)),
    )
    private val expenses2026 = listOf(expense(950_000, LocalDate.of(2026, 9, 15)), expense(684_250, LocalDate.of(2026, 10, 5)))

    private fun state(year: Int, income: List<Income>, expenses: List<Expense>) =
        YearUiState.build("MAD", year, bounds, income, expenses, today)

    /** 2026: income 24,550.00, spent 16,342.50, left 8,207.50; Nov and Dec still to come. */
    val current = state(2026, income2026, expenses2026)

    /** Every month of 2025 has records; March spends more than its income. */
    val pastFull = state(
        2025,
        (1..12).map { income(1_150_000 + it * 10_000L, LocalDate.of(2025, it, 1)) },
        (1..12).map { expense(if (it == 3) 1_400_000 else 700_000 + it * 25_000L, LocalDate.of(2025, it, 20)) },
    )

    val empty = state(2026, emptyList(), emptyList())

    /** October 2026 with an extra 6,000.00: over by 292.50. */
    val overBudget = state(2026, income2026, expenses2026 + expense(600_000, LocalDate.of(2026, 10, 11)))
}
