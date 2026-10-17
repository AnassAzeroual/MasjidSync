package com.mosquetracker.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Aggregates for the History stats card, computed from the already-loaded rows. */
data class VisitStats(
    val total: Int = 0,
    val thisWeek: Int = 0,
    /** Consecutive days with at least one visit, ending today or yesterday. */
    val dayStreak: Int = 0,
    /** Visits per mosque, most-visited first. */
    val perMosque: List<Pair<String, Int>> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    attendanceRepository: AttendanceRepository,
) : ViewModel() {

    val history: StateFlow<List<AttendanceWithMosque>> = attendanceRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<VisitStats> = history
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitStats())

    private companion object {
        fun computeStats(rows: List<AttendanceWithMosque>): VisitStats {
            if (rows.isEmpty()) return VisitStats()
            val zone = ZoneId.systemDefault()
            val weekStart = TimeFormat.startOfWeekMillis()
            val days = rows.map {
                Instant.ofEpochMilli(it.log.entryTime).atZone(zone).toLocalDate()
            }.toSortedSet()

            var streak = 0
            var cursor = LocalDate.now(zone)
            // A streak stays alive through today: missing today starts counting from yesterday.
            if (cursor !in days) cursor = cursor.minusDays(1)
            while (cursor in days) {
                streak++
                cursor = cursor.minusDays(1)
            }

            return VisitStats(
                total = rows.size,
                thisWeek = rows.count { it.log.entryTime >= weekStart },
                dayStreak = streak,
                perMosque = rows.groupingBy { it.mosqueName ?: "Deleted mosque" }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value },
            )
        }
    }
}
