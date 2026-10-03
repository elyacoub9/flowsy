package com.elyacoub9.flowsy.ui.year

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.ExpenseRepository
import com.elyacoub9.flowsy.data.IncomeRepository
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.year.MonthBars
import com.elyacoub9.flowsy.domain.year.YearBounds
import com.elyacoub9.flowsy.domain.year.YearCalculator
import com.elyacoub9.flowsy.domain.year.YearChart
import com.elyacoub9.flowsy.domain.year.YearNavigation
import com.elyacoub9.flowsy.domain.year.YearSummary
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

data class YearUiState(
    val currencyCode: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val summary: YearSummary,
    /** Null when every month is 0: the chart shows an empty state. */
    val bars: List<MonthBars>?,
    /** Month whose values are labelled until another column is tapped. */
    val defaultChartMonth: YearMonth?,
) {
    val year: Int get() = summary.year

    companion object {
        fun build(
            currencyCode: String,
            year: Int,
            bounds: YearBounds,
            income: List<Income>,
            expenses: List<Expense>,
            today: LocalDate,
        ): YearUiState {
            val summary = YearCalculator.summary(year, income, expenses, today)
            return YearUiState(
                currencyCode = currencyCode,
                canGoBack = bounds.canGoBack(year),
                canGoForward = bounds.canGoForward(year),
                summary = summary,
                bars = YearChart.bars(summary),
                defaultChartMonth = YearChart.defaultSelection(summary),
            )
        }
    }
}

private data class YearRecords(val year: Int, val bounds: YearBounds, val income: List<Income>, val expenses: List<Expense>)

@OptIn(ExperimentalCoroutinesApi::class)
class YearViewModel(
    private val savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    incomeRepository: IncomeRepository,
    expenseRepository: ExpenseRepository,
    private val dayTracker: DayTracker,
    installDate: LocalDate,
) : ViewModel() {

    private val bounds: Flow<YearBounds> = combine(
        incomeRepository.observeEarliestDate(),
        expenseRepository.observeEarliestDate(),
        dayTracker.day,
    ) { earliestIncome, earliestExpense, today ->
        YearNavigation.bounds(listOfNotNull(earliestIncome, earliestExpense).minOrNull(), installDate, today)
    }.distinctUntilChanged()

    /** Saved selection kept within bounds; no selection = current year. Room flows keep it live. */
    private val records: Flow<YearRecords> = combine(savedStateHandle.getStateFlow<Int?>(YEAR_KEY, null), bounds) { selected, bounds ->
        bounds.coerce(selected ?: bounds.last) to bounds
    }
        .distinctUntilChanged()
        .flatMapLatest { (year, bounds) ->
            combine(incomeRepository.observeYear(year), expenseRepository.observeYear(year)) { income, expenses ->
                YearRecords(year, bounds, income, expenses)
            }
        }

    val state: StateFlow<YearUiState?> = combine(
        settingsRepository.observe().filterNotNull().map { it.currencyCode }.distinctUntilChanged(),
        dayTracker.day,
        records,
    ) { currencyCode, today, records ->
        YearUiState.build(currencyCode, records.year, records.bounds, records.income, records.expenses, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousYear() = select(state.value?.takeIf { it.canGoBack }?.year?.minus(1))

    fun nextYear() = select(state.value?.takeIf { it.canGoForward }?.year?.plus(1))

    /** Back on the current year, the selection is cleared so the screen follows the date. */
    private fun select(year: Int?) {
        year ?: return
        savedStateHandle[YEAR_KEY] = year.takeIf { it < dayTracker.day.value.year }
    }

    companion object {
        const val YEAR_KEY = "year"

        val Factory = viewModelFactory {
            initializer {
                val c = container()
                YearViewModel(
                    createSavedStateHandle(), c.settingsRepository, c.incomeRepository, c.expenseRepository,
                    c.dayTracker, c.installDate,
                )
            }
        }
    }
}
