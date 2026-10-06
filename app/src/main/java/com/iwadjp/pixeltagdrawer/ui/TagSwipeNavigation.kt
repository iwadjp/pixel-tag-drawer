package com.iwadjp.pixeltagdrawer.ui

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.iwadjp.pixeltagdrawer.BuildConfig

/** Horizontal travel (dp) a release must cover before it counts as a tag swipe. */
internal const val TAG_SWIPE_MIN_DISTANCE_DP = 72f

/** Horizontal travel must be at least this many times the vertical travel. */
internal const val TAG_SWIPE_DOMINANCE_RATIO = 2f

/**
 * Next/previous tag in display order, wrapping at both ends (same rule as the ◀▶ buttons).
 * Returns null (no navigation) when there is nothing to move to: fewer than two tags,
 * no current tag, or a current tag that is not in [tagIds].
 */
internal fun adjacentTagId(tagIds: List<Long>, currentId: Long?, delta: Int): Long? {
    if (tagIds.size < 2 || currentId == null) return null
    val currentIndex = tagIds.indexOf(currentId)
    if (currentIndex < 0) return null
    return tagIds[Math.floorMod(currentIndex + delta, tagIds.size)]
}

/**
 * Classifies a finished one-finger drag. Returns +1 (next tag, finger moved left),
 * -1 (previous tag, finger moved right), or null when it is not a clear horizontal swipe.
 */
internal fun resolveHorizontalSwipe(
    dx: Float,
    dy: Float,
    minDistancePx: Float,
    dominanceRatio: Float = TAG_SWIPE_DOMINANCE_RATIO,
): Int? {
    if (abs(dx) < minDistancePx) return null
    if (abs(dx) < abs(dy) * dominanceRatio) return null
    return if (dx < 0f) +1 else -1
}

/** True when a touch starting at [startX] (window coordinates) lies in a system back-gesture edge. */
internal fun startsInSystemGestureEdge(
    startX: Float,
    windowWidthPx: Float,
    leftInsetPx: Float,
    rightInsetPx: Float,
): Boolean = startX < leftInsetPx || startX > windowWidthPx - rightInsetPx

