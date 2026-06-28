package com.iwadjp.pixeltagdrawer.ui

import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * タグ作成・一覧と、選択中アプリへのタグ割り当てのUI状態 (最小)。
 * 絞り込みは後続。
 */
data class TagUiState(
    val tags: List<TagEntity> = emptyList(),
    val tagName: String = "",
    val message: String? = null,
    // タグ割り当て対象として選択中のアプリ (未選択時は null)
    val selectedApp: LauncherApp? = null,
    // 選択中アプリに付与済みの tagId 集合
    val selectedAppTagIds: Set<Long> = emptySet(),
    // 一覧絞り込みに選択中の tagId 集合 (空ならタグ条件で絞らない)
    val selectedFilterTagIds: Set<Long> = emptySet(),
    // 各アプリ ("packageName/className") に付与済みの tagId 集合。一覧絞り込み用。
    val appTagMap: Map<String, Set<Long>> = emptyMap(),
)
