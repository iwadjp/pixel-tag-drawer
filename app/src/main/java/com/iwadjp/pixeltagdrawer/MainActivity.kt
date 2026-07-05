package com.iwadjp.pixeltagdrawer

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.model.LauncherApp
import com.iwadjp.pixeltagdrawer.ui.AppListViewModel
import com.iwadjp.pixeltagdrawer.ui.AppSortMode
import com.iwadjp.pixeltagdrawer.ui.TagViewModel
import com.iwadjp.pixeltagdrawer.ui.sortApps
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    // 起動Intentで指定されたタグフィルタ。onCreate / onNewIntent で更新する。
    private val launchFilter = mutableStateOf<LaunchFilter?>(null)
    // onNewIntent ごとに +1。filter=null の「明示的な新Intent (通常アイコンタップ等)」を、
    // 初期起動 (seq=0) や単なるタスク復帰 (Intent が来ない) と区別するために使う。
    private val newIntentSeq = mutableStateOf(0)
    // 初期 onCreate が「通常ランチャーIntent (MAIN/LAUNCHER/filterなし)」だったか。
    // 実機診断で、通常アイコンタップが onNewIntent ではなく新規 onCreate として届くと判明したため、
    // これを「明示的な通常アイコン起動」とみなして seq=0 でもフィルタを解除するのに使う。
    private val initialNormalLauncher = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerfLog.start()
        PerfLog.log("MainActivity.onCreate start")
        PerfLog.log("[LM] Activity.onCreate (${describeIntent(intent)})")
        launchFilter.value = parseLaunchFilter(intent)
        initialNormalLauncher.value = isNormalLauncherIntent(intent)
        PerfLog.log(
            "[LM] Activity.onCreate parsed launchFilter=${formatLaunchFilter(launchFilter.value)} " +
                "normalLauncher=${initialNormalLauncher.value}",
        )
        PerfLog.log("setContent start (launchFilter=${formatLaunchFilter(launchFilter.value)})")
        setContent {
            PixelTagDrawerApp(
                launchFilter = launchFilter.value,
                newIntentSeq = newIntentSeq.value,
                initialNormalLauncher = initialNormalLauncher.value,
            )
        }
    }

    // 既に起動中のインスタンスへ Intent が届いた場合にも反映する
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchFilter.value = parseLaunchFilter(intent)
        newIntentSeq.value = newIntentSeq.value + 1
        PerfLog.log(
            "[LM] Activity.onNewIntent seq=${newIntentSeq.value} " +
                "parsed=${formatLaunchFilter(launchFilter.value)} (${describeIntent(intent)})",
        )
    }

    // 観測用: 通常アイコンタップ時に onNewIntent が来ているか / 純resume なのかを実機で見分ける。
    // uiMode は Compose 側 state のため Activity からは参照せず、ここでは launchFilter / seq のみ記録する。
    override fun onResume() {
        super.onResume()
        PerfLog.log(
            "[LM] Activity.onResume launchFilter=${formatLaunchFilter(launchFilter.value)} " +
                "seq=${newIntentSeq.value}",
        )
    }

    /** 起動Intent extras を解釈する。タグなし指定を優先。指定が無ければ null。 */
    private fun parseLaunchFilter(intent: Intent?): LaunchFilter? {
        intent ?: return null
        if (intent.getBooleanExtra(EXTRA_SHOW_UNTAGGED_ONLY, false)) {
            return LaunchFilter.Untagged
        }
        if (intent.hasExtra(EXTRA_FILTER_TAG_ID)) {
            val tagId = intent.getLongExtra(EXTRA_FILTER_TAG_ID, -1L)
            if (tagId >= 0) return LaunchFilter.Tag(tagId)
        }
        return null
    }

    /**
     * ホームの通常アプリアイコンから起動された「通常ランチャーIntent」かどうかを判定する。
     * 実機診断では action=MAIN / category=LAUNCHER / extras=none で、ショートカット用 extras を持たない。
     * この場合は明示的な通常アイコン起動とみなし、初期 onCreate でもフィルタを解除する。
     */
    private fun isNormalLauncherIntent(intent: Intent?): Boolean {
        intent ?: return false
        if (intent.action != Intent.ACTION_MAIN) return false
        if (intent.categories?.contains(Intent.CATEGORY_LAUNCHER) != true) return false
        // ショートカット用 extras があれば通常ランチャー起動ではない (filter が解釈できる = ショートカット)。
        return parseLaunchFilter(intent) == null
    }

    companion object {
        // 将来の Pinned Shortcut 作成側からも再利用できるよう公開定数にする
        const val EXTRA_FILTER_TAG_ID = "com.iwadjp.pixeltagdrawer.extra.FILTER_TAG_ID"
        const val EXTRA_SHOW_UNTAGGED_ONLY = "com.iwadjp.pixeltagdrawer.extra.SHOW_UNTAGGED_ONLY"
    }
}

/** 起動Intentで指定されたタグフィルタ。 */
sealed interface LaunchFilter {
    data class Tag(val tagId: Long) : LaunchFilter
    object Untagged : LaunchFilter
}

/** 計測ログ用に LaunchFilter を読みやすい文字列にする (object のクラス名表記を避ける)。 */
private fun formatLaunchFilter(filter: LaunchFilter?): String = when (filter) {
    is LaunchFilter.Tag -> "Tag(tagId=${filter.tagId})"
    LaunchFilter.Untagged -> "Untagged"
    null -> "none"
}

/**
 * 診断用に Intent の概要を読みやすい1行にする。観測専用。
 * 個人情報を避けるため値は出さず、action / categories / extras の key 名 / flags のみを出す。
 */
private fun describeIntent(intent: Intent?): String {
    intent ?: return "intent=null"
    val action = intent.action?.removePrefix("android.intent.action.") ?: "none"
    val categories = intent.categories
        ?.joinToString(",") { it.removePrefix("android.intent.category.") }
        ?.ifEmpty { "none" }
        ?: "none"
    val extras = intent.extras?.keySet()
        ?.joinToString(",") { it.removePrefix("com.iwadjp.pixeltagdrawer.extra.") }
        ?.ifEmpty { "none" }
        ?: "none"
    val flags = "0x" + intent.flags.toString(16)
    return "action=$action categories=[$categories] extras=[$extras] flags=$flags"
}

private const val TAG_SHORTCUT = "PinShortcut"

/**
 * 指定タグで開く Pinned Shortcut の作成をリクエストする。
 * res に launcher icon 資源が無いため、コードで生成した Bitmap アイコンを必ず設定する。
 * 非対応ランチャーや失敗時はクラッシュせず false を返す。
 */
