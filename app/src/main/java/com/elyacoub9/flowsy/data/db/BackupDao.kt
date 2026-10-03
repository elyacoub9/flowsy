package com.elyacoub9.flowsy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Whole-table reads, inserts with original ids and wipes, for backup and restore only. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM settings ORDER BY id") suspend fun settings(): List<SettingsEntity>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM expenses ORDER BY id") suspend fun expenses(): List<ExpenseEntity>
    @Query("SELECT * FROM income ORDER BY id") suspend fun income(): List<IncomeEntity>
    @Query("SELECT * FROM pots ORDER BY id") suspend fun pots(): List<PotEntity>
    @Query("SELECT * FROM pot_movements ORDER BY id") suspend fun movements(): List<PotMovementEntity>

    @Insert suspend fun insertSettings(rows: List<SettingsEntity>)
    @Insert suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert suspend fun insertExpenses(rows: List<ExpenseEntity>)
    @Insert suspend fun insertIncome(rows: List<IncomeEntity>)
    @Insert suspend fun insertPots(rows: List<PotEntity>)
    @Insert suspend fun insertMovements(rows: List<PotMovementEntity>)

    @Query("DELETE FROM pot_movements") suspend fun deleteMovements()
    @Query("DELETE FROM expenses") suspend fun deleteExpenses()
    @Query("DELETE FROM pots") suspend fun deletePots()
    @Query("DELETE FROM income") suspend fun deleteIncome()
    @Query("DELETE FROM categories") suspend fun deleteCategories()
    @Query("DELETE FROM settings") suspend fun deleteSettings()
}
