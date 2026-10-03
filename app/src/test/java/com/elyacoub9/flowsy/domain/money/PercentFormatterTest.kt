package com.elyacoub9.flowsy.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PercentFormatterTest {

    @Test
    fun english() {
        assertEquals("54%", PercentFormatter.format(54, Locale.ENGLISH))
        assertEquals("112%", PercentFormatter.format(112, Locale.ENGLISH))
        assertEquals("1,250%", PercentFormatter.format(1250, Locale.ENGLISH))
    }

    @Test
    fun french_nonBreakingSpaceBeforeSign() {
        val text = PercentFormatter.format(54, Locale.FRENCH)
        assertTrue(text, Regex("54[  ]%").matches(text))
    }
}
