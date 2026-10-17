package com.mosquetracker.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ZonePresenceTest {

    private fun presence(
        hasFix: Boolean = true,
        insideMosqueId: Long? = null,
        fixUsable: Boolean = true,
        monitoringEnabled: Boolean = true,
        openVisitIds: Set<Long> = emptySet(),
    ) = ZonePresence.of(hasFix, insideMosqueId, fixUsable, monitoringEnabled, openVisitIds)

    @Test
    fun noFixWinsOverEverything() {
        assertEquals(ZonePresence.NO_FIX, presence(hasFix = false, insideMosqueId = 1L, openVisitIds = setOf(1L)))
    }

    @Test
    fun outsideWithNoOpenVisit() {
        assertEquals(ZonePresence.OUTSIDE, presence())
    }

    @Test
    fun insideWithOpenVisitIsInside() {
        assertEquals(ZonePresence.INSIDE, presence(insideMosqueId = 1L, openVisitIds = setOf(1L)))
    }

    @Test
    fun insideWithOpenVisitStaysInsideOnWeakFix() {
        assertEquals(
            ZonePresence.INSIDE,
            presence(insideMosqueId = 1L, fixUsable = false, openVisitIds = setOf(1L)),
        )
    }

    @Test
    fun insideWithoutVisitIsConfirming() {
        assertEquals(ZonePresence.CONFIRMING, presence(insideMosqueId = 1L))
    }

    @Test
    fun insideOtherMosqueThanOpenVisitIsConfirming() {
        assertEquals(ZonePresence.CONFIRMING, presence(insideMosqueId = 2L, openVisitIds = setOf(1L)))
    }

    @Test
    fun insideOnCoarseFixIsWeakFix() {
        assertEquals(ZonePresence.WEAK_FIX, presence(insideMosqueId = 1L, fixUsable = false))
    }

    @Test
    fun insideWithDetectionOffSaysSo() {
        assertEquals(
            ZonePresence.DETECTION_OFF,
            presence(insideMosqueId = 1L, monitoringEnabled = false, openVisitIds = setOf(1L)),
        )
    }

    @Test
    fun outsideWithOpenVisitIsLeaving() {
        assertEquals(ZonePresence.LEAVING, presence(openVisitIds = setOf(1L)))
    }

    @Test
    fun outsideWithOpenVisitButDetectionOffIsOutside() {
        assertEquals(ZonePresence.OUTSIDE, presence(monitoringEnabled = false, openVisitIds = setOf(1L)))
    }
}
