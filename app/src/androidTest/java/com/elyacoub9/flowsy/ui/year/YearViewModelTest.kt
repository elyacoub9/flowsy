package com.elyacoub9.flowsy.ui.year

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.data.CategoryRepository
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.ExpenseRepository
import com.elyacoub9.flowsy.data.FakeToday
import com.elyacoub9.flowsy.data.IncomeRepository
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.data.date
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.testDatabase
import com.elyacoub9.flowsy.domain.year.MonthStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Year totals, chart and month cards follow records added or deleted elsewhere (e.g. in the opened month). */
@RunWith(AndroidJUnit4::class)
class YearViewModelTest {

    private lateinit var db: FlowsyDatabase
    private val today = FakeToday(date(2026, 10, 12))
    private lateinit var income: IncomeRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var viewModel: YearViewModel

    @Before
    fun setUp() = runBlocking {
        db = testDatabase()
        income = IncomeRepository(db, today) { 0L }
        expenses = ExpenseRepository(db, today) { 0L }
        viewModel = YearViewModel(
            SavedStateHandle(), SettingsRepository(db), income, expenses, DayTracker(today), installDate = date(2026, 9, 1),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun awaitState(condition: (YearUiState) -> Boolean): YearUiState =
        withTimeout(5_000) { viewModel.state.first { it != null && condition(it) }!! }

    @Test
    fun addAndDeleteRecords_updateTotalsChartAndCards() = runBlocking<Unit> {
        val empty = awaitState { true }
        assertEquals(2026, empty.year)
        assertEquals(null, empty.bars)

        val groceries = CategoryRepository(db).observeAll().first().first { it.builtInKey == "groceries" }.id
        income.add("Salary", 1_200_000, date(2026, 9, 1), null)
        val expenseId = expenses.add(950_000, groceries, date(2026, 9, 15), null)

        val filled = awaitState { it.summary.totals.spent == 950_000L }
        assertEquals(1_200_000L, filled.summary.totals.income)
        assertEquals(MonthStatus.RECORDS, filled.summary.months[8].status)
        assertEquals(1f, filled.bars!![8].income, 0.0001f)

        expenses.delete(expenseId)
        val afterDelete = awaitState { it.summary.totals.spent == 0L }
        assertEquals(1_200_000L, afterDelete.summary.months[8].totals.left)
        assertEquals(0f, afterDelete.bars!![8].spent, 0.0001f)
    }

    @Test
    fun yearSwitcher_boundsFromEarliestRecord() = runBlocking<Unit> {
        income.add("Salary", 100, date(2024, 6, 1), null)
        val state = awaitState { it.canGoBack }
        assertEquals(2026, state.year)
        viewModel.previousYear()
        assertEquals(2025, awaitState { it.year == 2025 }.year)
        viewModel.previousYear()
        val first = awaitState { it.year == 2024 }
        assertEquals(false, first.canGoBack)
        assertEquals(MonthStatus.RECORDS, first.summary.months[5].status)
    }
}
