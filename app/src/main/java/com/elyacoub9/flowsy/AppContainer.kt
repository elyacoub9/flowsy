package com.elyacoub9.flowsy

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import com.elyacoub9.flowsy.data.CategoryRepository
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.ExpenseRepository
import com.elyacoub9.flowsy.data.IncomeRepository
import com.elyacoub9.flowsy.data.LanguageManager
import com.elyacoub9.flowsy.data.PotRepository
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.data.backup.BackupRepository
import com.elyacoub9.flowsy.data.backup.DocumentStore
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Manual dependency container, created once by [FlowsyApp]. The defaults are the real ones; UI tests
 * pass an in-memory database, a fixed clock and an install date.
 */
class AppContainer(
    context: Context,
    databaseFactory: () -> FlowsyDatabase = { FlowsyDatabase.build(context) },
    /** System clock in the phone's current time zone, created on every call so zone changes apply. */
    val clock: () -> Clock = { Clock.systemDefaultZone() },
    installDateOverride: LocalDate? = null,
) {

    /** The only place that reads the device date. Re-read on every resume (DayTracker), so midnight is picked up. */
    val today: () -> LocalDate = { LocalDate.now(clock()) }

    val clockMillis: () -> Long = { clock().millis() }

    /** Phone locale, ignoring the in-app language (used for the default currency region). */
    val systemLocale: () -> Locale = { Resources.getSystem().configuration.locales[0] }

    private val database by lazy(databaseFactory)

    private val packageInfo by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    /** Day the app was first installed on this phone, in the current time zone. */
    val installDate: LocalDate by lazy {
        installDateOverride ?: Instant.ofEpochMilli(packageInfo.firstInstallTime).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /** Written into backups (meta.json). */
    private val appVersion: String = BuildConfig.VERSION_NAME

    val dayTracker = DayTracker(today)

    val incomeRepository by lazy { IncomeRepository(database, today, clockMillis) }

    val expenseRepository by lazy { ExpenseRepository(database, today, clockMillis) }

    val categoryRepository by lazy { CategoryRepository(database) }

    val potRepository by lazy { PotRepository(database, today, clockMillis) }

    val settingsRepository by lazy { SettingsRepository(database) }

    val languageManager = LanguageManager(systemLocale)

    val documentStore by lazy { DocumentStore(context.contentResolver) }

    val backupRepository by lazy { BackupRepository(database, { clock().instant() }, appVersion) }

    /** Bumped after a restore so the screens start over (Month, current month). */
    val restoreCount = MutableStateFlow(0)
}
