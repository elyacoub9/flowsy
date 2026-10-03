package com.elyacoub9.flowsy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row settings table (id is always [SINGLE_ID]). The row exists once onboarding is done. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val currencyCode: String,
    val languageTag: String,
    val onboardingDone: Boolean,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}
