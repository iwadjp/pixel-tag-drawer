package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * アプリ - タグの多対多を表す中間テーブル (v0.1-spec.md の app_tags)。
 * 主キーは packageName + className + tagId。
 * tagId に索引を張り、タグ別の絞り込みを効率化する。
 */
@Entity(
    tableName = "app_tags",
    primaryKeys = ["packageName", "className", "tagId"],
    indices = [Index(value = ["tagId"])],
)
data class AppTagCrossRef(
    val packageName: String,
    val className: String,
    val tagId: Long,
)
