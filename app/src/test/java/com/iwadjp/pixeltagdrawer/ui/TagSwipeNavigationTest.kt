package com.iwadjp.pixeltagdrawer.ui

import org.junit.Assert.*
import org.junit.Test

class TagSwipeLogicTest {
    private val tags = listOf(10L, 20L, 30L)

    @Test fun next_and_previous() {
        assertEquals(20L, adjacentTagId(tags, 10L, +1))
        assertEquals(10L, adjacentTagId(tags, 20L, -1))
    }

    @Test fun wraps_around_both_ways() {
        assertEquals(10L, adjacentTagId(tags, 30L, +1))
        assertEquals(30L, adjacentTagId(tags, 10L, -1))
    }

    @Test fun no_navigation_for_one_zero_unknown_or_unselected() {
        assertNull(adjacentTagId(listOf(10L), 10L, +1))
        assertNull(adjacentTagId(emptyList(), 10L, +1))
        assertNull(adjacentTagId(tags, 99L, +1))
        assertNull(adjacentTagId(tags, null, +1))
    }

    @Test fun system_gesture_edges() {
        assertTrue(startsInSystemGestureEdge(10f, 1000f, 30f, 30f))
        assertTrue(startsInSystemGestureEdge(990f, 1000f, 30f, 30f))
        assertFalse(startsInSystemGestureEdge(500f, 1000f, 30f, 30f))
        assertFalse(startsInSystemGestureEdge(500f, 1000f, 0f, 0f))
    }
}
