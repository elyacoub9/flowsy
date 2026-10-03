package com.elyacoub9.flowsy.domain.income

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeLabelsTest {

    private val defaults = listOf("Salary", "Freelance", "Bonus", "Other")

    @Test
    fun noUsage_defaultsInOrder() {
        assertEquals(defaults, IncomeLabels.chips(defaults, emptyMap()))
    }

    @Test
    fun mostUsedFirst_thenRemainingDefaults() {
        val chips = IncomeLabels.chips(defaults, mapOf("Bonus" to 2, "Refund" to 3, "Salary" to 1))
        assertEquals(listOf("Refund", "Bonus", "Salary", "Freelance", "Other"), chips)
    }

    @Test
    fun sameLabelDifferentCase_shownOnce() {
        val chips = IncomeLabels.chips(defaults, mapOf("salary" to 2, "Salary " to 1))
        assertEquals(listOf("salary", "Freelance", "Bonus", "Other"), chips)
    }

    @Test
    fun ties_alphabetical() {
        assertEquals(listOf("Alpha", "beta"), IncomeLabels.chips(emptyList(), mapOf("beta" to 1, "Alpha" to 1)))
    }

    @Test
    fun rules() {
        assertTrue(IncomeRules.isValidAmount(1))
        assertFalse(IncomeRules.isValidAmount(0))
        assertFalse(IncomeRules.isValidLabel("  "))
        assertTrue(IncomeRules.isValidLabel("Salary"))
    }
}
