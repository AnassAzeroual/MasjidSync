package com.mosquetracker.app.data.repository

import com.mosquetracker.app.data.local.AttendanceDao
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import com.mosquetracker.app.model.DetectionMethod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of [AttendanceRepository.recordEntry]. [isNew] is false when a visit was already open,
 * which is how callers tell a genuine arrival from a repeated ENTER transition.
 */
data class EntryResult(val id: Long, val isNew: Boolean)

/** Reads and writes the local attendance log. */
@Singleton
class AttendanceRepository @Inject constructor(
    private val attendanceDao: AttendanceDao,
) {
    val history: Flow<List<AttendanceWithMosque>> = attendanceDao.observeHistory()

    fun visitsSince(sinceEpochMillis: Long): Flow<Int> =
        attendanceDao.observeVisitsSince(sinceEpochMillis)

    /** Most recent visit, for the Home "Checked In at …" card. */
    val latestEntry: Flow<AttendanceWithMosque?> = attendanceDao.observeLatestEntry()

    /**
     * Opens a visit. If one is already open for this mosque the existing row is returned with
     * [EntryResult.isNew] false, so repeated ENTER transitions do not duplicate visits.
     */
    suspend fun recordEntry(
        mosqueId: Long,
        epochMillis: Long = System.currentTimeMillis(),
        method: DetectionMethod,
    ): EntryResult {
        attendanceDao.openLogFor(mosqueId)?.let { return EntryResult(it.id, isNew = false) }
        val id = attendanceDao.insert(
            AttendanceLogEntity(
                mosqueId = mosqueId,
                entryTime = epochMillis,
                detectionMethod = method.name,
            ),
        )
        return EntryResult(id, isNew = true)
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
