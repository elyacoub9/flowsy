package com.elyacoub9.flowsy.domain.expense

import java.time.LocalDate

data class Expense(
    val id: Long,
    val amount: Long,
    val categoryId: Long,
    val date: LocalDate,
    val note: String?,
    val createdAt: Long,
)

object ExpenseRules {
    fun isValidAmount(amount: Long): Boolean = amount > 0
}
