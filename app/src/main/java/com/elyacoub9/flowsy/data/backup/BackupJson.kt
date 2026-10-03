package com.elyacoub9.flowsy.data.backup

import com.elyacoub9.flowsy.domain.backup.BackupException
import com.elyacoub9.flowsy.domain.backup.BackupMeta
import com.elyacoub9.flowsy.domain.backup.BackupProblem
import com.elyacoub9.flowsy.domain.backup.BackupSettings
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate

/**
 * meta.json and data.json <-> domain values. Field names mirror the table columns; money is integer
 * minor units, dates are epoch days, createdAt is epoch millis. Parsing is strict: a missing field or
 * a non-integer amount throws, so a damaged file is refused instead of being half read.
 */
internal object BackupJson {

    private const val SETTINGS = "settings"
    private const val CATEGORIES = "categories"
    private const val EXPENSES = "expenses"
    private const val INCOME = "income"
    private const val POTS = "pots"
    private const val MOVEMENTS = "potMovements"

    // Writing

    fun meta(meta: BackupMeta): JSONObject = JSONObject()
        .put("format", meta.format)
        .put("formatVersion", meta.formatVersion)
        .put("appVersion", meta.appVersion)
        .put("schemaVersion", meta.schemaVersion)
        .put("exportDate", meta.exportDate.toString())

    fun data(
        settings: List<BackupSettings>,
        categories: List<Category>,
        expenses: List<Expense>,
        income: List<Income>,
        pots: List<Pot>,
        movements: List<PotMovement>,
    ): JSONObject = JSONObject()
        .put(SETTINGS, array(settings) {
            JSONObject().put("id", it.id).put("currencyCode", it.currencyCode)
                .put("languageTag", it.languageTag ?: JSONObject.NULL).put("onboardingDone", it.onboardingDone)
        })
        .put(CATEGORIES, array(categories) {
            JSONObject().put("id", it.id).put("name", it.name ?: JSONObject.NULL).put("builtInKey", it.builtInKey ?: JSONObject.NULL)
                .put("icon", it.iconKey).put("color", it.colorKey).put("archived", it.archived).put("sortOrder", it.sortOrder)
        })
        .put(EXPENSES, array(expenses) {
            JSONObject().put("id", it.id).put("amount", it.amount).put("categoryId", it.categoryId)
                .put("date", it.date.toEpochDay()).put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt)
        })
        .put(INCOME, array(income) {
            JSONObject().put("id", it.id).put("label", it.label).put("amount", it.amount)
                .put("date", it.date.toEpochDay()).put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt)
        })
        .put(POTS, array(pots) {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorKey).put("icon", it.iconKey)
                .put("goalAmount", it.goalAmount ?: JSONObject.NULL).put("goalDate", it.goalDate?.toEpochDay() ?: JSONObject.NULL)
                .put("openingBalance", it.openingBalance).put("archived", it.archived).put("sortOrder", it.sortOrder)
        })
        .put(MOVEMENTS, array(movements) {
            JSONObject().put("id", it.id).put("potId", it.potId).put("type", it.type.name).put("amount", it.amount)
                .put("date", it.date.toEpochDay()).put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt)
        })

    private fun <T> array(items: List<T>, toJson: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(toJson(it)) } }

    // Reading

    fun parseMeta(text: String): BackupMeta = strict {
        val o = JSONObject(text)
        BackupMeta(
            format = o.getString("format"),
            formatVersion = o.int("formatVersion"),
            appVersion = o.getString("appVersion"),
            schemaVersion = o.int("schemaVersion"),
            exportDate = Instant.parse(o.getString("exportDate")),
        )
    }

    class Tables(
        val settings: List<BackupSettings>,
        val categories: List<Category>,
        val expenses: List<Expense>,
        val income: List<Income>,
        val pots: List<Pot>,
        val movements: List<PotMovement>,
    )

    fun parseData(o: JSONObject): Tables = strict {
        Tables(
            settings = o.objects(SETTINGS) {
                BackupSettings(it.int("id"), it.getString("currencyCode"), it.nullableString("languageTag"), it.getBoolean("onboardingDone"))
            },
            categories = o.objects(CATEGORIES) {
                Category(
                    it.long("id"), it.nullableString("name"), it.nullableString("builtInKey"), it.getString("icon"),
                    it.getString("color"), it.getBoolean("archived"), it.int("sortOrder"),
                )
            },
            expenses = o.objects(EXPENSES) {
                Expense(it.long("id"), it.long("amount"), it.long("categoryId"), it.date("date"), it.nullableString("note"), it.long("createdAt"))
            },
            income = o.objects(INCOME) {
                Income(it.long("id"), it.getString("label"), it.long("amount"), it.date("date"), it.nullableString("note"), it.long("createdAt"))
            },
            pots = o.objects(POTS) {
                Pot(
                    it.long("id"), it.getString("name"), it.getString("color"), it.getString("icon"), it.nullableLong("goalAmount"),
                    if (it.isNull("goalDate")) null else it.date("goalDate"), it.long("openingBalance"), it.getBoolean("archived"),
                    it.int("sortOrder"),
                )
            },
            movements = o.objects(MOVEMENTS) {
                PotMovement(
                    it.long("id"), it.long("potId"), MovementType.valueOf(it.getString("type")), it.long("amount"),
                    it.date("date"), it.nullableString("note"), it.long("createdAt"),
                )
            },
        )
    }

    /** Damaged structure → CORRUPT; an impossible date → DATE. */
    private inline fun <T> strict(block: () -> T): T = try {
        block()
    } catch (e: DateTimeException) {
        throw BackupException(BackupProblem.DATE, e)
    } catch (e: JSONException) {
        throw BackupException(BackupProblem.CORRUPT, e)
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupProblem.CORRUPT, e)
    }

    private fun <T> JSONObject.objects(key: String, parse: (JSONObject) -> T): List<T> {
        val array = getJSONArray(key)
        return (0 until array.length()).map { parse(array.getJSONObject(it)) }
    }

    /** Integers only: 12.5 or "12" are refused, never rounded. */
    private fun JSONObject.long(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw JSONException("$key is not an integer")
    }

    private fun JSONObject.int(key: String): Int = Math.toIntExact(long(key))

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else long(key)

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.date(key: String): LocalDate = LocalDate.ofEpochDay(long(key))
}
