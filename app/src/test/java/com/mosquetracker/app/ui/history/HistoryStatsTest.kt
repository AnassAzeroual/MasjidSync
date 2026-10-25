package com.mosquetracker.app.ui.history

import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.data.local.entity.AttendanceLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryStatsTest {

    @Test
    fun emptyHistoryHasZeroCountsAndSevenMondayBasedDays() {
        val state = state(emptyList())

        assertEquals(
            VisitStats(
                total = 0,
                today = 0,
                thisWeek = 0,
                dayStreak = 0,
                weekDays = (0L..6L).map { DailyVisits(LocalDate.of(2025, 1, 6).plusDays(it), 0) },
                perMosque = emptyList(),
            ),
            state.stats,
        )
        HistoryPeriod.entries.forEach { period ->
            assertTrue(state.entries(period).isEmpty())
            assertTrue(state.entries(period, 1L).isEmpty())
            assertTrue(state.mosques(period).isEmpty())
        }
    }

    @Test
    fun mosquesWithTheSameNameRemainSeparateById() {
        val rows = listOf(
            visit("2025-01-08", 11L, "Central Mosque"),
            visit("2025-01-07", 11L, "Central Mosque"),
            visit("2025-01-08", 22L, "Central Mosque"),
        )
        val state = state(rows)

        assertEquals(
            setOf(
                MosqueVisits(11L, "Central Mosque", today = 1, thisWeek = 2, total = 2),
                MosqueVisits(22L, "Central Mosque", today = 1, thisWeek = 1, total = 1),
            ),
            state.stats.perMosque.toSet(),
        )
        assertEquals(2, state.stats.perMosque.size)
        assertPeriodCountsAgree(state)
    }

    @Test
    fun todayWeekAndAllEntriesAgreeWithSummaryAndMosqueCounts() {
        val old = visit("2024-12-20", 3L)
        val sunday = visit("2025-01-05", 1L)
        val monday = visit("2025-01-06", 1L)
        val tuesday = visit("2025-01-07", 2L)
        val todayFirst = visit("2025-01-08", 1L)
        val todaySecond = visit("2025-01-08", 2L)
        val future = visit("2025-01-10", 2L)
        val rows = listOf(future, todaySecond, todayFirst, tuesday, monday, sunday, old)
        val state = state(rows)

        assertEquals(listOf(todaySecond, todayFirst), state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(todaySecond, todayFirst, tuesday, monday), state.entries(HistoryPeriod.WEEK))
        assertEquals(rows, state.entries(HistoryPeriod.ALL))
        assertEquals(2, state.stats.today)
        assertEquals(4, state.stats.thisWeek)
        assertEquals(7, state.stats.total)
        assertEquals(listOf(1, 1, 2, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertPeriodCountsAgree(state)
    }

    @Test
    fun mondayAtNewYearStartsANewWeek() {
        val sunday = visitAt("2023-12-31T23:59:59.999Z")
        val monday = visitAt("2024-01-01T00:00:00Z")
        val nextSunday = visit("2024-01-07")
        val state = state(listOf(sunday, monday, nextSunday), today = "2024-01-01")

        assertEquals(listOf(monday), state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(monday), state.entries(HistoryPeriod.WEEK))
        assertEquals(1, state.stats.thisWeek)
        assertEquals(2, state.stats.dayStreak)
        assertEquals(
            (0L..6L).map { DailyVisits(LocalDate.of(2024, 1, 1).plusDays(it), if (it == 0L) 1 else 0) },
            state.stats.weekDays,
        )
        assertPeriodCountsAgree(state)
    }

    @Test
    fun weekSpanningNewYearIncludesDecemberMondayAndTuesday() {
        val sunday = visit("2024-12-29")
        val monday = visit("2024-12-30")
        val tuesday = visit("2024-12-31")
        val wednesday = visit("2025-01-01")
        val thursday = visit("2025-01-02")
        val state = state(listOf(sunday, monday, tuesday, wednesday, thursday), today = "2025-01-01")

        assertEquals(listOf(monday, tuesday, wednesday), state.entries(HistoryPeriod.WEEK))
        assertEquals(3, state.stats.thisWeek)
        assertEquals(LocalDate.of(2024, 12, 30), state.stats.weekDays.first().date)
        assertEquals(LocalDate.of(2025, 1, 5), state.stats.weekDays.last().date)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertPeriodCountsAgree(state)
    }

    @Test
    fun futureDatesAreExcludedFromCurrentWeekAndChartButRemainInAllTime() {
        val monday = visit("2025-01-06", 1L)
        val today = visit("2025-01-08", 1L)
        val tomorrow = visit("2025-01-09", 2L)
        val sunday = visit("2025-01-12", 2L)
        val nextMonday = visit("2025-01-13", 2L)
        val rows = listOf(monday, today, tomorrow, sunday, nextMonday)
        val state = state(rows)

        assertEquals(listOf(today), state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(monday, today), state.entries(HistoryPeriod.WEEK))
        assertEquals(rows, state.entries(HistoryPeriod.ALL))
        assertEquals(listOf(1L), state.mosques(HistoryPeriod.WEEK).map { it.id })
        assertEquals(listOf(1, 0, 1, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertEquals(1, state.stats.dayStreak)
        assertPeriodCountsAgree(state)
    }

    @Test
    fun eastOfUtcUsesLocalMidnightAndLocalMonday() {
        val sunday = visitAt("2025-01-05T14:59:59.999Z")
        val monday = visitAt("2025-01-05T15:00:00Z")
        val tuesday = visitAt("2025-01-06T15:00:00Z")
        val state = state(listOf(sunday, monday, tuesday), today = "2025-01-06", zone = "Asia/Tokyo")

        assertEquals(listOf(monday), state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(monday), state.entries(HistoryPeriod.WEEK))
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertEquals(2, state.stats.dayStreak)
        assertPeriodCountsAgree(state)
    }

    @Test
    fun westOfUtcUsesLocalEntryDateAcrossUtcMidnight() {
        val sunday = visitAt("2025-01-06T07:59:59.999Z")
        val mondayStart = visitAt("2025-01-06T08:00:00Z")
        val mondayEnd = visitAt("2025-01-07T07:59:59.999Z")
        val tuesday = visitAt("2025-01-07T08:00:00Z")
        val state = state(
            listOf(sunday, mondayStart, mondayEnd, tuesday),
            today = "2025-01-06",
            zone = "America/Los_Angeles",
        )

        assertEquals(listOf(mondayStart, mondayEnd), state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(mondayStart, mondayEnd), state.entries(HistoryPeriod.WEEK))
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertPeriodCountsAgree(state)
    }

    @Test
    fun springDstUsesTheTwentyThreeHourLocalDay() {
        val saturday = visitAt("2025-03-09T04:59:59.999Z")
        val midnight = visitAt("2025-03-09T05:00:00Z")
        val beforeJump = visitAt("2025-03-09T06:59:59.999Z")
        val afterJump = visitAt("2025-03-09T07:00:00Z")
        val sundayEnd = visitAt("2025-03-10T03:59:59.999Z")
        val monday = visitAt("2025-03-10T04:00:00Z")
        val sundayRows = listOf(midnight, beforeJump, afterJump, sundayEnd)
        val state = state(listOf(saturday) + sundayRows + monday, today = "2025-03-09", zone = "America/New_York")

        assertEquals(sundayRows, state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(saturday) + sundayRows, state.entries(HistoryPeriod.WEEK))
        assertEquals(4, state.stats.today)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 4), state.stats.weekDays.map { it.count })
        assertEquals(2, state.stats.dayStreak)
        assertPeriodCountsAgree(state)
    }

    @Test
    fun autumnDstCountsBothRepeatedHoursOnTheTwentyFiveHourLocalDay() {
        val saturday = visitAt("2025-11-02T03:59:59.999Z")
        val midnight = visitAt("2025-11-02T04:00:00Z")
        val firstOneThirty = visitAt("2025-11-02T05:30:00Z")
        val secondOneThirty = visitAt("2025-11-02T06:30:00Z")
        val sundayEnd = visitAt("2025-11-03T04:59:59.999Z")
        val monday = visitAt("2025-11-03T05:00:00Z")
        val sundayRows = listOf(midnight, firstOneThirty, secondOneThirty, sundayEnd)
        val state = state(listOf(saturday) + sundayRows + monday, today = "2025-11-02", zone = "America/New_York")

        assertEquals(sundayRows, state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(saturday) + sundayRows, state.entries(HistoryPeriod.WEEK))
        assertEquals(4, state.stats.today)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 4), state.stats.weekDays.map { it.count })
        assertEquals(2, state.stats.dayStreak)
        assertPeriodCountsAgree(state)
    }

    @Test
    fun overnightVisitsCountOnceOnEntryRatherThanExitOrEachOccupiedDay() {
        val sunday = visitAt("2025-01-05T23:30:00Z", exit = "2025-01-06T01:00:00Z")
        val monday = visitAt("2025-01-06T23:30:00Z", exit = "2025-01-07T01:00:00Z")
        val tuesday = visitAt("2025-01-07T23:30:00Z", exit = "2025-01-09T01:00:00Z")
        val state = state(listOf(sunday, monday, tuesday))

        assertEquals(3, state.stats.total)
        assertEquals(0, state.stats.today)
        assertEquals(2, state.stats.thisWeek)
        assertTrue(state.entries(HistoryPeriod.TODAY).isEmpty())
        assertEquals(listOf(monday, tuesday), state.entries(HistoryPeriod.WEEK))
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertPeriodCountsAgree(state)
    }

    @Test
    fun openVisitsCountOnceOnEntryWithoutExtendingThroughToday() {
        val sunday = visit("2025-01-05")
        val monday = visit("2025-01-06")
        val today = visit("2025-01-08")
        val state = state(listOf(sunday, monday, today))

        assertTrue(state.history.all { it.log.exitTime == null })
        assertEquals(3, state.stats.total)
        assertEquals(1, state.stats.today)
        assertEquals(2, state.stats.thisWeek)
        assertEquals(listOf(today), state.entries(HistoryPeriod.TODAY))
        assertEquals(listOf(monday, today), state.entries(HistoryPeriod.WEEK))
        assertEquals(listOf(1, 0, 1, 0, 0, 0, 0), state.stats.weekDays.map { it.count })
        assertEquals(1, state.stats.dayStreak)
        assertPeriodCountsAgree(state)
    }

    @Test
    fun streakStartingTodayCountsDistinctConsecutiveEntryDays() {
        val state = state(
            listOf(
                visit("2025-01-04"),
                visit("2025-01-06", 1L),
                visit("2025-01-06", 2L),
                visit("2025-01-07"),
                visit("2025-01-08", 1L),
                visit("2025-01-08", 2L),
                visit("2025-01-09"),
            ),
        )

        assertEquals(3, state.stats.dayStreak)
    }

    @Test
    fun streakCanEndYesterdayWhenTodayHasNoVisits() {
        val state = state(
            listOf(visit("2025-01-05"), visit("2025-01-06"), visit("2025-01-07"), visit("2025-01-09")),
        )

        assertEquals(0, state.stats.today)
        assertEquals(3, state.stats.dayStreak)
    }

    @Test
    fun streakStopsAtAMissingDayEvenWithOlderVisits() {
        val state = state(listOf(visit("2025-01-05"), visit("2025-01-06"), visit("2025-01-08")))

        assertEquals(1, state.stats.dayStreak)
    }

    @Test
    fun streakIsZeroWhenNeitherTodayNorYesterdayHasVisits() {
        val state = state(listOf(visit("2025-01-05"), visit("2025-01-06"), visit("2025-01-09")))

        assertEquals(0, state.stats.dayStreak)
    }

    @Test
    fun mosqueDrilldownMatchesBothSelectedIdAndPeriod() {
        val old = visit("2025-01-05", 11L, "Central Mosque")
        val monday = visit("2025-01-06", 11L, "Central Mosque")
        val today = visit("2025-01-08", 11L, "Central Mosque")
        val otherMonday = visit("2025-01-06", 22L, "Central Mosque")
        val otherToday = visit("2025-01-08", 22L, "Central Mosque")
        val future = visit("2025-01-09", 11L, "Central Mosque")
        val state = state(listOf(future, otherToday, today, otherMonday, monday, old))

        assertEquals(listOf(today), state.entries(HistoryPeriod.TODAY, 11L))
        assertEquals(listOf(today, monday), state.entries(HistoryPeriod.WEEK, 11L))
        assertEquals(listOf(future, today, monday, old), state.entries(HistoryPeriod.ALL, 11L))
        assertEquals(listOf(otherToday), state.entries(HistoryPeriod.TODAY, 22L))
        assertEquals(listOf(otherToday, otherMonday), state.entries(HistoryPeriod.WEEK, 22L))
        assertEquals(listOf(otherToday, otherMonday), state.entries(HistoryPeriod.ALL, 22L))
        HistoryPeriod.entries.forEach { period ->
            assertTrue(state.entries(period, 999L).isEmpty())
            assertEquals(state.entries(period), state.entries(period, null))
        }
        assertPeriodCountsAgree(state)
    }

    @Test
    fun mosqueOrderingUsesSelectedPeriodCountsAndHidesZeroCounts() {
        val rows = (10..12).map { visitAt("2025-01-08T${it}:00:00Z", 1L, "Alpha") } +
            listOf(visit("2025-01-08", 2L, "Beta")) +
            (10..12).map { visitAt("2025-01-06T${it}:00:00Z", 2L, "Beta") } +
            (10..14).map { visitAt("2025-01-05T${it}:00:00Z", 3L, "Gamma") }
        val state = state(rows)

        assertEquals(listOf(1L, 2L), state.mosques(HistoryPeriod.TODAY).map { it.id })
        assertEquals(listOf(3, 1), state.mosques(HistoryPeriod.TODAY).map { it.count(HistoryPeriod.TODAY) })
        assertEquals(listOf(2L, 1L), state.mosques(HistoryPeriod.WEEK).map { it.id })
        assertEquals(listOf(4, 3), state.mosques(HistoryPeriod.WEEK).map { it.count(HistoryPeriod.WEEK) })
        assertEquals(listOf(3L, 2L, 1L), state.mosques(HistoryPeriod.ALL).map { it.id })
        assertEquals(listOf(5, 4, 3), state.mosques(HistoryPeriod.ALL).map { it.count(HistoryPeriod.ALL) })
        assertPeriodCountsAgree(state)
    }

    @Test
    fun equalMosqueCountsAreOrderedByNameThenId() {
        val state = state(
            listOf(
                visit("2025-01-08", 1L, "Zulu"),
                visit("2025-01-08", 3L, "Alpha"),
                visit("2025-01-08", 2L, "Alpha"),
            ),
        )

        HistoryPeriod.entries.forEach { period ->
            assertEquals(listOf(2L, 3L, 1L), state.mosques(period).map { it.id })
        }
    }

    private fun state(
        rows: List<AttendanceWithMosque>,
        today: String = "2025-01-08",
        zone: String = "UTC",
    ): HistoryState {
        val date = LocalDate.parse(today)
        val zoneId = ZoneId.of(zone)
        return HistoryState(rows, date, zoneId, computeVisitStats(rows, date, zoneId))
    }

    private fun visit(
        date: String,
        mosqueId: Long = 1L,
        name: String = "Mosque $mosqueId",
    ): AttendanceWithMosque = visitAt("${date}T12:00:00Z", mosqueId, name)

    private fun visitAt(
        entry: String,
        mosqueId: Long = 1L,
        name: String = "Mosque $mosqueId",
        exit: String? = null,
    ): AttendanceWithMosque = AttendanceWithMosque(
        log = AttendanceLogEntity(
            mosqueId = mosqueId,
            entryTime = Instant.parse(entry).toEpochMilli(),
            exitTime = exit?.let { Instant.parse(it).toEpochMilli() },
            detectionMethod = "manual",
        ),
        mosqueName = name,
    )

    private fun assertPeriodCountsAgree(state: HistoryState) {
        assertEquals(state.stats.thisWeek, state.stats.weekDays.sumOf { it.count })
        HistoryPeriod.entries.forEach { period ->
            val expected = when (period) {
                HistoryPeriod.TODAY -> state.stats.today
                HistoryPeriod.WEEK -> state.stats.thisWeek
                HistoryPeriod.ALL -> state.stats.total
            }
            assertEquals("$period entries", expected, state.entries(period).size)
            assertEquals("$period mosque list", expected, state.mosques(period).sumOf { it.count(period) })
            assertEquals("$period mosque totals", expected, state.stats.perMosque.sumOf { it.count(period) })
            assertEquals(
                "$period mosque IDs",
                state.entries(period).map { it.log.mosqueId }.toSet(),
                state.mosques(period).map { it.id }.toSet(),
            )
            state.stats.perMosque.forEach { mosque ->
                assertEquals("$period mosque ${mosque.id}", mosque.count(period), state.entries(period, mosque.id).size)
            }
        }
    }
}
