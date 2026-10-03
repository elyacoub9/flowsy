package com.elyacoub9.flowsy.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class IncomeRepositoryTest {

    private lateinit var db: FlowsyDatabase
    private val today = FakeToday(date(2026, 10, 12))
    private lateinit var income: IncomeRepository

    @Before
    fun setUp() = runBlocking {
        db = testDatabase()
        income = IncomeRepository(db, today) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun october() = income.observeMonth(YearMonth.of(2026, 10)).first()

    private suspend inline fun <reified T : Throwable> assertThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }

    @Test
    fun add_listedInItsCalendarMonth_newestFirst() = runBlocking<Unit> {
        income.add("Salary", 1_235_000, date(2026, 10, 1), null)
        income.add("Refund", 20_000, date(2026, 10, 10), "Tax")
        income.add("Bonus", 50_000, date(2026, 9, 30), null)
        assertEquals(listOf("Refund", "Salary"), october().map { it.label })
        assertEquals(listOf("Bonus"), income.observeMonth(YearMonth.of(2026, 9)).first().map { it.label })
    }

    @Test
    fun label_trimmed_andRequired() = runBlocking<Unit> {
        income.add("  Salary ", 100, today(), null)
        assertEquals("Salary", october().single().label)
        assertThrows<IllegalArgumentException> { income.add("  ", 100, today(), null) }
    }

    @Test
    fun zeroAmountAndFutureDateRefused_pastAllowed() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> { income.add("Salary", 0, today(), null) }
        assertThrows<IllegalArgumentException> { income.add("Salary", 100, today().plusDays(1), null) }
        income.add("Salary", 100, date(2018, 6, 30), null)
    }

    @Test
    fun update_changesAllFields() = runBlocking<Unit> {
        val id = income.add("Salary", 100, today(), null)
        income.update(id, "Freelance", 250, date(2026, 10, 2), "Client")
        val updated = october().single()
        assertEquals(listOf("Freelance", 250L, date(2026, 10, 2), "Client"), listOf(updated.label, updated.amount, updated.date, updated.note))
        assertThrows<IllegalArgumentException> { income.update(id, "Freelance", 250, today().plusDays(1), null) }
    }

    @Test
    fun deleteAndRestore_keepsIdAndCreatedAt() = runBlocking<Unit> {
        val id = income.add("Salary", 100, today(), null)
        val deleted = income.delete(id)!!
        assertTrue(october().isEmpty())
        income.restore(deleted)
        assertEquals(listOf(deleted), october())
    }

    @Test
    fun labelUsage_last90Days() = runBlocking<Unit> {
        income.add("Salary", 100, today(), null)
        income.add("Salary", 100, date(2026, 9, 1), null)
        income.add("Refund", 100, date(2026, 7, 1), null) // more than 90 days before today
        assertEquals(mapOf("Salary" to 2), income.observeLabelUsage(today()).first())
    }

    @Test
    fun earliestDate_nullWhenEmpty_thenOldestIncome() = runBlocking<Unit> {
        assertEquals(null, income.observeEarliestDate().first())
        income.add("Salary", 100, date(2026, 9, 1), null)
        income.add("Salary", 100, date(2025, 12, 31), null)
        assertEquals(date(2025, 12, 31), income.observeEarliestDate().first())
    }
}
