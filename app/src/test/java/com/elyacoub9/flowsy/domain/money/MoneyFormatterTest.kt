package com.elyacoub9.flowsy.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency
import java.util.Locale

class MoneyFormatterTest {

    /** Locale data uses various no-break spaces; compare with plain spaces. */
    private fun format(minor: Long, code: String, locale: Locale) =
        MoneyFormatter.format(minor, Currency.getInstance(code), locale)
            .replace(' ', ' ')
            .replace(' ', ' ')

    @Test
    fun englishUsesCommaGroupingAndDotDecimals() {
        val text = format(123_450, "MAD", Locale.ENGLISH)
        assertTrue(text, text.contains("1,234.50"))
        assertTrue(text, text.contains("MAD"))
    }

    @Test
    fun frenchUsesSpaceGroupingAndCommaDecimals() {
        val text = format(123_450, "MAD", Locale.FRENCH)
        assertTrue(text, text.contains("1 234,50"))
    }

    @Test
    fun alwaysShowsCurrencyDecimals() {
        assertTrue(format(1_200_000, "MAD", Locale.ENGLISH).contains("12,000.00"))
        assertTrue(format(5, "MAD", Locale.ENGLISH).contains("0.05"))
    }

    @Test
    fun zeroDecimalCurrency() {
        val text = format(1500, "JPY", Locale.ENGLISH)
        assertTrue(text, text.contains("1,500"))
        assertTrue(text, !text.contains("."))
    }

    @Test
    fun threeDecimalCurrency() {
        assertTrue(format(1_234, "BHD", Locale.ENGLISH).contains("1.234"))
    }

    @Test
    fun negativeAmount() {
        val text = format(-24_150, "MAD", Locale.ENGLISH)
        assertTrue(text, text.contains("241.50"))
        assertTrue(text, text.contains("-") || text.contains("−"))
    }

    @Test
    fun currencyDefaults_regionAndFallback() {
        assertEquals("MAD", CurrencyDefaults.forRegion("MA").currencyCode)
        assertEquals("EUR", CurrencyDefaults.forRegion("FR").currencyCode)
        assertEquals(CurrencyDefaults.FALLBACK_CODE, CurrencyDefaults.forRegion("").currencyCode)
        assertEquals(CurrencyDefaults.FALLBACK_CODE, CurrencyDefaults.forRegion(null).currencyCode)
        // Antarctica has no currency.
        assertEquals(CurrencyDefaults.FALLBACK_CODE, CurrencyDefaults.forRegion("AQ").currencyCode)
        assertEquals(CurrencyDefaults.FALLBACK_CODE, CurrencyDefaults.forRegion("not a region").currencyCode)
    }

    @Test
    fun currencyDefaults_availableIsCurrentCurrenciesOnly() {
        val codes = CurrencyDefaults.available().map { it.currencyCode }
        listOf("MAD", "EUR", "USD", "JPY", "GBP").forEach { assertTrue(it, it in codes) }
        listOf("XXX", "ADP", "AFA", "ALK", "FRF", "DEM", "XAU").forEach { assertTrue(it, it !in codes) }
        assertEquals(codes.distinct(), codes)
        assertEquals(codes.sorted(), codes)
    }
}
