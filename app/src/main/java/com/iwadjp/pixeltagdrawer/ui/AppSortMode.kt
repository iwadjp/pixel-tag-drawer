package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * App list sort mode.
 *
 * Recent / Count use package-level device usage history from UsageStatsManager.
 * Multiple launcher activities in the same package share the same usage values.
 *
 * Recommended (「おすすめ」) uses a separate, shorter-window session summary
 * (recommendedSessionCount / recommendedLastSessionAt) built from UsageEvents,
 * combining recency and short-term repeat frequency. See RecommendedScore.kt.
 */
enum class AppSortMode(val prefValue: String) {
    Name("name"),
    Recent("recent"),
    Count("count"),
    Recommended("recommended"),
    ;

    companion object {
        fun fromPrefValue(value: String?): AppSortMode =
            entries.firstOrNull { it.prefValue == value } ?: Name
    }
}

/**
 * @param now Recommended のスコア計算 (経過時間) にのみ使う基準時刻。テスト用に差し替え可能。
 *   他モードでは参照しない。
 */
fun sortApps(apps: List<LauncherApp>, mode: AppSortMode, now: Long = System.currentTimeMillis()): List<LauncherApp> {
    val byName = compareBy<LauncherApp> { it.label.lowercase() }
        .thenBy { it.packageName }
    return when (mode) {
        AppSortMode.Name -> apps.sortedWith(byName)
        AppSortMode.Recent -> apps.sortedWith(
            compareByDescending<LauncherApp> { it.usageLastUsedAt }.then(byName),
        )
        AppSortMode.Count -> apps.sortedWith(
            compareByDescending<LauncherApp> { it.usageLaunchCount }
                .thenByDescending { it.usageLastUsedAt }
                .then(byName),
        )
        AppSortMode.Recommended -> apps.sortedWith(
            compareByDescending<LauncherApp> {
                recommendedScore(it.recommendedSessionCount, now - it.recommendedLastSessionAt)
            }
                .thenByDescending { it.recommendedLastSessionAt }
                .then(byName)
                .thenBy { it.className },
        )
    }
}
