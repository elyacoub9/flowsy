package com.elyacoub9.flowsy.domain.money

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyRulesTest {
    @Test
    fun changeable_onlyWithoutAnyRecord_potsIncluded() {
        assertTrue(CurrencyRules.canChange(0, 0, 0))
        assertFalse(CurrencyRules.canChange(1, 0, 0))
        assertFalse(CurrencyRules.canChange(0, 1, 0))
        assertFalse(CurrencyRules.canChange(0, 0, 1))
    }
}
