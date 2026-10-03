package com.elyacoub9.flowsy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SettingsEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        IncomeEntity::class,
        PotEntity::class,
        PotMovementEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class FlowsyDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun incomeDao(): IncomeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun potDao(): PotDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "flowsy.db"

        /** Fresh installs are created at the latest version, so migrations don't run: seed here. */
        val SeedOnCreate = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedBuiltInCategories(db)
            }
        }

        // Every schema change: bump the version and add a Migration here. Never destructive.
        fun build(context: Context): FlowsyDatabase =
            Room.databaseBuilder(context, FlowsyDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(SeedOnCreate)
                .build()
    }
}
