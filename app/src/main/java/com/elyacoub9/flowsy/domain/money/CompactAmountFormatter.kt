package com.elyacoub9.flowsy.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Short amounts for chart labels: "12.4k", "1.3M" in English; "12,4 k", "1,3 M" in French. */
object CompactAmountFormatter {

    private val units = listOf("" to 0, "k" to 3, "M" to 6, "B" to 9)

    /**
     * [minorUnits] in a currency with [fractionDigits] decimals. Below 1,000 the amount is a whole number;
     * above, one decimal at most (half up). A value that rounds up to the next unit moves to it:
     * 999,950.00 → "1M", not "1000k".
     */
    fun format(minorUnits: Long, fractionDigits: Int, locale: Locale): String {
        val major = BigDecimal.valueOf(minorUnits, fractionDigits).abs()
        var index = units.indexOfLast { (_, exponent) -> major >= BigDecimal.ONE.movePointRight(exponent) }.coerceAtLeast(0)
        var scaled: BigDecimal
        while (true) {
            val decimals = if (index == 0) 0 else 1
            scaled = major.movePointLeft(units[index].second).setScale(decimals, RoundingMode.HALF_UP)
            if (scaled < BigDecimal(1000) || index == units.lastIndex) break
            index++
        }
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
            roundingMode = RoundingMode.HALF_UP
        }.format(scaled.stripTrailingZeros())
        val suffix = units[index].first
        val separator = if (suffix.isNotEmpty() && locale.language == Locale.FRENCH.language) " " else ""
        val sign = if (minorUnits < 0) "-" else ""
        return sign + number + separator + suffix
    }
}
