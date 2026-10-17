package com.mosquetracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import com.mosquetracker.app.data.local.entity.MosqueEntity

@Database(
    entities = [MosqueEntity::class, AttendanceLogEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mosqueDao(): MosqueDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        const val NAME = "mosque_tracker.db"

        /** v1 -> v2: drops the unused Wi-Fi/BLE binding columns from `mosques`. */
        /** v2 -> v3: adds the per-mosque `tracking_enabled` switch (defaults to on). */
        /** v3 -> v4: adds the per-mosque `alert_mode` (defaults to FULL_SILENT). */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
