package com.iwadjp.pixeltagdrawer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test fun clear_horizontal_swipe_is_accepted() {
        assertEquals(+1, resolveHorizontalSwipe(-300f, 20f, 100f))
        assertEquals(-1, resolveHorizontalSwipe(300f, -20f, 100f))
    }

    @Test fun vertical_and_diagonal_drags_are_rejected() {
        assertNull(resolveHorizontalSwipe(20f, 400f, 100f))
        assertNull(resolveHorizontalSwipe(-200f, 150f, 100f)) // diagonal: dx < 2 * dy
        assertEquals(+1, resolveHorizontalSwipe(-200f, 100f, 100f)) // exactly at the ratio
    }

    @Test fun small_movement_is_rejected() {
        assertNull(resolveHorizontalSwipe(-99f, 0f, 100f))
        assertNull(resolveHorizontalSwipe(0f, 0f, 100f))
    }

    @Test fun system_gesture_edges() {
        assertTrue(startsInSystemGestureEdge(10f, 1000f, 30f, 30f))
        assertTrue(startsInSystemGestureEdge(990f, 1000f, 30f, 30f))
        assertFalse(startsInSystemGestureEdge(500f, 1000f, 30f, 30f))
        assertFalse(startsInSystemGestureEdge(500f, 1000f, 0f, 0f))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class TagSwipeModifierTest {
    @get:Rule val composeRule = createComposeRule()
    private val swipes = mutableListOf<Int>()
    private var taps = 0
    private lateinit var listState: LazyListState
    private lateinit var gridState: LazyGridState

    private fun setContent(enabled: Boolean, grid: Boolean = false) {
        composeRule.setContent {
            listState = rememberLazyListState()
            gridState = rememberLazyGridState()
            Box(Modifier.fillMaxSize().testTag("area").tagSwipeNavigation(enabled) { swipes += it }) {
                // Production AppRow/AppGridCell are clickable: their DOWN is consumed even
                // when the eventual gesture is horizontal rather than a tap or vertical drag.
                if (grid) {
                    LazyVerticalGrid(columns = GridCells.Fixed(4), state = gridState, modifier = Modifier.fillMaxSize()) {
                        items((0 until 100).toList()) {
                            Text("cell $it", Modifier.fillMaxWidth().height(64.dp).clickable { taps++ })
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items((0 until 100).toList()) {
                            Text("row $it", Modifier.fillMaxWidth().height(64.dp).clickable { taps++ })
                        }
                    }
                }
            }
        }
    }

    @Test fun left_swipe_is_next_and_right_swipe_is_previous() {
        setContent(enabled = true)
        composeRule.onNodeWithTag("area").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("area").performTouchInput { swipeRight() }
        assertEquals(listOf(+1, -1), swipes)
        assertEquals(0, taps)
    }

    @Test fun clickable_grid_accepts_horizontal_swipes_without_clicking_cells() {
        setContent(enabled = true, grid = true)
        composeRule.onNodeWithTag("area").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("area").performTouchInput { swipeRight() }
        assertEquals(listOf(+1, -1), swipes)
        assertEquals(0, taps)
    }

    @Test fun clickable_row_keeps_taps_without_switching_tag() {
        setContent(enabled = true)
        composeRule.onNodeWithTag("area").performTouchInput { click() }
        assertEquals(1, taps)
        assertTrue(swipes.isEmpty())
    }

    @Test fun clickable_grid_keeps_taps_without_switching_tag() {
        setContent(enabled = true, grid = true)
        composeRule.onNodeWithTag("area").performTouchInput { click() }
        assertEquals(1, taps)
        assertTrue(swipes.isEmpty())
    }

    @Test fun clickable_grid_keeps_vertical_scrolling_without_switching_tag() {
        setContent(enabled = true, grid = true)
        composeRule.onNodeWithTag("area").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertTrue(gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
        }
        assertTrue(swipes.isEmpty())
        assertEquals(0, taps)
    }

    @Test fun vertical_scroll_does_not_switch_tag() {
        setContent(enabled = true)
        composeRule.onNodeWithTag("area").performTouchInput { swipeUp() }
        assertTrue(swipes.isEmpty())
        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
        assertEquals(0, taps)
    }

    @Test fun mostly_vertical_diagonal_scroll_does_not_switch_tag() {
        setContent(enabled = true)
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.8f), Offset(width * 0.3f, height * 0.2f))
        }
        assertTrue(swipes.isEmpty())
    }

    @Test fun disabled_ignores_swipes() {
        setContent(enabled = false)
        composeRule.onNodeWithTag("area").performTouchInput { swipeLeft() }
        assertTrue(swipes.isEmpty())
    }
}
