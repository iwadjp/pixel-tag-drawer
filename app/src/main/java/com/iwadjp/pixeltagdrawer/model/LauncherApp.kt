package com.iwadjp.pixeltagdrawer.model

/**
 * 起動可能アプリ1件を表す。
 * アプリIDは packageName + className の組み合わせ (v0.1-spec.md 準拠)。
 */
data class LauncherApp(
    val label: String,
    val packageName: String,
    val className: String,
)
