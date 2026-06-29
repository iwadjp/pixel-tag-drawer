package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * アプリ一覧の並び順 (Phase 1-A)。
 * - Name: 名前順 (label 昇順)。既定。
 * - Recent: 最近起動順 (lastLaunchedAt 降順、未起動は後ろ、同値は名前順)。
 * - Count: 起動回数順 (launchCount 降順、未起動は後ろ、同値は lastLaunchedAt 降順→名前順)。
 *
 * 起動履歴は pixel-tag-drawer 内の起動だけを使う (UsageStats は使わない)。
 */
enum class AppSortMode(val prefValue: String) {
    Name("name"),
    Recent("recent"),
    Count("count"),
    ;

    companion object {
        /** 不正/未保存値は Name にフォールバックする。 */
        fun fromPrefValue(value: String?): AppSortMode =
            entries.firstOrNull { it.prefValue == value } ?: Name
    }
}

/**
 * 指定の並び順でアプリ一覧を並べ替える (元リストは変更しない)。
 * 検索・タグ絞り込み後の最終リストへ適用する想定。
 */
fun sortApps(apps: List<LauncherApp>, mode: AppSortMode): List<LauncherApp> {
    val byName = compareBy<LauncherApp> { it.label.lowercase() }
        .thenBy { it.packageName }
    return when (mode) {
        AppSortMode.Name -> apps.sortedWith(byName)
        AppSortMode.Recent -> apps.sortedWith(
            compareByDescending<LauncherApp> { it.lastLaunchedAt }.then(byName),
        )
        AppSortMode.Count -> apps.sortedWith(
            compareByDescending<LauncherApp> { it.launchCount }
                .thenByDescending { it.lastLaunchedAt }
                .then(byName),
        )
    }
}
