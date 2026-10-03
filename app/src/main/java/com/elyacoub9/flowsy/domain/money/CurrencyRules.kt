package com.elyacoub9.flowsy.domain.money

object CurrencyRules {
    /** The currency can change only while nothing is recorded. Any pot counts, even an empty one. */
    fun canChange(incomeCount: Int, expenseCount: Int, potCount: Int): Boolean =
        incomeCount == 0 && expenseCount == 0 && potCount == 0
}
