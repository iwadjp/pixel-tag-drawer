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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.model.LauncherApp
import com.iwadjp.pixeltagdrawer.ui.AppListViewModel
import com.iwadjp.pixeltagdrawer.ui.TagViewModel

class MainActivity : ComponentActivity() {

    // 起動Intentで指定されたタグフィルタ。onCreate / onNewIntent で更新する。
    private val launchFilter = mutableStateOf<LaunchFilter?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerfLog.start()
        PerfLog.log("MainActivity.onCreate start")
        launchFilter.value = parseLaunchFilter(intent)
        PerfLog.log("setContent start (launchFilter=${formatLaunchFilter(launchFilter.value)})")
        setContent {
            PixelTagDrawerApp(launchFilter = launchFilter.value)
        }
    }

    // 既に起動中のインスタンスへ Intent が届いた場合にも反映する
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchFilter.value = parseLaunchFilter(intent)
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

private const val TAG_SHORTCUT = "PinShortcut"

/**
 * 指定タグで開く Pinned Shortcut の作成をリクエストする。
 * res に launcher icon 資源が無いため、コードで生成した Bitmap アイコンを必ず設定する。
 * 非対応ランチャーや失敗時はクラッシュせず false を返す。
 */
private fun requestPinTagShortcut(context: Context, tagId: Long, tagName: String): Boolean {
    val intent = Intent(context, MainActivity::class.java).apply {
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
    val intent = Intent(context, MainActivity::class.java).apply {
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
fun PixelTagDrawerApp(launchFilter: LaunchFilter? = null) {
    val context = LocalContext.current
    val colorScheme = dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppListScreen(launchFilter = launchFilter)
        }
    }
}

@Composable
fun AppListScreen(
    viewModel: AppListViewModel = viewModel(),
    tagViewModel: TagViewModel = viewModel(),
    launchFilter: LaunchFilter? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tagState by tagViewModel.uiState.collectAsStateWithLifecycle()

    // 表示状態の最小永続化。前回の表示モード / タグ管理の開閉を復元する。
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context) }

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

    // ショートカット/外部フィルタ起動かどうか。「一覧に戻る」導線の表示判定に使う (セッション内固定)。
    var launchedViaShortcut by remember { mutableStateOf(launchFilter != null) }
    // 簡素表示中か。ショートカット起動時は初期ON。「編集」/「一覧に戻る」で切り替える。
    var simplifiedView by remember { mutableStateOf(launchFilter != null) }

    // 起動Intentで指定されたタグフィルタを適用し、ショートカット起動時は編集系を畳む。
    // これは保存済みフィルタの復元より優先される。
    LaunchedEffect(launchFilter) {
        when (launchFilter) {
            is LaunchFilter.Tag -> tagViewModel.applyLaunchFilterTag(launchFilter.tagId)
            LaunchFilter.Untagged -> tagViewModel.applyLaunchUntaggedFilter()
            null -> Unit
        }
        launchedViaShortcut = launchFilter != null
        simplifiedView = launchFilter != null
        PerfLog.log("launch filter applied (filter=${formatLaunchFilter(launchFilter)}, simplified=$simplifiedView)")
        if (launchFilter != null) {
            // ショートカット起動時は検索 + 絞り込み一覧中心にし、編集系の残留状態をクリア
            tagEditMode = false
            selectedBulkApps = emptySet()
            bulkTargetTagId = null
            tagViewModel.clearSelectedApp()
        }
    }

    // 一覧の最終要素がナビゲーションバーに隠れないよう、その分を一覧下端の余白に加える。
    // 固定エリアには付けず、スクロール領域 (List/Grid) の contentPadding だけに効かせる。
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
            // 簡素表示中は「編集」で従来UIへ。ショートカット起動時の通常UIには「一覧に戻る」を出す。
            if (simplifiedView) {
                TextButton(onClick = { simplifiedView = false }) {
                    Text("編集")
                }
            } else {
                if (launchedViaShortcut) {
                    TextButton(onClick = {
                        // 簡素表示へ戻す。絞り込み条件は維持し、編集系の状態だけ畳む。
                        simplifiedView = true
                        showTagManagement = false
                        tagEditMode = false
                        selectedBulkApps = emptySet()
                        bulkTargetTagId = null
                        tagViewModel.clearSelectedApp()
                    }) {
                        Text("一覧に戻る")
                    }
                }
                TextButton(onClick = { showTagManagement = !showTagManagement }) {
                    Text(if (showTagManagement) "閉じる" else "タグ管理")
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
        if (showTagManagement && !simplifiedView) {
            TagSection(
                state = tagState,
                shortcutMessage = shortcutMessage,
                onNameChange = tagViewModel::updateTagName,
                onCreate = tagViewModel::createTag,
                onDelete = tagViewModel::deleteTag,
                onStartRename = tagViewModel::startRenameTag,
                onEditingNameChange = tagViewModel::updateEditingTagName,
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

        // 検索欄はアプリが読み込まれているときだけ表示する
        if (uiState.apps.isNotEmpty()) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }

        // 絞り込みチップはタグがあり、かつショートカット簡素表示でないときだけ表示する
        if (tagState.tags.isNotEmpty() && !simplifiedView) {
            TagFilterSection(
                state = tagState,
                onToggle = tagViewModel::toggleFilterTag,
                onClear = tagViewModel::clearFilterTags,
                onToggleUntagged = tagViewModel::toggleUntaggedFilter,
                onToggleMultiSelect = tagViewModel::toggleMultiSelectFilter,
            )
        }

        // 検索 (名前/パッケージ) で絞った結果に、タグ条件をANDで合成する。
        // 「タグなし」は未付与アプリのみ。通常タグ選択時は全タグを持つアプリのみ。両者は排他。
        val filteredApps = remember(
            uiState.filteredApps,
            tagState.selectedFilterTagIds,
            tagState.showUntaggedOnly,
            tagState.appTagMap,
        ) {
            val selected = tagState.selectedFilterTagIds
            when {
                tagState.showUntaggedOnly -> uiState.filteredApps.filter { app ->
                    tagState.appTagMap["${app.packageName}/${app.className}"].isNullOrEmpty()
                }
                selected.isEmpty() -> uiState.filteredApps
                else -> uiState.filteredApps.filter { app ->
                    val appTags = tagState.appTagMap["${app.packageName}/${app.className}"]
                        ?: emptySet()
                    appTags.containsAll(selected)
                }
            }
        }

        // tagId -> タグ名。アプリ行に付与済みタグ名を表示するために使う。
        val tagNameById = remember(tagState.tags) {
            tagState.tags.associate { it.tagId to it.name }
        }

        // 調査用: アプリ読込完了後の初回 filteredApps を1回だけ計測する。
        // 0件 (例: タグなし絞り込みで該当なし) でも記録できるよう、空判定ではなく apps 読込で判定する。
        val perfFirstListLogged = remember { mutableStateOf(false) }
        LaunchedEffect(uiState.apps.isNotEmpty(), filteredApps) {
            if (!perfFirstListLogged.value && uiState.apps.isNotEmpty()) {
                perfFirstListLogged.value = true
                PerfLog.log("first visible filtered list count=${filteredApps.size}")
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

            filteredApps.isEmpty() -> {
                Text(
                    text = "一致するアプリがありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            else -> {
                // 件数と表示モード切替は固定エリアに残し、一覧だけをスクロールさせる
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${filteredApps.size} 件",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        // タグ編集モード切替。簡素表示中は出さない。OFF で一括選択も閉じる
                        if (!simplifiedView) {
                            FilterChip(
                                selected = tagEditMode,
                                onClick = {
                                    tagEditMode = !tagEditMode
                                    if (!tagEditMode) {
                                        tagViewModel.clearSelectedApp()
                                        selectedBulkApps = emptySet()
                                        bulkTargetTagId = null
                                    }
                                },
                                label = { Text("タグ編集") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                }

                // タグ編集ON かつ簡素表示でないときだけ、一括付与/解除バーを表示する
                if (tagEditMode && !simplifiedView) {
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

                when (displayMode) {
                    AppDisplayMode.List -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarPadding + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(
                                items = filteredApps,
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
                                            viewModel.launch(app)
                                        }
                                    },
                                    onTag = {},
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    AppDisplayMode.Grid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = navBarPadding + 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItems(
                                items = filteredApps,
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
                                            viewModel.launch(app)
                                        }
                                    },
                                    onTag = {},
                                )
                            }
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
    onEditingNameChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onPinTag: (com.iwadjp.pixeltagdrawer.data.db.TagEntity) -> Unit,
    onPinUntagged: () -> Unit,
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
            state.tags.forEach { tag ->
                if (state.editingTag?.tagId == tag.tagId) {
                    // 編集中: 名前入力欄 + 保存 / キャンセル
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.editingTagName,
                            onValueChange = onEditingNameChange,
                            singleLine = true,
                            label = { Text("タグ名") },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onConfirmRename) {
                            Text("保存")
                        }
                        TextButton(onClick = onCancelRename) {
                            Text("キャンセル")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "# ${tag.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
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

@Composable
private fun TagFilterSection(
    state: com.iwadjp.pixeltagdrawer.ui.TagUiState,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onToggleUntagged: () -> Unit,
    onToggleMultiSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "タグで絞り込み",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            // 複数選択ON で複数タグAND。OFF(既定)はクリックで単一切替。
            FilterChip(
                selected = state.multiSelectFilter,
                onClick = onToggleMultiSelect,
                label = { Text("複数選択") },
            )
            // いずれかの絞り込みが効いている時だけ、まとめて解除できるようにする
            if (state.selectedFilterTagIds.isNotEmpty() || state.showUntaggedOnly) {
                TextButton(onClick = onClear) {
                    Text("解除")
                }
            }
        }
        // タグが多くても固定エリアの高さを抑えるため、横スクロールのチップ列にする
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 先頭に「タグなし」(未付与アプリのみ)。通常タグとは排他
            FilterChip(
                selected = state.showUntaggedOnly,
                onClick = onToggleUntagged,
                label = { Text("タグなし") },
            )
            state.tags.forEach { tag ->
                FilterChip(
                    selected = state.selectedFilterTagIds.contains(tag.tagId),
                    onClick = { onToggle(tag.tagId) },
                    label = { Text(tag.name) },
                )
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

/** アプリ一覧の表示モード。 */
private enum class AppDisplayMode { List, Grid }

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
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                            label = { Text(tag.name) },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onAssign, enabled = canApply) {
                    Text("付与")
                }
                TextButton(onClick = onRemove, enabled = canApply) {
                    Text("解除")
                }
                TextButton(onClick = onClearSelection, enabled = selectedCount > 0) {
                    Text("クリア")
                }
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
