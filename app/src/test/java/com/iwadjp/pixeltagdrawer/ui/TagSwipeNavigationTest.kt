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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
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
    private var childConsumedMoves = 0
    private var taps = 0
    private lateinit var listState: LazyListState
    private lateinit var gridState: LazyGridState
    private var gestureDensity = 1f

    private fun setContent(enabled: Boolean, grid: Boolean = false, plain: Boolean = false, empty: Boolean = false) {
        composeRule.setContent {
            gestureDensity = LocalDensity.current.density
            listState = rememberLazyListState()
            gridState = rememberLazyGridState()
            Box(Modifier.fillMaxSize().testTag("area").pointerInput(Unit) {
                awaitPointerEventScope {
                    var start = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed && !change.previousPressed) start = change.position
                            if (change.pressed && change.position != change.previousPosition && change.isConsumed &&
                                kotlin.math.abs(change.position.x - start.x) < 72f * gestureDensity) childConsumedMoves++
                        }
                    }
                }
            }.tagSwipeNavigation(
                enabled,
            ) { swipes += it }) {
                // Production AppRow/AppGridCell are clickable: their DOWN is consumed even
                // when the eventual gesture is horizontal rather than a tap or vertical drag.
                if (empty) {
                    Text("No matching apps")
                } else if (plain) {
                    Text("plain clickable content", Modifier.fillMaxSize().clickable { taps++ })
                } else if (grid) {
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

    @Test fun plain_clickable_content_preserves_horizontal_swipe() {
        setContent(enabled = true)
        composeRule.onNodeWithTag("area").performTouchInput { swipeLeft() }
        assertEquals(listOf(+1), swipes)
        assertEquals(0, taps)
    }

    @Test fun disabled_does_not_navigate() {
        setContent(enabled = false)
        composeRule.onNodeWithTag("area").performTouchInput { swipeLeft() }
        assertTrue(swipes.isEmpty())
    }

    @Test fun short_gesture_does_not_navigate() {
        setContent(enabled = true, plain = true)
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.5f), Offset(width * 0.55f, height * 0.5f))
        }
        assertTrue(swipes.isEmpty())
    }

    @Test fun diagonal_without_child_scroll_does_not_navigate() {
        setContent(enabled = true, plain = true)
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(320f, 400f) * gestureDensity, Offset(140f, 530f) * gestureDensity)
        }
        assertTrue(swipes.isEmpty())
    }

    @Test fun multitouch_does_not_navigate() {
        setContent(enabled = true, plain = true)
        composeRule.onNodeWithTag("area").performTouchInput {
            down(0, Offset(width * 0.7f, height * 0.4f))
            down(1, Offset(width * 0.7f, height * 0.6f))
            moveTo(0, Offset(width * 0.3f, height * 0.4f))
            up(0)
            up(1)
        }
        assertTrue(swipes.isEmpty())
    }

    @Test fun disposal_of_an_active_gesture_does_not_navigate() {
        val visible = mutableStateOf(true)
        composeRule.setContent {
            if (visible.value) {
                Box(Modifier.fillMaxSize().testTag("area").tagSwipeNavigation(
                    true,
                ) { swipes += it })
            }
        }
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            moveBy(Offset(-10f, 0f))
        }
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        assertTrue(swipes.isEmpty())
    }

    private fun measuredHorizontalSwipes(grid: Boolean) {
        setContent(enabled = true, grid = grid)
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(320f, 500f) * gestureDensity, Offset(100f, 440f) * gestureDensity, 500)
        }
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(80f, 500f) * gestureDensity, Offset(320f, 430f) * gestureDensity, 500)
        }
        // Real Lazy scrollables consume the early vertical component before 72dp is reached.
        // That history must not permanently veto a later, clearly horizontal gesture.
        assertTrue(childConsumedMoves > 0)
        assertEquals(listOf(+1, -1), swipes)
        assertEquals(0, taps)
    }

    @Test fun consumed_moves_in_clickable_list_do_not_veto_measured_horizontal_swipes() {
        measuredHorizontalSwipes(grid = false)
    }

    @Test fun consumed_moves_in_clickable_grid_do_not_veto_measured_horizontal_swipes() {
        measuredHorizontalSwipes(grid = true)
    }

    private fun measuredVerticalSwipe(grid: Boolean) {
        setContent(enabled = true, grid = grid)
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(180f, 550f) * gestureDensity, Offset(210f, 340f) * gestureDensity, 500)
        }
        composeRule.runOnIdle {
            val moved = if (grid) gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
                else listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            assertTrue(moved)
        }
        assertTrue(swipes.isEmpty())
        assertEquals(0, taps)
    }

    @Test fun measured_vertical_gesture_keeps_list_scrolling() { measuredVerticalSwipe(grid = false) }

    @Test fun measured_vertical_gesture_keeps_grid_scrolling() { measuredVerticalSwipe(grid = true) }

    @Test fun empty_tag_area_accepts_a_measured_horizontal_swipe() {
        setContent(enabled = true, empty = true)
        composeRule.onNodeWithTag("area").performTouchInput {
            swipe(Offset(300f, 500f) * gestureDensity, Offset(120f, 480f) * gestureDensity, 500)
        }
        assertEquals(listOf(+1), swipes)
        assertEquals(0, taps)
    }

    private fun cancelAfterHorizontalClaim() {
        setContent(enabled = true, plain = true)
        composeRule.onNodeWithTag("area").performTouchInput {
            down(Offset(320f, 500f) * gestureDensity)
            moveTo(Offset(100f, 440f) * gestureDensity)
            cancel()
        }
        assertTrue(swipes.isEmpty())
        assertEquals(0, taps)

    }

    @Test fun cancelled_horizontal_claim_never_navigates() { cancelAfterHorizontalClaim() }

}
