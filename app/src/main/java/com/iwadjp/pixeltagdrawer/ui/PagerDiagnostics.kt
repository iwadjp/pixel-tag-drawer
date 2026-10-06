package com.iwadjp.pixeltagdrawer.ui

import android.os.SystemClock
import androidx.compose.foundation.pager.PagerState
import com.iwadjp.pixeltagdrawer.BuildConfig
import java.util.Locale

/** Temporary, memory-only trace. IDs/coordinates only, no app/tag names or external logging. */
internal object PagerDiagnostics {
    private val lines = ArrayDeque<String>()
    private var sequence = 0
    private var epoch = SystemClock.elapsedRealtime()
    @Synchronized fun record(event: String, state: PagerState, selected: Long?, detail: String = "") {
        if (!BuildConfig.PAGER_DIAGNOSTICS) return
        lines.addLast(String.format(Locale.ROOT,
            "%03d +%dms %s current=%d settled=%d target=%d selected=%s scrolling=%s offset=%.3f %s",
            ++sequence, SystemClock.elapsedRealtime() - epoch, event, state.currentPage,
            state.settledPage, state.targetPage, selected, state.isScrollInProgress,
            state.currentPageOffsetFraction, detail))
        while (lines.size > 200) lines.removeFirst()
    }
    @Synchronized fun clear() { lines.clear(); sequence = 0; epoch = SystemClock.elapsedRealtime() }
    @Synchronized fun report() = "PAGER_DIAG temporary; last200; ms; tag IDs only; no gesture tuning\n" +
        "NATIVE_DOWN/UP = touch lifecycle; PAGER_DRAG_* = Pager recognized drag; EFFECT_* / *_BEGIN/END/CANCEL = programmatic paths.\n" +
        "FINAL_TOUCH describes actual displacement and whether consumed motion was seen before Pager drag. This is observation, not a new recognition rule.\n\n" +
        lines.sortedBy { it.substringBefore(' ').toInt() }.joinToString("\n")
}
