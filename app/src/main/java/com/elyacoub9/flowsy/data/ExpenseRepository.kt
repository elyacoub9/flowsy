package com.elyacoub9.flowsy.data

import androidx.room.withTransaction
import com.elyacoub9.flowsy.data.db.ExpenseEntity
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.toDomain
import com.elyacoub9.flowsy.data.db.toEntity
import com.elyacoub9.flowsy.domain.category.CategoryRules
import com.elyacoub9.flowsy.domain.date.RecordDateRule
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.expense.ExpenseRules
import com.elyacoub9.flowsy.domain.month.MonthRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class ExpenseRepository(
    private val db: FlowsyDatabase,
    private val today: () -> LocalDate,
    private val clockMillis: () -> Long,
) {
    private val dao = db.expenseDao()

    fun observeMonth(month: YearMonth): Flow<List<Expense>> {
        val range = MonthRange.of(month)
        return dao.observeBetween(range.start.toEpochDay(), range.end.toEpochDay())
            .map { list -> list.map { it.toDomain() } }
    }

    /** All expenses of [year] in one date-range query. */
    fun observeYear(year: Int): Flow<List<Expense>> =
        dao.observeBetween(LocalDate.of(year, 1, 1).toEpochDay(), LocalDate.of(year, 12, 31).toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun observeEarliestDate(): Flow<LocalDate?> = dao.observeEarliestDate().map { it?.let(LocalDate::ofEpochDay) }

    /** Returns the new expense's id. */
    suspend fun add(amount: Long, categoryId: Long, date: LocalDate, note: String?): Long = db.withTransaction {
        validate(amount, categoryId, date, currentCategoryId = null)
        dao.insert(
            ExpenseEntity(
                amount = amount,
                categoryId = categoryId,
                date = date.toEpochDay(),
                note = note,
                createdAt = clockMillis(),
            )
        )
    }

    /** Keeping an archived category is allowed; switching to one is not. */
    suspend fun update(id: Long, amount: Long, categoryId: Long, date: LocalDate, note: String?) =
        db.withTransaction {
            val expense = dao.get(id) ?: return@withTransaction
            validate(amount, categoryId, date, currentCategoryId = expense.categoryId)
            dao.update(expense.copy(amount = amount, categoryId = categoryId, date = date.toEpochDay(), note = note))
        }

    /** Deletes and returns the expense, so it can be restored with [restore] (Undo). */
    suspend fun delete(id: Long): Expense? = db.withTransaction {
        val expense = dao.get(id) ?: return@withTransaction null
        dao.delete(id)
        expense.toDomain()
    }

    /** Puts a deleted expense back exactly as it was (same id and creation time). */
    suspend fun restore(expense: Expense) {
        dao.insert(expense.toEntity())
    }

    private suspend fun validate(amount: Long, categoryId: Long, date: LocalDate, currentCategoryId: Long?) {
        require(ExpenseRules.isValidAmount(amount)) { "Expense must be more than 0" }
        require(RecordDateRule.isAllowed(date, today())) { "Expense date in the future" }
        val category = requireNotNull(db.categoryDao().get(categoryId)) { "Unknown category $categoryId" }
        require(CategoryRules.canUseForExpense(category.toDomain(), currentCategoryId)) { "Category is archived" }
    }
}
