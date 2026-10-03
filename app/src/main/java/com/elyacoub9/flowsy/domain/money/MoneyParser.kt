package com.elyacoub9.flowsy.domain.money

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

sealed interface ParseResult {
    data class Success(val minorUnits: Long) : ParseResult
    data class Error(val reason: ParseError) : ParseResult
}

enum class ParseError { EMPTY, INVALID, TOO_MANY_DECIMALS, TOO_LARGE }

/**
 * Parses typed amounts like "12", "12.5" or "12,50" into minor units.
 * '.' is always accepted as the decimal separator; the locale's own decimal separator is accepted
 * too (',' in French). So in English "1,5" is invalid rather than misread. Grouping separators
 * and signs are never accepted.
 */
object MoneyParser {

    /** At most 12 digits before the decimal point: max 999,999,999,999.999 → below 10^15 minor units. */
    const val MAX_WHOLE_DIGITS = 12

    fun parse(input: String, currency: Currency, locale: Locale): ParseResult {
        val text = input.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
        if (text.isEmpty()) return ParseResult.Error(ParseError.EMPTY)

        val separators = setOf('.', decimalSeparator(locale))
        val separatorIndex = text.indexOfFirst { it in separators }
        val whole = if (separatorIndex < 0) text else text.substring(0, separatorIndex)
        val fraction = if (separatorIndex < 0) "" else text.substring(separatorIndex + 1)
        if (!whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) {
            return ParseResult.Error(ParseError.INVALID)
        }
        if (whole.isEmpty() && fraction.isEmpty()) return ParseResult.Error(ParseError.INVALID)
        if (whole.trimStart('0').length > MAX_WHOLE_DIGITS) return ParseResult.Error(ParseError.TOO_LARGE)
        if (fraction.length > currency.fractionDigits()) {
            return ParseResult.Error(ParseError.TOO_MANY_DECIMALS)
        }

        val value = BigDecimal("${whole.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}")
        return ParseResult.Success(value.movePointRight(currency.fractionDigits()).longValueExact())
    }

    fun decimalSeparator(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator
}

/** Decimals used for minor units. Currencies without a defined value (e.g. XXX) use 0. */
fun Currency.fractionDigits(): Int = defaultFractionDigits.coerceAtLeast(0)

/**
 * Plain text for an editable amount field, with the locale's decimal separator:
 * 1235000 MAD → "12350.00" in English, "12350,00" in French. Parsable by [MoneyParser].
 */
fun amountInputText(minorUnits: Long, currency: Currency, locale: Locale): String {
    val plain = BigDecimal.valueOf(minorUnits, currency.fractionDigits()).toPlainString()
    return plain.replace('.', MoneyParser.decimalSeparator(locale))
}
