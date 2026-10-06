package com.iwadjp.pixeltagdrawer.ui

import android.view.MotionEvent
import com.iwadjp.pixeltagdrawer.BuildConfig
import kotlinx.coroutines.CancellationException
import java.util.Locale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.motionEventSpy

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
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
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt
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
    fun trace(event: String, detail: String = "") = PagerDiagnostics.record(event, state, currentSelected, detail)
    val observedDrag = remember(state) { booleanArrayOf(false) }
    LaunchedEffect(state) {
        trace("PAGER_INSTANCE", "identity=${System.identityHashCode(state)} pages=${state.pageCount}")
    }
    LaunchedEffect(state, ids) {
        if (BuildConfig.PAGER_DIAGNOSTICS) {
            snapshotFlow { listOf(state.currentPage, state.settledPage, state.targetPage,
                state.isScrollInProgress, currentSelected) }.distinctUntilChanged().collect { trace("STATE") }
        }
    }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = gestureInsets.getLeft(density, direction).toFloat()
    val right = gestureInsets.getRight(density, direction).toFloat()
    var origin by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val cancelled = remember(state) { booleanArrayOf(false) }
    val nativeAction = remember(state) { intArrayOf(MotionEvent.ACTION_CANCEL) }
    val pageOnDown = remember(state) { intArrayOf(state.settledPage) }
    val flingBehavior = PagerDefaults.flingBehavior(state)
    val navigationScope = rememberCoroutineScope()
    val valid = enabled && selectedId in ids
    LaunchedEffect(state, selectedId, enabled) {
        trace("EFFECT_SELECTED", "enabled=$enabled valid=$valid")
        if (enabled && selectedId in ids && tagIdAtPage(ids, state.settledPage) != selectedId) {
            // Chip/shortcut selection is external; buttons use animateScrollToPage directly.
            val destination = nearestTagPage(ids, state.currentPage, selectedId!!)
            trace("SELECTED_SCROLL_BEGIN", "destination=$destination")
            try {
                state.scrollToPage(destination)
                trace("SELECTED_SCROLL_END")
            } catch (e: CancellationException) { trace("SELECTED_SCROLL_CANCEL"); throw e }
        }
    }
    LaunchedEffect(state, ids, valid) {
        trace("EFFECT_SETTLED", "valid=$valid ids=$ids")
        if (!valid) return@LaunchedEffect
        snapshotFlow { state.isScrollInProgress to state.settledPage }
            .filter { !it.first }.distinctUntilChanged().collect { (_, page) ->
                val id = tagIdAtPage(ids, page)
                if (!cancelled[0] && id != currentSelected) { trace("SELECT_COMMIT", "destinationTag=$id"); currentOnSelect(id) }
                if (page < ids.size * 2 || page >= ids.size * (TAG_PAGE_CYCLES - 2)) {
                    val destination = tagPageCenter(ids.size) + Math.floorMod(page, ids.size)
                    trace("WRAP_SCROLL_BEGIN", "destination=$destination")
                    try { state.scrollToPage(destination); trace("WRAP_SCROLL_END") }
                    catch (e: CancellationException) { trace("WRAP_SCROLL_CANCEL"); throw e }
                }
            }
    }
    if (!enabled || selectedId !in ids) {
        androidx.compose.foundation.layout.Box(modifier) { content(null) }
    } else {
        HorizontalPager(
            state = state, modifier = modifier
                .semantics {
                    // The custom detector replaces userScrollEnabled; retain page actions.
                    pageLeft { navigationScope.launch { state.animateScrollToPage(state.currentPage - 1) }; true }
                    pageRight { navigationScope.launch { state.animateScrollToPage(state.currentPage + 1) }; true }
                }
                .onGloballyPositioned { origin = it.positionInWindow().x; width = it.findRootCoordinates().size.width }
                .motionEventSpy { event ->
                    nativeAction[0] = event.actionMasked
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { trace("NATIVE_DOWN"); cancelled[0] = false; pageOnDown[0] = state.settledPage }
                        MotionEvent.ACTION_UP -> trace("NATIVE_UP")
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                            trace("RESET_REQUEST_NATIVE", "action=${event.actionMasked} destination=${pageOnDown[0]}")
                            cancelled[0] = true
                        }
                    }
                }
                .then(if (BuildConfig.PAGER_DIAGNOSTICS) Modifier.pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var end = down.position
                        var consumedBeforeDrag = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            event.changes.firstOrNull { it.id == down.id }?.let { change ->
                                end = change.position
                                if (change.pressed && change.position != change.previousPosition &&
                                    change.isConsumed && !observedDrag[0]) consumedBeforeDrag = true
                            }
                        } while (event.changes.any { it.pressed })
                        val travel = (end - down.position) / density.density
                        trace("FINAL_TOUCH", String.format(Locale.ROOT, "dxDp=%.1f dyDp=%.1f consumedBeforePagerDrag=%s", travel.x, travel.y, consumedBeforeDrag))
                    }
                } else Modifier)
                .pointerInput(state, left, right, flingBehavior) {
                    coroutineScope {
                        // One job owns drag, fling and cancellation recovery. The next DOWN
                        // cancels it synchronously: no queued reset can stop a newer drag.
                        var dragJob: Job? = null
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            dragJob?.cancel()
                            cancelled[0] = false
                            observedDrag[0] = false
                            val startPage = state.settledPage
                            val edge = startsInSystemGestureEdge(origin + down.position.x, width.toFloat(), left, right)
                            trace("GUARD_DOWN", "edge=$edge left=$left right=$right origin=$origin width=$width enabled=$enabled")
                            val velocity = VelocityTracker()
                            velocity.addPointerInputChange(down)
                            val events = Channel<PagerDragEvent>(Channel.UNLIMITED)
                            var claimed = false
                            var ownsDrag = false
                            var blocked = false
                            var verticalReported = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (event.changes.size > 1) blocked = true
                                if (change != null) {
                                    velocity.addPointerInputChange(change)
                                    val travel = change.position - down.position
                                    if (!claimed && !blocked && change.pressed && abs(travel.x) > viewConfiguration.touchSlop &&
                                        abs(travel.x) >= abs(travel.y) * 2f) {
                                        if (edge || blocked) {
                                            blocked = true
                                            trace("GUARD_BLOCK", "edge=$edge pointers=${event.changes.size}")
                                        } else {
                                            claimed = true
                                            ownsDrag = true
                                            observedDrag[0] = true
                                            trace("HORIZONTAL_CLAIM", "dx=${travel.x / density.density} dy=${travel.y / density.density}")
                                            trace("PAGER_DRAG_START")
                                            dragJob = launch(start = CoroutineStart.UNDISPATCHED) {
                                                try {
                                                    var end: PagerDragEvent.End? = null
                                                    state.scroll(MutatePriority.UserInput) {
                                                        while (end == null) {
                                                            when (val input = events.receive()) {
                                                                is PagerDragEvent.Move -> scrollBy(-input.delta)
                                                                is PagerDragEvent.End -> end = input
                                                            }
                                                        }
                                                        if (!end!!.cancelled) {
                                                            // Let Pager's own DOWN/UP direction observer finish.
                                                            yield()
                                                            trace("FLING_BEGIN", "velocity=${end!!.velocity / density.density}")
                                                            val scrollScope = this
                                                            with(flingBehavior) {
                                                                performFling(-end!!.velocity) { remaining ->
                                                                    val size = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
                                                                    val target = state.currentPage + if (size == 0) 0 else (remaining / size).roundToInt()
                                                                    with(state) { scrollScope.updateTargetPage(target.coerceIn(0, state.pageCount - 1)) }
                                                                }
                                                            }
                                                            trace("FLING_END")
                                                        }
                                                    }
                                                    if (end!!.cancelled) {
                                                        trace("RESET_BEGIN", "destination=$startPage")
                                                        state.animateScrollToPage(startPage)
                                                        cancelled[0] = false
                                                        trace("RESET_END")
                                                    }
                                                } catch (e: CancellationException) {
                                                    trace("DRAG_JOB_CANCEL")
                                                    throw e
                                                } finally { events.close() }
                                            }
                                            // Preserve the normal slop dead zone, then follow every move.
                                            events.trySend(PagerDragEvent.Move(travel.x - sign(travel.x) * viewConfiguration.touchSlop))
                                        }
                                    } else if (claimed && change.pressed && !blocked) {
                                        events.trySend(PagerDragEvent.Move(change.position.x - change.previousPosition.x))
                                    }
                                    if (!claimed && !verticalReported && abs(travel.y) > viewConfiguration.touchSlop && abs(travel.y) > abs(travel.x)) {
                                        verticalReported = true
                                        trace("VERTICAL_HANDOFF")
                                    }
                                    if (claimed && (blocked || !change.pressed)) {
                                        val abort = blocked || (nativeAction[0] != MotionEvent.ACTION_UP && nativeAction[0] != MotionEvent.ACTION_POINTER_UP)
                                        cancelled[0] = abort
                                        observedDrag[0] = false
                                        trace(if (abort) "PAGER_DRAG_CANCEL" else "PAGER_DRAG_STOP")
                                        val maximumVelocity = viewConfiguration.maximumFlingVelocity
                                        events.trySend(PagerDragEvent.End(velocity.calculateVelocity(Velocity(maximumVelocity, maximumVelocity)).x, abort))
                                        claimed = false
                                        blocked = true
                                    }
                                }
                                // Initial consumption cancels a child drag/click only after horizontal
                                // intent is clear. Never consume UP: Pager also observes its direction.
                                if (claimed || blocked) event.changes.filter { it.pressed }.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            if (!ownsDrag) events.close()
                        }
                    }
                },
            // Arbitration above feeds the public scroll API; Pager supplies layout and snapping.
            // Disable only its competing pointer detector, never the child vertical scrollables.
            userScrollEnabled = false,
            flingBehavior = flingBehavior,
            reverseLayout = direction == androidx.compose.ui.unit.LayoutDirection.Rtl,
            beyondViewportPageCount = 0,
        ) { page -> content(tagIdAtPage(ids, page)) }
    }
}

private sealed interface PagerDragEvent {
    data class Move(val delta: Float) : PagerDragEvent
    data class End(val velocity: Float, val cancelled: Boolean) : PagerDragEvent
}
