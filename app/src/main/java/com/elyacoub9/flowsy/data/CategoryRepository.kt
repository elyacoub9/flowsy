package com.elyacoub9.flowsy.data

import androidx.room.withTransaction
import com.elyacoub9.flowsy.data.db.CategoryEntity
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.toDomain
import com.elyacoub9.flowsy.data.db.toEntity
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.category.CategoryRules
import com.elyacoub9.flowsy.domain.category.ChipOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CategoryRepository(private val db: FlowsyDatabase) {

    private val dao = db.categoryDao()

    /** All categories in sort order, archived included. */
    fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Expense count per category over the last [ChipOrder.USAGE_DAYS] days up to [day]. */
    fun observeUsage(day: LocalDate): Flow<Map<Long, Int>> =
        dao.observeUsage(day.minusDays(ChipOrder.USAGE_DAYS - 1).toEpochDay(), day.toEpochDay())
            .map { rows -> rows.associate { it.categoryId to it.count } }

    suspend fun add(name: String, iconKey: String, colorKey: String) {
        require(CategoryRules.isValidName(name, isBuiltIn = false)) { "Name required" }
        db.withTransaction {
            dao.insert(
                CategoryEntity(
                    name = CategoryRules.storedName(name),
                    builtInKey = null,
                    icon = iconKey,
                    color = colorKey,
                    archived = false,
                    sortOrder = dao.nextSortOrder(),
                )
            )
        }
    }

    /** A built-in saved with a blank name goes back to its translated name. */
    suspend fun update(id: Long, name: String, iconKey: String, colorKey: String) =
        db.withTransaction {
            val category = dao.get(id)?.toDomain() ?: return@withTransaction
            require(CategoryRules.isValidName(name, isBuiltIn = category.builtIn != null)) { "Name required" }
            dao.update(
                category.copy(
                    name = CategoryRules.storedName(name),
                    iconKey = iconKey,
                    colorKey = colorKey,
                ).toEntity()
            )
        }

    /** Swaps [id] with its neighbour above or below and renumbers all sort orders. */
    suspend fun move(id: Long, up: Boolean) = db.withTransaction {
        val list = dao.all().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) return@withTransaction
        list[index] = list[target].also { list[target] = list[index] }
        dao.updateAll(list.mapIndexed { i, c -> c.copy(sortOrder = i) })
    }

    /** "Other" can never be archived. */
    suspend fun setArchived(id: Long, archived: Boolean) = db.withTransaction {
        val category = dao.get(id)?.toDomain() ?: return@withTransaction
        if (archived) require(CategoryRules.canArchive(category)) { "Cannot archive ${category.builtInKey}" }
        dao.update(category.copy(archived = archived).toEntity())
    }

    suspend fun canDelete(id: Long): Boolean {
        val category = dao.get(id)?.toDomain() ?: return false
        return CategoryRules.canDelete(category, db.expenseDao().countForCategory(id))
    }

    /** Returns false, deleting nothing, for "Other" or a category with expenses (archive instead). */
    suspend fun delete(id: Long): Boolean = db.withTransaction {
        if (!canDelete(id)) return@withTransaction false
        dao.delete(id)
        true
    }
}
