package com.elyacoub9.flowsy.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [icon] and [color] are stable string keys (e.g. "cart", "green") mapped in code,
 * never resource IDs. [name] is null for a built-in that shows its translated name.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["builtInKey"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val builtInKey: String?,
    val icon: String,
    val color: String,
    val archived: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("date"), Index("categoryId")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val categoryId: Long,
    val date: Long,
    val note: String?,
    val createdAt: Long,
)
