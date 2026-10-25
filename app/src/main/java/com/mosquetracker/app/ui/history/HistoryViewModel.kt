package com.mosquetracker.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.data.repository.AttendanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class HistoryPeriod(val label: String) {
    TODAY("Today"),
    WEEK("This week"),
    ALL("All time"),
}

data class DailyVisits(val date: LocalDate, val count: Int)

data class MosqueVisits(
    val id: Long,
    val name: String,
    val today: Int,
    val thisWeek: Int,
    val total: Int,
) {
    fun count(period: HistoryPeriod): Int = when (period) {
        HistoryPeriod.TODAY -> today
        HistoryPeriod.WEEK -> thisWeek
        HistoryPeriod.ALL -> total
    }
}

data class VisitStats(
    val total: Int,
    val today: Int,
    val thisWeek: Int,
    val dayStreak: Int,
    val weekDays: List<DailyVisits>,
    val perMosque: List<MosqueVisits>,
)

data class HistoryState(
    val history: List<AttendanceWithMosque>,
    val today: LocalDate,
    val zone: ZoneId,
    val stats: VisitStats,
) {
    fun entries(period: HistoryPeriod, mosqueId: Long? = null): List<AttendanceWithMosque> =
        history.filter { entry ->
            (mosqueId == null || entry.log.mosqueId == mosqueId) &&
                period.includes(entry.visitDate(zone), today)
        }

    fun mosques(period: HistoryPeriod): List<MosqueVisits> = stats.perMosque
        .filter { it.count(period) > 0 }
        .sortedWith(compareByDescending<MosqueVisits> { it.count(period) }.thenBy { it.name }.thenBy { it.id })
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    attendanceRepository: AttendanceRepository,
) : ViewModel() {
    private val calendar = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            emit(LocalDate.now(zone) to zone)
            delay(30_000L)
        }
    }

    val state: StateFlow<HistoryState?> = combine(attendanceRepository.history, calendar) { rows, (today, zone) ->
        HistoryState(
            history = rows.sortedByDescending { it.log.entryTime },
            today = today,
            zone = zone,
            stats = computeVisitStats(rows, today, zone),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

internal fun AttendanceWithMosque.visitDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(log.entryTime).atZone(zone).toLocalDate()

private fun HistoryPeriod.includes(date: LocalDate, today: LocalDate): Boolean = when (this) {
    HistoryPeriod.TODAY -> date == today
    HistoryPeriod.WEEK -> date >= today.minusDays((today.dayOfWeek.value - 1).toLong()) && date <= today
    HistoryPeriod.ALL -> true
}

internal fun computeVisitStats(
    rows: List<AttendanceWithMosque>,
    today: LocalDate,
    zone: ZoneId,
): VisitStats {
    val dated = rows.map { it to it.visitDate(zone) }
    val dailyCounts = dated.groupingBy { it.second }.eachCount()
    var cursor = if (today in dailyCounts) today else today.minusDays(1)
    var streak = 0
    while (cursor in dailyCounts) {
        streak++
        cursor = cursor.minusDays(1)
    }
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return VisitStats(
        total = rows.size,
        today = dailyCounts[today] ?: 0,
        thisWeek = dated.count { HistoryPeriod.WEEK.includes(it.second, today) },
        dayStreak = streak,
        weekDays = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            DailyVisits(date, if (date <= today) dailyCounts[date] ?: 0 else 0)
        },
        perMosque = dated.groupBy { it.first.log.mosqueId }.map { (id, visits) ->
            MosqueVisits(
                id = id,
                name = visits.first().first.mosqueName ?: "Deleted mosque",
                today = visits.count { it.second == today },
                thisWeek = visits.count { HistoryPeriod.WEEK.includes(it.second, today) },
                total = visits.size,
            )
        },
    )
}
