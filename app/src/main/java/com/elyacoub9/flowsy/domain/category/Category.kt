package com.elyacoub9.flowsy.domain.category

/**
 * Built-in categories. [key] is stored in `builtInKey`; icon and color are stable string keys
 * mapped to drawables and theme colors in the UI.
 */
enum class BuiltInCategory(val key: String, val iconKey: String, val colorKey: String) {
    GROCERIES("groceries", "cart", "green"),
    RESTAURANTS("restaurants", "restaurant", "orange"),
    TRANSPORT("transport", "bus", "blue"),
    BILLS("bills", "bolt", "yellow"),
    HEALTH("health", "health", "red"),
    SHOPPING("shopping", "bag", "pink"),
    ENTERTAINMENT("entertainment", "movie", "violet"),
    HOME("home", "house", "cyan"),
    OTHER("other", "other", "teal");

    companion object {
        fun fromKey(key: String?): BuiltInCategory? = entries.firstOrNull { it.key == key }
    }
}

data class Category(
    val id: Long,
    /** Custom name. Null for a built-in that was never renamed (or whose name was cleared). */
    val name: String?,
    val builtInKey: String?,
    val iconKey: String,
    val colorKey: String,
    val archived: Boolean,
    val sortOrder: Int,
) {
    val builtIn: BuiltInCategory? get() = BuiltInCategory.fromKey(builtInKey)
    val isOther: Boolean get() = builtIn == BuiltInCategory.OTHER
}

object CategoryRules {

    /** "Other" can never be archived. */
    fun canArchive(category: Category): Boolean = !category.isOther

    /** "Other" can never be deleted; others only while they have no expenses. */
    fun canDelete(category: Category, expenseCount: Int): Boolean = !category.isOther && expenseCount == 0

    /**
     * The name to store. A built-in with a blank name stores null, so it shows its translated
     * name again. A custom category needs a name: returns null for blank input (invalid).
     */
    fun storedName(input: String): String? = input.trim().ifEmpty { null }

    fun isValidName(input: String, isBuiltIn: Boolean): Boolean = isBuiltIn || input.isNotBlank()

    /**
     * Categories an expense may use: any non-archived one, or [currentCategoryId] (the category the
     * expense already has), so editing an expense in an archived category keeps it.
     */
    fun canUseForExpense(category: Category, currentCategoryId: Long?): Boolean =
        !category.archived || category.id == currentCategoryId
}

/** Archived categories are hidden from pickers; the rest are ordered by use in the last 90 days. */
object ChipOrder {
    const val USAGE_DAYS = 90L

    fun mostUsedFirst(categories: List<Category>, usageCounts: Map<Long, Int>): List<Category> =
        categories
            .filterNot { it.archived }
            .sortedWith(
                compareByDescending<Category> { usageCounts[it.id] ?: 0 }
                    .thenBy { it.sortOrder }
                    .thenBy { it.id }
            )

    /** Chips for an expense: most used first; an archived [currentCategoryId] is shown first so it stays selected. */
    fun forExpense(categories: List<Category>, usageCounts: Map<Long, Int>, currentCategoryId: Long?): List<Category> {
        val archivedCurrent = categories.filter { it.archived && it.id == currentCategoryId }
        return archivedCurrent + mostUsedFirst(categories, usageCounts)
    }
}
