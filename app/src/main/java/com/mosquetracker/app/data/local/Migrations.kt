package com.mosquetracker.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history.
 *
 * v1 -> v2 drops the `mosques.wifi_bssid` and `mosques.ble_uuid` columns: the app is GPS-only now,
 * so those bindings were dead data.
 *
 * Android's SQLite only gained `ALTER TABLE ... DROP COLUMN` in API 34, well above the minSdk of 26,
 * so the columns are removed by rebuilding the table. `attendance_logs` declares a foreign key to
 * `mosques`, and SQLite refuses to drop a parent table that a child still references (when foreign
 * keys are enforced), so the child is parked without its FK first, then restored.
 *
 * Any orphaned attendance row — one whose mosque is already gone — cannot be restored into a table
 * that enforces the FK, so it is dropped along the way.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `attendance_logs_park` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `mosque_id` INTEGER NOT NULL,
                `entry_time` INTEGER NOT NULL,
                `exit_time` INTEGER,
                `detection_method` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `attendance_logs_park`
                (`id`, `mosque_id`, `entry_time`, `exit_time`, `detection_method`)
            SELECT `id`, `mosque_id`, `entry_time`, `exit_time`, `detection_method`
            FROM `attendance_logs`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `attendance_logs`")

        db.execSQL(
            """
            CREATE TABLE `mosques_park` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `geofence_radius` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `mosques_park`
                (`id`, `name`, `latitude`, `longitude`, `geofence_radius`, `created_at`)
            SELECT `id`, `name`, `latitude`, `longitude`, `geofence_radius`, `created_at`
            FROM `mosques`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `mosques`")
        db.execSQL(
            """
            CREATE TABLE `mosques` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `geofence_radius` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `mosques`
                (`id`, `name`, `latitude`, `longitude`, `geofence_radius`, `created_at`)
            SELECT `id`, `name`, `latitude`, `longitude`, `geofence_radius`, `created_at`
            FROM `mosques_park`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `mosques_park`")

        db.execSQL(
            """
            CREATE TABLE `attendance_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `mosque_id` INTEGER NOT NULL,
                `entry_time` INTEGER NOT NULL,
                `exit_time` INTEGER,
                `detection_method` TEXT NOT NULL,
                FOREIGN KEY(`mosque_id`) REFERENCES `mosques`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `attendance_logs`
                (`id`, `mosque_id`, `entry_time`, `exit_time`, `detection_method`)
            SELECT p.`id`, p.`mosque_id`, p.`entry_time`, p.`exit_time`, p.`detection_method`
            FROM `attendance_logs_park` AS p
            WHERE EXISTS (SELECT 1 FROM `mosques` AS m WHERE m.`id` = p.`mosque_id`)
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `attendance_logs_park`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_logs_mosque_id` ON `attendance_logs` (`mosque_id`)")
    }
}

/**
 * v2 -> v3 adds the per-mosque tracking switch. A plain `ADD COLUMN` is safe on every API
 * level (unlike `DROP COLUMN`), and existing mosques keep working by defaulting to enabled.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `mosques` ADD COLUMN `tracking_enabled` INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v3 -> v4 adds the per-mosque arrival behaviour. Same safe `ADD COLUMN` pattern;
 * every existing mosque keeps working exactly as before (full silent).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `mosques` ADD COLUMN `alert_mode` TEXT NOT NULL DEFAULT 'FULL_SILENT'")
    }
}
