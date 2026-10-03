package com.elyacoub9.flowsy.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.elyacoub9.flowsy.domain.category.BuiltInCategory

/**
 * Inserts the built-in categories on a fresh install (database created at the latest version).
 * Idempotent: the unique index on `builtInKey` makes INSERT OR IGNORE skip any that exist.
 * Migrations never call this: they use their own frozen SQL, so a deleted built-in stays deleted.
 */
fun seedBuiltInCategories(db: SupportSQLiteDatabase) {
    BuiltInCategory.entries.forEach { category ->
        db.execSQL(
            "INSERT OR IGNORE INTO categories (name, builtInKey, icon, color, archived, sortOrder) " +
                "VALUES (NULL, ?, ?, ?, 0, ?)",
            arrayOf<Any>(category.key, category.iconKey, category.colorKey, category.ordinal),
        )
    }
}
