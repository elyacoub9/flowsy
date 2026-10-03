package com.elyacoub9.flowsy.ui.month

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.data.CategoryRepository
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.ExpenseRepository
import com.elyacoub9.flowsy.data.FakeToday
import com.elyacoub9.flowsy.data.IncomeRepository
import com.elyacoub9.flowsy.data.PotInput
import com.elyacoub9.flowsy.data.PotRepository
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.data.date
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.testDatabase
import com.elyacoub9.flowsy.domain.pot.MovementType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** APP_SPEC: savings never affect a month's Left. */
@RunWith(AndroidJUnit4::class)
class MonthSavingsTest {

    private lateinit var db: FlowsyDatabase
    private val today = FakeToday(date(2026, 10, 12))

    @Before
    fun setUp() = runBlocking { db = testDatabase() }

    @After
    fun tearDown() = db.close()

    @Test
    fun potDepositsAndWithdrawals_doNotChangeLeft() = runBlocking<Unit> {
        val income = IncomeRepository(db, today) { 0L }
        val expenses = ExpenseRepository(db, today) { 0L }
        val categories = CategoryRepository(db)
        val viewModel = MonthViewModel(
            SavedStateHandle(), SettingsRepository(db), income, expenses, categories, DayTracker(today), date(2026, 10, 1),
        )
        income.add("Salary", 1_255_000, date(2026, 10, 1), null)
        val groceries = categories.observeAll().first().first { it.builtInKey == "groceries" }.id
        expenses.add(684_250, groceries, date(2026, 10, 5), null)
        val before = withTimeout(5_000) { viewModel.state.first { it?.totals?.spent == 684_250L }!! }
        assertEquals(570_750L, before.totals.left)

        val pots = PotRepository(db, today) { 0L }
        val pot = pots.add(PotInput("Emergency", "green", "heart", null, null, 1_000_000))
        pots.addMovement(pot, MovementType.DEPOSIT, 100_000, date(2026, 10, 6), null)
        pots.addMovement(pot, MovementType.WITHDRAWAL, 30_000, date(2026, 10, 7), null)
        delay(500) // let every flow settle

        val after = withTimeout(5_000) { viewModel.state.first { it != null }!! }
        assertEquals(before.totals, after.totals)
        assertEquals(570_750L, after.totals.left)
    }
}
