package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * app_tags (アプリ-タグの多対多) の最小DAO。土台のみで、まだ画面へは接続しない。
 */
@Dao
interface AppTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: AppTagCrossRef)

    @Delete
    suspend fun delete(crossRef: AppTagCrossRef)

    @Query("SELECT * FROM app_tags")
    fun observeAll(): Flow<List<AppTagCrossRef>>

    /** バックアップ書き出し用の一括取得 (Flow ではなく1回だけ取得)。 */
    @Query("SELECT * FROM app_tags")
    suspend fun getAllOnce(): List<AppTagCrossRef>

    /** バックアップ復元用: 全行削除。呼び出し側でトランザクションに包むこと。 */
    @Query("DELETE FROM app_tags")
    suspend fun deleteAll()

    /** バックアップ復元用: 一括挿入。復元前に deleteAll 済みである前提。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<AppTagCrossRef>)

    /** タグ削除時: 指定tagIdを参照する app_tags を先に削除する (孤児行を残さないため)。 */
    @Query("DELETE FROM app_tags WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)

    @Query("SELECT * FROM app_tags WHERE tagId = :tagId")
    fun observeByTag(tagId: Long): Flow<List<AppTagCrossRef>>

    @Query("SELECT tagId FROM app_tags WHERE packageName = :packageName AND className = :className")
    fun observeTagIdsForApp(packageName: String, className: String): Flow<List<Long>>
}
