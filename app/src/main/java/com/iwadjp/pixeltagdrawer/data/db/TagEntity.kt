package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * タグEntity (v0.1-spec.md の tags)。
 * name は重複不可 (unique index)。
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
    val name: String,
    val sortOrder: Int,
)
