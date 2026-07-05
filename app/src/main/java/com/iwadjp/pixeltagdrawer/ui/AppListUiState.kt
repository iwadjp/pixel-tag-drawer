package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * 起動可能アプリ一覧画面のUI状態。
 * 後続のタグ絞り込みUI導入に備え、画面状態を1つにまとめている。
 *
 * apps は全件リストを保持し、query に応じて filteredApps で絞り込む。
 */
data class AppListUiState(
    val isLoading: Boolean = false,
    val apps: List<LauncherApp> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
    val sortMode: AppSortMode = AppSortMode.Name,
    val usageStatsAccessGranted: Boolean = false,
    // Recent/Count 起動時の「並び確定待ち」。true の間、一覧の初回描画を保留して
    // usage stats 反映前の実質名前順 (label-only) 表示 → recent/count 順への
    // 二段階切り替わりを見せない。usage merge 完了・Name への切替・タイムアウトで解除。
    val initialSortSettling: Boolean = false,
) {
    /** app label または packageName に query を含むアプリ (大文字小文字を区別しない)。 */
    val filteredApps: List<LauncherApp>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return apps
            return apps.filter { app ->
                app.label.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true)
            }
        }
}
