package com.elyacoub9.flowsy.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.SettingsEntity
import java.time.LocalDate

/** In-memory database (built-in categories seeded) with settings for MAD, onboarding done. */
suspend fun testDatabase(): FlowsyDatabase {
    val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        FlowsyDatabase::class.java,
    ).addCallback(FlowsyDatabase.SeedOnCreate).build()
    db.settingsDao().upsert(SettingsEntity(currencyCode = "MAD", languageTag = "en", onboardingDone = true))
    return db
}

/** A settable "today", like moving the phone date. */
class FakeToday(var value: LocalDate) : () -> LocalDate {
    override fun invoke(): LocalDate = value
}

fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)
