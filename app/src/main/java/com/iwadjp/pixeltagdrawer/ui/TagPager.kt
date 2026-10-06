package com.iwadjp.pixeltagdrawer.ui

import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.motionEventSpy

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.MutatePriority

// Finite repeated cycles keep wrap-around adjacent, with no jump across all tags.
// Recentering at rest keeps navigation away from the finite boundaries.
internal const val TAG_PAGE_CYCLES = 1000
internal fun tagPageCenter(count: Int): Int = count * (TAG_PAGE_CYCLES / 2)
internal fun tagIdAtPage(ids: List<Long>, page: Int): Long = ids[Math.floorMod(page, ids.size)]
internal fun nearestTagPage(ids: List<Long>, currentPage: Int, id: Long): Int {
    val index = ids.indexOf(id)
    if (index < 0) return currentPage
    val cycle = currentPage - Math.floorMod(currentPage, ids.size)
    return listOf(cycle + index - ids.size, cycle + index, cycle + index + ids.size)
        .filter { it in 0 until ids.size * TAG_PAGE_CYCLES }
        .minBy { kotlin.math.abs(it - currentPage) }
}

@Composable
internal fun rememberTagPagerState(ids: List<Long>, selectedId: Long?): PagerState {
    return key(ids) {
        rememberPagerState(initialPage = if (ids.isEmpty()) 0 else
            tagPageCenter(ids.size) + ids.indexOf(selectedId).coerceAtLeast(0),
            pageCount = { (ids.size * TAG_PAGE_CYCLES).coerceAtLeast(1) })
    }
}

/** The selected tag commits only at rest; pages render their own tag during a drag. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TagPager(
    ids: List<Long>, selectedId: Long?, enabled: Boolean, state: PagerState,
    onSelect: (Long) -> Unit, modifier: Modifier = Modifier,
    gestureInsets: WindowInsets = WindowInsets.systemGestures,
    content: @Composable (Long?) -> Unit,
) {
    val currentSelected by rememberUpdatedState(selectedId)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = gestureInsets.getLeft(density, direction).toFloat()
    val right = gestureInsets.getRight(density, direction).toFloat()
    var origin by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val cancelled = remember(state) { booleanArrayOf(false) }
    val nativeAction = remember(state) { intArrayOf(MotionEvent.ACTION_CANCEL) }
    val pageOnDown = remember(state) { intArrayOf(state.settledPage) }
    var resetPage by remember(state) { mutableStateOf<Int?>(null) }
    LaunchedEffect(state, resetPage) {
        resetPage?.let {
            withFrameNanos { }
            state.stopScroll(MutatePriority.PreventUserInput)
            state.animateScrollToPage(it)
            cancelled[0] = false
            resetPage = null
        }
    }
    val valid = enabled && selectedId in ids
    LaunchedEffect(state, selectedId, enabled) {
        if (enabled && selectedId in ids && tagIdAtPage(ids, state.settledPage) != selectedId) {
            // Chip/shortcut selection is external; buttons use animateScrollToPage directly.
            state.scrollToPage(nearestTagPage(ids, state.currentPage, selectedId!!))
        }
    }
    LaunchedEffect(state, ids, valid) {
        if (!valid) return@LaunchedEffect
        snapshotFlow { state.isScrollInProgress to state.settledPage }
            .filter { !it.first }.distinctUntilChanged().collect { (_, page) ->
                val id = tagIdAtPage(ids, page)
                if (!cancelled[0] && id != currentSelected) currentOnSelect(id)
                if (page < ids.size * 2 || page >= ids.size * (TAG_PAGE_CYCLES - 2)) {
                    state.scrollToPage(tagPageCenter(ids.size) + Math.floorMod(page, ids.size))
                }
            }
    }
    if (!enabled || selectedId !in ids) {
        androidx.compose.foundation.layout.Box(modifier) { content(null) }
    } else {
        HorizontalPager(
            state = state, modifier = modifier
                .onGloballyPositioned { origin = it.positionInWindow().x; width = it.findRootCoordinates().size.width }
                .motionEventSpy { event ->
                    nativeAction[0] = event.actionMasked
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { cancelled[0] = false; pageOnDown[0] = state.settledPage }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                            cancelled[0] = true
                            resetPage = pageOnDown[0]
                        }
                    }
                }
                .pointerInput(left, right) {
                    // Guard only horizontal starts in the reserved Back edges. Do not disable
                    // Pager after DOWN (that loses its next gesture); arbitrate before Main.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val edge = startsInSystemGestureEdge(origin + down.position.x, width.toFloat(), left, right)
                        var block = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) {
                                if (!change.pressed && nativeAction[0] != MotionEvent.ACTION_UP &&
                                    nativeAction[0] != MotionEvent.ACTION_POINTER_UP) {
                                    cancelled[0] = true
                                    resetPage = pageOnDown[0]
                                }
                                val travel = change.position - down.position
                                if (edge && kotlin.math.abs(travel.x) > viewConfiguration.touchSlop &&
                                    kotlin.math.abs(travel.x) > kotlin.math.abs(travel.y)) block = true
                            }
                            if (event.changes.size > 1) block = true
                            if (block) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            userScrollEnabled = true,
            reverseLayout = direction == androidx.compose.ui.unit.LayoutDirection.Rtl,
            beyondViewportPageCount = 0,
        ) { page -> content(tagIdAtPage(ids, page)) }
    }
}
