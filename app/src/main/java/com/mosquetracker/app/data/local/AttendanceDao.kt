package com.mosquetracker.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import kotlinx.coroutines.flow.Flow

/** An attendance row joined with the name of its mosque. */
data class AttendanceWithMosque(
    @Embedded val log: AttendanceLogEntity,
    @ColumnInfo(name = "mosque_name") val mosqueName: String?,
)

@Dao
interface AttendanceDao {

    @Query(
        """
        SELECT a.*, m.name AS mosque_name
        FROM attendance_logs a
        LEFT JOIN mosques m ON m.id = a.mosque_id
        ORDER BY a.entry_time DESC
        """,
    )
    fun observeHistory(): Flow<List<AttendanceWithMosque>>

    @Query("SELECT COUNT(*) FROM attendance_logs WHERE entry_time >= :since")
    fun observeVisitsSince(since: Long): Flow<Int>

    /** Most recent visit (for the Home "Checked In at …" card), if any. */
    @Query(
        """
        SELECT a.*, m.name AS mosque_name
        FROM attendance_logs a
        LEFT JOIN mosques m ON m.id = a.mosque_id
        ORDER BY a.entry_time DESC LIMIT 1
        """,
    )
    fun observeLatestEntry(): Flow<AttendanceWithMosque?>

    @Insert
    suspend fun insert(log: AttendanceLogEntity): Long

    /** The still-open visit for a mosque, if any (used to de-duplicate entries). */
    @Query(
        """
        SELECT * FROM attendance_logs
        WHERE mosque_id = :mosqueId AND exit_time IS NULL
        ORDER BY entry_time DESC LIMIT 1
        """,
    )
    suspend fun openLogFor(mosqueId: Long): AttendanceLogEntity?

    @Query("SELECT * FROM attendance_logs WHERE mosque_id = :mosqueId AND exit_time IS NULL")
    suspend fun openLogsFor(mosqueId: Long): List<AttendanceLogEntity>

    @Query("UPDATE attendance_logs SET exit_time = :exitTime WHERE id = :id")
    suspend fun close(id: Long, exitTime: Long)
}
