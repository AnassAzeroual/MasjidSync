package com.mosquetracker.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The map numbers new zones instead of asking for a name; renaming happens in Settings. */
class MosqueAutoNameTest {

    @Test
    fun firstMosqueIsNumberOne() {
        assertEquals("Mosque 1", nextMosqueName(emptyList()))
    }

    @Test
    fun incrementsPastTheHighestAutoName() {
        assertEquals("Mosque 3", nextMosqueName(listOf("Mosque 1", "Mosque 2")))
    }

    @Test
    fun aDeletedNumberIsNotReused() {
        assertEquals("Mosque 4", nextMosqueName(listOf("Mosque 1", "Mosque 3")))
    }

    @Test
    fun customNamesAreIgnored() {
        assertEquals("Mosque 1", nextMosqueName(listOf("Al-Noor", "Masjid As-Salam")))
        assertEquals("Mosque 3", nextMosqueName(listOf("Al-Noor", "Mosque 2")))
    }

    @Test
    fun numberIsReadNumericallyNotAsText() {
        assertEquals("Mosque 11", nextMosqueName(listOf("Mosque 2", "Mosque 10")))
    }

    @Test
    fun aNameThatOnlyStartsWithThePatternDoesNotCount() {
        assertEquals("Mosque 1", nextMosqueName(listOf("Mosque 7 annex", "Old Mosque 9")))
    }
}
