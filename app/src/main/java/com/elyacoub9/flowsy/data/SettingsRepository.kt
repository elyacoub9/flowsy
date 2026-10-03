package com.elyacoub9.flowsy.data

import androidx.room.withTransaction
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.SettingsEntity
import com.elyacoub9.flowsy.domain.money.CurrencyRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SettingsRepository(private val db: FlowsyDatabase) {
    private val dao = db.settingsDao()

    /** null = onboarding not finished. */
    fun observe(): Flow<SettingsEntity?> = dao.observe()

    suspend fun completeOnboarding(currencyCode: String, languageTag: String) =
        dao.upsert(SettingsEntity(currencyCode = currencyCode, languageTag = languageTag, onboardingDone = true))

    suspend fun setLanguageTag(languageTag: String) = dao.updateLanguageTag(languageTag)

    /** True once any income, expense or pot exists (pots count even when empty). */
    fun observeCurrencyLocked(): Flow<Boolean> = combine(
        db.incomeDao().observeCount(),
        db.expenseDao().observeCount(),
        db.potDao().observeCount(),
    ) { income, expenses, pots -> !CurrencyRules.canChange(income, expenses, pots) }

    /** Changes the currency only while nothing is recorded, checked in the same transaction. Returns false if locked. */
    suspend fun setCurrency(currencyCode: String): Boolean = db.withTransaction {
        val allowed = CurrencyRules.canChange(db.incomeDao().count(), db.expenseDao().count(), db.potDao().count())
        if (allowed) dao.updateCurrencyCode(currencyCode)
        allowed
    }
}
