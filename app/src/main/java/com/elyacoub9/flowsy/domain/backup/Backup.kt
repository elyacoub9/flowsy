package com.elyacoub9.flowsy.domain.backup

import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.category.CategoryRules
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.expense.ExpenseRules
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.income.IncomeRules
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

object BackupFormat {
    const val FORMAT_ID = "flowsy-backup"
    const val FORMAT_VERSION = 1

    /** Database schema version written by this app (FlowsyDatabase version). */
    const val SCHEMA_VERSION = 6

    /** Oldest schema a backup can have: backups were introduced at schema 6. */
    const val OLDEST_SCHEMA_VERSION = 6

    const val META_FILE = "meta.json"
    const val DATA_FILE = "data.json"
}

data class BackupMeta(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val exportDate: Instant,
)

/** The single settings row. A blank or missing [languageTag] means "follow the phone's language". */
data class BackupSettings(
    val id: Int,
    val currencyCode: String,
    val languageTag: String?,
    val onboardingDone: Boolean,
)

/** Every table with its original ids. Money in minor units; dates as epoch days in the file. */
data class BackupData(
    val meta: BackupMeta,
    val settings: List<BackupSettings>,
    val categories: List<Category>,
    val expenses: List<Expense>,
    val income: List<Income>,
    val pots: List<Pot>,
    val movements: List<PotMovement>,
)

/** Why a backup is refused. Each has its own message. */
enum class BackupProblem {
    CORRUPT, MISSING_FILE, TOO_LARGE,
    UNKNOWN_FORMAT, NEWER_SCHEMA, TOO_OLD_SCHEMA,
    SETTINGS, CURRENCY, DUPLICATE_ID, BROKEN_LINK, AMOUNT, DATE,
    NEGATIVE_POT, BUILT_IN_KEYS, OTHER_MISSING, CATEGORY_NAME,
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception(problem.name, cause)

object BackupValidator {

    /** Dates far outside this range are refused; future dates are fine (clock or time zone differences). */
    val EARLIEST_DATE: LocalDate = LocalDate.of(1970, 1, 1)
    val LATEST_DATE: LocalDate = LocalDate.of(2100, 12, 31)

    /** Format and schema only; checked before the data is read. */
    fun checkMeta(meta: BackupMeta): BackupProblem? = when {
        meta.format != BackupFormat.FORMAT_ID || meta.formatVersion != BackupFormat.FORMAT_VERSION -> BackupProblem.UNKNOWN_FORMAT
        meta.schemaVersion > BackupFormat.SCHEMA_VERSION -> BackupProblem.NEWER_SCHEMA
        meta.schemaVersion < BackupFormat.OLDEST_SCHEMA_VERSION -> BackupProblem.TOO_OLD_SCHEMA
        else -> null
    }

    /** The first problem found, or null when the backup can be restored. Uses the app's own rules. */
    fun validate(data: BackupData): BackupProblem? {
        checkMeta(data.meta)?.let { return it }

        val settings = data.settings.singleOrNull() ?: return BackupProblem.SETTINGS
        if (settings.id != SETTINGS_ID) return BackupProblem.SETTINGS
        if (runCatching { Currency.getInstance(settings.currencyCode) }.isFailure) return BackupProblem.CURRENCY

        if (listOf(data.categories.map { it.id }, data.expenses.map { it.id }, data.income.map { it.id },
                data.pots.map { it.id }, data.movements.map { it.id }).any { it.size != it.toSet().size }
        ) return BackupProblem.DUPLICATE_ID

        // Categories: the same rules as the app (built-in keys, names, "Other").
        val builtInKeys = data.categories.mapNotNull { it.builtInKey }
        if (builtInKeys.size != builtInKeys.toSet().size || builtInKeys.any { BuiltInCategory.fromKey(it) == null }) {
            return BackupProblem.BUILT_IN_KEYS
        }
        if (data.categories.none { it.isOther }) return BackupProblem.OTHER_MISSING
        if (data.categories.any { !CategoryRules.isValidName(it.name.orEmpty(), isBuiltIn = it.builtIn != null) }) {
            return BackupProblem.CATEGORY_NAME
        }

        // Links.
        val categoryIds = data.categories.map { it.id }.toSet()
        val potsById = data.pots.associateBy { it.id }
        if (data.expenses.any { it.categoryId !in categoryIds } || data.movements.any { it.potId !in potsById }) {
            return BackupProblem.BROKEN_LINK
        }

        // Amounts and texts.
        if (data.expenses.any { !ExpenseRules.isValidAmount(it.amount) } ||
            data.income.any { !IncomeRules.isValidAmount(it.amount) || !IncomeRules.isValidLabel(it.label) } ||
            data.movements.any { !PotRules.isValidAmount(it.amount) } ||
            data.pots.any { it.name.isBlank() || !PotRules.isValidOptionalAmount(it.goalAmount) }
        ) return BackupProblem.AMOUNT

        // Dates: a sane range only, not RecordDateRule. The goal date may be in the future too.
        val dates = data.expenses.map { it.date } + data.income.map { it.date } + data.movements.map { it.date } +
            data.pots.mapNotNull { it.goalDate }
        if (dates.any { it.isBefore(EARLIEST_DATE) || it.isAfter(LATEST_DATE) }) return BackupProblem.DATE

        // Every pot's total (opening balance included) must stay ≥ 0.
        val movementsByPot = data.movements.groupBy { it.potId }
        if (data.pots.any { !PotRules.canSetOpeningBalance(it.openingBalance, movementsByPot[it.id].orEmpty()) }) {
            return BackupProblem.NEGATIVE_POT
        }
        return null
    }

    private const val SETTINGS_ID = 1
}

/** Counts shown before "Replace all data". */
data class BackupPreview(
    val exportDate: Instant,
    val currencyCode: String,
    val categories: Int,
    val expenses: Int,
    val income: Int,
    val pots: Int,
    val movements: Int,
) {
    companion object {
        fun of(data: BackupData) = BackupPreview(
            exportDate = data.meta.exportDate,
            currencyCode = data.settings.single().currencyCode,
            categories = data.categories.size,
            expenses = data.expenses.size,
            income = data.income.size,
            pots = data.pots.size,
            movements = data.movements.size,
        )
    }
}

object BackupFileName {
    /** "flowsy-backup-2026-10-12.zip", always with ISO digits whatever the app language. */
    fun forDate(date: LocalDate): String = "flowsy-backup-%04d-%02d-%02d.zip".format(Locale.ROOT, date.year, date.monthValue, date.dayOfMonth)
}
