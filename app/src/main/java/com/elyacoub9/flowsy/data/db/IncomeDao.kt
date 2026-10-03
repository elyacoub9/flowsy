package com.elyacoub9.flowsy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class LabelUsageRow(
    val label: String,
    val count: Int,
)

@Dao
interface IncomeDao {

    @Query("SELECT * FROM income WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<IncomeEntity>>

    @Query("SELECT COUNT(*) FROM income")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM income")
    suspend fun count(): Int

    /** Date of the oldest income, null when there is none. */
    @Query("SELECT MIN(date) FROM income")
    fun observeEarliestDate(): Flow<Long?>

    @Query("SELECT * FROM income WHERE id = :id")
    suspend fun get(id: Long): IncomeEntity?

    /** Number of income records per label dated from [fromDay] to [toDay], for the label chips. */
    @Query("SELECT label, COUNT(*) AS count FROM income WHERE date BETWEEN :fromDay AND :toDay GROUP BY label")
    fun observeLabelUsage(fromDay: Long, toDay: Long): Flow<List<LabelUsageRow>>

    @Insert
    suspend fun insert(income: IncomeEntity): Long

    @Update
    suspend fun update(income: IncomeEntity)

    @Query("DELETE FROM income WHERE id = :id")
    suspend fun delete(id: Long)
}
