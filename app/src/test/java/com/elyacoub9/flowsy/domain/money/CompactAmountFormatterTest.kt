package com.elyacoub9.flowsy.domain.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CompactAmountFormatterTest {

    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH
    private fun mad(minor: Long, locale: Locale) = CompactAmountFormatter.format(minor, 2, locale)

    @Test
    fun belowThousand_wholeNumber() {
        assertEquals("950", mad(95_000, en))
        assertEquals("0", mad(0, en))
        assertEquals("150", mad(14_950, en)) // 149.50 rounds half up
    }

    @Test
    fun thousands_oneDecimal() {
        assertEquals("12.4k", mad(1_235_000, en)) // 12,350.00
        assertEquals("12,4 k", mad(1_235_000, fr))
        assertEquals("6.8k", mad(684_250, en)) // 6,842.50
        assertEquals("2k", mad(200_000, en)) // no ".0"
    }

    @Test
    fun rollsOverToTheNextUnit() {
        assertEquals("1k", mad(99_960, en)) // 999.60 → 1,000
        assertEquals("1M", mad(99_995_000, en)) // 999,950.00
        assertEquals("1 M", mad(99_995_000, fr))
    }

    @Test
    fun millions() {
        assertEquals("1.3M", mad(125_000_000, en)) // 1,250,000.00
        assertEquals("1,3 M", mad(125_000_000, fr))
    }

    @Test
    fun zeroDecimalCurrency() {
        assertEquals("12.4k", CompactAmountFormatter.format(12_350, 0, en)) // e.g. JPY
        assertEquals("1M", CompactAmountFormatter.format(999_950, 0, en))
        assertEquals("999", CompactAmountFormatter.format(999, 0, en))
    }

    @Test
    fun negative() {
        assertEquals("-2.9k", mad(-292_500, en))
    }
}
