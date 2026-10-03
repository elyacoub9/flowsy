package com.elyacoub9.flowsy.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyLockTest {

    private lateinit var db: FlowsyDatabase
    private lateinit var settings: SettingsRepository
    private val today = FakeToday(date(2026, 10, 12))

    @Before
    fun setUp() = runBlocking {
        db = testDatabase()
        settings = SettingsRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun noRecords_currencyCanChange() = runBlocking<Unit> {
        assertFalse(settings.observeCurrencyLocked().first())
        assertTrue(settings.setCurrency("EUR"))
        assertEquals("EUR", settings.observe().first()!!.currencyCode)
    }

    @Test
    fun emptyPot_countsAsRecord_locksCurrency() = runBlocking<Unit> {
        PotRepository(db, today) { 0L }.add(PotInput("Empty", "blue", "other", null, null, 0))
        assertTrue(settings.observeCurrencyLocked().first())
        assertFalse(settings.setCurrency("EUR"))
        assertEquals("MAD", settings.observe().first()!!.currencyCode)
    }

    @Test
    fun incomeOrExpense_locksCurrency() = runBlocking<Unit> {
        val income = IncomeRepository(db, today) { 0L }
        val id = income.add("Salary", 100, today(), null)
        assertTrue(settings.observeCurrencyLocked().first())
        income.delete(id)
        assertFalse(settings.observeCurrencyLocked().first())

        val groceries = CategoryRepository(db).observeAll().first().first { it.builtInKey == "groceries" }.id
        ExpenseRepository(db, today) { 0L }.add(100, groceries, today(), null)
        assertTrue(settings.observeCurrencyLocked().first())
    }
}
