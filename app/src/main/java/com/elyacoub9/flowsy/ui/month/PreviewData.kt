package com.elyacoub9.flowsy.ui.month

import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.month.MonthBounds
import java.time.LocalDate
import java.time.YearMonth

/** Sample data for Compose previews only: the spec's October 2026 money case and its variations. */
internal object PreviewData {
    private val today = LocalDate.of(2026, 10, 12)
    private val october = YearMonth.of(2026, 10)
    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 10, dayOfMonth)

    val categories = BuiltInCategory.entries.mapIndexed { i, b ->
        Category(id = i + 1L, name = null, builtInKey = b.key, iconKey = b.iconKey, colorKey = b.colorKey, archived = false, sortOrder = i)
    }

    private fun id(builtIn: BuiltInCategory) = categories[builtIn.ordinal].id

    val form = RecordFormData(
        currencyCode = "MAD",
        today = today,
        defaultDate = today,
        categories = categories,
        categoryUsage = mapOf(id(BuiltInCategory.GROCERIES) to 6, id(BuiltInCategory.BILLS) to 4),
        labelUsage = mapOf("Salary" to 3, "Refund" to 1),
    )

    private val income = listOf(
        Income(1, "Salary", 1_235_000, day(1), null, 1),
        Income(2, "Refund", 20_000, day(10), null, 2),
    )

    private val expenses = listOf(
        Expense(1, 450_000, id(BuiltInCategory.BILLS), day(1), "Rent", 3),
        Expense(2, 34_250, id(BuiltInCategory.BILLS), day(3), "Electricity", 4),
        Expense(3, 82_040, id(BuiltInCategory.GROCERIES), day(5), "Market", 5),
        Expense(4, 15_000, id(BuiltInCategory.TRANSPORT), day(8), null, 6),
        Expense(5, 38_960, id(BuiltInCategory.RESTAURANTS), day(10), "Dinner", 7),
        Expense(6, 64_000, id(BuiltInCategory.GROCERIES), today, null, 8),
    )

    private fun state(income: List<Income>, expenses: List<Expense>) = MonthUiState.build(
        month = october,
        bounds = MonthBounds(YearMonth.of(2026, 9), october),
        income = income,
        expenses = expenses,
        categories = categories,
        form = form,
    )

    /** Income 12,550.00, spent 6,842.50, left 5,707.50, 54 %. */
    val normal = state(income, expenses)

    /** Plus 6,000.00 of shopping: over by 292.50, 102 %. */
    val over = state(income, expenses + Expense(7, 600_000, id(BuiltInCategory.SHOPPING), today, "Laptop", 9))

    val noIncome = state(emptyList(), expenses)

    val noExpenses = state(income, emptyList())

    val empty = state(emptyList(), emptyList())
}
