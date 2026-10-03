package com.elyacoub9.flowsy.domain.money

import java.util.Currency
import java.util.Locale

object CurrencyDefaults {
    const val FALLBACK_CODE = "USD"

    /** Currency of the phone's region, or [FALLBACK_CODE] when the region has none. */
    fun forRegion(region: String?): Currency {
        val fromRegion = region
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Currency.getInstance(Locale.Builder().setRegion(it).build()) }.getOrNull() }
            ?.takeIf { it.defaultFractionDigits >= 0 }
        return fromRegion ?: Currency.getInstance(FALLBACK_CODE)
    }

    /** Currencies in current use by at least one country, sorted by code. */
    fun available(): List<Currency> = Locale.getISOCountries()
        .mapNotNull { country ->
            runCatching { Currency.getInstance(Locale.Builder().setRegion(country).build()) }.getOrNull()
        }
        .filter { it.defaultFractionDigits >= 0 }
        .distinct()
        .sortedBy { it.currencyCode }
}
