package com.elyacoub9.flowsy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elyacoub9.flowsy.data.backup.BackupRepository
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.domain.backup.BackupException
import com.elyacoub9.flowsy.domain.backup.BackupFormat
import com.elyacoub9.flowsy.domain.backup.BackupProblem
import com.elyacoub9.flowsy.domain.pot.MovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var db: FlowsyDatabase
    private lateinit var backups: BackupRepository
    private val today = FakeToday(date(2026, 10, 12))
    private val tables = listOf("settings", "categories", "expenses", "income", "pots", "pot_movements")

    @Before
    fun setUp() = runBlocking<Unit> {
        db = testDatabase()
        backups = BackupRepository(db, { Instant.parse("2026-10-12T10:00:00Z") }, "1.0-test")
        fillWithSpecData()
    }

    @After
    fun tearDown() = db.close()

    /** Spec Month and Year cases, a renamed built-in, archived items, pots with movements and a goal date. */
    private suspend fun fillWithSpecData() {
        var clock = 0L
        val categories = CategoryRepository(db)
        val income = IncomeRepository(db, today) { ++clock }
        val expenses = ExpenseRepository(db, today) { ++clock }
        val pots = PotRepository(db, today) { ++clock }
        val all = categories.observeAll().first()
        fun id(key: String) = all.first { it.builtInKey == key }.id

        categories.update(id("groceries"), "Food", "cart", "green")
        categories.add("Pets", "heart", "pink")
        val pets = categories.observeAll().first().first { it.name == "Pets" }.id

        income.add("Salary", 1_200_000, date(2026, 9, 1), null)
        income.add("Salary", 1_235_000, date(2026, 10, 1), null)
        income.add("Refund", 20_000, date(2026, 10, 10), "Tax")
        expenses.add(950_000, id("home"), date(2026, 9, 15), null)
        expenses.add(450_000, id("bills"), date(2026, 10, 1), "Rent")
        expenses.add(34_250, id("bills"), date(2026, 10, 3), "Electricity")
        expenses.add(82_040, id("groceries"), date(2026, 10, 5), null)
        expenses.add(15_000, id("transport"), date(2026, 10, 8), null)
        expenses.add(38_960, id("restaurants"), date(2026, 10, 10), null)
        expenses.add(64_000, id("groceries"), date(2026, 10, 12), null)
        expenses.add(12_000, pets, date(2026, 10, 2), "Vet")
        categories.setArchived(pets, true)

        val emergency = pots.add(PotInput("Emergency", "green", "heart", 3_000_000, date(2027, 6, 1), 1_000_000))
        pots.addMovement(emergency, MovementType.DEPOSIT, 100_000, date(2026, 10, 1), null)
        val travel = pots.add(PotInput("Travel", "blue", "plane", null, null, 0))
        pots.addMovement(travel, MovementType.DEPOSIT, 50_000, date(2026, 10, 2), "Trip")
        pots.addMovement(travel, MovementType.WITHDRAWAL, 30_000, date(2026, 10, 4), null)
        val old = pots.add(PotInput("Old car", "red", "car", null, null, 5_000))
        pots.setArchived(old, true)
    }

    private fun dump(): Map<String, List<List<Any?>>> = tables.associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }
    }

    private suspend fun exportBytes(): ByteArray = ByteArrayOutputStream().also { backups.export(it) }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** The exported backup with data.json (or meta.json) changed by [edit]. */
    private suspend fun edited(meta: (JSONObject) -> Unit = {}, data: (JSONObject) -> Unit = {}): ByteArray {
        val files = unzip(exportBytes())
        val metaJson = JSONObject(files.getValue(BackupFormat.META_FILE)).also(meta)
        val dataJson = JSONObject(files.getValue(BackupFormat.DATA_FILE)).also(data)
        return zip(BackupFormat.META_FILE to metaJson.toString(), BackupFormat.DATA_FILE to dataJson.toString())
    }

    private suspend fun assertRefused(bytes: ByteArray, vararg expected: BackupProblem) {
        val before = dump()
        try {
            backups.read(ByteArrayInputStream(bytes))
            fail("Backup should be refused")
        } catch (e: BackupException) {
            if (e.problem !in expected) fail("Expected ${expected.toList()}, got ${e.problem}")
        }
        assertEquals("database must be unchanged", before, dump())
    }

    private fun wipe() = db.openHelper.writableDatabase.run {
        listOf("pot_movements", "expenses", "pots", "income", "categories", "settings").forEach { execSQL("DELETE FROM $it") }
    }

    // Round trip

    @Test
    fun roundTrip_everyTableIdentical() = runBlocking<Unit> {
        assertEquals(BackupFormat.SCHEMA_VERSION, db.openHelper.readableDatabase.version)
        val before = dump()
        val bytes = exportBytes()
        wipe()
        assertNotEquals(before, dump())

        backups.replaceAll(backups.read(ByteArrayInputStream(bytes)))
        assertEquals(before, dump())
    }

    @Test
    fun metaAndFormat_asSpecified() = runBlocking<Unit> {
        val files = unzip(exportBytes())
        assertEquals(setOf("meta.json", "data.json"), files.keys)
        val meta = JSONObject(files.getValue("meta.json"))
        assertEquals("flowsy-backup", meta.getString("format"))
        assertEquals(1, meta.getInt("formatVersion"))
        assertEquals(6, meta.getInt("schemaVersion"))
        assertEquals("1.0-test", meta.getString("appVersion"))
        assertEquals("2026-10-12T10:00:00Z", meta.getString("exportDate"))
        val rent = JSONObject(files.getValue("data.json")).getJSONArray("expenses").getJSONObject(1)
        assertEquals(450_000L, rent.getLong("amount")) // integer minor units
        assertEquals(date(2026, 10, 1).toEpochDay(), rent.getLong("date")) // epoch days
    }

    // Refused, database unchanged

    @Test
    fun corruptedZip_refused() = runBlocking<Unit> {
        assertRefused(ByteArray(2048) { (it * 31).toByte() }, BackupProblem.CORRUPT)
        val bytes = exportBytes()
        assertRefused(bytes.copyOf(bytes.size / 2), BackupProblem.CORRUPT, BackupProblem.MISSING_FILE)
    }

    @Test
    fun missingFile_refused() = runBlocking<Unit> {
        val meta = unzip(exportBytes()).getValue(BackupFormat.META_FILE)
        assertRefused(zip(BackupFormat.META_FILE to meta), BackupProblem.MISSING_FILE)
    }

    @Test
    fun wrongFormat_refused() = runBlocking<Unit> {
        assertRefused(edited(meta = { it.put("format", "someone-else") }), BackupProblem.UNKNOWN_FORMAT)
    }

    @Test
    fun newerSchema_refused() = runBlocking<Unit> {
        assertRefused(edited(meta = { it.put("schemaVersion", BackupFormat.SCHEMA_VERSION + 1) }), BackupProblem.NEWER_SCHEMA)
    }

    @Test
    fun brokenForeignKey_refused() = runBlocking<Unit> {
        assertRefused(edited(data = { it.getJSONArray("expenses").getJSONObject(0).put("categoryId", 999) }), BackupProblem.BROKEN_LINK)
    }

    @Test
    fun negativePotBalance_refused() = runBlocking<Unit> {
        // Travel: 500.00 in, 300.00 out; make the withdrawal 600.00.
        assertRefused(
            edited(data = { json ->
                val movements = json.getJSONArray("potMovements")
                (0 until movements.length()).map { movements.getJSONObject(it) }
                    .first { it.getString("type") == "WITHDRAWAL" }.put("amount", 60_000)
            }),
            BackupProblem.NEGATIVE_POT,
        )
    }

    @Test
    fun missingOther_refused() = runBlocking<Unit> {
        assertRefused(
            edited(data = { json ->
                val categories = json.getJSONArray("categories")
                val index = (0 until categories.length()).first { categories.getJSONObject(it).optString("builtInKey") == "other" }
                categories.remove(index)
            }),
            BackupProblem.OTHER_MISSING,
        )
    }

    @Test
    fun decimalAmount_refused_notRounded() = runBlocking<Unit> {
        assertRefused(edited(data = { it.getJSONArray("income").getJSONObject(0).put("amount", 12.5) }), BackupProblem.CORRUPT)
    }

    // Zip safety

    @Test
    fun otherEntries_ignored() = runBlocking<Unit> {
        val files = unzip(exportBytes())
        val bytes = zip(
            "dir/meta.json" to "{}",
            "notes.txt" to "hello",
            BackupFormat.META_FILE to files.getValue(BackupFormat.META_FILE),
            BackupFormat.DATA_FILE to files.getValue(BackupFormat.DATA_FILE),
        )
        val data = backups.read(ByteArrayInputStream(bytes))
        assertEquals(3, data.income.size)
    }

    /** Android (target 34+) rejects "../" entry names itself; such a zip is refused and nothing is changed. */
    @Test
    fun pathTraversalEntry_refused() = runBlocking<Unit> {
        val files = unzip(exportBytes())
        val bytes = zip(
            "../data.json" to "{}",
            BackupFormat.META_FILE to files.getValue(BackupFormat.META_FILE),
            BackupFormat.DATA_FILE to files.getValue(BackupFormat.DATA_FILE),
        )
        assertRefused(bytes, BackupProblem.CORRUPT)
    }

    @Test
    fun oversizedFile_refused() = runBlocking<Unit> {
        val files = unzip(exportBytes())
        val hugeMeta = JSONObject(files.getValue(BackupFormat.META_FILE)).put("padding", "x".repeat(70_000)).toString()
        assertRefused(zip(BackupFormat.META_FILE to hugeMeta, BackupFormat.DATA_FILE to files.getValue(BackupFormat.DATA_FILE)), BackupProblem.TOO_LARGE)
    }

    @Test
    fun tooManyEntriesBeforeTheBackupFiles_refused() = runBlocking<Unit> {
        val files = unzip(exportBytes())
        val padding = (1..20).map { "junk$it.txt" to "x" }.toTypedArray()
        val bytes = zip(*padding, BackupFormat.META_FILE to files.getValue(BackupFormat.META_FILE), BackupFormat.DATA_FILE to files.getValue(BackupFormat.DATA_FILE))
        assertRefused(bytes, BackupProblem.MISSING_FILE)
    }

    // Transaction

    @Test
    fun failureDuringReplace_rollsBack_currentDataUntouched() = runBlocking<Unit> {
        val before = dump()
        val data = backups.read(ByteArrayInputStream(exportBytes()))
        val broken = data.copy(expenses = data.expenses + data.expenses.first().copy(id = 9_999, categoryId = 12_345))
        try {
            backups.replaceAll(broken) // skips the validator on purpose: the database must still refuse
            fail("Replace should fail")
        } catch (_: Exception) {
        }
        assertEquals(before, dump())
    }

    /** Onboarding "Restore from backup" on a new phone: no settings yet; afterwards onboarding is done. */
    @Test
    fun freshInstall_restore_finishesOnboarding() = runBlocking<Unit> {
        val bytes = exportBytes()
        val expected = dump()
        val fresh = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FlowsyDatabase::class.java,
        ).addCallback(FlowsyDatabase.SeedOnCreate).build()
        try {
            assertEquals(null, fresh.settingsDao().observe().first())
            val freshBackups = BackupRepository(fresh, { Instant.EPOCH }, "1.0-test")
            freshBackups.replaceAll(freshBackups.read(ByteArrayInputStream(bytes)))
            assertEquals(true, fresh.settingsDao().observe().first()?.onboardingDone)
            assertEquals(expected["income"]!!.size, fresh.backupDao().income().size)
            assertEquals(expected["categories"]!!.size, fresh.backupDao().categories().size)
        } finally {
            fresh.close()
        }
    }

    // Language

    @Test
    fun blankOrMissingLanguage_restoresWithoutCrash() = runBlocking<Unit> {
        val blank = edited(data = { it.getJSONArray("settings").getJSONObject(0).put("languageTag", "") })
        backups.replaceAll(backups.read(ByteArrayInputStream(blank)))
        assertEquals("", db.settingsDao().observe().first()!!.languageTag)

        val missing = edited(data = { it.getJSONArray("settings").getJSONObject(0).remove("languageTag") })
        val data = backups.read(ByteArrayInputStream(missing))
        assertEquals(null, data.settings.single().languageTag)
        backups.replaceAll(data)
        assertEquals("", db.settingsDao().observe().first()!!.languageTag)
    }
}
