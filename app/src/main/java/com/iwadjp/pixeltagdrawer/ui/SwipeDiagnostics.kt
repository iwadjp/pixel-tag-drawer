package com.iwadjp.pixeltagdrawer.ui

import java.util.Locale
import kotlin.math.abs

/** Temporary, memory-only diagnostic data: no app names, tags, files, or network traffic. */
internal data class SwipeGestureDiagnostic(
    val dxDp: Float,
    val dyDp: Float,
    val enabled: Boolean,
    val edge: Boolean,
    val multitouch: Boolean,
    val childConsumed: Boolean,
    val maxPointers: Int,
    val consumedAtDxDp: Float?,
    val consumedAtDyDp: Float?,
    val startWindowXDp: Float,
    val windowWidthDp: Float,
    val leftInsetDp: Float,
    val rightInsetDp: Float,
    val claimed: Boolean,
    val termination: String,
    val result: String,
    val verticalHandoff: Boolean,
) {
    fun line(): String {
        fun f(value: Float) = String.format(Locale.ROOT, "%.1f", value)
        val ratio = when {
            dyDp != 0f -> f(abs(dxDp) / abs(dyDp))
            dxDp != 0f -> "INF"
            else -> "0.0"
        }
        val direction = when { dxDp < 0 -> "LEFT"; dxDp > 0 -> "RIGHT"; else -> "NONE" }
        return "dir=$direction dxDp=${f(dxDp)} dyDp=${f(dyDp)} absDx=${f(abs(dxDp))} absDy=${f(abs(dyDp))}" +
            " ratio=$ratio distance72=${abs(dxDp) >= TAG_SWIPE_MIN_DISTANCE_DP}" +
            " ratio2=${abs(dxDp) >= abs(dyDp) * TAG_SWIPE_DOMINANCE_RATIO}" +
            " enabled=$enabled edge=$edge childConsumed=$childConsumed multi=$multitouch pointers=$maxPointers" +
            " consumedAtDp=${consumedAtDxDp?.let(::f) ?: "-"},${consumedAtDyDp?.let(::f) ?: "-"}" +
            " startWindowXDp=${f(startWindowXDp)} widthDp=${f(windowWidthDp)}" +
            " edgeInsetsDp=${f(leftInsetDp)},${f(rightInsetDp)} horizontalClaim=$claimed verticalHandoff=$verticalHandoff" +
            " end=$termination result=$result"
    }
}

/** One line per gesture, capped at 60; survives recomposition, not process death. */
internal object SwipeDiagnostics {
    private val lines = ArrayDeque<String>()
    private var sequence = 0

    @Synchronized fun record(gesture: SwipeGestureDiagnostic) {
        lines.addLast("#${++sequence} ${gesture.line()}")
        while (lines.size > 60) lines.removeFirst()
    }

    @Synchronized fun report(): String =
        "SWIPE_DIAG temporary; arbitration=Initial claim / Final observe; threshold=72dp; ratioMin=2; last60; dp units\n" +
            "Only gestures starting inside the app-list/empty area are observed.\n" +
            "ACCEPT means recognized; flags show all rejection conditions; end shows termination.\n" +
            lines.joinToString("\n").ifEmpty { "(no observed gestures yet)" }
}
