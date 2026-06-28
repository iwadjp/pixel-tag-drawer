package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.data.db.TagEntity

/**
 * タグ作成・一覧のUI状態 (最小)。
 * 今回はタグ作成と一覧表示のみ。割り当て・絞り込みは後続。
 */
data class TagUiState(
    val tags: List<TagEntity> = emptyList(),
    val tagName: String = "",
    val message: String? = null,
)
