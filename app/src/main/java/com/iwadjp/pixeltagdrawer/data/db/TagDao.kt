package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * tags の最小DAO。土台のみで、まだ画面へは接続しない。
 */
@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Delete
    suspend fun delete(tag: TagEntity)

    /**
     * タグ名を更新する。name は unique のため、重複時は OR IGNORE で何もしない。
     * @return 更新された行数 (重複で無視された場合は 0)。
     */
    @Query("UPDATE OR IGNORE tags SET name = :name WHERE tagId = :tagId")
    suspend fun updateTagName(tagId: Long, name: String): Int

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>
}
