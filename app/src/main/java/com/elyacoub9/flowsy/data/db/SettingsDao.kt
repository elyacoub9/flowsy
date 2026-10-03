package com.elyacoub9.flowsy.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    /** Emits null until onboarding has inserted the row. */
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Upsert
    suspend fun upsert(settings: SettingsEntity)

    @Query("UPDATE settings SET currencyCode = :currencyCode WHERE id = 1")
    suspend fun updateCurrencyCode(currencyCode: String)

    @Query("UPDATE settings SET languageTag = :languageTag WHERE id = 1")
    suspend fun updateLanguageTag(languageTag: String)
}
