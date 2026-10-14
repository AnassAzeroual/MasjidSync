package com.mosquetracker.app.data.repository

import com.mosquetracker.app.data.local.AttendanceDao
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import com.mosquetracker.app.model.DetectionMethod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes the local attendance log. */
@Singleton
class AttendanceRepository @Inject constructor(
    private val attendanceDao: AttendanceDao,
) {
    val history: Flow<List<AttendanceWithMosque>> = attendanceDao.observeHistory()

    fun visitsSince(sinceEpochMillis: Long): Flow<Int> =
        attendanceDao.observeVisitsSince(sinceEpochMillis)

    /**
     * Opens a visit. If one is already open for this mosque the existing row is
     * returned unchanged, so repeated ENTER transitions do not duplicate visits.
     */
    suspend fun recordEntry(
        mosqueId: Long,
        epochMillis: Long = System.currentTimeMillis(),
        method: DetectionMethod,
    ): Long {
        attendanceDao.openLogFor(mosqueId)?.let { return it.id }
        return attendanceDao.insert(
            AttendanceLogEntity(
                mosqueId = mosqueId,
                entryTime = epochMillis,
                detectionMethod = method.name,
            ),
        )
    }

    /** Closes every open visit for the mosque and returns how many were closed. */
    suspend fun recordExit(
        mosqueId: Long,
        epochMillis: Long = System.currentTimeMillis(),
    ): Int {
        val open = attendanceDao.openLogsFor(mosqueId)
        open.forEach { attendanceDao.close(it.id, epochMillis) }
        return open.size
    }
}
