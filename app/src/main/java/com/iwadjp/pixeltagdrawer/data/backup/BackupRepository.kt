package com.iwadjp.pixeltagdrawer.data.backup

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.iwadjp.pixeltagdrawer.BuildConfig
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.data.db.AppTagCrossRef
import com.iwadjp.pixeltagdrawer.data.db.LauncherAppEntity
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import java.time.Instant

/** Export/Import (バックアップ) の結果。UI 側はこれだけを見てメッセージを出し分ける。 */
sealed interface BackupImportOutcome {
    object Success : BackupImportOutcome
    object InvalidBackup : BackupImportOutcome
    object UnsupportedFormat : BackupImportOutcome
    object RestoreFailure : BackupImportOutcome
}

/**
 * タグ・アプリ割り当て・関連preferencesのバックアップ (export/import)。
 * OS管理情報 (Usage Access grant / pinned shortcut そのもの) は対象外 (別途手動再設定が必要)。
 * scope/format/semanticsは release-preparation.md の "Export / Import Implementation" 節を参照。
 */
class BackupRepository(
    private val db: PixelTagDrawerDatabase,
    private val prefs: AppPreferences,
) {
    /** 通常の呼び出し元向け: Context からプロセス内シングルトンDB/prefsを解決する。 */
    constructor(context: Context) : this(PixelTagDrawerDatabase.getInstance(context), AppPreferences(context))

    /** 現在のDB内容とpreferencesをJSON文字列として書き出す (read-only、DB/prefsは変更しない)。 */
    suspend fun exportJson(): String {
        val tags = db.tagDao().getAllOnce()
        val apps = db.launcherAppDao().getAll()
        val appTags = db.appTagDao().getAllOnce()
        val payload = BackupPayload(
            formatVersion = BackupPayload.CURRENT_FORMAT_VERSION,
            exportedAt = Instant.now().toString(),
            appVersion = BuildConfig.VERSION_NAME,
            tags = tags.map {
                BackupTag(tagId = it.tagId, name = it.name, sortOrder = it.sortOrder, displayLabel = it.displayLabel)
            },
            launcherApps = apps.map {
                BackupLauncherApp(
                    packageName = it.packageName,
                    className = it.className,
                    label = it.label,
                    isInstalled = it.isInstalled,
                    lastSeenAt = it.lastSeenAt,
                    launchCount = it.launchCount,
                    lastLaunchedAt = it.lastLaunchedAt,
                )
            },
            appTags = appTags.map {
                BackupAppTag(packageName = it.packageName, className = it.className, tagId = it.tagId)
            },
            preferences = BackupPreferences(
                isGridMode = prefs.isGridMode,
                showTagManagement = prefs.showTagManagement,
                showUntaggedOnly = prefs.showUntaggedOnly,
                multiSelectFilter = prefs.multiSelectFilter,
                selectedFilterTagIds = prefs.loadFilterTagIds().toList(),
                appSortMode = prefs.appSortMode,
                untaggedDisplayLabel = prefs.untaggedDisplayLabel,
            ),
        )
        return BackupJson.encode(payload)
    }

    /**
     * JSON文字列からDB/preferencesを復元する (replace semantics: 既存データは置き換わる)。
     * validationに失敗した場合はDB/prefsを一切変更しない。
     * DB更新は1トランザクションにまとめ、途中失敗で半端な状態にならないようにする。
     */
    suspend fun importJson(text: String): BackupImportOutcome {
        return when (val decoded = BackupJson.decode(text)) {
            is BackupDecodeResult.Success -> restore(decoded.payload)
            BackupDecodeResult.InvalidJson,
            BackupDecodeResult.InvalidStructure,
            BackupDecodeResult.MalformedReferences,
            -> BackupImportOutcome.InvalidBackup
            BackupDecodeResult.UnsupportedVersion -> BackupImportOutcome.UnsupportedFormat
        }
    }

    private suspend fun restore(payload: BackupPayload): BackupImportOutcome {
        return try {
            db.withTransaction {
                db.appTagDao().deleteAll()
                db.tagDao().deleteAll()
                db.launcherAppDao().deleteAll()

                db.tagDao().insertAll(
                    payload.tags.map {
                        TagEntity(tagId = it.tagId, name = it.name, sortOrder = it.sortOrder, displayLabel = it.displayLabel)
                    },
                )
                db.launcherAppDao().upsertAll(
                    payload.launcherApps.map {
                        LauncherAppEntity(
                            packageName = it.packageName,
                            className = it.className,
                            label = it.label,
                            isInstalled = it.isInstalled,
                            lastSeenAt = it.lastSeenAt,
                            launchCount = it.launchCount,
                            lastLaunchedAt = it.lastLaunchedAt,
                        )
                    },
                )
                db.appTagDao().insertAll(
                    payload.appTags.map { AppTagCrossRef(it.packageName, it.className, it.tagId) },
                )
            }
            // DB更新が成功した後だけ preferences を反映する (DBがtransactionで確定してから)。
            prefs.isGridMode = payload.preferences.isGridMode
            prefs.showTagManagement = payload.preferences.showTagManagement
            prefs.showUntaggedOnly = payload.preferences.showUntaggedOnly
            prefs.multiSelectFilter = payload.preferences.multiSelectFilter
            prefs.saveFilterTagIds(payload.preferences.selectedFilterTagIds.toSet())
            prefs.appSortMode = payload.preferences.appSortMode
            prefs.untaggedDisplayLabel = payload.preferences.untaggedDisplayLabel
            BackupImportOutcome.Success
        } catch (e: Exception) {
            Log.w(TAG, "バックアップの復元に失敗しました", e)
            BackupImportOutcome.RestoreFailure
        }
    }

    private companion object {
        const val TAG = "BackupRepository"
    }
}