private fun requestPinTagShortcut(context: Context, tagId: Long, tagName: String): Boolean {
    // 通常ランチャー task と分離するため、ショートカットは MainActivity ではなく中継 Activity を起動する。
    val intent = Intent(context, ShortcutEntryActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_FILTER_TAG_ID, tagId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    // アイコン中央の文字はタグ名の先頭1文字。空なら "#"。
    val iconText = tagName.trim().take(1).ifEmpty { "#" }
    return requestPin(context, id = "tag_$tagId", label = tagName, iconText = iconText, intent = intent)
}

/** 「タグなし」(未付与アプリのみ) で開く Pinned Shortcut の作成をリクエストする。 */
private fun requestPinUntaggedShortcut(context: Context): Boolean {
    // 通常ランチャー task と分離するため、ショートカットは MainActivity ではなく中継 Activity を起動する。
    val intent = Intent(context, ShortcutEntryActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_SHOW_UNTAGGED_ONLY, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return requestPin(context, id = "untagged", label = "タグなし", iconText = "無", intent = intent)
}

/**
 * 共通: 生成アイコン付き ShortcutInfo を組み立てて requestPinShortcut を呼ぶ。
 * ShortcutManager が無い / 非対応 / 構築・リクエスト失敗 のいずれもクラッシュさせず false を返す。
 */
private fun requestPin(context: Context, id: String, label: String, iconText: String, intent: Intent): Boolean {
    val manager = context.getSystemService(ShortcutManager::class.java)
    if (manager == null || !manager.isRequestPinShortcutSupported) {
        Log.w(TAG_SHORTCUT, "Pinned Shortcut 非対応 (manager=$manager)")
        return false
    }
    return try {
        val shortcut = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setIcon(buildShortcutIcon(context, iconText))
            .setIntent(intent)
            .build()
        manager.requestPinShortcut(shortcut, null)
    } catch (e: Exception) {
        Log.w(TAG_SHORTCUT, "Pinned Shortcut 作成に失敗しました (id=$id)", e)
        false
    }
}

/** ショートカット用の最小アイコンをコード生成する (円背景 + 中央に短い文字)。 */
private fun buildShortcutIcon(context: Context, text: String): Icon {
    val sizePx = (96 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius, bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    val baselineY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, radius, baselineY, textPaint)
    return Icon.createWithBitmap(bitmap)
}

@Composable
fun PixelTagDrawerApp(
    launchFilter: LaunchFilter? = null,
    newIntentSeq: Int = 0,
    initialNormalLauncher: Boolean = false,
) {
    val context = LocalContext.current
    val colorScheme = dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppListScreen(
                launchFilter = launchFilter,
                newIntentSeq = newIntentSeq,
                initialNormalLauncher = initialNormalLauncher,
            )
        }
    }
}

