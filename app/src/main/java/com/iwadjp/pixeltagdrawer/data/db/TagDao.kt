package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /** 新規タグを末尾に追加するための次の sortOrder (空なら 0)。 */
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM tags")
    suspend fun nextSortOrder(): Int

    /** 横スクロール用の表示名を更新する。null で未設定 (=name 表示) に戻す。 */
    @Query("UPDATE tags SET displayLabel = :displayLabel WHERE tagId = :tagId")
    suspend fun updateDisplayLabel(tagId: Long, displayLabel: String?): Int

    /** 指定タグの sortOrder を更新する (▲▼ 並び替え用)。 */
    @Query("UPDATE tags SET sortOrder = :sortOrder WHERE tagId = :tagId")
    suspend fun updateSortOrder(tagId: Long, sortOrder: Int): Int

    /** 隣接2タグの sortOrder を交換する (▲▼ 並び替え用)。途中失敗で片側だけ変わらないよう transaction にする。 */
    @Transaction
    suspend fun swapSortOrder(tagIdA: Long, sortOrderA: Int, tagIdB: Long, sortOrderB: Int) {
        updateSortOrder(tagIdA, sortOrderB)
        updateSortOrder(tagIdB, sortOrderA)
    }
}
