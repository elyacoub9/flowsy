package com.elyacoub9.flowsy.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2: cycles, income sources, income entries, extra income. Settings unchanged.
 * SQL copied from schemas/.../2.json. No data is inserted: the cycle engine creates
 * the current cycle on the next app start.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `anchorMonth` INTEGER NOT NULL, " +
                "`leftoverHandled` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cycles_anchorMonth` ON `cycles` (`anchorMonth`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cycles_startDate` ON `cycles` (`startDate`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `income_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `usualAmount` INTEGER NOT NULL, `amountVaries` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `income_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycleId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `expectedAmount` INTEGER NOT NULL, " +
                "`receivedAmount` INTEGER, `receivedDate` INTEGER, `status` TEXT NOT NULL, " +
                "FOREIGN KEY(`cycleId`) REFERENCES `cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`sourceId`) REFERENCES `income_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_income_entries_cycleId_sourceId` " +
                "ON `income_entries` (`cycleId`, `sourceId`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_income_entries_sourceId` ON `income_entries` (`sourceId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `extra_income` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`label` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, `note` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_extra_income_date` ON `extra_income` (`date`)")
    }
}

/** v3: categories (with the built-ins seeded) and expenses. Earlier tables unchanged. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, `builtInKey` TEXT, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                "`limitAmount` INTEGER, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_builtInKey` ON `categories` (`builtInKey`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amount` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`note` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")

        // The 8 built-ins of v3, frozen: later built-ins (Bills) are added by their own migration.
        listOf(
            "groceries" to ("cart" to "green"),
            "restaurants" to ("restaurant" to "orange"),
            "transport" to ("bus" to "blue"),
            "health" to ("health" to "red"),
            "shopping" to ("bag" to "pink"),
            "entertainment" to ("movie" to "violet"),
            "home" to ("house" to "cyan"),
            "other" to ("other" to "teal"),
        ).forEachIndexed { index, (key, visuals) ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, builtInKey, icon, color, limitAmount, archived, sortOrder) " +
                    "VALUES (NULL, ?, ?, ?, NULL, 0, ?)",
                arrayOf<Any>(key, visuals.first, visuals.second, index),
            )
        }
    }
}

/** v4: fixed charges and their per-cycle entries. Earlier tables unchanged, no data inserted. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fixed_charges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `usualAmount` INTEGER NOT NULL, `amountVaries` INTEGER NOT NULL, " +
                "`everyMonths` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `firstAnchorMonth` INTEGER NOT NULL, " +
                "`lastAnchorMonth` INTEGER, `status` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `charge_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycleId` INTEGER NOT NULL, `chargeId` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, `status` TEXT NOT NULL, `paidDate` INTEGER, " +
                "FOREIGN KEY(`cycleId`) REFERENCES `cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`chargeId`) REFERENCES `fixed_charges`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_charge_entries_cycleId_chargeId` " +
                "ON `charge_entries` (`cycleId`, `chargeId`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_charge_entries_chargeId` ON `charge_entries` (`chargeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_charge_entries_dueDate` ON `charge_entries` (`dueDate`)")
    }
}

/** v5: savings pots and their movements. Earlier tables unchanged, no data inserted. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `color` TEXT NOT NULL, `goalAmount` INTEGER, `goalDate` INTEGER, " +
                "`openingBalance` INTEGER NOT NULL, `autoAmount` INTEGER, `archived` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pot_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`potId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`note` TEXT, `source` TEXT NOT NULL, `cycleId` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`potId`) REFERENCES `pots`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`cycleId`) REFERENCES `cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pot_movements_potId` ON `pot_movements` (`potId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pot_movements_date` ON `pot_movements` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pot_movements_cycleId` ON `pot_movements` (`cycleId`)")
    }
}

/**
 * v6 (APP_SPEC v2, Phase S): calendar months replace cycles.
 * - RECEIVED income entries and extra income → `income`. EXPECTED / SKIPPED are dropped.
 * - Built-in "Bills" added at the end of the list; existing categories are not touched.
 * - PAID charge entries → expenses in Bills (note = charge name). UNPAID / SKIPPED are dropped.
 * - Settings keep currency, language and onboarding; categories lose limitAmount; pots gain an
 *   icon and lose autoAmount; movements lose source and cycleId (all movements are kept).
 * - Cycles, income sources and entries, extra income and fixed charges are dropped.
 *
 * Works whether or not foreign keys are enforced: rebuilt tables are copied to plain staging
 * tables, children are dropped before their parents, final tables are created under their real
 * names (no RENAME of a referenced table) and refilled parents first. Room runs the whole
 * migration in one transaction; a foreign key check at the end aborts it on any broken link.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Income, filled while the old tables still exist.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `income` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`label` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, `note` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_income_date` ON `income` (`date`)")
        db.execSQL(
            "INSERT INTO income (label, amount, date, note, createdAt) " +
                "SELECT s.name, e.receivedAmount, e.receivedDate, NULL, e.receivedDate * $MILLIS_PER_DAY " +
                "FROM income_entries e JOIN income_sources s ON s.id = e.sourceId " +
                "WHERE e.status = 'RECEIVED' AND e.receivedAmount > 0 AND e.receivedDate IS NOT NULL"
        )
        db.execSQL(
            "INSERT INTO income (label, amount, date, note, createdAt) " +
                "SELECT label, amount, date, note, createdAt FROM extra_income"
        )

        // 2. Staging copies (no constraints) of every table that is rebuilt.
        db.execSQL("CREATE TABLE m6_settings AS SELECT id, currencyCode, languageTag, onboardingDone FROM settings")
        db.execSQL(
            "CREATE TABLE m6_categories AS " +
                "SELECT id, name, builtInKey, icon, color, archived, sortOrder FROM categories"
        )
        db.execSQL("CREATE TABLE m6_expenses AS SELECT id, amount, categoryId, date, note, createdAt FROM expenses")
        db.execSQL(
            "CREATE TABLE m6_pots AS SELECT id, name, color, 'other' AS icon, goalAmount, goalDate, " +
                "openingBalance, archived, sortOrder FROM pots"
        )
        db.execSQL(
            "CREATE TABLE m6_pot_movements AS " +
                "SELECT id, potId, type, amount, date, note, createdAt FROM pot_movements"
        )

        // 3. Drop rebuilt tables, children first: no RESTRICT parent is dropped while still referenced.
        listOf("expenses", "pot_movements", "categories", "pots", "settings").forEach { db.execSQL("DROP TABLE $it") }

        // 4. Final tables under their real names (SQL from schemas/.../6.json), refilled parents first.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, " +
                "`languageTag` TEXT NOT NULL, `onboardingDone` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO settings (id, currencyCode, languageTag, onboardingDone) " +
                "SELECT id, currencyCode, languageTag, onboardingDone FROM m6_settings"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, `builtInKey` TEXT, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                "`archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_builtInKey` ON `categories` (`builtInKey`)")
        db.execSQL(
            "INSERT INTO categories (id, name, builtInKey, icon, color, archived, sortOrder) " +
                "SELECT id, name, builtInKey, icon, color, archived, sortOrder FROM m6_categories"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `goalAmount` INTEGER, " +
                "`goalDate` INTEGER, `openingBalance` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO pots (id, name, color, icon, goalAmount, goalDate, openingBalance, archived, sortOrder) " +
                "SELECT id, name, color, icon, goalAmount, goalDate, openingBalance, archived, sortOrder FROM m6_pots"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amount` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`note` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")
        db.execSQL(
            "INSERT INTO expenses (id, amount, categoryId, date, note, createdAt) " +
                "SELECT id, amount, categoryId, date, note, createdAt FROM m6_expenses"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pot_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`potId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`note` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`potId`) REFERENCES `pots`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pot_movements_potId` ON `pot_movements` (`potId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pot_movements_date` ON `pot_movements` (`date`)")
        db.execSQL(
            "INSERT INTO pot_movements (id, potId, type, amount, date, note, createdAt) " +
                "SELECT id, potId, type, amount, date, note, createdAt FROM m6_pot_movements"
        )

        // 5. Bills, then paid charges as expenses in it.
        db.execSQL(
            "INSERT OR IGNORE INTO categories (name, builtInKey, icon, color, archived, sortOrder) " +
                "VALUES (NULL, 'bills', 'bolt', 'yellow', 0, (SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories))"
        )
        db.execSQL(
            "INSERT INTO expenses (amount, categoryId, date, note, createdAt) " +
                "SELECT ce.amount, (SELECT id FROM categories WHERE builtInKey = 'bills'), ce.paidDate, fc.name, " +
                "ce.paidDate * $MILLIS_PER_DAY " +
                "FROM charge_entries ce JOIN fixed_charges fc ON fc.id = ce.chargeId " +
                "WHERE ce.status = 'PAID' AND ce.amount > 0 AND ce.paidDate IS NOT NULL"
        )

        // 6. Old tables (children first), then the staging copies.
        listOf(
            "charge_entries", "income_entries", "fixed_charges", "income_sources", "extra_income", "cycles",
            "m6_settings", "m6_categories", "m6_expenses", "m6_pots", "m6_pot_movements",
        ).forEach { db.execSQL("DROP TABLE $it") }

        // 7. Any broken link aborts the migration and rolls the transaction back.
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "Foreign key violation in table ${cursor.getString(0)}" }
        }
    }
}

/** createdAt (epoch millis) for migrated records that had none: midnight UTC of their date. */
private const val MILLIS_PER_DAY = 86_400_000L

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
