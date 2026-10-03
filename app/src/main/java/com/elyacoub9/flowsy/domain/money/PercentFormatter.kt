package com.elyacoub9.flowsy.domain.money

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object PercentFormatter {
    /** Whole percent in the locale's style: 54 → "54%" in English, "54 %" (non-breaking space) in French. */
    fun format(percent: Int, locale: Locale): String =
        NumberFormat.getPercentInstance(locale).format(BigDecimal.valueOf(percent.toLong(), 2))
}
