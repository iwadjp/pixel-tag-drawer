package com.iwadjp.pixeltagdrawer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * launcher_apps の最小DAO。
 */
@Dao
interface LauncherAppDao {

    @Upsert
    suspend fun upsertAll(apps: List<LauncherAppEntity>)

    /**
     * アプリ一覧同期: 1件の label/isInstalled/lastSeenAt を更新 (無ければ挿入)。
     * launchCount / lastLaunchedAt は **上書きしない** (起動履歴を同期で消さないため)。
     */
    @Query(
        """
        INSERT INTO launcher_apps (packageName, className, label, isInstalled, lastSeenAt, launchCount, lastLaunchedAt)
        VALUES (:packageName, :className, :label, 1, :now, 0, 0)
        ON CONFLICT(packageName, className) DO UPDATE SET
            label = :label,
            isInstalled = 1,
            lastSeenAt = :now
        """,
    )
    suspend fun upsertSeen(packageName: String, className: String, label: String, now: Long)

    /** 一覧同期をまとめて1トランザクションで行う (起動履歴は保持)。 */
    @Transaction
    suspend fun upsertAllSeen(apps: List<LauncherAppEntity>, now: Long) {
        apps.forEach { upsertSeen(it.packageName, it.className, it.label, now) }
    }

    /**
     * アプリ起動を記録する: launchCount を +1、lastLaunchedAt を now に更新 (無ければ挿入)。
     * label は行が無い異常時のみ空で挿入する (通常は一覧同期済みで既存行が更新される)。
     */
    @Query(
        """
        INSERT INTO launcher_apps (packageName, className, label, isInstalled, lastSeenAt, launchCount, lastLaunchedAt)
        VALUES (:packageName, :className, '', 1, :now, 1, :now)
        ON CONFLICT(packageName, className) DO UPDATE SET
            launchCount = launchCount + 1,
            lastLaunchedAt = :now
        """,
    )
    suspend fun recordLaunch(packageName: String, className: String, now: Long)

    @Query("SELECT * FROM launcher_apps ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LauncherAppEntity>>

    @Query("SELECT * FROM launcher_apps")
    suspend fun getAll(): List<LauncherAppEntity>

    /** バックアップ復元用: 全行削除。呼び出し側でトランザクションに包むこと。 */
    @Query("DELETE FROM launcher_apps")
    suspend fun deleteAll()
}
