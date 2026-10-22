package dev.switchreborn

import dev.switchreborn.home.Metrics
import dev.switchreborn.home.RowLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class RowLayoutTest {
    private val w = Metrics.REF_W

    @Test fun firstFourTilesNeedNoScroll() {
        for (i in 0..3) assertEquals(0f, RowLayout.scrollFor(i, 12, w, 0f), 0.01f)
    }

    @Test fun fifthTileScrollsOnePitch() {
        assertEquals(Metrics.TILE_PITCH, RowLayout.scrollFor(4, 12, w, 0f), 0.01f)
    }

    @Test fun movingLeftWithinViewKeepsScroll() {
        val s = RowLayout.scrollFor(6, 12, w, 0f)
        assertEquals(s, RowLayout.scrollFor(4, 12, w, s), 0.01f)
    }

    @Test fun goingBackPastTheLeftEdgeAlignsToTheMargin() {
        val s = RowLayout.scrollFor(8, 12, w, 0f)
        assertEquals(2 * Metrics.TILE_PITCH, RowLayout.scrollFor(2, 12, w, s), 0.01f)
    }

    @Test fun allSoftwareEndsAtTheMargin() {
        val s = RowLayout.scrollFor(12, 12, w, 0f)
        assertEquals(RowLayout.maxScroll(12, w), s, 0.01f)
        assertEquals(w - Metrics.TILE_X, RowLayout.slotX(12) + Metrics.ALL_D - s, 0.01f)
    }

    @Test fun shortRowNeverScrolls() {
        assertEquals(0f, RowLayout.maxScroll(2, w), 0.01f)
        assertEquals(0f, RowLayout.scrollFor(2, 2, w, 0f), 0.01f)
    }

    @Test fun hitTestsSlotsAndGaps() {
        assertEquals(0, RowLayout.hit(170f, 0f, 12))
        assertEquals(-1, RowLayout.hit(Metrics.TILE_X + Metrics.TILE + 5f, 0f, 12))
        assertEquals(1, RowLayout.hit(170f + Metrics.TILE_PITCH, 0f, 12))
        assertEquals(2, RowLayout.hit(170f + Metrics.TILE_PITCH, Metrics.TILE_PITCH, 12))
        assertEquals(-1, RowLayout.hit(100f, 0f, 12))
    }
}
