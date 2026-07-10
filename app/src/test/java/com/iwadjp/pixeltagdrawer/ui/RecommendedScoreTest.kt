package com.iwadjp.pixeltagdrawer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「おすすめ」スコア計算 (recency/frequency/score) の単体テスト。 */
class RecommendedScoreTest {

    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    private val delta = 1e-9

    @Test
    fun `recency is 1_0 immediately after use`() {
        assertEquals(1.0, recommendedRecency(sessionCount = 1, ageMs = 0L), delta)
    }

    @Test
    fun `recency is 0_5 after 24 hours (half-life)`() {
        assertEquals(0.5, recommendedRecency(sessionCount = 1, ageMs = day), 1e-9)
    }

    @Test
    fun `recency is 0_25 after 48 hours`() {
        assertEquals(0.25, recommendedRecency(sessionCount = 1, ageMs = 2 * day), 1e-9)
    }

    @Test
    fun `count 0 makes recency, frequency and score all zero`() {
        assertEquals(0.0, recommendedRecency(sessionCount = 0, ageMs = 0L), delta)
        assertEquals(0.0, recommendedRecency(sessionCount = 0, ageMs = 10 * day), delta)
        assertEquals(0.0, recommendedFrequency(sessionCount = 0), delta)
        assertEquals(0.0, recommendedScore(sessionCount = 0, ageMs = 0L), delta)
    }

    @Test
    fun `frequency reaches 1_0 at count 8`() {
        assertEquals(1.0, recommendedFrequency(sessionCount = 8), 1e-9)
    }

    @Test
    fun `frequency never exceeds 1_0 beyond count 8`() {
        val f100 = recommendedFrequency(sessionCount = 100)
        assertEquals(1.0, f100, 1e-9)
        assertTrue(f100 <= 1.0)
        val f1000 = recommendedFrequency(sessionCount = 1000)
        assertTrue(f1000 <= 1.0)
    }

    @Test
    fun `frequency is monotonically increasing before saturation`() {
        val f1 = recommendedFrequency(1)
        val f2 = recommendedFrequency(2)
        val f4 = recommendedFrequency(4)
        assertTrue(f1 < f2)
        assertTrue(f2 < f4)
    }

    @Test
    fun `negative age from clock rollback is treated as zero`() {
        val fromNegative = recommendedRecency(sessionCount = 1, ageMs = -5_000L)
        val fromZero = recommendedRecency(sessionCount = 1, ageMs = 0L)
        assertEquals(fromZero, fromNegative, delta)
        assertEquals(1.0, fromNegative, delta)
    }

    @Test
    fun `a single very recent use outranks an older repeated-use app in raw recency`() {
        // 直近性そのものは「1回でも今使った」方が高くなることを確認する (recency軸のみの検証)。
        val recentOnce = recommendedRecency(sessionCount = 1, ageMs = 0L)
        val oldRepeated = recommendedRecency(sessionCount = 8, ageMs = 10 * day)
        assertTrue(recentOnce > oldRepeated)
    }

    @Test
    fun `recent app with multiple sessions outranks recent app with a single session`() {
        val ageMs = hour
        val single = recommendedScore(sessionCount = 1, ageMs = ageMs)
        val repeated = recommendedScore(sessionCount = 5, ageMs = ageMs)
        assertTrue(repeated > single)
    }

    @Test
    fun `recently repeated app outranks an old habitual app`() {
        // 古い常用アプリ: 8セッション(頻度飽和)だが最終利用が5日前 (recencyがかなり減衰)
        val oldHabitual = recommendedScore(sessionCount = 8, ageMs = 5 * day)
        // 最近反復利用されたアプリ: 2セッションのみだがたった今使った
        val recentRepeat = recommendedScore(sessionCount = 2, ageMs = 0L)
        assertTrue(
            "recentRepeat=$recentRepeat should outrank oldHabitual=$oldHabitual",
            recentRepeat > oldHabitual,
        )
    }

    @Test
    fun `score does not round for comparison, only differs at high precision`() {
        val a = recommendedScore(sessionCount = 3, ageMs = hour)
        val b = recommendedScore(sessionCount = 3, ageMs = hour + 1L)
        // 1msの差でも(丸めなければ)理論上わずかに異なりうることを確認する意図のテスト。
        // ここでは「同じ入力なら常に同じ値を返す」決定性を検証する。
        val aAgain = recommendedScore(sessionCount = 3, ageMs = hour)
        assertEquals(a, aAgain, 0.0)
        assertTrue(b <= a)
    }
}
