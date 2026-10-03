package com.elyacoub9.flowsy.data.backup

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.SettingsEntity
import com.elyacoub9.flowsy.data.db.toDomain
import com.elyacoub9.flowsy.data.db.toEntity
import com.elyacoub9.flowsy.domain.backup.BackupData
import com.elyacoub9.flowsy.domain.backup.BackupException
import com.elyacoub9.flowsy.domain.backup.BackupFormat
import com.elyacoub9.flowsy.domain.backup.BackupMeta
import com.elyacoub9.flowsy.domain.backup.BackupProblem
import com.elyacoub9.flowsy.domain.backup.BackupSettings
import com.elyacoub9.flowsy.domain.backup.BackupValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

class BackupRepository(
    private val db: FlowsyDatabase,
    private val now: () -> Instant,
    private val appVersion: String,
) {
    private val dao = db.backupDao()

    /** Writes the whole database (read in one transaction, so it is consistent) as a backup zip. */
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val data = db.withTransaction {
            BackupJson.data(
                settings = dao.settings().map { BackupSettings(it.id, it.currencyCode, it.languageTag, it.onboardingDone) },
                categories = dao.categories().map { it.toDomain() },
                expenses = dao.expenses().map { it.toDomain() },
                income = dao.income().map { it.toDomain() },
                pots = dao.pots().map { it.toDomain() },
                movements = dao.movements().map { it.toDomain() },
            )
        }
        val meta = BackupMeta(BackupFormat.FORMAT_ID, BackupFormat.FORMAT_VERSION, appVersion, BackupFormat.SCHEMA_VERSION, now())
        BackupZip.write(output, BackupJson.meta(meta).toString(), data.toString())
    }

    /**
     * Reads, upgrades and validates a whole backup without touching the database.
     * Throws [BackupException] with the reason when it cannot be restored.
     */
    suspend fun read(input: InputStream): BackupData = withContext(Dispatchers.IO) {
        val files = BackupZip.read(input)
        val meta = BackupJson.parseMeta(files.meta)
        BackupValidator.checkMeta(meta)?.let { throw BackupException(it) }
        val json = try {
            BackupUpgrades.upgrade(JSONObject(files.data), meta.schemaVersion)
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.CORRUPT, e)
        }
        val tables = BackupJson.parseData(json)
        val data = BackupData(meta, tables.settings, tables.categories, tables.expenses, tables.income, tables.pots, tables.movements)
        BackupValidator.validate(data)?.let { throw BackupException(it) }
        data
    }

    /**
     * Replaces everything with [data] in one transaction: wipe (children first), insert with the original
     * ids (parents first), then check every foreign key. Any failure rolls back; the current data stays.
     */
    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        dao.deleteMovements()
        dao.deleteExpenses()
        dao.deletePots()
        dao.deleteIncome()
        dao.deleteCategories()
        dao.deleteSettings()

        dao.insertSettings(data.settings.map { SettingsEntity(it.id, it.currencyCode, it.languageTag.orEmpty(), it.onboardingDone) })
        dao.insertCategories(data.categories.map { it.toEntity() })
        dao.insertPots(data.pots.map { it.toEntity() })
        dao.insertIncome(data.income.map { it.toEntity() })
        dao.insertExpenses(data.expenses.map { it.toEntity() })
        dao.insertMovements(data.movements.map { it.toEntity() })

        db.query(SimpleSQLiteQuery("PRAGMA foreign_key_check")).use { cursor ->
            check(!cursor.moveToFirst()) { "Foreign key violation in ${cursor.getString(0)}" }
        }
    }
}
