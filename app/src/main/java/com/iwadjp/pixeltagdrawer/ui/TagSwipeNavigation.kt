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
 * Yields to child taps/scrolling until the unchanged distance and ratio rules identify a
 * horizontal swipe. Claim in Initial before children process that movement; observe child
 * consumption in Final without treating its earlier scroll activity as a permanent veto.
 * Multi-touch, cancellation, and system gesture edge starts never navigate.
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
    // Observe native termination (including synthesized cancellation); never consume here.
    val nativeAction = remember { intArrayOf(MotionEvent.ACTION_CANCEL) }
    val minDistancePx = with(density) { TAG_SWIPE_MIN_DISTANCE_DP.dp.toPx() }
    val leftInsetPx = WindowInsets.systemGestures.getLeft(density, layoutDirection).toFloat()
    val rightInsetPx = WindowInsets.systemGestures.getRight(density, layoutDirection).toFloat()
    var originX by remember { mutableFloatStateOf(0f) }
    var windowWidthPx by remember { mutableIntStateOf(0) }
    return this
        .then(if (enabled || diagnostics) Modifier.motionEventSpy { nativeAction[0] = it.actionMasked } else Modifier)
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
                var verticalHandoff = false
                try {
                    while (true) {
                        // Initial travels parent -> child: arbitrate before scrollable/clickable.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
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
                        end = change.position
                        val moved = change.position != change.previousPosition
                        val nativeUp = nativeAction[0] == MotionEvent.ACTION_UP ||
                            nativeAction[0] == MotionEvent.ACTION_POINTER_UP
                        var consumedByParent = false
                        if (enabled && !rejected) {
                            if ((change.pressed || nativeUp) &&
                                resolveHorizontalSwipe(end.x - start.x, end.y - start.y, minDistancePx) != null) {
                                horizontalClaimed = true
                            }
                            // Only a confirmed horizontal gesture owns movement/UP. This also
                            // cancels a pending app click and an already-started child scroll.
                            if (horizontalClaimed && (moved || !change.pressed)) {
                                change.consume()
                                consumedByParent = true
                            }
                        }
                        val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                        val finalChange = finalEvent.changes.firstOrNull { it.id == down.id }
                        if (!consumedByParent && moved && change.pressed && finalChange?.isConsumed == true) {
                            if (!childConsumed) consumedAt = change.position - start
                            childConsumed = true
                        }
                        if (!change.pressed) {
                            termination = if (nativeUp) "UP" else "CANCELLED"
                            break
                        }
                    }
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    verticalHandoff = enabled && !rejected && !horizontalClaimed &&
                        abs(dy) >= viewConfiguration.touchSlop && abs(dy) > abs(dx)
                    val delta = if (enabled && !rejected && termination == "UP")
                        resolveHorizontalSwipe(dx, dy, minDistancePx) else null
                    result = when {
                        termination == "CANCELLED" -> "REJECT_CANCELLED"
                        termination == "POINTER_LOST" -> "REJECT_POINTER_LOST"
                        !enabled -> "REJECT_DISABLED"
                        edge -> "REJECT_EDGE"
                        multitouch -> "REJECT_MULTITOUCH"
                        delta != null -> "ACCEPT"
                        verticalHandoff -> "VERTICAL_HANDOFF"
                        abs(dx) < minDistancePx -> "REJECT_DISTANCE"
                        abs(dx) < abs(dy) * TAG_SWIPE_DOMINANCE_RATIO -> "REJECT_DIRECTION_RATIO"
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
                            verticalHandoff = verticalHandoff,
                        ))
                    }
                }
            }
        }
}
