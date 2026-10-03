package com.elyacoub9.flowsy.data

import androidx.room.withTransaction
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.IncomeEntity
import com.elyacoub9.flowsy.data.db.toDomain
import com.elyacoub9.flowsy.data.db.toEntity
import com.elyacoub9.flowsy.domain.date.RecordDateRule
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.income.IncomeLabels
import com.elyacoub9.flowsy.domain.income.IncomeRules
import com.elyacoub9.flowsy.domain.month.MonthRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class IncomeRepository(
    private val db: FlowsyDatabase,
    private val today: () -> LocalDate,
    private val clockMillis: () -> Long,
) {
    private val dao = db.incomeDao()

    fun observeMonth(month: YearMonth): Flow<List<Income>> {
        val range = MonthRange.of(month)
        return dao.observeBetween(range.start.toEpochDay(), range.end.toEpochDay())
            .map { list -> list.map { it.toDomain() } }
    }

    /** All income of [year] in one date-range query. */
    fun observeYear(year: Int): Flow<List<Income>> =
        dao.observeBetween(LocalDate.of(year, 1, 1).toEpochDay(), LocalDate.of(year, 12, 31).toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun observeEarliestDate(): Flow<LocalDate?> = dao.observeEarliestDate().map { it?.let(LocalDate::ofEpochDay) }

    /** Income count per label over the last [IncomeLabels.USAGE_DAYS] days up to [day]. */
    fun observeLabelUsage(day: LocalDate): Flow<Map<String, Int>> =
        dao.observeLabelUsage(day.minusDays(IncomeLabels.USAGE_DAYS - 1).toEpochDay(), day.toEpochDay())
            .map { rows -> rows.associate { it.label to it.count } }

    /** Returns the new income's id. */
    suspend fun add(label: String, amount: Long, date: LocalDate, note: String?): Long {
        validate(label, amount, date)
        return dao.insert(
            IncomeEntity(label = label.trim(), amount = amount, date = date.toEpochDay(), note = note, createdAt = clockMillis())
        )
    }

    suspend fun update(id: Long, label: String, amount: Long, date: LocalDate, note: String?) = db.withTransaction {
        validate(label, amount, date)
        val income = dao.get(id) ?: return@withTransaction
        dao.update(income.copy(label = label.trim(), amount = amount, date = date.toEpochDay(), note = note))
    }

    /** Deletes and returns the income, so it can be restored with [restore] (Undo). */
    suspend fun delete(id: Long): Income? = db.withTransaction {
        val income = dao.get(id) ?: return@withTransaction null
        dao.delete(id)
        income.toDomain()
    }

    /** Puts a deleted income back exactly as it was (same id and creation time). */
    suspend fun restore(income: Income) {
        dao.insert(income.toEntity())
    }

    private fun validate(label: String, amount: Long, date: LocalDate) {
        require(IncomeRules.isValidLabel(label)) { "Label required" }
        require(IncomeRules.isValidAmount(amount)) { "Income must be more than 0" }
        require(RecordDateRule.isAllowed(date, today())) { "Income date in the future" }
    }
}
