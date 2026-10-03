package com.elyacoub9.flowsy.data.backup

import com.elyacoub9.flowsy.domain.backup.BackupFormat
import org.json.JSONObject

/**
 * Brings data.json from an older schema up to the current one, one step per schema version, before it
 * is parsed. Every schema change must add a step here (key = the old version) and a restore test for a
 * backup of the previous version. Backups started at schema 6, so there is no step yet.
 */
internal object BackupUpgrades {

    private val steps: Map<Int, (JSONObject) -> JSONObject> = emptyMap()

    fun upgrade(data: JSONObject, fromSchema: Int): JSONObject =
        (fromSchema until BackupFormat.SCHEMA_VERSION).fold(data) { current, version ->
            val step = checkNotNull(steps[version]) { "No backup upgrade from schema $version" }
            step(current)
        }
}
