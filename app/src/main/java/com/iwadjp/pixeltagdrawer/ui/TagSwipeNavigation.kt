package com.iwadjp.pixeltagdrawer.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

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
 * Observes one-finger drags over the content without consuming them, so child taps and the
 * vertical list scroll keep working. A drag the child already consumed (vertical scroll),
 * a multi-touch gesture, or one that starts in a system gesture edge is ignored.
 * [onSwipe] receives +1 (next) or -1 (previous).
 */
@Composable
internal fun Modifier.tagSwipeNavigation(enabled: Boolean, onSwipe: (Int) -> Unit): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val minDistancePx = with(density) { TAG_SWIPE_MIN_DISTANCE_DP.dp.toPx() }
    val leftInsetPx = WindowInsets.systemGestures.getLeft(density, layoutDirection).toFloat()
    val rightInsetPx = WindowInsets.systemGestures.getRight(density, layoutDirection).toFloat()
    var originX by remember { mutableFloatStateOf(0f) }
    var windowWidthPx by remember { mutableIntStateOf(0) }
    return this
        .onGloballyPositioned { coords ->
            originX = coords.positionInWindow().x
            windowWidthPx = coords.findRootCoordinates().size.width
        }
        .pointerInput(enabled, minDistancePx, leftInsetPx, rightInsetPx) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val start = down.position
                var end: Offset = start
                var rejected = startsInSystemGestureEdge(
                    startX = originX + start.x,
                    windowWidthPx = windowWidthPx.toFloat(),
                    leftInsetPx = leftInsetPx,
                    rightInsetPx = rightInsetPx,
                )
                while (true) {
                    // Final pass: children (e.g. the vertical scroller) have already had their say.
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.size > 1) rejected = true
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed && change.pressed) rejected = true
                    end = change.position
                    if (!change.pressed) break
                }
                if (!rejected) {
                    resolveHorizontalSwipe(end.x - start.x, end.y - start.y, minDistancePx)
                        ?.let { currentOnSwipe(it) }
                }
            }
        }
}
