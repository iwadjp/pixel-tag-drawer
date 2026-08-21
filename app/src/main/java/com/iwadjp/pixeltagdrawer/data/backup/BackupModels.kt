package com.iwadjp.pixeltagdrawer.data.backup

/**
 * バックアップ (export/import) の versioned data model。
 * v0.1-spec.md の tags / launcher_apps / app_tags と AppPreferences の7keyに対応する。
 * OS管理情報 (Usage Access grant / pinned shortcut そのもの) はここに含めない。
 */
data class BackupPayload(
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val tags: List<BackupTag>,
    val launcherApps: List<BackupLauncherApp>,
    val appTags: List<BackupAppTag>,
    val preferences: BackupPreferences,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

data class BackupTag(
    val tagId: Long,
    val name: String,
    val sortOrder: Int,
    val displayLabel: String?,
)

data class BackupLauncherApp(
    val packageName: String,
    val className: String,
    val label: String,
    val isInstalled: Boolean,
    val lastSeenAt: Long,
    val launchCount: Int,
    val lastLaunchedAt: Long,
)

data class BackupAppTag(
    val packageName: String,
    val className: String,
    val tagId: Long,
)

data class BackupPreferences(
    val isGridMode: Boolean,
    val showTagManagement: Boolean,
    val showUntaggedOnly: Boolean,
    val multiSelectFilter: Boolean,
    val selectedFilterTagIds: List<Long>,
    val appSortMode: String,
    val untaggedDisplayLabel: String?,
)

/** decode 結果。invalid入力でも例外を投げず、呼び出し側が分岐できるようにする。 */
sealed interface BackupDecodeResult {
    data class Success(val payload: BackupPayload) : BackupDecodeResult
    object InvalidJson : BackupDecodeResult
    object InvalidStructure : BackupDecodeResult
    object UnsupportedVersion : BackupDecodeResult
    object MalformedReferences : BackupDecodeResult
}
