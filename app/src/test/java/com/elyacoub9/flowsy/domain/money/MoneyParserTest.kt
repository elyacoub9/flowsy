package com.elyacoub9.flowsy.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency
import java.util.Locale

class MoneyParserTest {

    private val mad = Currency.getInstance("MAD")
    private val jpy = Currency.getInstance("JPY")
    private val bhd = Currency.getInstance("BHD")
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH

    private fun ok(minor: Long) = ParseResult.Success(minor)
    private fun err(reason: ParseError) = ParseResult.Error(reason)
    private fun parse(text: String, currency: Currency = mad, locale: Locale = fr) =
        MoneyParser.parse(text, currency, locale)

    @Test
    fun twoDecimals() {
        assertEquals(ok(1_234_500), parse("12345"))
        assertEquals(ok(12_345), parse("123.45"))
        assertEquals(ok(12_345), parse("123,45"))
        assertEquals(ok(12_350), parse("123.5"))
        assertEquals(ok(12_300), parse("123."))
        assertEquals(ok(50), parse(".5"))
        assertEquals(ok(50), parse(",5"))
        assertEquals(ok(0), parse("0"))
        assertEquals(ok(34_250), parse(" 342,50 "))
    }

    @Test
    fun french_acceptsCommaAndDot() {
        assertEquals(ok(1_250), parse("12,50", locale = fr))
        assertEquals(ok(1_250), parse("12.50", locale = fr))
    }

    @Test
    fun english_acceptsDotOnly() {
        assertEquals(ok(1_250), parse("12.50", locale = en))
        assertEquals(err(ParseError.INVALID), parse("12,50", locale = en))
        assertEquals(err(ParseError.INVALID), parse("1,250", locale = en))
    }

    @Test
    fun zeroDecimals() {
        assertEquals(ok(1500), parse("1500", jpy))
        assertEquals(ok(1500), parse("1500.", jpy))
        assertEquals(err(ParseError.TOO_MANY_DECIMALS), parse("1500.5", jpy))
    }

    @Test
    fun threeDecimals() {
        assertEquals(ok(1_234), parse("1.234", bhd))
        assertEquals(ok(1_200), parse("1,2", bhd))
        assertEquals(err(ParseError.TOO_MANY_DECIMALS), parse("1.2345", bhd))
    }

    @Test
    fun errors() {
        assertEquals(err(ParseError.EMPTY), parse(""))
        assertEquals(err(ParseError.EMPTY), parse("   "))
        assertEquals(err(ParseError.INVALID), parse("."))
        assertEquals(err(ParseError.INVALID), parse("-5"))
        assertEquals(err(ParseError.INVALID), parse("+5"))
        assertEquals(err(ParseError.INVALID), parse("1.2.3"))
        assertEquals(err(ParseError.INVALID), parse("1,2.3"))
        assertEquals(err(ParseError.INVALID), parse("1,234.50", locale = en))
        assertEquals(err(ParseError.INVALID), parse("12a"))
        assertEquals(err(ParseError.INVALID), parse("1e5"))
        assertEquals(err(ParseError.INVALID), parse("١٢"))
        assertEquals(err(ParseError.TOO_MANY_DECIMALS), parse("1.234"))
    }

    @Test
    fun maxTwelveWholeDigits() {
        assertEquals(ok(99_999_999_999_999), parse("999999999999.99"))
        assertEquals(ok(999_999_999_999_999), parse("999999999999.999", bhd))
        assertEquals(ok(100), parse("0000000000000001")) // leading zeros don't count
        assertEquals(err(ParseError.TOO_LARGE), parse("1000000000000"))
        assertEquals(err(ParseError.TOO_LARGE), parse("1000000000000.5"))
        assertEquals(err(ParseError.TOO_LARGE), parse("99999999999999999999999"))
    }

    @Test
    fun largestAmount_cannotOverflowLong() {
        val max = (parse("999999999999.999", bhd) as ParseResult.Success).minorUnits
        assertTrue(max < 1_000_000_000_000_000L)
        // Even 9,000 records at the maximum fit in a Long.
        assertTrue(Long.MAX_VALUE / max > 9_000)
    }

    @Test
    fun amountInputText_usesLocaleSeparator_andRoundTrips() {
        assertEquals("12350.00", amountInputText(1_235_000, mad, en))
        assertEquals("12350,00", amountInputText(1_235_000, mad, fr))
        assertEquals("1500", amountInputText(1500, jpy, fr))
        assertEquals("0.005", amountInputText(5, bhd, en))
        listOf(0L, 1L, 34_250L, 99_999_999_999_999L).forEach {
            assertEquals(ok(it), parse(amountInputText(it, mad, fr), mad, fr))
            assertEquals(ok(it), parse(amountInputText(it, mad, en), mad, en))
        }
    }
}
