package com.elyacoub9.flowsy.domain.income

import java.time.LocalDate

data class Income(
    val id: Long,
    val label: String,
    val amount: Long,
    val date: LocalDate,
    val note: String?,
    val createdAt: Long,
)

object IncomeRules {
    fun isValidAmount(amount: Long): Boolean = amount > 0

    fun isValidLabel(label: String): Boolean = label.isNotBlank()
}

/** Quick label chips on the income sheet. */
object IncomeLabels {
    const val USAGE_DAYS = 90L

    /**
     * Labels used in the last [USAGE_DAYS] days, most used first (ties: alphabetical), then the
     * [defaults] (already translated) not in that list. Compared ignoring case and spaces at the ends.
     */
    fun chips(defaults: List<String>, usageCounts: Map<String, Int>): List<String> {
        val used = usageCounts.entries
            .filter { it.key.isNotBlank() && it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key.trim() }
            .distinctBy { it.lowercase() }
        val usedKeys = used.map { it.lowercase() }.toSet()
        return used + defaults.filter { it.trim().lowercase() !in usedKeys }
    }
}