/**
 * Observes one-finger drags, yielding to child taps and vertical scrolling until a clear
 * horizontal swipe is recognized. Only then consume movement to cancel the child's tap.
 * A movement the child already consumed (vertical scroll),
 * a multi-touch gesture, or one that starts in a system gesture edge is ignored.
 * [onSwipe] receives +1 (next) or -1 (previous).
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.tagSwipeNavigation(
    enabled: Boolean,
    diagnostics: Boolean = BuildConfig.SWIPE_DIAGNOSTICS,
    onDiagnostic: (SwipeGestureDiagnostic) -> Unit = SwipeDiagnostics::record,
    onSwipe: (Int) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnDiagnostic by rememberUpdatedState(onDiagnostic)
    // Diagnostic observation only: never captures or consumes Android MotionEvents.
    val nativeAction = remember { intArrayOf(MotionEvent.ACTION_CANCEL) }
    val minDistancePx = with(density) { TAG_SWIPE_MIN_DISTANCE_DP.dp.toPx() }
    val leftInsetPx = WindowInsets.systemGestures.getLeft(density, layoutDirection).toFloat()
    val rightInsetPx = WindowInsets.systemGestures.getRight(density, layoutDirection).toFloat()
    var originX by remember { mutableFloatStateOf(0f) }
    var windowWidthPx by remember { mutableIntStateOf(0) }
    return this
        .then(if (diagnostics) Modifier.motionEventSpy { nativeAction[0] = it.actionMasked } else Modifier)
        .onGloballyPositioned { coords ->
            originX = coords.positionInWindow().x
            windowWidthPx = coords.findRootCoordinates().size.width
        }
        .pointerInput(enabled, diagnostics, minDistancePx, leftInsetPx, rightInsetPx) {
            if (!enabled && !diagnostics) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val start = down.position
                var end: Offset = start
                var horizontalClaimed = false
                val startWindowX = originX + start.x
                val widthOnDown = windowWidthPx.toFloat()
                val edge = startsInSystemGestureEdge(
                    startX = startWindowX,
                    windowWidthPx = widthOnDown,
                    leftInsetPx = leftInsetPx,
                    rightInsetPx = rightInsetPx,
                )
                var rejected = edge
                var multitouch = false
                var childConsumed = false
                var maxPointers = 1
                var consumedAt: Offset? = null
                var termination = "CANCELLED"
                var result = "REJECT_CANCELLED"
                try {
                    while (true) {
                        // Final pass: children (e.g. the vertical scroller) have already had their say.
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        maxPointers = maxOf(maxPointers, event.changes.size)
                        if (event.changes.size > 1) {
                            multitouch = true
                            rejected = true
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            termination = "POINTER_LOST"
                            break
                        }
                        // clickable consumes DOWN in Main, before we see that same event in
                        // Final. That is a tap candidate, not a consumed drag. Only consumed
                        // movement disqualifies navigation; keep yielding to child scrolling.
                        if (change.isConsumed && change.pressed && change.position != change.previousPosition) {
                            if (!childConsumed) consumedAt = change.position - start
                            childConsumed = true
                            rejected = true
                        }
                        end = change.position
                        if (!change.pressed) {
                            // Compose can synthesize a released pointer when a node is removed
                            // or Android cancels input. Don't confuse that with a native UP.
                            termination = if (diagnostics && nativeAction[0] != MotionEvent.ACTION_UP &&
                                nativeAction[0] != MotionEvent.ACTION_POINTER_UP) "CANCELLED" else "UP"
                            break
                        }
                        if (enabled && !rejected) {
                            if (resolveHorizontalSwipe(end.x - start.x, end.y - start.y, minDistancePx) != null) {
                                horizontalClaimed = true
                            }
                            // Final travels parent -> child. Cancel a clickable's pending tap
                            // before its Final pass, but leave DOWN and vertical drags untouched.
                            if (horizontalClaimed && change.position != change.previousPosition) {
                                change.consume()
                            }
                        }
                    }
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val delta = if (enabled && !rejected) resolveHorizontalSwipe(dx, dy, minDistancePx) else null
                    result = when {
                        diagnostics && termination == "CANCELLED" && delta == null -> "REJECT_CANCELLED"
                        !enabled -> "REJECT_DISABLED"
                        edge -> "REJECT_EDGE"
                        multitouch -> "REJECT_MULTITOUCH"
                        childConsumed -> "REJECT_CONSUMED"
                        abs(dx) < minDistancePx -> "REJECT_DISTANCE"
                        abs(dx) < abs(dy) * TAG_SWIPE_DOMINANCE_RATIO -> "REJECT_DIRECTION_RATIO"
                        delta != null -> "ACCEPT"
                        else -> "REJECT_OTHER"
                    }
                    delta?.let { currentOnSwipe(it) }
                } finally {
                    if (diagnostics) {
                        val scale = density.density
                        currentOnDiagnostic(SwipeGestureDiagnostic(
                            dxDp = (end.x - start.x) / scale,
                            dyDp = (end.y - start.y) / scale,
                            enabled = enabled, edge = edge, multitouch = multitouch,
                            childConsumed = childConsumed, maxPointers = maxPointers,
                            consumedAtDxDp = consumedAt?.x?.div(scale),
                            consumedAtDyDp = consumedAt?.y?.div(scale),
                            startWindowXDp = startWindowX / scale,
                            windowWidthDp = widthOnDown / scale,
                            leftInsetDp = leftInsetPx / scale, rightInsetDp = rightInsetPx / scale,
                            claimed = horizontalClaimed, termination = termination, result = result,
                        ))
                    }
                }
            }
        }
}
