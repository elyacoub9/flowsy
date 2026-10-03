package com.elyacoub9.flowsy.data.db

import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import java.time.LocalDate

// Entity <-> domain mapping. The domain package never sees Room types.

fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

fun IncomeEntity.toDomain() = Income(id, label, amount, date.toLocalDate(), note, createdAt)

fun Income.toEntity() = IncomeEntity(id, label, amount, date.toEpochDay(), note, createdAt)

fun CategoryEntity.toDomain() = Category(id, name, builtInKey, icon, color, archived, sortOrder)

fun Category.toEntity() = CategoryEntity(id, name, builtInKey, iconKey, colorKey, archived, sortOrder)

fun ExpenseEntity.toDomain() = Expense(id, amount, categoryId, date.toLocalDate(), note, createdAt)

fun Expense.toEntity() = ExpenseEntity(id, amount, categoryId, date.toEpochDay(), note, createdAt)

fun PotEntity.toDomain() =
    Pot(id, name, color, icon, goalAmount, goalDate?.toLocalDate(), openingBalance, archived, sortOrder)

fun Pot.toEntity() =
    PotEntity(id, name, colorKey, iconKey, goalAmount, goalDate?.toEpochDay(), openingBalance, archived, sortOrder)

fun PotMovementEntity.toDomain() = PotMovement(id, potId, type, amount, date.toLocalDate(), note, createdAt)

fun PotMovement.toEntity() = PotMovementEntity(id, potId, type, amount, date.toEpochDay(), note, createdAt)
