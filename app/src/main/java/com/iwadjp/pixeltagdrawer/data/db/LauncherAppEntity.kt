package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Entity

/**
 * 起動可能アプリの永続化用Entity (v0.1-spec.md の launcher_apps)。
 * 主キーは packageName + className。
 * 今回はDBの土台のみで、まだ画面・取得処理へは接続しない。
 */
@Entity(tableName = "launcher_apps", primaryKeys = ["packageName", "className"])
data class LauncherAppEntity(
    val packageName: String,
    val className: String,
    val label: String,
    val isInstalled: Boolean,
    val lastSeenAt: Long,
)
