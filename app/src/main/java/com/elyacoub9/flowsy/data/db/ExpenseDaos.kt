package com.elyacoub9.flowsy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CategoryUsageRow(
    val categoryId: Long,
    val count: Int,
)

@Dao
interface CategoryDao {

    /** All categories, archived included (past expenses still show them). */
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    /** Number of expenses per category dated from [fromDay] to [toDay], for "most used first". */
    @Query(
        """
        SELECT categoryId, COUNT(*) AS count FROM expenses
        WHERE date BETWEEN :fromDay AND :toDay
        GROUP BY categoryId
        """
    )
    fun observeUsage(fromDay: Long, toDay: Long): Flow<List<CategoryUsageRow>>
}

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    /** Date of the oldest expense, null when there is none. */
    @Query("SELECT MIN(date) FROM expenses")
    fun observeEarliestDate(): Flow<Long?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
