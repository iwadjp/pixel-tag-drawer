package com.iwadjp.pixeltagdrawer.data

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UsageEvents からのセッション生成ロジックの単体テスト。
 * Robolectricは使わず、classifySessionEventType が UsageEvents.Event の型定数のみを
 * 参照する (インスタンスを扱わない) ことを利用して、素のJVM上でテストする。
 */
class UsageSessionTest {

    private val pkg = "com.example.app"
    private val classA = "com.example.app.MainActivity"
    private val classB = "com.example.app.DetailActivity"

    private fun start(cls: String, atMs: Long) =
        RawSessionEvent(pkg, cls, atMs, RawSessionEvent.Kind.START)

    private fun end(cls: String, atMs: Long) =
        RawSessionEvent(pkg, cls, atMs, RawSessionEvent.Kind.END)

    // --- セッション生成 ---

    @Test
    fun `normal start and end become one session`() {
        val sessions = buildUsageSessions(
            listOf(start(classA, 0L), end(classA, 3_000L)),
            windowEndMs = 10_000L,
        )
        assertEquals(1, sessions.size)
        assertEquals(UsageSession(pkg, 0L, 3_000L), sessions.first())
    }

    @Test
    fun `session under 2 seconds is excluded`() {
        val accepted = buildAcceptedSessions(
            events = listOf(start(classA, 0L), end(classA, 1_999L)),
            windowEndMs = 10_000L,
            mergeGapMs = 30_000L,
            minSessionMs = 2_000L,
        )
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `session of exactly 2 seconds is accepted`() {
        val accepted = buildAcceptedSessions(
            events = listOf(start(classA, 0L), end(classA, 2_000L)),
            windowEndMs = 10_000L,
            mergeGapMs = 30_000L,
            minSessionMs = 2_000L,
        )
        assertEquals(1, accepted.size)
        assertEquals(2_000L, accepted.first().durationMs)
    }

    @Test
    fun `activity transition within same package does not overlap-split (zero gap)`() {
        // Detail が Main の PAUSED より前に RESUMED する典型的な遷移順序では、
        // 前景Activity集合が一度も空にならないため、素のbuildUsageSessionsだけで1セッションになる。
        val events = listOf(
            start(classA, 0L),
            start(classB, 5_000L),
            end(classA, 5_010L),
            end(classB, 9_000L),
        )
        val sessions = buildUsageSessions(events, windowEndMs = 20_000L)
        assertEquals(1, sessions.size)
        assertEquals(UsageSession(pkg, 0L, 9_000L), sessions.first())
    }

    @Test
    fun `activity transition with brief real gap still counts once after full pipeline`() {
        // Main -> Detail -> (10ms の空白) -> Main という、PAUSEDがRESUMEDより先に来る順序。
        // buildUsageSessionsだけでは2候補に割れるが、近接統合(30秒以内)後は1回の起動として扱われる。
        val events = listOf(
            start(classA, 0L),
            start(classB, 5_000L),
            end(classA, 5_010L),
            end(classB, 9_000L),
            start(classA, 9_010L),
            end(classA, 15_000L),
        )
        val raw = buildUsageSessions(events, windowEndMs = 20_000L)
        assertEquals(2, raw.size) // 中間状態: まだ統合前

        val accepted = buildAcceptedSessions(
            events = events,
            windowEndMs = 20_000L,
            mergeGapMs = 30_000L,
            minSessionMs = 2_000L,
        )
        assertEquals(1, accepted.size)
        assertEquals(UsageSession(pkg, 0L, 15_000L), accepted.first())
    }

    @Test
    fun `resume within 30 seconds gap is merged (boundary inclusive)`() {
        val sessions = listOf(
            UsageSession(pkg, 0L, 3_000L),
            UsageSession(pkg, 33_000L, 36_000L), // gap = 30_000ms ちょうど
        )
        val merged = mergeCloseSessions(sessions, mergeGapMs = 30_000L)
        assertEquals(1, merged.size)
        assertEquals(UsageSession(pkg, 0L, 36_000L), merged.first())
    }

    @Test
    fun `resume beyond 30 seconds gap stays as separate sessions`() {
        val sessions = listOf(
            UsageSession(pkg, 0L, 3_000L),
            UsageSession(pkg, 33_001L, 36_000L), // gap = 30_001ms (境界超え)
        )
        val merged = mergeCloseSessions(sessions, mergeGapMs = 30_000L)
        assertEquals(2, merged.size)
    }

    @Test
    fun `only pause or stop events do not crash and produce no session`() {
        val sessions = buildUsageSessions(
            listOf(end(classA, 1_000L), end(classA, 2_000L)),
            windowEndMs = 10_000L,
        )
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `duplicate resumed events do not cause negative or inflated state`() {
        val sessions = buildUsageSessions(
            listOf(start(classA, 0L), start(classA, 100L), end(classA, 5_000L)),
            windowEndMs = 10_000L,
        )
        assertEquals(1, sessions.size)
        assertEquals(UsageSession(pkg, 0L, 5_000L), sessions.first())
    }

    @Test
    fun `activity still foreground at window end is safely closed at window end`() {
        val accepted = buildAcceptedSessions(
            events = listOf(start(classA, 90_000L)),
            windowEndMs = 100_000L,
            mergeGapMs = 30_000L,
            minSessionMs = 2_000L,
        )
        assertEquals(1, accepted.size)
        assertEquals(UsageSession(pkg, 90_000L, 100_000L), accepted.first())
    }

    // --- イベント分類・二重計上防止 ---
    // 実SDKの定数値を確認したところ、ACTIVITY_RESUMED は MOVE_TO_FOREGROUND と、
    // ACTIVITY_PAUSED は MOVE_TO_BACKGROUND と同一の整数値のエイリアスであり、別イベントとして
    // 二重に届くことはない (以下のテストで実際の定数値を用いて検証する)。
    // 実際にAPIレベルで挙動が変わるのは ACTIVITY_STOPPED (API29+限定の追加終了シグナル) のみ。

    @Test
    fun `ACTIVITY_RESUMED and legacy MOVE_TO_FOREGROUND share the same underlying value`() {
        @Suppress("DEPRECATION")
        assertEquals(UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.ACTIVITY_RESUMED)
    }

    @Test
    fun `ACTIVITY_PAUSED and legacy MOVE_TO_BACKGROUND share the same underlying value`() {
        @Suppress("DEPRECATION")
        assertEquals(UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_PAUSED)
    }

    @Test
    fun `ACTIVITY_RESUMED classifies to START regardless of includeActivityStopped`() {
        assertEquals(
            RawSessionEvent.Kind.START,
            classifySessionEventType(UsageEvents.Event.ACTIVITY_RESUMED, includeActivityStopped = true),
        )
        assertEquals(
            RawSessionEvent.Kind.START,
            classifySessionEventType(UsageEvents.Event.ACTIVITY_RESUMED, includeActivityStopped = false),
        )
    }

    @Test
    fun `ACTIVITY_PAUSED classifies to END regardless of includeActivityStopped`() {
        assertEquals(
            RawSessionEvent.Kind.END,
            classifySessionEventType(UsageEvents.Event.ACTIVITY_PAUSED, includeActivityStopped = true),
        )
        assertEquals(
            RawSessionEvent.Kind.END,
            classifySessionEventType(UsageEvents.Event.ACTIVITY_PAUSED, includeActivityStopped = false),
        )
    }

    @Test
    fun `ACTIVITY_STOPPED only classifies to END when includeActivityStopped is true`() {
        assertEquals(
            RawSessionEvent.Kind.END,
            classifySessionEventType(UsageEvents.Event.ACTIVITY_STOPPED, includeActivityStopped = true),
        )
        assertEquals(
            null,
            classifySessionEventType(UsageEvents.Event.ACTIVITY_STOPPED, includeActivityStopped = false),
        )
    }

    @Test
    fun `unrelated event types classify to null`() {
        assertEquals(
            null,
            classifySessionEventType(UsageEvents.Event.CONFIGURATION_CHANGE, includeActivityStopped = true),
        )
    }

    @Test
    fun `ACTIVITY_PAUSED followed by ACTIVITY_STOPPED for the same close is not double counted`() {
        // 実機ではPAUSEDの後にSTOPPEDが重ねて届くことがある (onPauseの後のonStop)。
        // 両方をENDとして扱っても、buildUsageSessionsのclassName除去がidempotentなため
        // セッションが1つ余分に切れたり、件数が二重になったりしない。
        val events = listOf(
            RawSessionEvent(pkg, classA, 0L, RawSessionEvent.Kind.START),
            RawSessionEvent(pkg, classA, 5_000L, RawSessionEvent.Kind.END), // PAUSED相当
            RawSessionEvent(pkg, classA, 5_050L, RawSessionEvent.Kind.END), // STOPPED相当 (重複)
        )
        val sessions = buildUsageSessions(events, windowEndMs = 10_000L)
        assertEquals(1, sessions.size)
        assertEquals(UsageSession(pkg, 0L, 5_000L), sessions.first())
    }
}
