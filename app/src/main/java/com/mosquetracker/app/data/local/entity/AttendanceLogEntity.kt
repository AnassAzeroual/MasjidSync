package com.mosquetracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One attendance event. A row is created on entry and closed (exit_time set)
 * when the matching exit transition fires.
 */
@Entity(
    tableName = "attendance_logs",
    foreignKeys = [
        ForeignKey(
            entity = MosqueEntity::class,
            parentColumns = ["id"],
            childColumns = ["mosque_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mosque_id")],
)
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "mosque_id")
    val mosqueId: Long,
    @ColumnInfo(name = "entry_time")
    val entryTime: Long,
    @ColumnInfo(name = "exit_time")
    val exitTime: Long? = null,
    @ColumnInfo(name = "detection_method")
    val detectionMethod: String,
)
