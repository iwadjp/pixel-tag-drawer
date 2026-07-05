package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * タグEntity (v0.1-spec.md の tags)。
 * name は重複不可 (unique index)。
 * displayLabel は横スクロールチップ用の短い表示名。null/blank なら name を表示する。
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
    val name: String,
    val sortOrder: Int,
    val displayLabel: String? = null,
)
