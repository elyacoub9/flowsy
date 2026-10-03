package com.elyacoub9.flowsy.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Builds real old-version database files, then opens them with Room and [ALL_MIGRATIONS].
 * On open, Room runs the migrations and validates every table, column, index and foreign key
 * against the latest schema; any mismatch throws.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    // Building old versions

    /** v1 schema, frozen from schemas/.../1.json. */
    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, " +
                "`payday` INTEGER NOT NULL, `shiftWeekendToFriday` INTEGER NOT NULL, " +
                "`languageTag` TEXT NOT NULL, `onboardingDone` INTEGER NOT NULL, " +
                "`reminderPayday` INTEGER NOT NULL, `reminderBills` INTEGER NOT NULL, " +
                "`billReminderDaysBefore` INTEGER NOT NULL, `reminderHour` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL(
            "INSERT INTO settings (id, currencyCode, payday, shiftWeekendToFriday, languageTag, " +
                "onboardingDone, reminderPayday, reminderBills, billReminderDaysBefore, reminderHour) " +
                "VALUES (1, 'MAD', 25, 0, 'fr', 1, 0, 0, 1, 9)"
        )
    }

    /** Creates the file at [version] with [create] and closes it. */
    private fun createFile(version: Int, create: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }
    }

    private fun setHash(db: SupportSQLiteDatabase, hash: String) =
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$hash')")

    private fun createV1() = createFile(1) { db ->
        createV1Schema(db)
        setHash(db, V1_HASH)
    }

    /** v4 through the real migrations, with the data an install had by then. */
    private fun createV4Data(db: SupportSQLiteDatabase) {
        createV1Schema(db)
        MIGRATION_1_2.migrate(db)
        db.execSQL("INSERT INTO cycles (id, startDate, endDate, anchorMonth, leftoverHandled) VALUES (1, $SEP25, $OCT24, 24320, 0)")
        db.execSQL(
            "INSERT INTO income_sources (id, name, usualAmount, amountVaries, status, sortOrder) " +
                "VALUES (1, 'Salary', 1200000, 0, 'ACTIVE', 0)"
        )
        db.execSQL(
            "INSERT INTO income_entries (id, cycleId, sourceId, expectedAmount, receivedAmount, receivedDate, status) " +
                "VALUES (1, 1, 1, 1200000, 1235000, $SEP25, 'RECEIVED')"
        )
        db.execSQL("INSERT INTO extra_income (id, label, amount, date, note, createdAt) VALUES (1, 'Refund', 20000, $OCT1, NULL, 7)")
        MIGRATION_2_3.migrate(db)
        db.execSQL("INSERT INTO expenses (id, amount, categoryId, date, note, createdAt) VALUES (1, 82040, 1, $OCT1, 'Market', 0)")
        db.execSQL("UPDATE categories SET limitAmount = 150000, name = 'Food' WHERE builtInKey = 'groceries'")
        MIGRATION_3_4.migrate(db)
        db.execSQL(
            "INSERT INTO fixed_charges (id, name, usualAmount, amountVaries, everyMonths, dueDay, firstAnchorMonth, " +
                "lastAnchorMonth, status, sortOrder) VALUES (1, 'Rent', 450000, 0, 1, 1, 24312, NULL, 'ACTIVE', 0)"
        )
        db.execSQL(
            "INSERT INTO charge_entries (id, cycleId, chargeId, dueDate, amount, status, paidDate) " +
                "VALUES (1, 1, 1, $OCT1, 450000, 'PAID', $OCT1)"
        )
    }

    private fun createV4WithData() = createFile(4) { db ->
        createV4Data(db)
        setHash(db, V4_HASH)
    }

    /**
     * v5 with every case the v6 migration handles: income entries RECEIVED / EXPECTED / SKIPPED in two
     * cycles, extra income, charge entries PAID / UNPAID / SKIPPED, expenses in an untouched built-in,
     * a renamed built-in, a custom category named "Bills" and an archived custom one, and movements
     * (manual, automatic and leftover) in every pot.
     */
    private fun createV5Data(db: SupportSQLiteDatabase) {
        createV4Data(db)
        MIGRATION_4_5.migrate(db)

        db.execSQL("INSERT INTO cycles (id, startDate, endDate, anchorMonth, leftoverHandled) VALUES (2, $AUG25, $SEP24, 24319, 1)")
        db.execSQL(
            "INSERT INTO income_sources (id, name, usualAmount, amountVaries, status, sortOrder) VALUES " +
                "(2, 'Freelance', 150000, 1, 'ACTIVE', 1), (3, 'Rental', 300000, 0, 'PAUSED', 2)"
        )
        db.execSQL(
            "INSERT INTO income_entries (id, cycleId, sourceId, expectedAmount, receivedAmount, receivedDate, status) VALUES " +
                "(2, 1, 2, 150000, NULL, NULL, 'EXPECTED'), " +
                "(3, 1, 3, 300000, NULL, NULL, 'SKIPPED'), " +
                "(4, 2, 1, 1200000, 1200000, $AUG25, 'RECEIVED')"
        )
        db.execSQL("INSERT INTO extra_income (id, label, amount, date, note, createdAt) VALUES (2, 'Gift', 5000, $SEP3, 'Birthday', 9)")

        // Categories 1..8 are the v3 built-ins; 1 (groceries) is renamed "Food".
        db.execSQL(
            "INSERT INTO categories (id, name, builtInKey, icon, color, limitAmount, archived, sortOrder) VALUES " +
                "(9, 'Bills', NULL, 'house', 'red', 100000, 0, 8), " +
                "(10, 'Pets', NULL, 'heart', 'pink', NULL, 1, 9)"
        )
        db.execSQL("UPDATE categories SET limitAmount = 50000 WHERE builtInKey = 'restaurants'")
        db.execSQL(
            "INSERT INTO expenses (id, amount, categoryId, date, note, createdAt) VALUES " +
                "(2, 15000, 3, $OCT1, NULL, 11), " +
                "(3, 99000, 9, $SEP3, 'Water', 12), " +
                "(4, 12000, 10, $SEP3, 'Vet', 13)"
        )

        db.execSQL(
            "INSERT INTO fixed_charges (id, name, usualAmount, amountVaries, everyMonths, dueDay, firstAnchorMonth, " +
                "lastAnchorMonth, status, sortOrder) VALUES " +
                "(2, 'Internet', 24900, 0, 1, 5, 24312, NULL, 'ACTIVE', 1), " +
                "(3, 'Gym', 20000, 0, 1, 10, 24312, NULL, 'ACTIVE', 2), " +
                "(4, 'Electricity', 30000, 1, 1, 3, 24312, NULL, 'ACTIVE', 3)"
        )
        db.execSQL(
            "INSERT INTO charge_entries (id, cycleId, chargeId, dueDate, amount, status, paidDate) VALUES " +
                "(2, 1, 2, $OCT5, 24900, 'UNPAID', NULL), " +
                "(3, 1, 3, $OCT10, 20000, 'SKIPPED', NULL), " +
                "(4, 1, 4, $OCT3, 34250, 'PAID', $OCT3), " +
                "(5, 2, 1, $SEP1, 450000, 'PAID', $SEP1)"
        )

        db.execSQL(
            "INSERT INTO pots (id, name, color, goalAmount, goalDate, openingBalance, autoAmount, archived, sortOrder) VALUES " +
                "(1, 'Emergency', 'green', 3000000, $GOAL_DATE, 1000000, 100000, 0, 0), " +
                "(2, 'Travel', 'blue', NULL, NULL, 0, NULL, 1, 1)"
        )
        db.execSQL(
            "INSERT INTO pot_movements (id, potId, type, amount, date, note, source, cycleId, createdAt) VALUES " +
                "(1, 1, 'DEPOSIT', 100000, $SEP25, NULL, 'AUTO', 1, 21), " +
                "(2, 1, 'DEPOSIT', 50000, $SEP24, NULL, 'LEFTOVER', 2, 22), " +
                "(3, 2, 'DEPOSIT', 50000, $SEP3, 'Trip', 'MANUAL', NULL, 23), " +
                "(4, 2, 'WITHDRAWAL', 30000, $OCT1, NULL, 'MANUAL', NULL, 24)"
        )
    }

    private fun createV5WithData() = createFile(5) { db ->
        createV5Data(db)
        setHash(db, V5_HASH)
    }

    // Reading results

    private fun openMigrated(): FlowsyDatabase =
        Room.databaseBuilder(context, FlowsyDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(FlowsyDatabase.SeedOnCreate)
            .build()

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<Any?>> = query(sql).use { c ->
        buildList {
            while (c.moveToNext()) {
                add((0 until c.columnCount).map { i ->
                    when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        else -> c.getString(i)
                    }
                })
            }
        }
    }

    private fun SupportSQLiteDatabase.count(table: String): Long = rows("SELECT COUNT(*) FROM $table")[0][0] as Long

    private fun SupportSQLiteDatabase.tables(): Set<String> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table'").map { it[0] as String }.toSet()

    private fun SupportSQLiteDatabase.columns(table: String): List<String> =
        rows("PRAGMA table_info($table)").map { it[1] as String }

    private fun <T> withMigrated(block: (SupportSQLiteDatabase) -> T): T {
        val db = openMigrated()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(6, sql.version)
            assertTrue("Foreign key violations", sql.rows("PRAGMA foreign_key_check").isEmpty())
            return block(sql)
        } finally {
            db.close()
        }
    }

    private val removedTables = listOf("cycles", "income_sources", "income_entries", "extra_income", "fixed_charges", "charge_entries")

    private fun SupportSQLiteDatabase.assertOldTablesGone() {
        val tables = tables()
        (removedTables + listOf("m6_settings", "m6_categories", "m6_expenses", "m6_pots", "m6_pot_movements")).forEach {
            assertTrue("$it should be dropped", it !in tables)
        }
    }

    private fun SupportSQLiteDatabase.billsId(): Long = rows("SELECT id FROM categories WHERE builtInKey = 'bills'")[0][0] as Long

    // v5 → v6

    @Test
    fun migrate5To6_movesIncomeAndPaidCharges_keepsEverythingElse() {
        createV5WithData()
        withMigrated { db ->
            // Income: RECEIVED entries (label = source name, date = received date) and extra income. EXPECTED / SKIPPED dropped.
            assertEquals(
                listOf(
                    listOf("Salary", 1_200_000L, AUG25, null, AUG25 * MILLIS_PER_DAY),
                    listOf("Gift", 5_000L, SEP3, "Birthday", 9L),
                    listOf("Salary", 1_235_000L, SEP25, null, SEP25 * MILLIS_PER_DAY),
                    listOf("Refund", 20_000L, OCT1, null, 7L),
                ),
                db.rows("SELECT label, amount, date, note, createdAt FROM income ORDER BY date"),
            )

            // Bills: added once, at the end, as a built-in with its translation key.
            val bills = db.billsId()
            assertEquals(
                listOf(listOf(null, "bills", "bolt", "yellow", 0L, 10L)),
                db.rows("SELECT name, builtInKey, icon, color, archived, sortOrder FROM categories WHERE id = $bills"),
            )

            // Existing categories untouched (ids, names, keys, archived, order); limitAmount gone.
            assertEquals(listOf("id", "name", "builtInKey", "icon", "color", "archived", "sortOrder"), db.columns("categories"))
            assertEquals(
                listOf(
                    listOf(1L, "Food", "groceries", 0L, 0L),
                    listOf(3L, null, "transport", 0L, 2L),
                    listOf(9L, "Bills", null, 0L, 8L),
                    listOf(10L, "Pets", null, 1L, 9L),
                ),
                db.rows("SELECT id, name, builtInKey, archived, sortOrder FROM categories WHERE id IN (1, 3, 9, 10) ORDER BY id"),
            )
            assertEquals(11L, db.count("categories"))

            // Expenses kept with their ids and categories (built-in, renamed, custom "Bills", archived custom).
            assertEquals(
                listOf(
                    listOf(1L, 82_040L, 1L, OCT1, "Market", 0L),
                    listOf(2L, 15_000L, 3L, OCT1, null, 11L),
                    listOf(3L, 99_000L, 9L, SEP3, "Water", 12L),
                    listOf(4L, 12_000L, 10L, SEP3, "Vet", 13L),
                ),
                db.rows("SELECT id, amount, categoryId, date, note, createdAt FROM expenses WHERE id <= 4 ORDER BY id"),
            )

            // PAID charge entries → expenses in Bills (amount, paid date, note = charge name). UNPAID / SKIPPED dropped.
            assertEquals(
                listOf(
                    listOf(450_000L, SEP1, "Rent", SEP1 * MILLIS_PER_DAY),
                    listOf(450_000L, OCT1, "Rent", OCT1 * MILLIS_PER_DAY),
                    listOf(34_250L, OCT3, "Electricity", OCT3 * MILLIS_PER_DAY),
                ),
                db.rows("SELECT amount, date, note, createdAt FROM expenses WHERE categoryId = $bills ORDER BY date, id"),
            )
            assertEquals(7L, db.count("expenses"))

            // Settings: three values kept, the rest dropped.
            assertEquals(listOf("id", "currencyCode", "languageTag", "onboardingDone"), db.columns("settings"))
            assertEquals(listOf(listOf(1L, "MAD", "fr", 1L)), db.rows("SELECT * FROM settings"))

            // Pots: icon added, autoAmount dropped, everything else kept.
            assertEquals(
                listOf(
                    listOf(1L, "Emergency", "green", "other", 3_000_000L, GOAL_DATE, 1_000_000L, 0L, 0L),
                    listOf(2L, "Travel", "blue", "other", null, null, 0L, 1L, 1L),
                ),
                db.rows("SELECT * FROM pots ORDER BY id"),
            )

            // Every movement kept (automatic and leftover become plain movements), so balances are unchanged.
            assertEquals(listOf("id", "potId", "type", "amount", "date", "note", "createdAt"), db.columns("pot_movements"))
            assertEquals(4L, db.count("pot_movements"))
            val balances = db.rows(
                "SELECT p.id, p.openingBalance + COALESCE(SUM(CASE m.type WHEN 'DEPOSIT' THEN m.amount ELSE -m.amount END), 0) " +
                    "FROM pots p LEFT JOIN pot_movements m ON m.potId = p.id GROUP BY p.id ORDER BY p.id"
            )
            assertEquals(listOf(listOf(1L, 1_150_000L), listOf(2L, 20_000L)), balances)

            db.assertOldTablesGone()
        }
    }

    /** The migration never relies on foreign keys being off: run it by hand with them enforced. */
    @Test
    fun migrate5To6_worksWithForeignKeysOn() {
        createV5WithData()
        var enforced = -1L
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onConfigure(db: SupportSQLiteDatabase) = db.execSQL("PRAGMA foreign_keys = ON")
                override fun onCreate(db: SupportSQLiteDatabase) = fail("File should exist")
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    enforced = db.rows("PRAGMA foreign_keys")[0][0] as Long
                    MIGRATION_5_6.migrate(db)
                    setHash(db, V6_HASH)
                }
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }
        assertEquals("foreign_keys must be ON during the migration", 1L, enforced)

        withMigrated { db ->
            assertEquals(4L, db.count("income"))
            assertEquals(7L, db.count("expenses"))
            assertEquals(3L, db.count("expenses WHERE categoryId = ${db.billsId()}"))
            assertEquals(4L, db.count("pot_movements"))
            db.assertOldTablesGone()
        }
    }

    /** A broken link aborts the whole migration: the file stays at v5 with all its data. */
    @Test
    fun migrate5To6_brokenForeignKey_rollsBackEverything() {
        createFile(5) { db ->
            createV5Data(db)
            setHash(db, V5_HASH)
            // Foreign keys are off here, as they were for any old file that could hold such a row.
            db.execSQL("INSERT INTO expenses (id, amount, categoryId, date, note, createdAt) VALUES (99, 100, 999, $OCT1, NULL, 0)")
        }
        val room = openMigrated()
        try {
            room.openHelper.writableDatabase
            fail("Migration should have failed")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Foreign key"))
        } finally {
            room.close()
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) = fail("File should exist")
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.readableDatabase
            assertEquals(5, db.version)
            assertTrue(db.tables().containsAll(removedTables))
            assertTrue("income" !in db.tables())
            assertEquals(5L, db.count("expenses"))
            assertEquals(5L, db.count("charge_entries"))
        }
    }

    // Older versions

    @Test
    fun migrate4To6_movesIncomeAndPaidCharges() {
        createV4WithData()
        withMigrated { db ->
            assertEquals(
                listOf(listOf("Salary", 1_235_000L, SEP25), listOf("Refund", 20_000L, OCT1)),
                db.rows("SELECT label, amount, date FROM income ORDER BY date"),
            )
            val bills = db.billsId()
            assertEquals(listOf(listOf(8L)), db.rows("SELECT sortOrder FROM categories WHERE id = $bills"))
            assertEquals(listOf(listOf("Food")), db.rows("SELECT name FROM categories WHERE id = 1"))
            assertEquals(
                listOf(listOf(1L, 82_040L, 1L, "Market"), listOf(2L, 450_000L, bills, "Rent")),
                db.rows("SELECT id, amount, categoryId, note FROM expenses ORDER BY id"),
            )
            assertEquals(0L, db.count("pots"))
            db.assertOldTablesGone()
        }
    }

    @Test
    fun migrate1To6_keepsSettings_seedsBuiltInsWithBillsLast() {
        createV1()
        withMigrated { db ->
            assertEquals(listOf(listOf(1L, "MAD", "fr", 1L)), db.rows("SELECT * FROM settings"))
            listOf("income", "expenses", "pots", "pot_movements").forEach { assertEquals("$it should be empty", 0L, db.count(it)) }
            val v3Order = BuiltInCategory.entries.filter { it != BuiltInCategory.BILLS }.map { it.key }
            assertEquals(
                (v3Order + "bills").mapIndexed { i, key -> listOf(key, i.toLong()) },
                db.rows("SELECT builtInKey, sortOrder FROM categories ORDER BY sortOrder"),
            )
            db.assertOldTablesGone()
        }
    }

    /** Fresh install: created at v6, no migration runs; the onCreate callback seeds the 9 built-ins in spec order. */
    @Test
    fun freshInstall_seedsNineBuiltInsIncludingBills_idempotent() {
        val db = openMigrated()
        try {
            val sql = db.openHelper.writableDatabase
            val expected = BuiltInCategory.entries.map {
                listOf(null, it.key, it.iconKey, it.colorKey, 0L, it.ordinal.toLong())
            }
            val query = "SELECT name, builtInKey, icon, color, archived, sortOrder FROM categories ORDER BY sortOrder"
            assertEquals(9, expected.size)
            assertEquals(listOf(null, "bills", "bolt", "yellow", 0L, 3L), expected[3])
            assertEquals(expected, sql.rows(query))
            seedBuiltInCategories(sql)
            assertEquals(expected, sql.rows(query))
        } finally {
            db.close()
        }
    }

    private companion object {
        const val V1_HASH = "1f6c85211f65bdf20281f4ac25efb88a"
        const val V4_HASH = "7a4178605b5949258e54fc6947360eac"
        const val V5_HASH = "5c866914142b738ffabc523eab2432a7"
        const val V6_HASH = "c3073d786073be14b3444185dfc660ec"

        const val MILLIS_PER_DAY = 86_400_000L

        val AUG25 = LocalDate.of(2026, 8, 25).toEpochDay()
        val SEP1 = LocalDate.of(2026, 9, 1).toEpochDay()
        val SEP3 = LocalDate.of(2026, 9, 3).toEpochDay()
        val SEP24 = LocalDate.of(2026, 9, 24).toEpochDay()
        val SEP25 = LocalDate.of(2026, 9, 25).toEpochDay()
        val OCT1 = LocalDate.of(2026, 10, 1).toEpochDay()
        val OCT3 = LocalDate.of(2026, 10, 3).toEpochDay()
        val OCT5 = LocalDate.of(2026, 10, 5).toEpochDay()
        val OCT10 = LocalDate.of(2026, 10, 10).toEpochDay()
        val OCT24 = LocalDate.of(2026, 10, 24).toEpochDay()
        val GOAL_DATE = LocalDate.of(2027, 6, 1).toEpochDay()
    }
}
