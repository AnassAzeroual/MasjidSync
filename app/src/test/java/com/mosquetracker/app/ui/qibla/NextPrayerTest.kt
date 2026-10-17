package com.mosquetracker.app.ui.qibla

import com.mosquetracker.app.detection.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Test

class NextPrayerTest {

    private val hour = 3_600_000L
    private val day = PrayerDay(
        today = PrayerTimes.Times(
            fajr = 5 * hour,
            sunrise = 7 * hour,
            dhuhr = 13 * hour,
            asr = 16 * hour,
            maghrib = 19 * hour,
            isha = 21 * hour,
        ),
        tomorrowFajr = 29 * hour,
    )

    @Test
    fun beforeFajrIsTodaysFajr() {
        assertEquals(NextPrayer("Fajr", 5 * hour), QiblaViewModel.nextPrayerAfter(day, 1 * hour))
    }

    @Test
    fun sunriseIsSkipped() {
        assertEquals(NextPrayer("Dhuhr", 13 * hour), QiblaViewModel.nextPrayerAfter(day, 6 * hour))
    }

    @Test
    fun atTheStartInstantMovesOn() {
        assertEquals(NextPrayer("Asr", 16 * hour), QiblaViewModel.nextPrayerAfter(day, 13 * hour))
    }

    @Test
    fun afterIshaIsTomorrowsFajr() {
        assertEquals(
            NextPrayer("Fajr", 29 * hour, tomorrow = true),
            QiblaViewModel.nextPrayerAfter(day, 22 * hour),
        )
    }
}
