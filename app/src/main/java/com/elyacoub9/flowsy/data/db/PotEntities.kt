package com.elyacoub9.flowsy.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.elyacoub9.flowsy.domain.pot.MovementType

/** Balances are never stored: always opening balance + movements. [color] and [icon] are stable keys. */
@Entity(tableName = "pots")
data class PotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val icon: String,
    val goalAmount: Long?,
    val goalDate: Long?,
    val openingBalance: Long,
    val archived: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "pot_movements",
    foreignKeys = [
        ForeignKey(
            entity = PotEntity::class,
            parentColumns = ["id"],
            childColumns = ["potId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("potId"), Index("date")],
)
data class PotMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val potId: Long,
    val type: MovementType,
    val amount: Long,
    val date: Long,
    val note: String?,
    val createdAt: Long,
)