@Composable
fun AppListScreen(
    viewModel: AppListViewModel = viewModel(),
    tagViewModel: TagViewModel = viewModel(),
    launchFilter: LaunchFilter? = null,
    newIntentSeq: Int = 0,
    initialNormalLauncher: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tagState by tagViewModel.uiState.collectAsStateWithLifecycle()

    // 表示状態の最小永続化。前回の表示モード / タグ管理の開閉を復元する。
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) { AppPreferences(context) }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ホーム画面ショートカット作成リクエストの結果メッセージ (タグ管理内に表示)。
    var shortcutMessage by remember { mutableStateOf<String?>(null) }

    // 起動計測の診断ダイアログ (adb 不要で計測値を確認/コピーするため)。
    val clipboard = LocalClipboardManager.current
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticsReport by remember { mutableStateOf("") }

    // 表示モード (リスト / アイコン)。前回値を復元し、変更時に保存する。
    var displayMode by remember {
        mutableStateOf(if (prefs.isGridMode) AppDisplayMode.Grid else AppDisplayMode.List)
    }
    LaunchedEffect(displayMode) { prefs.isGridMode = displayMode == AppDisplayMode.Grid }

    // タグ管理UI (作成/変更/削除) の開閉。前回値を復元し、変更時に保存する。
    var showTagManagement by remember { mutableStateOf(prefs.showTagManagement) }
    LaunchedEffect(showTagManagement) { prefs.showTagManagement = showTagManagement }

    // タグ編集モード。ON のときだけアプリ行/セルに「タグ」ボタンを出す。
    // 誤操作防止のため永続化せず、起動時は必ず OFF。
    var tagEditMode by remember { mutableStateOf(false) }

    // 一括タグ付け対象として選択中のアプリキー ("pkg/cls") とその対象タグ。永続化なし。
    var selectedBulkApps by remember { mutableStateOf(emptySet<String>()) }
    var bulkTargetTagId by remember { mutableStateOf<Long?>(null) }

    // UIモード。通常起動=None、ショートカット起動=Simplified、その編集画面=Editing。
    // 初期値: launchFilter があれば Simplified、無ければ None。以後はモード遷移操作で更新。
    var uiMode by remember {
        mutableStateOf(
            if (launchFilter != null) ShortcutUiMode.Simplified else ShortcutUiMode.None,
        )
    }
    // 簡素モード (編集系UIを隠す) かどうか。表示条件はすべてこのモードに基づき一意に決める。
    val simplified = uiMode == ShortcutUiMode.Simplified

    // モード遷移時に編集系の一時stateを畳む共通処理 (フィルタ・検索は触らない)。
    val foldEditing: () -> Unit = {
        tagEditMode = false
        selectedBulkApps = emptySet()
        bulkTargetTagId = null
        tagViewModel.clearSelectedApp()
    }

    // 観測用: 初回 compose 時の入力値と初期モードを1回だけ記録する (再compose の連発は記録しない)。
    LaunchedEffect(Unit) {
        PerfLog.log(
            "[LM] AppListScreen first compose " +
                "launchFilter=${formatLaunchFilter(launchFilter)} seq=$newIntentSeq " +
                "normalLauncher=$initialNormalLauncher uiMode=$uiMode (reason=initial)",
        )
    }

    // 起動/新Intent の扱い (newIntentSeq でキーするので onNewIntent ごとに再評価される):
    //  - launchFilter != null: ショートカット起動 → filter 適用 + Simplified に初期化。
    //  - launchFilter == null かつ newIntentSeq > 0: 明示的な通常アイコンタップ (onNewIntent) → None に戻し、
    //    ショートカット由来の絞り込みを解除する。
    //  - launchFilter == null かつ seq==0 かつ initialNormalLauncher: 通常アイコンの初期 onCreate 起動
    //    (実機では通常タップが新規 onCreate=MAIN/LAUNCHER で届く) → None + フィルタ解除。
    //  - launchFilter == null かつ seq==0 かつ 通常ランチャーでない: プロセス復元等 → 何もしない (状態維持)。
    //    単なるタスク復帰は onCreate/onNewIntent が来ず、この LaunchedEffect 自体が再評価されない。
    LaunchedEffect(launchFilter, newIntentSeq) {
        PerfLog.log(
            "[LM] LaunchedEffect(launch) enter filter=${formatLaunchFilter(launchFilter)} seq=$newIntentSeq " +
                "normalLauncher=$initialNormalLauncher (filter is ${if (launchFilter == null) "null" else "non-null"})",
        )
        when (launchFilter) {
            is LaunchFilter.Tag -> {
                // ショートカット由来は非永続。通常モードの手動フィルタ prefs を汚さない。
                tagViewModel.applyShortcutFilterTag(launchFilter.tagId)
                // 絞り込み起動の初期ソートは保存値に関係なく名前順 (非永続)。
                // ショートカットタスクは excludeFromRecents のため「ホーム→復帰」は必ず
                // ショートカット再タップ (onNewIntent 再配送 or 再生成) として届く。そこで
                // 再適用の抑止は seq ではなく VM 生存中の once フラグで行い、一時ソートを維持する。
                viewModel.applyFilteredLaunchInitialSortOnce()
                uiMode = ShortcutUiMode.Simplified
                foldEditing()
                PerfLog.log("[LM] branch=shortcut filter applied -> uiMode=Simplified, transient (reason=shortcut intent)")
            }
            LaunchFilter.Untagged -> {
                tagViewModel.applyShortcutUntaggedFilter()
                viewModel.applyFilteredLaunchInitialSortOnce()
                uiMode = ShortcutUiMode.Simplified
                foldEditing()
                PerfLog.log("[LM] branch=shortcut untagged applied -> uiMode=Simplified, transient (reason=shortcut intent)")
            }
            null -> {
                when {
                    newIntentSeq > 0 -> {
                        // 通常アイコンの明示起動 (onNewIntent 経由): ショートカット一時フィルタを捨て、
                        // 保存済みの手動フィルタを復元する (手動フィルタは消さない)。
                        uiMode = ShortcutUiMode.None
                        tagViewModel.restoreManualFilters()
                        // 同一プロセスに絞り込み起動の一時ソートが残っていても、通常起動は保存値へ戻す。
                        viewModel.restoreSavedSortMode()
                        foldEditing()
                        PerfLog.log("[LM] branch=normal new intent reset -> uiMode=None, restoreManualFilters (reason=normal intent reset)")
                    }
                    initialNormalLauncher -> {
                        // 通常アイコンの明示起動 (新規 onCreate 経由): こちらも手動フィルタを復元する。
                        // 注: ここでは restoreSavedSortMode を呼ばない。冷間起動時は VM init が
                        // prefs から初期化済みで、権限チェック完了前に setSortMode を通すと
                        // Recent/Count が誤って Name に落ちるため (usageStatsAccessGranted は非同期確定)。
                        uiMode = ShortcutUiMode.None
                        tagViewModel.restoreManualFilters()
                        foldEditing()
                        PerfLog.log("[LM] branch=initial normal launcher reset -> uiMode=None, restoreManualFilters (reason=initial normal launcher)")
                    }
                    else -> {
                        // 通常ランチャーでない null 起動 (プロセス復元など): 状態を維持する。
                        PerfLog.log("[LM] branch=initial normal no-op (seq=0, normalLauncher=false)")
                    }
                }
            }
        }
        PerfLog.log("launch filter applied (filter=${formatLaunchFilter(launchFilter)}, seq=$newIntentSeq, mode=$uiMode)")
    }

    // 一覧の最終要素がナビゲーションバーに隠れないよう、その分を一覧下端の余白に加える。
    // 固定エリアには付けず、スクロール領域 (List/Grid) の contentPadding だけに効かせる。
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var previousScrollSortMode by remember { mutableStateOf<AppSortMode?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var appListMenuExpanded by remember { mutableStateOf(false) }

    // 操作エリア (タイトル/タグ/検索/件数) は固定し、アプリ一覧だけをスクロールさせる。
    // そのため全体は Column、一覧部分のみ weight(1f) を持つ LazyColumn にする。
    Column(
        modifier = Modifier
            .fillMaxSize()
            // エッジツーエッジ時にステータスバーへ潜り込まないようインセット分を空ける。
            // これがないとタイトル行のタグ管理ボタンが最上部に被ってタップできない。
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 常用時の画面占有を抑えるため、タイトルは小さく1行・説明文は撤去する。
        // 右端にタグ管理 (作成/変更/削除) の開閉トグルを置く。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Pixel Tag Drawer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // 起動計測の診断。通常/簡素表示のどちらでも参照できるよう常時表示する。
            TextButton(onClick = {
                diagnosticsReport = PerfLog.report()
                showDiagnostics = true
            }) {
                Text("診断")
            }
            // 「編集」「一覧に戻る」の表示条件は3モードに基づき一意に決める。
            // 通常モード: どちらも出さない / 簡素: 編集のみ / 編集: 一覧に戻るのみ。
            when (uiMode) {
                ShortcutUiMode.Simplified -> {
                    TextButton(onClick = {
                        uiMode = ShortcutUiMode.Editing
                        PerfLog.log("[LM] button=Edit -> uiMode=Editing (reason=edit clicked)")
                    }) {
                        Text("編集")
                    }
                }
                ShortcutUiMode.Editing -> {
                    TextButton(onClick = {
                        // 簡素モードへ戻す。編集中に ◀▶ やタグタップで別タグへ移動していても、
                        // 絞り込みは起動時の初期条件 (launchFilter) へ復元する。launchFilter は
                        // 起動 Intent 由来の不変値なので、編集中の操作では上書きされない。
                        // 起動タグが編集中に削除されていても applyShortcutFilterTag は安全 (絞り込みなしに戻る)。
                        uiMode = ShortcutUiMode.Simplified
                        when (launchFilter) {
                            is LaunchFilter.Tag ->
                                tagViewModel.applyShortcutFilterTag(launchFilter.tagId)
                            LaunchFilter.Untagged ->
                                tagViewModel.applyShortcutUntaggedFilter()
                            null -> {
                                // 絞り込み起動でない場合 (通常は到達しない): 従来どおり現在の条件を維持する
                            }
                        }
                        // 一時ソートも起動時の初期値 (名前順) へ戻す。保存ソート (prefs) は変更しない。
                        viewModel.setSortMode(AppSortMode.Name, persist = false)
                        showTagManagement = false
                        tagEditMode = false
                        selectedBulkApps = emptySet()
                        bulkTargetTagId = null
                        tagViewModel.clearSelectedApp()
                        PerfLog.log(
                            "[LM] button=ListReturn -> uiMode=Simplified, " +
                                "restore launch filter=${formatLaunchFilter(launchFilter)} (reason=list return clicked)",
                        )
                    }) {
                        Text("一覧に戻る")
                    }
                    // タグ管理はタイトル行には置かない。通常起動と同じく一覧上部の ⋯ メニューから開く
                    // (編集モードは simplified=false のため ⋯ メニューが表示される)。
                }
                ShortcutUiMode.None -> {
                    // 通常画面では低頻度操作を一覧上部の ⋯ メニューへ寄せる。
                }
            }
        }

        // 起動計測の診断ダイアログ。adb なしで計測値を確認・コピーできる。
        if (showDiagnostics) {
            DiagnosticsDialog(
                report = diagnosticsReport,
                onRefresh = { diagnosticsReport = PerfLog.report() },
                onCopy = { clipboard.setText(AnnotatedString(diagnosticsReport)) },
                onDismiss = { showDiagnostics = false },
            )
        }

        // タグ管理UIは開いているときだけ表示。ショートカット簡素表示中は隠す。
        if (showTagManagement && !simplified) {
            TagSection(
                state = tagState,
                shortcutMessage = shortcutMessage,
                onNameChange = tagViewModel::updateTagName,
                onCreate = tagViewModel::createTag,
                onDelete = tagViewModel::deleteTag,
                onStartRename = tagViewModel::startRenameTag,
                onMoveUp = tagViewModel::moveTagUp,
                onMoveDown = tagViewModel::moveTagDown,
                onEditingNameChange = tagViewModel::updateEditingTagName,
                onEditingDisplayLabelChange = tagViewModel::updateEditingTagDisplayLabel,
                onConfirmRename = tagViewModel::confirmRenameTag,
                onCancelRename = tagViewModel::cancelRenameTag,
                onPinTag = { tag ->
                    val ok = requestPinTagShortcut(context, tag.tagId, tag.name)
                    shortcutMessage = if (ok) {
                        "「${tag.name}」のホーム追加をリクエストしました"
                    } else {
                        "ホーム画面への追加に失敗しました"
                    }
                },
                onPinUntagged = {
                    val ok = requestPinUntaggedShortcut(context)
                    shortcutMessage = if (ok) {
                        "「タグなし」のホーム追加をリクエストしました"
                    } else {
                        "ホーム画面への追加に失敗しました"
                    }
                },
                onStartEditUntagged = tagViewModel::startEditUntaggedLabel,
                onEditingUntaggedLabelChange = tagViewModel::updateEditingUntaggedLabel,
                onConfirmUntaggedLabel = tagViewModel::confirmUntaggedLabel,
                onCancelEditUntaggedLabel = tagViewModel::cancelEditUntaggedLabel,
            )
        }

        // 個別タグ編集パネルは廃止し、タグ付けは「タグ編集→選択→一括バー」に一本化する。
        // (TagViewModel 側の個別付与/解除・Undo/Redo 実装は将来再利用のため残置)

        uiState.errorMessage?.let { msg ->
            // 目立ちすぎないよう小さめのテキストで表示する
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // タグ行表示中かつ単一タグ選択中だけ、前/次遷移 (表示順で±1、端は循環) を有効にする。
        // 複数選択・未選択・タグなし絞り込み・遷移先なし (タグ1件) では無効 (◀▶ボタン非表示)。
        val tagRowVisible = tagState.tags.isNotEmpty() && !simplified
        val singleTagNavEnabled = tagRowVisible && !tagState.showUntaggedOnly &&
            tagState.selectedFilterTagIds.size == 1 && tagState.tags.size > 1
        val selectAdjacentTag: (Int) -> Unit = { delta ->
            val tags = tagState.tags
            val currentId = tagState.selectedFilterTagIds.firstOrNull()
            val currentIndex = tags.indexOfFirst { it.tagId == currentId }
            if (currentIndex >= 0) {
                val nextIndex = (currentIndex + delta + tags.size) % tags.size
                PerfLog.log("[LM] tag nav delta=$delta tagId=${tags[nextIndex].tagId}")
                tagViewModel.selectSingleFilterTag(tags[nextIndex].tagId)
            }
        }

        // 検索欄はアプリが読み込まれているときだけ表示する。
        // 単一タグ選択中は検索欄を少し短くし、右側に前/次タグの ◀▶ ボタンを出す。
        if (uiState.apps.isNotEmpty()) {
            // 検索BOX直下の見た目の余白を、次セクションがタグ行でも操作行でも約6dpに揃える。
            // タグ行あり: spacedBy 4dp + タグ行の上 2dp = 6dp。
            // タグ行なし (簡素表示など): 直後の操作行が offset(y=-6dp) で上に詰めて描画されるため、
            // そのままだと見た目ほぼ0dpになる。8dp 足して 4+8-6 = 6dp に合わせる。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = if (tagRowVisible) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::updateQuery,
                    singleLine = true,
                    label = { Text("アプリ名 / パッケージ名で検索") },
                    // 入力があるときだけ、一発クリアできるボタンを出す
                    trailingIcon = if (uiState.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Text("✕")
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
                if (singleTagNavEnabled) {
                    IconButton(
                        onClick = { selectAdjacentTag(-1) },
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "前のタグ" },
                    ) {
                        Text("◀", style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(
                        onClick = { selectAdjacentTag(+1) },
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "次のタグ" },
                    ) {
                        Text("▶", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // 絞り込みチップはタグがあり、かつショートカット簡素表示でないときだけ表示する
        if (tagRowVisible) {
            TagFilterSection(
                state = tagState,
                onToggle = { tagId ->
                    PerfLog.log("[LM] manual filter toggle tagId=$tagId")
                    tagViewModel.toggleFilterTag(tagId)
                },
                onClear = tagViewModel::clearFilterTags,
                onToggleUntagged = tagViewModel::toggleUntaggedFilter,
            )
        }

        // 検索 (名前/パッケージ) で絞った結果に、タグ条件をANDで合成し、最後に並び順を適用する。
        // 「タグなし」は未付与アプリのみ。通常タグ選択時は全タグを持つアプリのみ。両者は排他。
        // 並び替えは検索・タグ絞り込み後の最終リストに効く (簡素モードでも現在の sortMode が効く)。
        val searchFilteredApps = uiState.filteredApps
        val effectiveSortMode = remember(uiState.sortMode, uiState.usageStatsAccessGranted) {
            if (uiState.usageStatsAccessGranted || uiState.sortMode == AppSortMode.Name) {
                uiState.sortMode
            } else {
                AppSortMode.Name
            }
        }
        LaunchedEffect(effectiveSortMode) {
            val previous = previousScrollSortMode
            previousScrollSortMode = effectiveSortMode
            if (previous != null && previous != effectiveSortMode) {
                // 表示中の state を先に戻す。非表示側の state への scrollToItem は
                // remeasure 先がなく完了しないことがあるため、後回しにして表示側を確実に戻す。
                if (displayMode == AppDisplayMode.Grid) {
                    gridState.scrollToItem(0)
                    listState.scrollToItem(0)
                } else {
                    listState.scrollToItem(0)
                    gridState.scrollToItem(0)
                }
            }
        }
        val usageStatsKey = remember(searchFilteredApps) {
            searchFilteredApps.joinToString(separator = "|") { app ->
                "${app.packageName}/${app.className}:${app.usageLaunchCount}:${app.usageLastUsedAt}"
            }
        }
        // icon 後追いロード中の不要な再ソート抑制。icon batch のたびに apps のインスタンスが
        // 変わり remember が再計算されるが、icon はどのソートキーにも影響しない。
        // 並びに影響する入力 (mode / 対象集合 / label / usage値) の署名が前回と同じなら、
        // 再ソートせず前回の並び順に新インスタンス (icon 差し替え済み) を並べ直すだけにする。
        // plain holder にするのは、composition 中の書き込みで再compose を誘発しないため。
        class SortOrderCache {
            var signature: String? = null
            var order: List<String>? = null
        }
        val sortOrderCache = remember { SortOrderCache() }
        val sortedApps = remember(
            searchFilteredApps,
            tagState.selectedFilterTagIds,
            tagState.showUntaggedOnly,
            tagState.appTagMap,
            effectiveSortMode,
            usageStatsKey,
        ) {
            val selected = tagState.selectedFilterTagIds
            val tagFiltered = when {
                tagState.showUntaggedOnly -> searchFilteredApps.filter { app ->
                    tagState.appTagMap["${app.packageName}/${app.className}"].isNullOrEmpty()
                }
                selected.isEmpty() -> searchFilteredApps
                else -> searchFilteredApps.filter { app ->
                    val appTags = tagState.appTagMap["${app.packageName}/${app.className}"]
                        ?: emptySet()
                    appTags.containsAll(selected)
                }
            }
            // 並びに影響する入力だけの署名 (icon は含めない)。Name は label、
            // Recent/Count は usage 値がソートキーのため、その全てと対象集合を含める。
            val signature = effectiveSortMode.prefValue + "|" + tagFiltered.joinToString("|") {
                "${it.packageName}/${it.className}:${it.label}:${it.usageLaunchCount}:${it.usageLastUsedAt}"
            }
            val cachedOrder = sortOrderCache.order
            val result = if (signature == sortOrderCache.signature && cachedOrder != null) {
                // icon 差し替えのみの更新: 既存順を維持して並べ直すだけで再ソートしない
                val byId = tagFiltered.associateBy { "${it.packageName}/${it.className}" }
                val arranged = cachedOrder.mapNotNull { byId[it] }
                if (arranged.size == tagFiltered.size) {
                    arranged
                } else {
                    // 万一 id 集合が食い違ったら安全側で通常ソートに落とす
                    sortApps(tagFiltered, effectiveSortMode)
                }
            } else {
                sortApps(tagFiltered, effectiveSortMode)
            }
            sortOrderCache.signature = signature
            sortOrderCache.order = result.map { "${it.packageName}/${it.className}" }
            result
        }
        // アプリの「並びだけ」を表すキー (usage 値は含めない)。並び替わり検出に使う。
        val appOrderKey = remember(sortedApps) {
            sortedApps.joinToString(separator = "|") { "${it.packageName}/${it.className}" }
        }
        // 最近/回数順の「上位アプリへ早くアクセスする」を最優先するための先頭貼り直し。
        // 背景1: items(key=...) のキー追従で、並び替わり時にビューポートが旧先頭アイテムへ追従して
        //   下へ流れる (使うたびに蓄積し中位表示になる)。
        // 背景2: rememberLazyListState/GridState は saveable のため、Activity 再生成のたびに
        //   蓄積済みドリフト位置が復元される。一方 previousAppOrderKey (remember) は null に戻るので、
        //   「並び変化時のみ」の判定では初回 compose で一度も貼り直されない。
        // 対応: Recent/Count では「初回 compose (起動・再生成復帰)」と「並び変化時」の両方で先頭へ戻す。
        // 名前順は並びがほぼ安定 (追加/削除程度) のため、復元位置を尊重して貼り直さない。
        // 手動スクロール操作中は邪魔しない。
        var previousAppOrderKey by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(appOrderKey) {
            val previous = previousAppOrderKey
            previousAppOrderKey = appOrderKey
            if (effectiveSortMode == AppSortMode.Name) return@LaunchedEffect
            val initial = previous == null
            val orderChanged = previous != null && previous != appOrderKey
            if (!initial && !orderChanged) return@LaunchedEffect
            // 表示中の displayMode 側の state だけを判定・操作する。
            // 非表示側の LazyColumn/Grid は composition に存在せず、その state への
            // scrollToItem は remeasure 先がなく完了しないことがある (done が出ずに止まる)。
            val isGrid = displayMode == AppDisplayMode.Grid
            val before = if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            val scrolling = if (isGrid) gridState.isScrollInProgress else listState.isScrollInProgress
            val top3 = sortedApps.take(3).joinToString(",") { it.label.take(12) }
            PerfLog.log(
                "[SORT] re-pin check mode=${effectiveSortMode.prefValue} display=$displayMode " +
                    "initial=$initial changed=$orderChanged " +
                    "prevHash=${previous?.hashCode() ?: 0} curHash=${appOrderKey.hashCode()} " +
                    "before=$before scrolling=$scrolling top3=[$top3]",
            )
            // 初回 compose で既に先頭なら何もしない (冷間起動の通常ケース)
            if (initial && before == 0) {
                PerfLog.log("[SORT] re-pin skip (initial at top)")
                return@LaunchedEffect
            }
            if (scrolling) {
                PerfLog.log("[SORT] re-pin skip (scroll in progress)")
                return@LaunchedEffect
            }
            if (isGrid) gridState.scrollToItem(0) else listState.scrollToItem(0)
            // 同フレームの後続レイアウトでキー追従が位置を引き戻す可能性への保険として、
            // 1フレーム待ってからもう一度先頭を確定する。
            withFrameNanos { }
            if (isGrid) gridState.scrollToItem(0) else listState.scrollToItem(0)
            val after = if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            PerfLog.log("[SORT] re-pin done display=$displayMode after=$after")
        }
        // 並び (appOrderKey) が実際に変わった時だけ記録する。sortedApps インスタンスを key に
        // すると icon batch のたびに同一の並びで連発してしまう。
        LaunchedEffect(effectiveSortMode, appOrderKey, simplified) {
            val first = sortedApps.firstOrNull()
            PerfLog.log(
                "[SORT] sort result mode=${effectiveSortMode.prefValue} " +
                    "count=${sortedApps.size} " +
                    "nonZero=${sortedApps.count { it.usageLaunchCount > 0 || it.usageLastUsedAt > 0L }} " +
                    "simplified=$simplified " +
                    "top=${first?.label?.take(24)}:${first?.usageLaunchCount}:${first?.usageLastUsedAt}",
            )
        }

        // tagId -> タグ名。アプリ行に付与済みタグ名を表示するために使う。
        val tagNameById = remember(tagState.tags) {
            tagState.tags.associate { it.tagId to it.name }
        }

        // 調査用: アプリ読込完了後の初回 filteredApps を1回だけ計測する。
        // 0件 (例: タグなし絞り込みで該当なし) でも記録できるよう、空判定ではなく apps 読込で判定する。
        val perfFirstListLogged = remember { mutableStateOf(false) }
        LaunchedEffect(uiState.apps.isNotEmpty(), sortedApps) {
            if (!perfFirstListLogged.value && uiState.apps.isNotEmpty()) {
                perfFirstListLogged.value = true
                PerfLog.log("first visible filtered list count=${sortedApps.size}")
            }
        }

        when {
            uiState.isLoading && uiState.apps.isEmpty() -> {
                Text(
                    text = "アプリ一覧を読み込んでいます...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            uiState.apps.isEmpty() -> {
                Text(
                    text = "起動可能なアプリが見つかりませんでした",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            // Recent/Count 起動の並び確定待ち: usage stats 反映前の実質名前順を一瞬見せて
            // 数百ms後に並び替わる二段階表示を避ける。VM 側のタイムアウトで必ず解除される。
            uiState.initialSortSettling -> {
                Text(
                    text = "アプリ一覧を読み込んでいます...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            sortedApps.isEmpty() -> {
                Text(
                    text = "一致するアプリがありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            else -> {
                // 件数・表示切替・並び替え・低頻度操作を1行にまとめ、一覧だけをスクロールさせる
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-6).dp)
                        .padding(top = 0.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Text(
                        text = "${sortedApps.size} 件",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = displayMode == AppDisplayMode.List,
                            onClick = { displayMode = AppDisplayMode.List },
                            label = { Text("リスト") },
                        )
                        FilterChip(
                            selected = displayMode == AppDisplayMode.Grid,
                            onClick = { displayMode = AppDisplayMode.Grid },
                            label = { Text("アイコン") },
                        )
                        val sortLabel = when (effectiveSortMode) {
                            AppSortMode.Name -> "名前順"
                            AppSortMode.Recent -> "最近"
                            AppSortMode.Count -> "回数"
                        }
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Text("$sortLabel ▼")
                            }
                            // 通常モード (None) の変更だけ永続化する。
                            // 絞り込み起動中 (Simplified/Editing) の変更は一時的で、保存ソートを汚さない。
                            val persistSort = uiMode == ShortcutUiMode.None
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("名前順") },
                                    onClick = {
                                        sortMenuExpanded = false
                                        viewModel.setSortMode(AppSortMode.Name, persist = persistSort)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("最近起動") },
                                    enabled = uiState.usageStatsAccessGranted,
                                    onClick = {
                                        sortMenuExpanded = false
                                        viewModel.setSortMode(AppSortMode.Recent, persist = persistSort)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("起動回数") },
                                    enabled = uiState.usageStatsAccessGranted,
                                    onClick = {
                                        sortMenuExpanded = false
                                        viewModel.setSortMode(AppSortMode.Count, persist = persistSort)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "最近起動・起動回数は端末の使用履歴に基づきます。同じパッケージの複数アプリは同じ統計を共有します",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    enabled = false,
                                    onClick = {},
                                )
                            }
                        }
                        if (!simplified) {
                            Box {
                                IconButton(
                                    onClick = { appListMenuExpanded = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .semantics { contentDescription = "その他" },
                                ) {
                                    TopActionOverflowDots()
                                }
                                DropdownMenu(
                                    expanded = appListMenuExpanded,
                                    onDismissRequest = { appListMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (tagEditMode) "タグ編集を終了" else "タグ編集") },
                                        onClick = {
                                            appListMenuExpanded = false
                                            tagEditMode = !tagEditMode
                                            if (!tagEditMode) {
                                                tagViewModel.clearSelectedApp()
                                                selectedBulkApps = emptySet()
                                                bulkTargetTagId = null
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (tagState.multiSelectFilter) "複数選択をオフ" else "複数選択")
                                        },
                                        onClick = {
                                            appListMenuExpanded = false
                                            tagViewModel.toggleMultiSelectFilter()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (showTagManagement) "タグ管理を閉じる" else "タグ管理") },
                                        onClick = {
                                            appListMenuExpanded = false
                                            showTagManagement = !showTagManagement
                                        },
                                    )
                                    if (!uiState.usageStatsAccessGranted) {
                                        DropdownMenuItem(
                                            text = { Text("使用状況アクセス設定") },
                                            onClick = {
                                                appListMenuExpanded = false
                                                PerfLog.log("[USAGE] usage access settings opened")
                                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "最近起動・起動回数は端末の使用履歴に基づきます。同じパッケージの複数アプリは同じ統計を共有します",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        enabled = false,
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                }

                // タグ編集ON かつ簡素表示でないときだけ、一括付与/解除バーを表示する
                if (tagEditMode && !simplified) {
                    BulkTagBar(
                        tags = tagState.tags,
                        selectedCount = selectedBulkApps.size,
                        bulkTargetTagId = bulkTargetTagId,
                        message = tagState.message,
                        onPickTag = { bulkTargetTagId = it },
                        onAssign = {
                            val tagId = bulkTargetTagId
                            if (tagId != null) {
                                val targets = uiState.apps
                                    .filter { selectedBulkApps.contains("${it.packageName}/${it.className}") }
                                    .map { it.packageName to it.className }
                                tagViewModel.bulkAssignTag(targets, tagId)
                            }
                        },
                        onRemove = {
                            val tagId = bulkTargetTagId
                            if (tagId != null) {
                                val targets = uiState.apps
                                    .filter { selectedBulkApps.contains("${it.packageName}/${it.className}") }
                                    .map { it.packageName to it.className }
                                tagViewModel.bulkRemoveTag(targets, tagId)
                            }
                        },
                        // 「クリア」は選択アプリと対象タグの両方を解除する
                        onClearSelection = {
                            selectedBulkApps = emptySet()
                            bulkTargetTagId = null
                        },
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                when (displayMode) {
                    AppDisplayMode.List -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarPadding + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(
                                items = sortedApps,
                                key = { "${it.packageName}/${it.className}" },
                            ) { app ->
                                // このアプリに付与済みのタグ名 (名前順)。未付与なら空。
                                val tagNames = tagState.appTagMap["${app.packageName}/${app.className}"]
                                    ?.mapNotNull { tagNameById[it] }
                                    ?.sorted()
                                    ?: emptyList()
                                val key = "${app.packageName}/${app.className}"
                                AppRow(
                                    app = app,
                                    tagNames = tagNames,
                                    // 個別「タグ」ボタンは廃止 (タグ付けは一括バーへ一本化)
                                    showTagButton = false,
                                    selectionMode = tagEditMode,
                                    selected = selectedBulkApps.contains(key),
                                    // 編集ON時はタップで一括選択トグル、通常時は起動
                                    onClick = {
                                        if (tagEditMode) {
                                            selectedBulkApps = selectedBulkApps.toMutableSet()
                                                .apply { if (!add(key)) remove(key) }
                                        } else {
                                            PerfLog.log("[LM] app launch clicked")
                                            viewModel.launch(app)
                                        }
                                    },
                                    onTag = {},
                                )
                                HorizontalDivider()
                            }
                        }
                        ListScrollIndicator(listState)
                    }

                    AppDisplayMode.Grid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarPadding + 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItems(
                                items = sortedApps,
                                key = { "${it.packageName}/${it.className}" },
                            ) { app ->
                                val key = "${app.packageName}/${app.className}"
                                AppGridCell(
                                    app = app,
                                    // 個別「タグ」ボタンは廃止 (タグ付けは一括バーへ一本化)
                                    showTagButton = false,
                                    selected = tagEditMode && selectedBulkApps.contains(key),
                                    onClick = {
                                        if (tagEditMode) {
                                            selectedBulkApps = selectedBulkApps.toMutableSet()
                                                .apply { if (!add(key)) remove(key) }
                                        } else {
                                            PerfLog.log("[LM] app launch clicked")
                                            viewModel.launch(app)
                                        }
                                    },
                                    onTag = {},
                                )
                            }
                        }
                        GridScrollIndicator(gridState)
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun TagSection(
    state: com.iwadjp.pixeltagdrawer.ui.TagUiState,
    shortcutMessage: String?,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (com.iwadjp.pixeltagdrawer.data.db.TagEntity) -> Unit,
    onStartRename: (com.iwadjp.pixeltagdrawer.data.db.TagEntity) -> Unit,
    onMoveUp: (com.iwadjp.pixeltagdrawer.data.db.TagEntity) -> Unit,
    onMoveDown: (com.iwadjp.pixeltagdrawer.data.db.TagEntity) -> Unit,
    onEditingNameChange: (String) -> Unit,
    onEditingDisplayLabelChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onPinTag: (com.iwadjp.pixeltagdrawer.data.db.TagEntity) -> Unit,
    onPinUntagged: () -> Unit,
    onStartEditUntagged: () -> Unit,
    onEditingUntaggedLabelChange: (String) -> Unit,
    onConfirmUntaggedLabel: () -> Unit,
    onCancelEditUntaggedLabel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "タグ",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.tagName,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text("新しいタグ名") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onCreate) {
                Text("タグ追加")
            }
        }
        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // 「タグなし」で開くショートカットをホームに追加する導線
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ホーム画面に追加",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPinUntagged) {
                Text("タグなしを追加")
            }
        }
        shortcutMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 「タグなし」特別行 (DBのタグではない・チップ先頭固定)。表示名の編集だけできる。
        // 削除・正式名変更・ホーム・▲▼ は付けない (並び替え対象外)。
        if (state.editingUntagged) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = state.editingUntaggedLabel,
                    onValueChange = onEditingUntaggedLabelChange,
                    singleLine = true,
                    label = { Text("表示名 (空欄なら「タグなし」)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onConfirmUntaggedLabel) {
                        Text("保存")
                    }
                    TextButton(onClick = onCancelEditUntaggedLabel) {
                        Text("キャンセル")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "タグなし (先頭固定)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.untaggedDisplayLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        Text(
                            text = "表示: $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onStartEditUntagged) {
                    Text("変更")
                }
            }
        }
        if (state.tags.isEmpty()) {
            Text(
                text = "タグがありません",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // タグが多くても全タグへ到達できるよう、一覧部だけ高さ上限付きで縦スクロールにする。
            // タグ管理を開いている時は広めに使ってよい (常用時は閉じている)。
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            state.tags.forEachIndexed { index, tag ->
                if (state.editingTag?.tagId == tag.tagId) {
                    // 編集中: 正式タグ名 + チップ用表示名 (空欄なら name 表示) + 保存 / キャンセル
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = state.editingTagName,
                            onValueChange = onEditingNameChange,
                            singleLine = true,
                            label = { Text("タグ名") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.editingTagDisplayLabel,
                            onValueChange = onEditingDisplayLabelChange,
                            singleLine = true,
                            label = { Text("表示名 (空欄ならタグ名を表示)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onConfirmRename) {
                                Text("保存")
                            }
                            TextButton(onClick = onCancelRename) {
                                Text("キャンセル")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "# ${tag.name}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // 表示名が設定されているタグだけ、チップでの見え方を補助表示する
                            tag.displayLabel?.takeIf { it.isNotBlank() }?.let { label ->
                                Text(
                                    text = "表示: $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // ▲▼ で表示順を1つずつ移動する。先頭の▲・末尾の▼は無効。
                        IconButton(
                            onClick = { onMoveUp(tag) },
                            enabled = index > 0,
                            modifier = Modifier
                                .size(32.dp)
                                .semantics { contentDescription = "上へ移動" },
                        ) {
                            Text("▲", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = { onMoveDown(tag) },
                            enabled = index < state.tags.lastIndex,
                            modifier = Modifier
                                .size(32.dp)
                                .semantics { contentDescription = "下へ移動" },
                        ) {
                            Text("▼", style = MaterialTheme.typography.labelLarge)
                        }
                        // 控えめなテキストボタン。誤操作を避けるため小さめに留める
                        TextButton(onClick = { onPinTag(tag) }) {
                            Text("ホーム")
                        }
                        TextButton(onClick = { onStartRename(tag) }) {
                            Text("変更")
                        }
                        TextButton(onClick = { onDelete(tag) }) {
                            Text("削除")
                        }
                    }
                }
            }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

// 起動直後・スクロール中・停止直後の一定時間だけ表示するフェード付きアルファ値。
// 常時表示にはしない (FB2要件)。
@Composable
private fun rememberScrollIndicatorAlpha(isScrollInProgress: Boolean): Float {
    val visible = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1800)
        visible.value = false
    }
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            visible.value = true
        } else {
            delay(1800)
            visible.value = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible.value) 0.6f else 0f,
        label = "scrollIndicatorAlpha",
    )
    return alpha
}

@Composable
private fun BoxScope.ListScrollIndicator(state: LazyListState) {
    val layoutInfo = state.layoutInfo
    val totalCount = layoutInfo.totalItemsCount
    val visibleCount = layoutInfo.visibleItemsInfo.size
    if (totalCount == 0 || visibleCount >= totalCount) return
    val alpha = rememberScrollIndicatorAlpha(state.isScrollInProgress)
    if (alpha <= 0f) return
    val firstIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val fraction = firstIndex.toFloat() / (totalCount - visibleCount).coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / totalCount).coerceIn(0.08f, 1f)
    ScrollIndicatorThumb(fraction = fraction, thumbFraction = thumbFraction, alpha = alpha)
}

@Composable
private fun BoxScope.GridScrollIndicator(state: LazyGridState) {
    val layoutInfo = state.layoutInfo
    val totalCount = layoutInfo.totalItemsCount
    val visibleCount = layoutInfo.visibleItemsInfo.size
    if (totalCount == 0 || visibleCount >= totalCount) return
    val alpha = rememberScrollIndicatorAlpha(state.isScrollInProgress)
    if (alpha <= 0f) return
    val firstIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val fraction = firstIndex.toFloat() / (totalCount - visibleCount).coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / totalCount).coerceIn(0.08f, 1f)
    ScrollIndicatorThumb(fraction = fraction, thumbFraction = thumbFraction, alpha = alpha)
}

@Composable
private fun BoxScope.ScrollIndicatorThumb(fraction: Float, thumbFraction: Float, alpha: Float) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(4.dp)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(thumbFraction.coerceIn(0f, 1f))
                .fillMaxWidth()
                .align(BiasAlignment(0f, (fraction.coerceIn(0f, 1f) * 2f) - 1f))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
        )
    }
}

// 三点メニューの「⋯」。フォント字形依存で欠けて見える端末があるため、Textではなく自前で3点を描画する。
@Composable
private fun TopActionOverflowDots() {
    val dotColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(24.dp)) {
        val radius = 2.1.dp.toPx()
        val gap = 5.dp.toPx()
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        listOf(-gap, 0f, gap).forEach { dx ->
            drawCircle(color = dotColor, radius = radius, center = Offset(centerX + dx, centerY))
        }
    }
}

@Composable
private fun TagFilterSection(
    state: com.iwadjp.pixeltagdrawer.ui.TagUiState,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onToggleUntagged: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // tagId -> チップのコンテンツ座標での (左端x, 幅)。スクロール量に依存しない位置。
    val chipBounds = remember { mutableStateMapOf<Long, Pair<Float, Float>>() }
    // 単一タグ選択中だけ自動スクロール対象にする (複数選択・タグなし絞り込みは対象外)
    val autoScrollTargetId = if (!state.showUntaggedOnly) {
        state.selectedFilterTagIds.singleOrNull()
    } else {
        null
    }
    val targetBounds = autoScrollTargetId?.let { chipBounds[it] }
    // 選択タグが変わった時・チップ位置が確定した時に、可視範囲外なら見える位置へ寄せる。
    // すでに見えている場合はスクロールしない (手動スクロールを不要に動かさない)。
    LaunchedEffect(autoScrollTargetId, targetBounds) {
        if (autoScrollTargetId == null || targetBounds == null) return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val margin = with(density) { 16.dp.toPx() }
        val (chipLeft, chipWidth) = targetBounds
        val chipRight = chipLeft + chipWidth
        val visibleStart = scrollState.value.toFloat()
        val visibleEnd = visibleStart + viewport
        when {
            chipLeft < visibleStart + margin ->
                scrollState.animateScrollTo((chipLeft - margin).toInt().coerceAtLeast(0))
            chipRight > visibleEnd - margin ->
                scrollState.animateScrollTo((chipRight + margin - viewport).toInt().coerceAtLeast(0))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // タグが多くても固定エリアの高さを抑えるため、横スクロールのチップ列にする
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "タグ:",
                style = MaterialTheme.typography.labelMedium,
            )
            // 先頭に「タグなし」(未付与アプリのみ)。通常タグとは排他。
            // 表示名 (prefs) があればそれを表示し、未設定/空白なら既定の「タグなし」
            FilterChip(
                selected = state.showUntaggedOnly,
                onClick = onToggleUntagged,
                label = { Text(state.untaggedDisplayLabel?.takeIf { it.isNotBlank() } ?: "タグなし") },
            )
            state.tags.forEach { tag ->
                FilterChip(
                    selected = state.selectedFilterTagIds.contains(tag.tagId),
                    onClick = { onToggle(tag.tagId) },
                    // チップだけ短い表示名 (displayLabel) を優先し、未設定/空白なら正式タグ名
                    label = { Text(tagChipLabel(tag)) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        chipBounds[tag.tagId] =
                            coords.positionInParent().x to coords.size.width.toFloat()
                    },
                )
            }
            // いずれかの絞り込みが効いている時だけ、まとめて解除できるようにする
            if (state.selectedFilterTagIds.isNotEmpty() || state.showUntaggedOnly) {
                TextButton(onClick = onClear) {
                    Text("解除")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsDialog(
    report: String,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("起動計測 診断") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = report.ifEmpty { "(計測データなし)" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                if (copied) {
                    Text(
                        text = "診断情報をコピーしました",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCopy()
                copied = true
            }) {
                Text("コピー")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onRefresh()
                    copied = false
                }) {
                    Text("更新")
                }
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        },
    )
}

/**
 * UIモード。通常起動/ショートカット起動の表示文脈を一意に表す。
 * None: 通常モード (編集/一覧に戻るは出さない)。
 * Simplified: ショートカット簡素モード (編集のみ表示、編集系UIは隠す)。
 * Editing: ショートカット編集モード (一覧に戻るのみ表示、編集系UIを出す)。
 */
private enum class ShortcutUiMode { None, Simplified, Editing }

/** アプリ一覧の表示モード。 */
private enum class AppDisplayMode { List, Grid }

/** タグチップの表示ラベル。displayLabel が null/blank なら正式タグ名 name にフォールバックする。 */
private fun tagChipLabel(tag: com.iwadjp.pixeltagdrawer.data.db.TagEntity): String =
    tag.displayLabel?.takeIf { it.isNotBlank() } ?: tag.name

@Composable
private fun BulkTagBar(
    tags: List<com.iwadjp.pixeltagdrawer.data.db.TagEntity>,
    selectedCount: Int,
    bulkTargetTagId: Long?,
    message: String?,
    onPickTag: (Long) -> Unit,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-8).dp)
            .padding(top = 0.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (tags.isEmpty()) {
            // タグ未作成時は1行のみ。作成導線はタグ管理に委ねる
            Text(
                text = "一括: $selectedCount 件 (タグ管理でタグを作成)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 1行目: 件数 + 対象タグ選択チップ (チップ列だけ横スクロールで見切れを防ぐ)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "一括: $selectedCount 件",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = bulkTargetTagId == tag.tagId,
                            onClick = { onPickTag(tag.tagId) },
                            label = { Text(tagChipLabel(tag)) },
                        )
                    }
                }
            }
            // 2行目: 短い操作ボタン。横が足りなくても見切れないようスクロール可能にする
            val canApply = selectedCount > 0 && bulkTargetTagId != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactBulkAction(text = "付与", enabled = canApply, onClick = onAssign)
                CompactBulkAction(text = "解除", enabled = canApply, onClick = onRemove)
                CompactBulkAction(
                    text = "クリア",
                    enabled = selectedCount > 0,
                    onClick = onClearSelection,
                )
            }
        }
        // 一括操作の結果など短いメッセージ (出るときだけの1行)
        message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun CompactBulkAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun AppGridCell(
    app: LauncherApp,
    showTagButton: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onTag: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // 一括選択中のセルは背景でハイライトする
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                else Modifier,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(app)
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        // タグ編集モード時だけ、セル内の最小タグ導線を出す
        if (showTagButton) {
            TextButton(
                onClick = onTag,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(text = "タグ", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AppRow(
    app: LauncherApp,
    tagNames: List<String>,
    showTagButton: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onTag: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 一括選択中は先頭に選択状態のチェックを表示 (タップ判定は行全体が担う)
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        AppIcon(app)
        Column(
            // 行タップ起動を保ちつつ、タグボタンを右端へ寄せる
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 付与済みタグがあるときだけ控えめに表示する (例: #Google #仕事)
            if (tagNames.isNotEmpty()) {
                Text(
                    text = tagNames.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // タグ編集モード時だけ、行タップ(起動)とは別のタグ導線を出す
        if (showTagButton) {
            TextButton(onClick = onTag) {
                Text("タグ")
            }
        }
    }
}

@Composable
private fun SelectedAppTagPanel(
    state: com.iwadjp.pixeltagdrawer.ui.TagUiState,
    onToggle: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val app = state.selectedApp ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "「${app.label}」のタグ",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            // タグ付与/解除に限定した Undo/Redo。可否に応じて有効化する
            TextButton(onClick = onUndo, enabled = state.canUndo) {
                Text("元に戻す")
            }
            TextButton(onClick = onRedo, enabled = state.canRedo) {
                Text("やり直す")
            }
            TextButton(onClick = onClose) {
                Text("閉じる")
            }
        }
        // Undo/Redo の結果など短いメッセージを表示する
        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.tags.isEmpty()) {
            Text(
                text = "タグがありません",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.tags.forEach { tag ->
                val checked = state.selectedAppTagIds.contains(tag.tagId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(tag.tagId, !checked) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(tag.tagId, it) },
                    )
                    Text(
                        text = "# ${tag.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AppIcon(app: LauncherApp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
    } else {
        // アイコン取得失敗時のプレースホルダー: ラベル先頭1文字
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
