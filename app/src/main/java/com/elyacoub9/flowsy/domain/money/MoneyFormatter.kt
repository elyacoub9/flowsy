package com.elyacoub9.flowsy.domain.money

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MoneyFormatter {
    /** Formats [minorUnits] as a currency amount, e.g. 123450 MAD → "MAD 1,234.50" in English. */
    fun format(minorUnits: Long, currency: Currency, locale: Locale): String {
        val digits = currency.fractionDigits()
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(BigDecimal.valueOf(minorUnits, digits))
    }
}
