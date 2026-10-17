package com.mosquetracker.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class QiblaTest {

    @Test
    fun londonFacesSoutheast() {
        // Widely published reference: ~119° from London.
        assertEquals(119.0, Qibla.bearingDegrees(51.5074, -0.1278), 3.0)
    }

    @Test
    fun newYorkFacesNortheast() {
        // Reference: ~58° from New York.
        assertEquals(58.0, Qibla.bearingDegrees(40.7128, -74.0060), 3.0)
    }

    @Test
    fun jakartaFacesNorthwest() {
        // Reference: ~295° from Jakarta.
        assertEquals(295.0, Qibla.bearingDegrees(-6.2088, 106.8456), 3.0)
    }

    @Test
    fun compassLabels() {
        assertEquals("N", Qibla.compassLabel(0.0))
        assertEquals("E", Qibla.compassLabel(90.0))
        assertEquals("ESE", Qibla.compassLabel(119.0))
        assertEquals("SE", Qibla.compassLabel(135.0))
        assertEquals("S", Qibla.compassLabel(180.0))
        assertEquals("W", Qibla.compassLabel(270.0))
    }
}

class PrayerTimesTest {

    private val london = ZoneId.of("Europe/London")
    private val june = LocalDate.of(2026, 6, 1)

    @Test
    fun timesAreOrderedThroughTheDay() {
        val t = PrayerTimes.forDate(51.5074, -0.1278, june, london).ordered().map { it.second }
        assertEquals(6, t.size)
        for (i in 0 until t.size - 1) {
            assertTrue("prayer ${i + 1} must be after prayer $i", t[i] < t[i + 1])
        }
    }

    @Test
    fun londonJuneSanity() {
        val t = PrayerTimes.forDate(51.5074, -0.1278, june, london)
        val hour = { epoch: Long ->
            java.time.Instant.ofEpochMilli(epoch).atZone(london).hour +
                java.time.Instant.ofEpochMilli(epoch).atZone(london).minute / 60.0
        }
        // Summer London (BST): Fajr in the small hours, Dhuhr just after midday,
        // Maghrib late evening. Wide bands — the algorithm is minute-grade, not exact.
        assertTrue(hour(t.fajr) < 4.0)
        assertTrue(hour(t.sunrise) in 4.0..6.0)
        assertTrue(hour(t.dhuhr) in 11.5..14.0)
        assertTrue(hour(t.maghrib) in 19.5..22.0)
        assertTrue(hour(t.isha) > 21.0)
    }

    @Test
    fun meccaWinterSanity() {
        val zone = ZoneId.of("Asia/Riyadh")
        val t = PrayerTimes.forDate(21.4225, 39.8262, LocalDate.of(2026, 1, 1), zone)
        val hour = { epoch: Long ->
            java.time.Instant.ofEpochMilli(epoch).atZone(zone).hour +
                java.time.Instant.ofEpochMilli(epoch).atZone(zone).minute / 60.0
        }
        assertTrue(hour(t.fajr) in 4.5..6.0)
        assertTrue(hour(t.dhuhr) in 11.5..13.0)
        assertTrue(hour(t.maghrib) in 17.0..18.5)
    }
}
