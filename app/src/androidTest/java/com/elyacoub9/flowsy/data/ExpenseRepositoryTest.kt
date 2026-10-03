package com.elyacoub9.flowsy.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import com.elyacoub9.flowsy.domain.category.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryTest {

    private lateinit var db: FlowsyDatabase
    private val today = FakeToday(date(2026, 10, 2))
    private lateinit var expenses: ExpenseRepository
    private lateinit var categories: CategoryRepository

    @Before
    fun setUp() = runBlocking<Unit> {
        db = testDatabase()
        expenses = ExpenseRepository(db, today) { 1_000L }
        categories = CategoryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun category(builtIn: BuiltInCategory): Category =
        categories.observeAll().first().first { it.builtInKey == builtIn.key }

    private suspend fun currentExpenses() = expenses.observeMonth(YearMonth.from(today())).first()

    private suspend inline fun <reified T : Throwable> assertThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }

    @Test
    fun add_andListInCalendarMonth() = runBlocking<Unit> {
        val groceries = category(BuiltInCategory.GROCERIES)
        expenses.add(82_040, groceries.id, today(), "Market")
        expenses.add(64_000, groceries.id, date(2026, 10, 1), null)
        expenses.add(1_000, groceries.id, date(2026, 9, 30), null) // September: not listed in October
        assertEquals(listOf(82_040L, 64_000L), currentExpenses().map { it.amount })
    }

    @Test
    fun dates_futureRefused_pastAllowed() = runBlocking<Unit> {
        val id = category(BuiltInCategory.GROCERIES).id
        assertThrows<IllegalArgumentException> { expenses.add(100, id, today().plusDays(1), null) }
        expenses.add(100, id, date(2020, 1, 15), null)
        expenses.add(100, id, today(), null)
    }

    @Test
    fun zeroAmountRefused() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> { expenses.add(0, category(BuiltInCategory.HOME).id, today(), null) }
    }

    @Test
    fun archivedCategory_keptWhenEditing_refusedForNewOrSwitch() = runBlocking<Unit> {
        val groceries = category(BuiltInCategory.GROCERIES)
        val transport = category(BuiltInCategory.TRANSPORT)
        val inGroceries = expenses.add(1_000, groceries.id, today(), null)
        val inTransport = expenses.add(2_000, transport.id, today(), null)
        categories.setArchived(groceries.id, true)

        // Saving without changing the archived category keeps it.
        expenses.update(inGroceries, 1_500, groceries.id, today(), "edited")
        val edited = currentExpenses().first { it.id == inGroceries }
        assertEquals(groceries.id, edited.categoryId)
        assertEquals(1_500L, edited.amount)

        assertThrows<IllegalArgumentException> { expenses.add(1, groceries.id, today(), null) }
        assertThrows<IllegalArgumentException> { expenses.update(inTransport, 2_000, groceries.id, today(), null) }
    }

    @Test
    fun deleteAndRestore_keepsIdAndCreatedAt() = runBlocking<Unit> {
        val id = expenses.add(5_000, category(BuiltInCategory.SHOPPING).id, today(), "Shoes")
        val deleted = expenses.delete(id)!!
        assertTrue(currentExpenses().isEmpty())
        expenses.restore(deleted)
        assertEquals(listOf(deleted), currentExpenses())
    }

    @Test
    fun categoryWithExpenses_cannotBeDeleted_foreignKeyRestricts() = runBlocking<Unit> {
        val home = category(BuiltInCategory.HOME)
        expenses.add(1_000, home.id, today(), null)
        assertFalse(categories.delete(home.id))
        try {
            db.categoryDao().delete(home.id)
            fail("Expected a foreign key error")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun categoryWithoutExpenses_canBeDeleted() = runBlocking<Unit> {
        val health = category(BuiltInCategory.HEALTH)
        assertTrue(categories.delete(health.id))
        assertNull(categories.observeAll().first().firstOrNull { it.id == health.id })
    }

    @Test
    fun other_neverArchivedOrDeleted() = runBlocking<Unit> {
        val other = category(BuiltInCategory.OTHER)
        assertFalse(categories.delete(other.id))
        assertThrows<IllegalArgumentException> { categories.setArchived(other.id, true) }
        assertFalse(category(BuiltInCategory.OTHER).archived)
    }

    @Test
    fun builtInRenamedThenCleared_storesNull() = runBlocking<Unit> {
        val groceries = category(BuiltInCategory.GROCERIES)
        categories.update(groceries.id, "Food", groceries.iconKey, groceries.colorKey)
        assertEquals("Food", category(BuiltInCategory.GROCERIES).name)
        categories.update(groceries.id, "  ", groceries.iconKey, groceries.colorKey)
        assertNull(category(BuiltInCategory.GROCERIES).name)
    }

    @Test
    fun customCategory_needsName_andMoves() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> { categories.add(" ", "paw", "red") }
        categories.add("Pets", "paw", "red")
        val pets = categories.observeAll().first().last()
        assertEquals("Pets", pets.name)
        categories.move(pets.id, up = true)
        val order = categories.observeAll().first().map { it.name ?: it.builtInKey }
        assertEquals(listOf("Pets", "other"), order.takeLast(2))
    }

    @Test
    fun usage_countsLast90Days() = runBlocking<Unit> {
        val transport = category(BuiltInCategory.TRANSPORT)
        expenses.add(100, transport.id, today(), null)
        expenses.add(100, transport.id, date(2026, 9, 25), null)
        assertEquals(mapOf(transport.id to 2), categories.observeUsage(today()).first())
        // 90 days later the September expense no longer counts.
        assertEquals(mapOf(transport.id to 1), categories.observeUsage(date(2026, 12, 30)).first())
    }

    @Test
    fun earliestDate_nullWhenEmpty_thenOldestExpense() = runBlocking<Unit> {
        assertNull(expenses.observeEarliestDate().first())
        val id = category(BuiltInCategory.HOME).id
        expenses.add(100, id, today(), null)
        expenses.add(100, id, date(2024, 2, 29), null)
        assertEquals(date(2024, 2, 29), expenses.observeEarliestDate().first())
    }
}
