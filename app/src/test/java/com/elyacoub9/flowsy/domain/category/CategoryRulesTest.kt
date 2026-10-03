package com.elyacoub9.flowsy.domain.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRulesTest {

    private fun category(
        id: Long,
        builtIn: BuiltInCategory? = null,
        archived: Boolean = false,
        sortOrder: Int = id.toInt(),
    ) = Category(
        id = id,
        name = if (builtIn == null) "Custom $id" else null,
        builtInKey = builtIn?.key,
        iconKey = "other",
        colorKey = "red",
        archived = archived,
        sortOrder = sortOrder,
    )

    @Test
    fun other_neverArchivedOrDeleted() {
        val other = category(8, BuiltInCategory.OTHER)
        assertFalse(CategoryRules.canArchive(other))
        assertFalse(CategoryRules.canDelete(other, expenseCount = 0))
    }

    @Test
    fun deleteOnlyWithoutExpenses() {
        val groceries = category(1, BuiltInCategory.GROCERIES)
        assertTrue(CategoryRules.canArchive(groceries))
        assertTrue(CategoryRules.canDelete(groceries, expenseCount = 0))
        assertFalse(CategoryRules.canDelete(groceries, expenseCount = 1))
        assertTrue(CategoryRules.canDelete(category(9), expenseCount = 0))
    }

    @Test
    fun names_builtInClearedShowsTranslatedAgain() {
        assertNull(CategoryRules.storedName("   "))
        assertEquals("Food", CategoryRules.storedName("  Food "))
        assertTrue(CategoryRules.isValidName("", isBuiltIn = true))
        assertFalse(CategoryRules.isValidName(" ", isBuiltIn = false))
        assertTrue(CategoryRules.isValidName("Pets", isBuiltIn = false))
    }

    @Test
    fun archivedCategory_onlyForItsExistingExpenses() {
        val archived = category(5, archived = true)
        assertFalse(CategoryRules.canUseForExpense(archived, currentCategoryId = null))
        assertFalse(CategoryRules.canUseForExpense(archived, currentCategoryId = 1))
        assertTrue(CategoryRules.canUseForExpense(archived, currentCategoryId = 5))
        assertTrue(CategoryRules.canUseForExpense(category(1), currentCategoryId = null))
    }

    @Test
    fun builtInKeys_unique_andLookup() {
        assertEquals(9, BuiltInCategory.entries.map { it.key }.toSet().size)
        assertEquals(BuiltInCategory.OTHER, BuiltInCategory.fromKey("other"))
        assertNull(BuiltInCategory.fromKey(null))
        assertNull(BuiltInCategory.fromKey("unknown"))
    }

    @Test
    fun chips_mostUsedFirst_archivedHidden_tiesBySortOrder() {
        val a = category(1, sortOrder = 0)
        val b = category(2, sortOrder = 1)
        val c = category(3, sortOrder = 2)
        val archived = category(4, archived = true, sortOrder = 3)
        val ordered = ChipOrder.mostUsedFirst(listOf(a, b, c, archived), mapOf(3L to 5, 2L to 5, 4L to 99))
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })

        val all = listOf(a, b, c, archived)
        assertEquals(listOf(1L, 2L, 3L), ChipOrder.forExpense(all, emptyMap(), currentCategoryId = null).map { it.id })
        assertEquals(listOf(4L, 1L, 2L, 3L), ChipOrder.forExpense(all, emptyMap(), currentCategoryId = 4).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), ChipOrder.forExpense(all, emptyMap(), currentCategoryId = 2).map { it.id })
    }

    @Test
    fun builtIns_specOrder_withBills() {
        assertEquals(
            listOf("groceries", "restaurants", "transport", "bills", "health", "shopping", "entertainment", "home", "other"),
            BuiltInCategory.entries.map { it.key },
        )
        assertEquals("bolt", BuiltInCategory.BILLS.iconKey)
    }
}
