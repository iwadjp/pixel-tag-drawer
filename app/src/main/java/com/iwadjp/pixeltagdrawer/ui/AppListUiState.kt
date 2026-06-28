package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * 起動可能アプリ一覧画面のUI状態。
 * 後続の検索・タグDB導入に備え、画面状態を1つにまとめている。
 */
data class AppListUiState(
    val isLoading: Boolean = false,
    val apps: List<LauncherApp> = emptyList(),
    val errorMessage: String? = null,
)
