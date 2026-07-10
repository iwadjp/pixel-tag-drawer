package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iwadjp.pixeltagdrawer.PerfLog
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.data.AppRepository
import com.iwadjp.pixeltagdrawer.model.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
        PerfLog.log("AppListViewModel init (prefs.appSortMode=${prefs.appSortMode} initialSort=${_uiState.value.sortMode.prefValue})")
        refresh()
    }

    fun refresh() {
        val generation = ++loadGeneration
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                // まず icon なし (label のみ) で一覧を早く表示する。
                // ただし Recent/Count 起動では usage stats 反映前の実質名前順表示を避けるため、
                // データは流しつつ一覧描画だけを保留する (initialSortSettling)。
                // usage merge 完了 / Name への切替 / タイムアウトのいずれかで解除される。
                val list = withContext(Dispatchers.IO) { repository.loadLaunchableApps() }
                val settling = _uiState.value.sortMode != AppSortMode.Name
                _uiState.update {
                    it.copy(isLoading = false, apps = list, errorMessage = null, initialSortSettling = settling)
                }
                PerfLog.log("apps loaded into uiState count=${list.size} settling=$settling")
                if (settling) {
                    // 保険: usage 取得が長引いても label-only 表示へフォールバックする
                    viewModelScope.launch {
                        delay(SETTLING_TIMEOUT_MS)
                        if (_uiState.value.initialSortSettling) {
                            _uiState.update { it.copy(initialSortSettling = false) }
                            PerfLog.log("[SORT] settling timeout -> show label-only list")
                        }
                    }
                }

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
                // usage merge は「先に始まった方を優先」する: 冷間起動では ON_RESUME 由来の
                // refreshUsageStats() が先行して usage を取得中/取得済みのことがあり (こちらの方が速い)、
                // その場合は本体側の重複 merge をスキップする。
                //  - 実行中スキップ: apps publish 済みなら、進行中の resume 側 merge が update 時点の
                //    apps (公開済み) に反映するので本体側は不要。apps が空ならスキップしない
                //    (resume 側が空リストに merge して usage 未反映になる恐れがあるため)。
                //  - 完了済みスキップ: 直近2秒以内かつ applied>0。resume 側が publish 前の空リストに
                //    merge していた場合 (applied=0) は usage 未反映なので、本体側で改めて実行する。
                val mergeAge = SystemClock.elapsedRealtime() - lastUsageMergeAtMs
                val publishedAppsCount = _uiState.value.apps.size
                when {
                    usageMergeInProgress && publishedAppsCount > 0 -> {
                        PerfLog.log(
                            "[USAGE] refresh-side merge skipped by in-flight merge apps=$publishedAppsCount",
                        )
                    }
                    !usageMergeInProgress &&
                        lastUsageMergeAtMs != 0L &&
                        mergeAge < USAGE_REFRESH_MIN_INTERVAL_MS &&
                        lastUsageMergeAppliedCount > 0 -> {
                        PerfLog.log(
                            "[USAGE] refresh-side merge skipped by earlier merge " +
                                "ageMs=$mergeAge applied=$lastUsageMergeAppliedCount",
                        )
                    }
                    else -> mergeUsageStats(generation)
                }
                loadIcons(list, generation)
            } catch (e: Exception) {
                if (_uiState.value.initialSortSettling) {
                    PerfLog.log("[SORT] settling cleared by refresh failure")
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "アプリ一覧の読み込みに失敗しました",
                        initialSortSettling = false,
                    )
                }
            }
        }
    }

    // 直近の mergeUsageStats 完了時刻 (elapsedRealtime)。プロセス内メモリのみで永続化しない。
    // 冷間起動では refresh() 内の merge 直後に ON_RESUME 由来の refreshUsageStats() が必ず来て、
    // usage クエリと apps 全件再インスタンス化が二重に走るため、短時間の再要求をスキップする。
    @Volatile
    private var lastUsageMergeAtMs = 0L

    // usage merge が実行中かどうか (mergeUsageStats の実行区間で true)。
    // 「先に始まった merge を優先」するための判定に使う。resume 側・本体側どちらの merge も
    // この関数を通るため、実行中の再要求 (resume 連打等) はスキップされる。
    @Volatile
    private var usageMergeInProgress = false

    // 直近の usage merge が「何件の apps に反映されたか」。resume 側 merge が apps publish 前の
    // 空リストに走ってしまったケース (applied=0) を検出し、本体側 merge で救済するために使う。
    @Volatile
    private var lastUsageMergeAppliedCount = 0

    fun refreshUsageStats() {
        if (usageMergeInProgress) {
            PerfLog.log("[USAGE] usage refresh skipped merge in progress reason=resume")
            return
        }
        val age = SystemClock.elapsedRealtime() - lastUsageMergeAtMs
        if (lastUsageMergeAtMs != 0L && age < USAGE_REFRESH_MIN_INTERVAL_MS) {
            PerfLog.log("[USAGE] usage refresh skipped recent merge ageMs=$age")
            return
        }
        PerfLog.log("[USAGE] usage refresh start reason=resume")
        val generation = loadGeneration
        viewModelScope.launch {
            mergeUsageStats(generation)
        }
    }

    private suspend fun mergeUsageStats(generation: Int) {
        usageMergeInProgress = true
        try {
            mergeUsageStatsInternal(generation)
        } finally {
            // 例外・generation不一致の早期return・権限なし分岐のいずれでも必ず解除する
            usageMergeInProgress = false
        }
    }

    private suspend fun mergeUsageStatsInternal(generation: Int) {
        val granted = withContext(Dispatchers.IO) { repository.hasUsageStatsAccess() }
        PerfLog.log("[USAGE] usage access granted=$granted")
        if (generation != loadGeneration) return

        if (!granted) {
            if (_uiState.value.initialSortSettling) {
                PerfLog.log("[SORT] settling cleared by name fallback (usage access missing)")
            }
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
                    // 権限なしなら usage は来ないので、並び確定待ちも解除して Name で即表示する
                    initialSortSettling = false,
                    apps = state.apps.map {
                        if (it.usageLaunchCount != 0 || it.usageLastUsedAt != 0L ||
                            it.recommendedSessionCount != 0 || it.recommendedLastSessionAt != 0L
                        ) {
                            it.copy(
                                usageLaunchCount = 0,
                                usageLastUsedAt = 0L,
                                recommendedSessionCount = 0,
                                recommendedLastSessionAt = 0L,
                            )
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
            // 権限なしの確認も1回の merge 試行として扱い、直後の再要求をスキップ対象にする
            lastUsageMergeAtMs = SystemClock.elapsedRealtime()
            return
        }

        // Recommended の入力 (7日観測のセッション集計) も、Recent/Count と同じ merge タイミングで
        // 一緒に取得する。これにより権限チェック・二重取得抑制・settling 解除の既存経路をそのまま
        // 共用でき、モード切替時に改めて取得を待たず即座に並び替えられる (Recent/Count と同じ体験)。
        val (usageStats, recommendedStats) = withContext(Dispatchers.IO) {
            val usageDeferred = async { repository.loadUsageStats() }
            val recommendedDeferred = async { repository.loadRecommendedUsage() }
            usageDeferred.await() to recommendedDeferred.await()
        }
        if (generation != loadGeneration) return
        val wasSettling = _uiState.value.initialSortSettling
        var appliedCount = 0
        _uiState.update { state ->
            val mergedApps = state.apps.map { app ->
                val usage = usageStats[app.packageName]
                val recommended = recommendedStats[app.packageName]
                app.copy(
                    usageLaunchCount = usage?.launchCount ?: 0,
                    usageLastUsedAt = usage?.lastUsedAt ?: 0L,
                    recommendedSessionCount = recommended?.sessionCount ?: 0,
                    recommendedLastSessionAt = recommended?.lastSessionStartAt ?: 0L,
                )
            }
            PerfLog.log(
                "[USAGE] usage stats loaded packages=${usageStats.size} " +
                    "nonZeroRecent=${mergedApps.count { it.usageLastUsedAt > 0L }} " +
                    "nonZeroCount=${mergedApps.count { it.usageLaunchCount > 0 }} " +
                    "recommendedPackages=${recommendedStats.size} " +
                    "nonZeroRecommended=${mergedApps.count { it.recommendedSessionCount > 0 }}",
            )
            // usage 反映済みの並びが作れるようになったので、初回描画の保留を解除する
            if (wasSettling) {
                PerfLog.log(
                    "[SORT] settling cleared by usage merge mode=${state.sortMode.prefValue} " +
                        "apps=${mergedApps.size} " +
                        "nonZeroRecent=${mergedApps.count { it.usageLastUsedAt > 0L }} " +
                        "nonZeroCount=${mergedApps.count { it.usageLaunchCount > 0 }}",
                )
            }
            if (state.sortMode == AppSortMode.Recommended) {
                logTopRecommended(mergedApps)
            }
            appliedCount = mergedApps.size
            state.copy(usageStatsAccessGranted = true, apps = mergedApps, initialSortSettling = false)
        }
        lastUsageMergeAppliedCount = appliedCount
        lastUsageMergeAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * 「おすすめ」上位10件を診断ログへ出す (実機評価用)。既存の PerfLog リングバッファへ積むだけで、
     * 専用UIは追加しない (診断ダイアログの既存 report() でそのまま確認できる)。
     * ソートモードが Recommended の時の merge 完了時のみ呼ぶため、通常運用でのログ増加は限定的。
     */
    private fun logTopRecommended(apps: List<LauncherApp>) {
        val now = System.currentTimeMillis()
        val ranked = apps
            .filter { it.recommendedSessionCount > 0 }
            .sortedByDescending { recommendedScore(it.recommendedSessionCount, now - it.recommendedLastSessionAt) }
            .take(10)
        PerfLog.log("[RECO] top${ranked.size} (of nonZero=${apps.count { it.recommendedSessionCount > 0 }})")
        ranked.forEachIndexed { index, app ->
            val age = now - app.recommendedLastSessionAt
            val recency = recommendedRecency(app.recommendedSessionCount, age)
            val frequency = recommendedFrequency(app.recommendedSessionCount)
            val score = recommendedScore(app.recommendedSessionCount, age)
            PerfLog.log(
                "[RECO] #${index + 1} label=${app.safeLogLabel()} pkg=${app.packageName} " +
                    "score=${score.round3()} sessions=${app.recommendedSessionCount} " +
                    "lastSession=${app.recommendedLastSessionAt} recency=${recency.round3()} frequency=${frequency.round3()}",
            )
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
            // 初期画面の体感優先: アイコンは現在の表示順 (sortMode 順) の上位から読む。
            // Recent/Count では usage merge (冷間起動では resume 側が進行中のことがある) の完了を
            // 少しだけ待ってから順序を確定する。Name は usage 不要なので待たない。
            // 待ちや並べ替えは「読み込み順」だけに影響し、UI の並び順・batch flush は変えない。
            if (_uiState.value.sortMode != AppSortMode.Name) {
                val waitStart = SystemClock.elapsedRealtime()
                while ((usageMergeInProgress || _uiState.value.initialSortSettling) &&
                    SystemClock.elapsedRealtime() - waitStart < ICON_ORDER_WAIT_TIMEOUT_MS
                ) {
                    delay(50)
                }
            }
            // 反復順のスナップショット。usage 反映済みの state.apps を優先し、
            // (別 generation 等で) 件数が合わない場合はロード時のリストで従来どおり読む。
            val stateApps = _uiState.value.apps
            val orderSource = if (stateApps.size == apps.size) stateApps else apps
            val orderedApps = sortApps(orderSource, _uiState.value.sortMode)
            val top3 = orderedApps.take(3).joinToString(",") { it.label.take(12) }
            PerfLog.log(
                "icon lazy load start count=${orderedApps.size} " +
                    "mode=${_uiState.value.sortMode.prefValue} top3=[$top3]",
            )
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

            for (app in orderedApps) {
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

    /**
     * 並び順を変更する。同じ値なら何もしない。
     * persist=true (通常起動): prefs.appSortMode へ永続化する。
     * persist=false (タグ絞り込み起動中の一時変更): state のみ更新し、prefs は汚さない。
     * UsageStats 権限なしで Recent/Count を要求された場合の Name fallback も同じ persist 規則に従う。
     */
    fun setSortMode(mode: AppSortMode, persist: Boolean = true) {
        // Name の適用は usage を待つ必要がないため、並び確定待ちを解除する
        // (絞り込み起動の初期 Name 適用で初回表示を遅らせないため。sortMode が既に Name でも解除する)。
        if (mode == AppSortMode.Name && _uiState.value.initialSortSettling) {
            _uiState.update { it.copy(initialSortSettling = false) }
            PerfLog.log("[SORT] settling cleared by name applied")
        }
        if (mode != AppSortMode.Name && !_uiState.value.usageStatsAccessGranted) {
            if (_uiState.value.sortMode != AppSortMode.Name) {
                _uiState.update { it.copy(sortMode = AppSortMode.Name, initialSortSettling = false) }
                if (persist) prefs.appSortMode = AppSortMode.Name.prefValue
            }
            PerfLog.log("[USAGE] sort mode blocked mode=${mode.prefValue} reason=usage_access_missing persist=$persist")
            return
        }
        if (_uiState.value.sortMode == mode) return
        val previous = _uiState.value.sortMode
        _uiState.update { it.copy(sortMode = mode) }
        if (persist) prefs.appSortMode = mode.prefValue
        PerfLog.log(
            "[SORT] sort mode changed ${previous.prefValue}->${mode.prefValue} " +
                "persist=$persist granted=${_uiState.value.usageStatsAccessGranted}",
        )
    }

    // 絞り込み起動の「初期ソート=名前順」を適用済みか。
    // ショートカット再タップ (onNewIntent 再配送) やタスク復帰では再適用しないよう、
    // Activity/Compose ではなく VM (プロセス) 生存中のフラグで持つ。
    // 通常起動へ戻った時 (restoreSavedSortMode) にリセットし、次の絞り込みセッションで再び名前順から始める。
    private var filteredLaunchSortInitialized = false

    /**
     * 絞り込み起動の初期ソート (名前順・非永続) を適用する。
     * VM 生存中の絞り込みセッションにつき初回だけ効き、2回目以降 (ショートカット再タップによる
     * Intent 再配送・Activity 再生成) では何もしない = ユーザーの一時ソートを維持する。
     * プロセス再生成後は VM が作り直されるため、改めて名前順から始まる。
     */
    fun applyFilteredLaunchInitialSortOnce() {
        if (filteredLaunchSortInitialized) {
            PerfLog.log("[SORT] filtered initial sort skipped (already initialized, keep temp sort=${_uiState.value.sortMode.prefValue})")
            return
        }
        filteredLaunchSortInitialized = true
        PerfLog.log("[SORT] filtered initial sort apply mode=name (transient)")
        setSortMode(AppSortMode.Name, persist = false)
    }

    /**
     * 保存済みソート (prefs.appSortMode) を state へ復元する。prefs は変更しない。
     * ショートカット起動の一時ソートが残った同一プロセスで、通常起動へ戻った時に使う。
     * 絞り込みセッションを終えるので、初期ソート適用フラグもリセットする。
     * 権限なしで保存値が Recent/Count の場合は setSortMode の fallback で Name になる (prefs は汚さない)。
     */
    fun restoreSavedSortMode() {
        filteredLaunchSortInitialized = false
        val saved = AppSortMode.fromPrefValue(prefs.appSortMode)
        PerfLog.log("[SORT] restore saved sort mode=${saved.prefValue} (from prefs, transient)")
        setSortMode(saved, persist = false)
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
        // Recent/Count 起動時に usage stats を待つ上限。超えたら label-only 表示へフォールバック。
        const val SETTLING_TIMEOUT_MS = 1200L
        // この間隔内の usage 再取得要求 (ON_RESUME 由来) はスキップする。冷間起動の二重取得対策。
        const val USAGE_REFRESH_MIN_INTERVAL_MS = 2000L
        // icon 読み込み順を Recent/Count 順で確定するために usage merge 完了を待つ上限。
        // 超えたらその時点の並びで読み始める (読み込み順だけの問題で、表示順には影響しない)。
        const val ICON_ORDER_WAIT_TIMEOUT_MS = 500L
        // icon 後追いロードの反映バッチ件数 (再描画を抑える)。
        const val ICON_FLUSH_BATCH = 12
    }
}

private fun LauncherApp.appId(): String = "$packageName/$className"

private fun LauncherApp.hasLaunchStats(): Boolean = launchCount > 0 || lastLaunchedAt > 0L

private fun LauncherApp.safeLogLabel(): String = label.take(24)

/** 診断ログ表示専用の丸め (小数第3位)。比較・並び替えには使わない。 */
private fun Double.round3(): Double = kotlin.math.round(this * 1000.0) / 1000.0
