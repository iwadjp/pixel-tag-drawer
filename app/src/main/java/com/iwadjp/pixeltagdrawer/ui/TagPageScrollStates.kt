package com.iwadjp.pixeltagdrawer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** Independent positions for neighboring pages, retained across Activity recreation. */
internal class TagPageScrollStates {
    private val lists = mutableMapOf<Long, LazyListState>()
    private val grids = mutableMapOf<Long, LazyGridState>()
    fun list(id: Long) = lists.getOrPut(id) { LazyListState() }
    fun grid(id: Long) = grids.getOrPut(id) { LazyGridState() }
    companion object {
        val saver = Saver<TagPageScrollStates, List<Long>>(
            save = { states -> buildList {
                states.lists.forEach { (id, state) -> addAll(listOf(0L, id, state.firstVisibleItemIndex.toLong(), state.firstVisibleItemScrollOffset.toLong())) }
                states.grids.forEach { (id, state) -> addAll(listOf(1L, id, state.firstVisibleItemIndex.toLong(), state.firstVisibleItemScrollOffset.toLong())) }
            } },
            restore = { values -> TagPageScrollStates().apply {
                values.chunked(4).forEach { (kind, id, index, offset) ->
                    if (kind == 0L) lists[id] = LazyListState(index.toInt(), offset.toInt())
                    else grids[id] = LazyGridState(index.toInt(), offset.toInt())
                }
            } },
        )
    }
}
@Composable
internal fun rememberTagPageScrollStates() = rememberSaveable(saver = TagPageScrollStates.saver) { TagPageScrollStates() }
