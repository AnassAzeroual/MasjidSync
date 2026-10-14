package com.mosquetracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import com.mosquetracker.app.data.local.entity.MosqueEntity

@Database(
    entities = [MosqueEntity::class, AttendanceLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mosqueDao(): MosqueDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        const val NAME = "mosque_tracker.db"
    }
}
