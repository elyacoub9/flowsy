package com.elyacoub9.flowsy.ui.month

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.CategoryRepository
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.ExpenseRepository
import com.elyacoub9.flowsy.data.IncomeRepository
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.month.CategorySlice
import com.elyacoub9.flowsy.domain.month.DayTotal
import com.elyacoub9.flowsy.domain.month.MonthBounds
import com.elyacoub9.flowsy.domain.month.MonthCalculator
import com.elyacoub9.flowsy.domain.month.MonthNavigation
import com.elyacoub9.flowsy.domain.month.MonthTotals
import com.elyacoub9.flowsy.domain.month.SpentRatio
import com.elyacoub9.flowsy.domain.month.yearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

sealed interface MonthRecord {
    val date: LocalDate
    val createdAt: Long

    data class IncomeRecord(val income: Income) : MonthRecord {
        override val date get() = income.date
        override val createdAt get() = income.createdAt
    }

    data class ExpenseRecord(val expense: Expense, val category: Category) : MonthRecord {
        override val date get() = expense.date
        override val createdAt get() = expense.createdAt
    }
}

data class RecordDay(val date: LocalDate, val total: DayTotal, val records: List<MonthRecord>)

data class MonthUiState(
    val month: YearMonth,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val totals: MonthTotals,
    val ratio: SpentRatio,
    /** Largest first; empty when nothing was spent. */
    val slices: List<CategorySlice>,
    /** Newest day first; inside a day, newest record first. */
    val days: List<RecordDay>,
    val form: RecordFormData,
) {
    val currencyCode: String get() = form.currencyCode
    val today: LocalDate get() = form.today

    companion object {
        /** All values derived from the month's records; the money math itself is in the domain. */
        fun build(
            month: YearMonth,
            bounds: MonthBounds,
            income: List<Income>,
            expenses: List<Expense>,
            categories: List<Category>,
            form: RecordFormData,
        ): MonthUiState {
            val totals = MonthCalculator.totals(month, income, expenses)
            return MonthUiState(
                month = month,
                canGoBack = bounds.canGoBack(month),
                canGoForward = bounds.canGoForward(month),
                totals = totals,
                ratio = MonthCalculator.spentRatio(totals),
                slices = MonthCalculator.categoryBreakdown(month, expenses, categories),
                days = days(income, expenses, categories),
                form = form,
            )
        }

        private fun days(income: List<Income>, expenses: List<Expense>, categories: List<Category>): List<RecordDay> {
            val byId = categories.associateBy { it.id }
            val totals = MonthCalculator.dayTotals(income, expenses)
            val all = income.map { MonthRecord.IncomeRecord(it) } +
                expenses.mapNotNull { e -> byId[e.categoryId]?.let { MonthRecord.ExpenseRecord(e, it) } }
            return all
                .sortedWith(compareByDescending<MonthRecord> { it.date }.thenByDescending { it.createdAt })
                .groupBy { it.date }
                .map { (date, list) -> RecordDay(date, totals.getValue(date), list) }
        }
    }
}

private data class MonthRecords(val month: YearMonth, val income: List<Income>, val expenses: List<Expense>)

private data class Usage(val categories: Map<Long, Int>, val labels: Map<String, Int>)

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    private val savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    incomeRepository: IncomeRepository,
    expenseRepository: ExpenseRepository,
    categoryRepository: CategoryRepository,
    private val dayTracker: DayTracker,
    installDate: LocalDate,
) : ViewModel() {

    val editor = RecordEditor(expenseRepository, incomeRepository, viewModelScope)

    /** Month passed by a Year card, kept for the life of this screen so the switcher can reach it. */
    private val startMonth: YearMonth? =
        (savedStateHandle.get<String>(START_KEY) ?: savedStateHandle.get<String>(MONTH_ARG)?.also { savedStateHandle[START_KEY] = it })
            ?.let(YearMonth::parse)

    private val bounds: Flow<MonthBounds> = combine(
        incomeRepository.observeEarliestDate(),
        expenseRepository.observeEarliestDate(),
        dayTracker.day,
    ) { earliestIncome, earliestExpense, today ->
        MonthNavigation.bounds(listOfNotNull(earliestIncome, earliestExpense).minOrNull(), installDate, today)
            .including(startMonth)
    }.distinctUntilChanged()

    /** Shown month: the saved selection (or nav argument) kept within bounds; no selection = current month. */
    private val month: Flow<Pair<YearMonth, MonthBounds>> = combine(
        savedStateHandle.getStateFlow<String?>(MONTH_ARG, null),
        bounds,
    ) { selected, bounds -> bounds.coerce(selected?.let(YearMonth::parse) ?: bounds.last) to bounds }
        .distinctUntilChanged()

    private val records: Flow<Pair<MonthRecords, MonthBounds>> = month.flatMapLatest { (month, bounds) ->
        combine(incomeRepository.observeMonth(month), expenseRepository.observeMonth(month)) { income, expenses ->
            MonthRecords(month, income, expenses) to bounds
        }
    }

    private val usage: Flow<Usage> = dayTracker.day.flatMapLatest { day ->
        combine(categoryRepository.observeUsage(day), incomeRepository.observeLabelUsage(day), ::Usage)
    }

    val state: StateFlow<MonthUiState?> = combine(
        settingsRepository.observe().filterNotNull().map { it.currencyCode }.distinctUntilChanged(),
        dayTracker.day,
        categoryRepository.observeAll(),
        records,
        usage,
    ) { currencyCode, today, categories, (records, bounds), usage ->
        MonthUiState.build(
            month = records.month,
            bounds = bounds,
            income = records.income,
            expenses = records.expenses,
            categories = categories,
            form = RecordFormData(
                currencyCode = currencyCode,
                today = today,
                defaultDate = MonthNavigation.defaultRecordDate(records.month, today),
                categories = categories,
                categoryUsage = usage.categories,
                labelUsage = usage.labels,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = select(state.value?.takeIf { it.canGoBack }?.month?.minusMonths(1))

    fun nextMonth() = select(state.value?.takeIf { it.canGoForward }?.month?.plusMonths(1))

    /** Back on the current month, the selection is cleared so the screen follows the date across midnight. */
    private fun select(month: YearMonth?) {
        month ?: return
        savedStateHandle[MONTH_ARG] = month.takeIf { it < dayTracker.day.value.yearMonth() }?.toString()
    }

    companion object {
        /** Optional nav argument (e.g. "2026-10"), also the key of the saved selection. */
        const val MONTH_ARG = "month"
        private const val START_KEY = "startMonth"

        val Factory = viewModelFactory {
            initializer {
                val c = container()
                MonthViewModel(
                    createSavedStateHandle(), c.settingsRepository, c.incomeRepository, c.expenseRepository,
                    c.categoryRepository, c.dayTracker, c.installDate,
                )
            }
        }
    }
}
