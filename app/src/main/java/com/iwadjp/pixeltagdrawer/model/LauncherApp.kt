package com.iwadjp.pixeltagdrawer.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 起動可能アプリ1件を表す。
 * アプリIDは packageName + className の組み合わせ (v0.1-spec.md 準拠)。
 *
 * icon はメモリ上の表示用ビットマップ。取得失敗時は null (プレースホルダー表示にフォールバック)。
 * v0.1では永続化・高度なキャッシュは行わない。
 */
data class LauncherApp(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: ImageBitmap? = null,
)
