package com.elyacoub9.flowsy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PotDao {

    // Pots

    /** All pots, archived included (the Savings screen lists archived ones with their balance). */
    @Query("SELECT * FROM pots ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PotEntity>>

    @Query("SELECT * FROM pots WHERE id = :id")
    fun observe(id: Long): Flow<PotEntity?>

    @Query("SELECT * FROM pots WHERE id = :id")
    suspend fun get(id: Long): PotEntity?

    @Query("SELECT * FROM pots ORDER BY sortOrder, id")
    suspend fun allInOrder(): List<PotEntity>

    @Query("SELECT COUNT(*) FROM pots")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pots")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM pots")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(pot: PotEntity): Long

    @Update
    suspend fun update(pot: PotEntity)

    @Update
    suspend fun updateAll(pots: List<PotEntity>)

    @Query("DELETE FROM pots WHERE id = :id")
    suspend fun delete(id: Long)

    // Movements

    /** Every movement of every pot, for balances. */
    @Query("SELECT * FROM pot_movements ORDER BY date DESC, createdAt DESC")
    fun observeAllMovements(): Flow<List<PotMovementEntity>>

    @Query("SELECT * FROM pot_movements WHERE potId = :potId ORDER BY date DESC, createdAt DESC")
    fun observeMovements(potId: Long): Flow<List<PotMovementEntity>>

    @Query("SELECT * FROM pot_movements WHERE potId = :potId")
    suspend fun movements(potId: Long): List<PotMovementEntity>

    @Query("SELECT * FROM pot_movements WHERE id = :id")
    suspend fun movement(id: Long): PotMovementEntity?

    @Insert
    suspend fun insertMovement(movement: PotMovementEntity): Long

    @Update
    suspend fun updateMovement(movement: PotMovementEntity)

    @Query("DELETE FROM pot_movements WHERE id = :id")
    suspend fun deleteMovement(id: Long)
}
