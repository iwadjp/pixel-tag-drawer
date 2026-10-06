package com.iwadjp.pixeltagdrawer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class TagPagerTest {
    @get:Rule val rule = createComposeRule()
    private val ids = mutableStateOf(listOf(10L, 20L, 30L))
    private val selected = mutableStateOf<Long?>(10L)
    private val enabled = mutableStateOf(true)
    private lateinit var pager: PagerState
    private lateinit var scrolls: TagPageScrollStates
    private var density = 1f
    private var clicks = 0
    private val selections = mutableListOf<Long>()
    private fun content(grid: Boolean = false, empty: Boolean = false, edges: Boolean = false) {
        rule.setContent {
            density = LocalDensity.current.density
            pager = rememberTagPagerState(ids.value, selected.value)
            scrolls = rememberTagPageScrollStates()
            val scope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize()) {
                Row {
                    Text("Previous", Modifier.testTag("previous").clickable { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
                    Text("Next", Modifier.testTag("next").clickable { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
                }
                TagPager(ids.value, selected.value, enabled.value, pager,
                    onSelect = { selections += it; selected.value = it },
                    modifier = Modifier.weight(1f).testTag("pager"),
                    gestureInsets = WindowInsets(left = if (edges) (24 * density).toInt() else 0, right = if (edges) (24 * density).toInt() else 0)) { tag ->
                    val id = tag ?: selected.value ?: 10L
                    if (empty) Text("Empty $id", Modifier.fillMaxSize().testTag("page-$id"))
                    else if (grid) LazyVerticalGrid(GridCells.Fixed(4), state = scrolls.grid(id), modifier = Modifier.fillMaxSize()) {
                        items((0..99).toList()) { Text("$id-$it", Modifier.fillMaxWidth().height(64.dp).clickable { clicks++ }) }
                    } else LazyColumn(state = scrolls.list(id), modifier = Modifier.fillMaxSize()) {
                        items((0..99).toList()) { Text("$id-$it", Modifier.fillMaxWidth().height(64.dp).clickable { clicks++ }) }
                    }
                }
            }
        }
    }
    private fun horizontal(left: Boolean = true) {
        rule.onNodeWithTag("pager").performTouchInput {
            swipe(Offset(if (left) 320f else 80f, 500f) * density,
                Offset(if (left) 80f else 320f, 440f) * density, 500)
        }
        rule.waitForIdle()
    }
    @Test fun measured_horizontal_list_moves_next_previous_without_click() {
        content(); horizontal(); assertEquals(20L, selected.value)
        horizontal(false); assertEquals(10L, selected.value); assertEquals(0, clicks)
    }
    @Test fun measured_horizontal_grid_moves_next_previous_without_click() {
        content(grid = true); horizontal(); assertEquals(20L, selected.value)
        horizontal(false); assertEquals(10L, selected.value); assertEquals(0, clicks)
    }
    @Test fun empty_page_swipes_and_wraps_both_ways() {
        content(empty = true); horizontal(false); assertEquals(30L, selected.value)
        horizontal(); assertEquals(10L, selected.value)
    }
    private fun vertical(grid: Boolean) {
        content(grid = grid)
        rule.onNodeWithTag("pager").performTouchInput {
            swipe(Offset(180f, 550f) * density, Offset(210f, 340f) * density, 500)
        }
        rule.runOnIdle {
            val moved = if (grid) scrolls.grid(10L).firstVisibleItemIndex > 0 || scrolls.grid(10L).firstVisibleItemScrollOffset > 0
                else scrolls.list(10L).firstVisibleItemIndex > 0 || scrolls.list(10L).firstVisibleItemScrollOffset > 0
            assertTrue(moved)
            assertEquals(10L, selected.value); assertEquals(0, clicks)
        }
    }
    @Test fun measured_vertical_list_scrolls_without_tag_change() { vertical(false) }
    @Test fun measured_vertical_grid_scrolls_without_tag_change() { vertical(true) }
    @Test fun list_tap_clicks_without_paging() { content(); rule.onNodeWithTag("pager").performTouchInput { click() }; assertEquals(1, clicks); assertEquals(10L, selected.value) }
    @Test fun grid_tap_clicks_without_paging() { content(grid = true); rule.onNodeWithTag("pager").performTouchInput { click() }; assertEquals(1, clicks); assertEquals(10L, selected.value) }
    @Test fun disabled_does_not_change_tag() { enabled.value = false; content(); horizontal(); assertEquals(10L, selected.value); assertTrue(selections.isEmpty()) }
    @Test fun buttons_animate_and_commit_the_same_selection() {
        content(); rule.onNodeWithTag("next").performClick(); rule.waitForIdle(); assertEquals(20L, selected.value)
        rule.onNodeWithTag("previous").performClick(); rule.waitForIdle(); assertEquals(10L, selected.value)
    }
    @Test fun external_selection_and_reorder_keep_selected_tag() {
        content(); rule.runOnIdle { selected.value = 30L }; rule.waitForIdle()
        rule.runOnIdle { assertEquals(30L, tagIdAtPage(ids.value, pager.settledPage)); ids.value = listOf(30L, 10L, 20L) }
        rule.waitForIdle(); assertEquals(30L, selected.value)
        horizontal(); assertEquals(10L, selected.value)
    }
    @Test fun drag_follows_finger_and_slow_short_drag_returns_to_current_tag() {
        content(empty = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pager").performTouchInput { down(Offset(280f, 400f) * density); moveTo(Offset(220f, 400f) * density, 500) }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertTrue(kotlin.math.abs(pager.currentPageOffsetFraction) > 0.02f); assertEquals(10L, selected.value) }
        rule.onNodeWithTag("page-20").assertIsDisplayed()
        rule.onNodeWithTag("pager").performTouchInput { advanceEventTime(500); up() }
        rule.mainClock.autoAdvance = true; rule.waitForIdle()
        assertEquals(10L, selected.value); assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
    }
    @Test fun quick_fling_can_settle_an_adjacent_page() {
        content(empty = true)
        rule.onNodeWithTag("pager").performTouchInput { swipe(Offset(280f, 400f) * density, Offset(160f, 400f) * density, 80) }
        rule.waitForIdle(); assertEquals(20L, selected.value)
    }
    @Test fun page_positions_are_independent() {
        content()
        rule.onNodeWithTag("pager").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val offset = scrolls.list(10L).firstVisibleItemIndex to scrolls.list(10L).firstVisibleItemScrollOffset
        horizontal(); assertEquals(0, scrolls.list(20L).firstVisibleItemIndex)
        horizontal(false); assertEquals(offset, scrolls.list(10L).firstVisibleItemIndex to scrolls.list(10L).firstVisibleItemScrollOffset)
    }
    @Test fun edge_start_does_not_page_but_next_interior_gesture_does() {
        content(empty = true, edges = true)
        rule.onNodeWithTag("pager").performTouchInput { down(Offset(2f, 400f) * density) }
        rule.waitForIdle()
        rule.onNodeWithTag("pager").performTouchInput { moveTo(Offset(260f, 400f) * density, 500); up() }
        rule.waitForIdle(); assertEquals(10L, selected.value)
        horizontal(); assertEquals(20L, selected.value)
    }
    @Test fun two_tags_wrap_without_sharing_visible_scroll_layouts() {
        ids.value = listOf(10L, 20L); content()
        horizontal(); assertEquals(20L, selected.value)
        horizontal(); assertEquals(10L, selected.value)
        horizontal(false); assertEquals(20L, selected.value)
    }
    @Test fun tag_add_delete_keeps_valid_selection() {
        content(); rule.runOnIdle { ids.value = listOf(10L, 40L, 20L, 30L) }; rule.waitForIdle()
        horizontal(); assertEquals(40L, selected.value)
        rule.runOnIdle { selected.value = 20L; ids.value = listOf(10L, 20L, 30L) }; rule.waitForIdle()
        assertEquals(20L, selected.value); assertEquals(20L, tagIdAtPage(ids.value, pager.settledPage))
    }
    @Test fun disabled_grid_still_scrolls_and_taps() {
        enabled.value = false; content(grid = true)
        rule.onNodeWithTag("pager").performTouchInput { swipeUp() }; rule.waitForIdle()
        assertTrue(scrolls.grid(10L).firstVisibleItemIndex > 0)
        rule.onNodeWithTag("pager").performTouchInput { click() }
        assertEquals(1, clicks); assertEquals(10L, selected.value)
    }
    @Test fun cancellation_does_not_launch_an_app() {
        content()
        rule.onNodeWithTag("pager").performTouchInput {
            down(Offset(280f, 400f) * density); moveTo(Offset(220f, 400f) * density, 500); cancel()
        }
        rule.waitForIdle(); assertEquals(0, clicks); assertEquals(10L, selected.value)
    }

    @Test fun cancelled_long_horizontal_drag_returns_to_selected_tag() {
        content()
        rule.onNodeWithTag("pager").performTouchInput {
            down(Offset(320f, 400f) * density); moveTo(Offset(80f, 400f) * density, 500); cancel()
        }
        rule.waitForIdle(); assertEquals(0, clicks); assertEquals(10L, selected.value)
    }
    @Test fun multitouch_does_not_navigate() {
        content()
        rule.onNodeWithTag("pager").performTouchInput {
            down(0, Offset(320f, 400f) * density); down(1, Offset(320f, 500f) * density)
            moveTo(0, Offset(80f, 400f) * density, 500); up(0); up(1)
        }
        rule.waitForIdle(); assertEquals(0, clicks); assertEquals(10L, selected.value)
    }

}
