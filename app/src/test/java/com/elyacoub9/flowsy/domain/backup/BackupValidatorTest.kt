package com.elyacoub9.flowsy.domain.backup

import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class BackupValidatorTest {

    private val meta = BackupMeta(BackupFormat.FORMAT_ID, BackupFormat.FORMAT_VERSION, "1.0", BackupFormat.SCHEMA_VERSION, Instant.parse("2026-10-12T10:00:00Z"))
    private val day = LocalDate.of(2026, 10, 1)

    private val categories = BuiltInCategory.entries.mapIndexed { i, b -> Category(i + 1L, null, b.key, b.iconKey, b.colorKey, false, i) } +
        Category(20, "Pets", null, "heart", "pink", true, 9)

    private val valid = BackupData(
        meta = meta,
        settings = listOf(BackupSettings(1, "MAD", "fr", true)),
        categories = categories,
        expenses = listOf(Expense(1, 450_000, 4, day, "Rent", 0), Expense(2, 12_000, 20, day, null, 0)),
        income = listOf(Income(1, "Salary", 1_235_000, day, null, 0)),
        pots = listOf(Pot(1, "Travel", "blue", "plane", 500_000, LocalDate.of(2030, 1, 1), 0, false, 0)),
        movements = listOf(
            PotMovement(1, 1, MovementType.DEPOSIT, 50_000, day, null, 0),
            PotMovement(2, 1, MovementType.WITHDRAWAL, 30_000, day, null, 0),
        ),
    )

    @Test
    fun validBackup_accepted_futureDatesAllowed() {
        assertNull(BackupValidator.validate(valid))
        val future = valid.copy(expenses = valid.expenses + Expense(3, 1, 1, LocalDate.of(2027, 5, 1), null, 0))
        assertNull(BackupValidator.validate(future))
    }

    @Test
    fun meta_unknownFormat_newerOrOlderSchema() {
        assertEquals(BackupProblem.UNKNOWN_FORMAT, BackupValidator.checkMeta(meta.copy(format = "other")))
        assertEquals(BackupProblem.UNKNOWN_FORMAT, BackupValidator.checkMeta(meta.copy(formatVersion = 2)))
        assertEquals(BackupProblem.NEWER_SCHEMA, BackupValidator.checkMeta(meta.copy(schemaVersion = BackupFormat.SCHEMA_VERSION + 1)))
        assertEquals(BackupProblem.TOO_OLD_SCHEMA, BackupValidator.checkMeta(meta.copy(schemaVersion = 5)))
        assertNull(BackupValidator.checkMeta(meta))
    }

    @Test
    fun settings_exactlyOneRow_validCurrency() {
        assertEquals(BackupProblem.SETTINGS, BackupValidator.validate(valid.copy(settings = emptyList())))
        assertEquals(BackupProblem.SETTINGS, BackupValidator.validate(valid.copy(settings = valid.settings + valid.settings)))
        assertEquals(BackupProblem.SETTINGS, BackupValidator.validate(valid.copy(settings = listOf(BackupSettings(2, "MAD", "fr", true)))))
        assertEquals(BackupProblem.CURRENCY, BackupValidator.validate(valid.copy(settings = listOf(BackupSettings(1, "XYZ1", "fr", true)))))
        // A blank or missing language is fine: it means "follow the phone".
        assertNull(BackupValidator.validate(valid.copy(settings = listOf(BackupSettings(1, "MAD", null, true)))))
        assertNull(BackupValidator.validate(valid.copy(settings = listOf(BackupSettings(1, "MAD", "", true)))))
    }

    @Test
    fun duplicateIds_refused() {
        assertEquals(BackupProblem.DUPLICATE_ID, BackupValidator.validate(valid.copy(income = valid.income + valid.income)))
    }

    @Test
    fun brokenForeignKeys_refused() {
        assertEquals(BackupProblem.BROKEN_LINK, BackupValidator.validate(valid.copy(expenses = listOf(Expense(1, 100, 999, day, null, 0)))))
        assertEquals(
            BackupProblem.BROKEN_LINK,
            BackupValidator.validate(valid.copy(movements = listOf(PotMovement(1, 99, MovementType.DEPOSIT, 1, day, null, 0)))),
        )
    }

    @Test
    fun amounts_mustBePositive_goalPositive() {
        assertEquals(BackupProblem.AMOUNT, BackupValidator.validate(valid.copy(expenses = listOf(Expense(1, 0, 1, day, null, 0)))))
        assertEquals(BackupProblem.AMOUNT, BackupValidator.validate(valid.copy(income = listOf(Income(1, "Salary", -5, day, null, 0)))))
        assertEquals(BackupProblem.AMOUNT, BackupValidator.validate(valid.copy(income = listOf(Income(1, " ", 5, day, null, 0)))))
        assertEquals(BackupProblem.AMOUNT, BackupValidator.validate(valid.copy(pots = listOf(valid.pots[0].copy(goalAmount = 0)))))
    }

    @Test
    fun dates_saneRangeOnly() {
        assertEquals(BackupProblem.DATE, BackupValidator.validate(valid.copy(income = listOf(Income(1, "Old", 1, LocalDate.of(1969, 12, 31), null, 0)))))
        assertEquals(BackupProblem.DATE, BackupValidator.validate(valid.copy(income = listOf(Income(1, "Far", 1, LocalDate.of(2101, 1, 1), null, 0)))))
    }

    @Test
    fun negativePotBalance_refused() {
        val overdrawn = valid.movements + PotMovement(3, 1, MovementType.WITHDRAWAL, 20_001, day, null, 0)
        assertEquals(BackupProblem.NEGATIVE_POT, BackupValidator.validate(valid.copy(movements = overdrawn)))
        assertEquals(BackupProblem.NEGATIVE_POT, BackupValidator.validate(valid.copy(pots = listOf(valid.pots[0].copy(openingBalance = -1)))))
    }

    @Test
    fun categories_otherRequired_builtInsUniqueAndKnown_customNamed() {
        assertEquals(BackupProblem.OTHER_MISSING, BackupValidator.validate(valid.copy(categories = categories.filterNot { it.isOther })))
        val duplicate = categories + Category(30, null, "groceries", "cart", "green", false, 10)
        assertEquals(BackupProblem.BUILT_IN_KEYS, BackupValidator.validate(valid.copy(categories = duplicate)))
        val unknown = categories + Category(30, null, "pets", "heart", "pink", false, 10)
        assertEquals(BackupProblem.BUILT_IN_KEYS, BackupValidator.validate(valid.copy(categories = unknown)))
        val unnamed = categories + Category(30, " ", null, "heart", "pink", false, 10)
        assertEquals(BackupProblem.CATEGORY_NAME, BackupValidator.validate(valid.copy(categories = unnamed)))
    }

    @Test
    fun preview_countsAndCurrency() {
        assertEquals(BackupPreview(meta.exportDate, "MAD", 10, 2, 1, 1, 2), BackupPreview.of(valid))
    }

    @Test
    fun fileName_isoDigits_whateverTheLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG")) // Arabic-Indic digits by default
            assertEquals("flowsy-backup-2026-10-03.zip", BackupFileName.forDate(LocalDate.of(2026, 10, 3)))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
