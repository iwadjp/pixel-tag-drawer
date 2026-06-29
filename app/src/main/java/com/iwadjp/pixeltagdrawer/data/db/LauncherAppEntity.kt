package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Entity

/**
 * 起動可能アプリの永続化用Entity (v0.1-spec.md の launcher_apps)。
 * 主キーは packageName + className。
 *
 * launchCount / lastLaunchedAt は pixel-tag-drawer 内でのアプリ起動履歴 (並び替え用)。
 * UsageStats や OS の利用履歴は使わず、本アプリからの起動だけを数える。
 * 既存行/未起動は 0。アプリ一覧同期 (label 等の更新) ではこの2値を上書きしない。
 */
@Entity(tableName = "launcher_apps", primaryKeys = ["packageName", "className"])
data class LauncherAppEntity(
    val packageName: String,
    val className: String,
    val label: String,
    val isInstalled: Boolean,
    val lastSeenAt: Long,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long = 0,
)
