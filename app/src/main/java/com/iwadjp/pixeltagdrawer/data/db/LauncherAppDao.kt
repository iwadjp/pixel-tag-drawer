package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * launcher_apps の最小DAO。土台のみで、まだ画面へは接続しない。
 */
@Dao
interface LauncherAppDao {

    @Upsert
    suspend fun upsertAll(apps: List<LauncherAppEntity>)

    @Query("SELECT * FROM launcher_apps ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LauncherAppEntity>>

    @Query("SELECT * FROM launcher_apps")
    suspend fun getAll(): List<LauncherAppEntity>
}
