package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * App list sort mode.
 *
 * Recent / Count use package-level device usage history from UsageStatsManager.
 * Multiple launcher activities in the same package share the same usage values.
 */
enum class AppSortMode(val prefValue: String) {
    Name("name"),
    Recent("recent"),
    Count("count"),
    ;

    companion object {
        fun fromPrefValue(value: String?): AppSortMode =
            entries.firstOrNull { it.prefValue == value } ?: Name
    }
}

fun sortApps(apps: List<LauncherApp>, mode: AppSortMode): List<LauncherApp> {
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
    }
}
