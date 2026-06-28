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

    @Query("SELECT * FROM app_tags WHERE tagId = :tagId")
    fun observeByTag(tagId: Long): Flow<List<AppTagCrossRef>>

    @Query("SELECT tagId FROM app_tags WHERE packageName = :packageName AND className = :className")
    fun observeTagIdsForApp(packageName: String, className: String): Flow<List<Long>>
}
