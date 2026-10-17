package com.mosquetracker.app.data.repository

import com.mosquetracker.app.data.local.AttendanceDao
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import com.mosquetracker.app.model.DetectionMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Guards the check-then-insert in [recordEntry] while two triggers can race for one visit. */
    private val entryMutex = Mutex()

    fun visitsSince(sinceEpochMillis: Long): Flow<Int> =
        attendanceDao.observeVisitsSince(sinceEpochMillis)

    /** Most recent visit, for the Home "Checked In at …" card. */
    val latestEntry: Flow<AttendanceWithMosque?> = attendanceDao.observeLatestEntry()

    /**
     * Opens a visit. If one is already open for this mosque the existing row is returned with
     * [EntryResult.isNew] false, so repeated ENTER transitions do not duplicate visits.
     *
     * The check and the insert are one step under [entryMutex]: the app can have a geofence event
     * and a software-watch pass arriving together, and two open rows for one mosque would read as
     * two visits in the history.
     */
    suspend fun recordEntry(
        mosqueId: Long,
        epochMillis: Long = System.currentTimeMillis(),
        method: DetectionMethod,
    ): EntryResult = entryMutex.withLock {
        attendanceDao.openLogFor(mosqueId)?.let { return@withLock EntryResult(it.id, isNew = false) }
        val id = attendanceDao.insert(
            AttendanceLogEntity(
                mosqueId = mosqueId,
                entryTime = epochMillis,
                detectionMethod = method.name,
            ),
        )
        return@withLock EntryResult(id, isNew = true)
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

    /**
     * Whether the app still believes the user is inside this mosque, i.e. a visit is open. Used
     * when a mosque is moved: an EXIT is only safe to synthesise if there is an arrival to end.
     */
    suspend fun hasOpenVisit(mosqueId: Long): Boolean = attendanceDao.openLogFor(mosqueId) != null
}
