package com.iwadjp.pixeltagdrawer.ui

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

/** True when a touch starting at [startX] (window coordinates) lies in a system back-gesture edge. */
internal fun startsInSystemGestureEdge(
    startX: Float,
    windowWidthPx: Float,
    leftInsetPx: Float,
    rightInsetPx: Float,
): Boolean = startX < leftInsetPx || startX > windowWidthPx - rightInsetPx
