package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.model.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「おすすめ」ソート (sortApps + AppSortMode.Recommended) の並び順・同点処理の単体テスト。 */
class AppSortModeRecommendedTest {

    private val now = 1_000_000_000L
    private val hour = 60L * 60L * 1000L

    private fun app(
        label: String,
        packageName: String,
        className: String = "MainActivity",
        sessionCount: Int = 0,
        lastSessionAt: Long = 0L,
    ) = LauncherApp(
        label = label,
        packageName = packageName,
        className = className,
        recommendedSessionCount = sessionCount,
        recommendedLastSessionAt = lastSessionAt,
    )

    @Test
    fun `apps with usage history are placed before apps with no history`() {
        val withHistory = app("Zeta", "com.example.zeta", sessionCount = 1, lastSessionAt = now - hour)
        val noHistory = app("Alpha", "com.example.alpha")
        val sorted = sortApps(listOf(noHistory, withHistory), AppSortMode.Recommended, now)
        assertEquals(withHistory.packageName, sorted.first().packageName)
        assertEquals(noHistory.packageName, sorted.last().packageName)
    }

    @Test
    fun `apps with no history at all are ordered like Name sort among themselves`() {
        val b = app("Banana", "com.example.b")
        val a = app("Apple", "com.example.a")
        val c = app("Cherry", "com.example.c")
        val sorted = sortApps(listOf(b, c, a), AppSortMode.Recommended, now)
        assertEquals(listOf("Apple", "Banana", "Cherry"), sorted.map { it.label })
    }

    @Test
    fun `tie-break falls back to label then packageName then className deterministically`() {
        // 4件とも score・lastSessionAt が同一 (sessionCount=0) になるよう揃え、
        // タイブレーク規則 (名前昇順 → packageName昇順 → className昇順) だけで決着させる。
        val pkgBDefaultClass = app("Same", "com.example.b")
        val pkgADefaultClass = app("Same", "com.example.a")
        val pkgAClassZulu = app("Same", "com.example.a", className = "Zulu")
        val pkgAClassAlpha = app("Same", "com.example.a", className = "Alpha")

        val apps = listOf(pkgBDefaultClass, pkgADefaultClass, pkgAClassZulu, pkgAClassAlpha)
        val sorted = sortApps(apps, AppSortMode.Recommended, now)

        val expectedOrder = listOf(
            "com.example.a/Alpha",
            "com.example.a/MainActivity",
            "com.example.a/Zulu",
            "com.example.b/MainActivity",
        )
        val actualPkgClassInOrder = sorted.map { "${it.packageName}/${it.className}" }
        assertEquals(expectedOrder, actualPkgClassInOrder)
    }

    @Test
    fun `sorting the same input repeatedly yields identical deterministic order`() {
        val apps = listOf(
            app("Same", "com.example.b"),
            app("Same", "com.example.a"),
            app("Other", "com.example.z", sessionCount = 3, lastSessionAt = now - hour),
            app("Other", "com.example.y", sessionCount = 3, lastSessionAt = now - hour),
        )
        val first = sortApps(apps, AppSortMode.Recommended, now).map { it.packageName }
        val second = sortApps(apps.shuffled(java.util.Random(42)), AppSortMode.Recommended, now).map { it.packageName }
        val third = sortApps(apps.reversed(), AppSortMode.Recommended, now).map { it.packageName }
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `higher score sorts before lower score`() {
        val higher = app("Higher", "com.example.higher", sessionCount = 8, lastSessionAt = now)
        val lower = app("Lower", "com.example.lower", sessionCount = 1, lastSessionAt = now - 5 * 24 * hour)
        val sorted = sortApps(listOf(lower, higher), AppSortMode.Recommended, now)
        assertEquals(higher.packageName, sorted.first().packageName)
    }

    @Test
    fun `equal score ties break on more recent lastSessionAt first`() {
        // sessionCount=0 の2件はscoreが必ず厳密に0.0でタイになる (ageに関わらず)。
        // その状態でも lastSessionAt (人為的に異なる値を与えた) 降順が2番目のタイブレークとして
        // 効くことを、score計算式そのものとは独立に検証する。
        val newer = app("Same", "com.example.a", sessionCount = 0, lastSessionAt = now - hour)
        val older = app("Same", "com.example.a", sessionCount = 0, lastSessionAt = now - 2 * hour)
        assertEquals(
            recommendedScore(newer.recommendedSessionCount, now - newer.recommendedLastSessionAt),
            recommendedScore(older.recommendedSessionCount, now - older.recommendedLastSessionAt),
            0.0,
        )
        val sorted = sortApps(listOf(older, newer), AppSortMode.Recommended, now)
        assertEquals(newer.recommendedLastSessionAt, sorted.first().recommendedLastSessionAt)
    }

    @Test
    fun `score for an app does not change when the surrounding app set changes`() {
        val target = app("Target", "com.example.target", sessionCount = 2, lastSessionAt = now - hour)
        val heavyPeer = app("Heavy", "com.example.heavy", sessionCount = 500, lastSessionAt = now)

        val scoreAlone = recommendedScore(target.recommendedSessionCount, now - target.recommendedLastSessionAt)
        val sortedWithHeavyPeer = sortApps(listOf(target, heavyPeer), AppSortMode.Recommended, now)
        val targetAfterSort = sortedWithHeavyPeer.first { it.packageName == target.packageName }
        val scoreInPresenceOfPeer = recommendedScore(
            targetAfterSort.recommendedSessionCount,
            now - targetAfterSort.recommendedLastSessionAt,
        )
        assertEquals(scoreAlone, scoreInPresenceOfPeer, 0.0)
        assertTrue(scoreAlone > 0.0)
    }
}
