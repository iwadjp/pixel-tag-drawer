package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iwadjp.pixeltagdrawer.PerfLog
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.data.AppRepository
import com.iwadjp.pixeltagdrawer.model.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 起動可能アプリ一覧の状態を保持し、起動操作を仲介するViewModel。
 * v0.1では読み取りと起動のみ。タグ機能・検索は後続で追加する。
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val prefs = AppPreferences(application)

    private val _uiState = MutableStateFlow(
        AppListUiState(isLoading = true, sortMode = AppSortMode.fromPrefValue(prefs.appSortMode)),
    )
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    // refresh() の世代。icon 後追いロードが古い世代の結果を反映しないためのガード。
    @Volatile
    private var loadGeneration = 0

    init {
        PerfLog.log("AppListViewModel init")
        refresh()
    }

    fun refresh() {
        val generation = ++loadGeneration
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                // まず icon なし (label のみ) で一覧を早く表示する。
                val list = withContext(Dispatchers.IO) { repository.loadLaunchableApps() }
                _uiState.update { it.copy(isLoading = false, apps = list, errorMessage = null) }
                PerfLog.log("apps loaded into uiState count=${list.size}")

                // DB同期は表示と独立。icon の有無で動作を変えない。失敗しても一覧表示は壊さない。
                try {
                    PerfLog.log("db sync start")
                    withContext(Dispatchers.IO) { repository.syncLaunchableApps(list) }
                    PerfLog.log("db sync end")
                } catch (e: Exception) {
                    Log.w(TAG, "launcher_apps への同期に失敗しました", e)
                }

                // 起動履歴を後追いマージする (並び替え用)。初期表示は名前順なら統計不要なので速いまま。
                // 失敗しても一覧表示は壊さない。古い世代の結果は反映しない。
                try {
                    val stats = withContext(Dispatchers.IO) { repository.loadLaunchStats() }
                    PerfLog.log(
                        "[SORT] launch stats loaded rows=${stats.size} " +
                            "nonZero=${stats.count { it.value.first > 0 || it.value.second > 0L }}",
                    )
                    if (generation == loadGeneration) {
                        _uiState.update { state ->
                            val mergedApps = state.apps.map { app ->
                                val s = stats[app.appId()]
                                    if (s != null && (app.launchCount != s.first || app.lastLaunchedAt != s.second)) {
                                        app.copy(launchCount = s.first, lastLaunchedAt = s.second)
                                    } else {
                                        app
                                    }
                            }
                            PerfLog.log(
                                "[SORT] launch stats merged apps=${mergedApps.size} " +
                                    "nonZero=${mergedApps.count { it.hasLaunchStats() }}",
                            )
                            state.copy(apps = mergedApps)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "起動履歴の読み込みに失敗しました", e)
                }

                // icon は初期表示後に後追いロードして該当アプリへ反映する。
                mergeUsageStats(generation)
                loadIcons(list, generation)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "アプリ一覧の読み込みに失敗しました")
                }
            }
        }
    }

    fun refreshUsageStats() {
        val generation = loadGeneration
        viewModelScope.launch {
            mergeUsageStats(generation)
        }
    }

    private suspend fun mergeUsageStats(generation: Int) {
        val granted = withContext(Dispatchers.IO) { repository.hasUsageStatsAccess() }
        PerfLog.log("[USAGE] usage access granted=$granted")
        if (generation != loadGeneration) return

        if (!granted) {
            var fallbackSortToName = false
            _uiState.update { state ->
                val nextSortMode = if (state.sortMode != AppSortMode.Name) {
                    fallbackSortToName = true
                    AppSortMode.Name
                } else {
                    state.sortMode
                }
                state.copy(
                    usageStatsAccessGranted = false,
                    sortMode = nextSortMode,
                    apps = state.apps.map {
                        if (it.usageLaunchCount != 0 || it.usageLastUsedAt != 0L) {
                            it.copy(usageLaunchCount = 0, usageLastUsedAt = 0L)
                        } else {
                            it
                        }
                    },
                )
            }
            if (fallbackSortToName) {
                prefs.appSortMode = AppSortMode.Name.prefValue
                PerfLog.log("[USAGE] sort mode fallback mode=name reason=usage_access_missing")
            }
            return
        }

        val usageStats = withContext(Dispatchers.IO) { repository.loadUsageStats() }
        if (generation != loadGeneration) return
        _uiState.update { state ->
            val mergedApps = state.apps.map { app ->
                val usage = usageStats[app.packageName]
                app.copy(
                    usageLaunchCount = usage?.launchCount ?: 0,
                    usageLastUsedAt = usage?.lastUsedAt ?: 0L,
                )
            }
            PerfLog.log(
                "[USAGE] usage stats loaded packages=${usageStats.size} " +
                    "nonZeroRecent=${mergedApps.count { it.usageLastUsedAt > 0L }} " +
                    "nonZeroCount=${mergedApps.count { it.usageLaunchCount > 0 }}",
            )
            state.copy(usageStatsAccessGranted = true, apps = mergedApps)
        }
    }

    /**
     * icon を非同期に後追いロードし、読み込めたアプリへ反映する。
     * - 順序は維持 (list を map して該当アプリの icon だけ差し替える)。
     * - refresh() が再実行されたら generation 不一致で中断し、古い結果を反映しない。
     * - 再描画を抑えるため、数件たまったらまとめて反映する。
     */
    private fun loadIcons(apps: List<LauncherApp>, generation: Int) {
        viewModelScope.launch {
            PerfLog.log("icon lazy load start count=${apps.size}")
            val pending = HashMap<String, ImageBitmap>()
            var loaded = 0
            var failed = 0
            var firstLogged = false

            fun flush() {
                if (pending.isEmpty()) return
                if (generation != loadGeneration) {
                    pending.clear()
                    return
                }
                val snapshot = HashMap(pending)
                pending.clear()
                _uiState.update { state ->
                    val mergedApps = state.apps.map { app ->
                        val icon = snapshot[app.appId()]
                        if (icon != null && app.icon == null) app.copy(icon = icon) else app
                    }
                    PerfLog.log(
                        "[SORT] icon batch applied preserves stats " +
                            "nonZero=${mergedApps.count { it.hasLaunchStats() }}",
                    )
                    state.copy(apps = mergedApps)
                }
            }

            for (app in apps) {
                if (generation != loadGeneration) return@launch
                val icon = withContext(Dispatchers.IO) {
                    repository.loadIcon(app.packageName, app.className)
                }
                if (icon != null) {
                    loaded++
                    if (!firstLogged) {
                        firstLogged = true
                        PerfLog.log("icon lazy load first icon")
                    }
                    pending[app.appId()] = icon
                    if (pending.size >= ICON_FLUSH_BATCH) flush()
                } else {
                    failed++
                }
            }
            flush()
            PerfLog.log("icon lazy load end loaded=$loaded failed=$failed")
        }
    }

    /**
     * 対象アプリを起動する。失敗してもクラッシュさせず、エラーメッセージで通知する。
     */
    fun launch(app: LauncherApp) {
        val context = getApplication<Application>()
        try {
            PerfLog.log(
                "[SORT] app launch clicked label=${app.safeLogLabel()} " +
                    "countBefore=${app.launchCount} lastBefore=${app.lastLaunchedAt}",
            )
            context.startActivity(repository.buildLaunchIntent(app))
            _uiState.update { it.copy(errorMessage = null) }
            // 起動できた時だけ履歴を更新する (通常モード/簡素モードどちらの起動も対象)。
            recordLaunch(app)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "起動に失敗しました: ${app.label} (${app.packageName})")
            }
        }
    }

    /**
     * アプリ起動を履歴に記録する。
     * 並び替えへ即反映するためメモリ上の統計をローカル更新し、DBへは非同期で永続化する。
     */
    private fun recordLaunch(app: LauncherApp) {
        val now = System.currentTimeMillis()
        val key = app.appId()
        _uiState.update { state ->
            state.copy(
                apps = state.apps.map {
                    if (it.appId() == key) {
                        it.copy(launchCount = it.launchCount + 1, lastLaunchedAt = now)
                    } else {
                        it
                    }
                },
            )
        }
        _uiState.value.apps.firstOrNull { it.appId() == key }?.let { updated ->
            PerfLog.log(
                "[SORT] app launch stats updated in memory label=${updated.safeLogLabel()} " +
                    "countAfter=${updated.launchCount} lastAfter=${updated.lastLaunchedAt} " +
                    "nonZero=${_uiState.value.apps.count { it.hasLaunchStats() }}",
            )
        }
        viewModelScope.launch {
            try {
                PerfLog.log("[SORT] DB recordLaunch requested appHash=${key.hashCode()}")
                val stats = withContext(Dispatchers.IO) {
                    repository.recordLaunch(app.packageName, app.className)
                    repository.loadLaunchStats()
                }
                PerfLog.log(
                    "[SORT] launch stats loaded after record rows=${stats.size} " +
                        "nonZero=${stats.count { it.value.first > 0 || it.value.second > 0L }}",
                )
                _uiState.update { state ->
                    val mergedApps = state.apps.map { current ->
                        val s = stats[current.appId()]
                        if (s != null && (current.launchCount != s.first || current.lastLaunchedAt != s.second)) {
                            current.copy(launchCount = s.first, lastLaunchedAt = s.second)
                        } else {
                            current
                        }
                    }
                    PerfLog.log(
                        "[SORT] launch stats merged after record apps=${mergedApps.size} " +
                            "nonZero=${mergedApps.count { it.hasLaunchStats() }}",
                    )
                    state.copy(apps = mergedApps)
                }
            } catch (e: Exception) {
                Log.w(TAG, "起動履歴の更新に失敗しました", e)
            }
        }
    }

    /** 並び順を変更し永続化する。同じ値なら何もしない。 */
    fun setSortMode(mode: AppSortMode) {
        if (mode != AppSortMode.Name && !_uiState.value.usageStatsAccessGranted) {
            if (_uiState.value.sortMode != AppSortMode.Name) {
                _uiState.update { it.copy(sortMode = AppSortMode.Name) }
                prefs.appSortMode = AppSortMode.Name.prefValue
            }
            PerfLog.log("[USAGE] sort mode blocked mode=${mode.prefValue} reason=usage_access_missing")
            return
        }
        if (_uiState.value.sortMode == mode) return
        _uiState.update { it.copy(sortMode = mode) }
        prefs.appSortMode = mode.prefValue
        PerfLog.log("[SORT] sort mode changed mode=${mode.prefValue}")
    }

    /** 検索文字列を更新する。絞り込みは UiState.filteredApps が行う。 */
    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val TAG = "AppListViewModel"
        // icon 後追いロードの反映バッチ件数 (再描画を抑える)。
        const val ICON_FLUSH_BATCH = 12
    }
}

private fun LauncherApp.appId(): String = "$packageName/$className"

private fun LauncherApp.hasLaunchStats(): Boolean = launchCount > 0 || lastLaunchedAt > 0L

private fun LauncherApp.safeLogLabel(): String = label.take(24)
