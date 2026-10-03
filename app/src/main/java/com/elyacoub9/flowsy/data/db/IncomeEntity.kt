package com.elyacoub9.flowsy.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "income", indices = [Index("date")])
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val amount: Long,
    val date: Long,
    val note: String?,
    val createdAt: Long,
)
