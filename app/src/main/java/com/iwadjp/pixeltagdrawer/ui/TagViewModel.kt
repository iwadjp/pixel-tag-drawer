package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iwadjp.pixeltagdrawer.PerfLog
import com.iwadjp.pixeltagdrawer.R
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.data.TagRepository
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import com.iwadjp.pixeltagdrawer.model.LauncherApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * タグ作成・一覧の最小ViewModel。
 * TagRepository を介して observeTags / createTag / deleteTag を扱う。
 * 割り当て・絞り込みは後続。
 */
class TagViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TagRepository(application)
    private val prefs = AppPreferences(application)

    /** ViewModelはComposableではないため、stringResourceではなくContext.getStringで解決する。 */
    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    // 前回の絞り込み選択を復元する。削除済みIDは observeTags の intersect で除外される。
    private val _uiState = MutableStateFlow(buildInitialFilterState())
    val uiState: StateFlow<TagUiState> = _uiState.asStateFlow()

    /**
     * 復元時の初期フィルタ状態。単一選択モードで複数IDが保存されていたら先頭1つに単一化し、
     * UI破綻を防ぐ (単一化した場合は保存も更新する)。
     */
    private fun buildInitialFilterState(): TagUiState {
        val multi = prefs.multiSelectFilter
        val saved = prefs.loadFilterTagIds()
        val ids = if (!multi && saved.size > 1) setOf(saved.first()) else saved
        if (ids != saved) prefs.saveFilterTagIds(ids)
        return TagUiState(
            selectedFilterTagIds = ids,
            showUntaggedOnly = prefs.showUntaggedOnly,
            multiSelectFilter = multi,
            untaggedDisplayLabel = prefs.untaggedDisplayLabel,
        )
    }

    // 選択中アプリの付与済みタグID購読。選択切替時に張り替える。
    private var selectedAppTagJob: Job? = null

    // タグ付与/解除の Undo/Redo 履歴 (メモリ上のみ・永続化しない)。
    private val undoStack = ArrayDeque<TagEditAction>()
    private val redoStack = ArrayDeque<TagEditAction>()

    init {
        PerfLog.log("TagViewModel init")
        var tagsFirst = true
        var appTagsFirst = true
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
                if (tagsFirst) {
                    tagsFirst = false
                    PerfLog.log("tags first emission count=${tags.size}")
                }
                // タグが削除されても絞り込み選択が宙に浮かないよう、存在するIDだけ残す
                val validIds = tags.mapTo(mutableSetOf()) { it.tagId }
                _uiState.update {
                    it.copy(
                        tags = tags,
                        selectedFilterTagIds = it.selectedFilterTagIds.intersect(validIds),
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeAllAppTags().collect { refs ->
                if (appTagsFirst) {
                    appTagsFirst = false
                    PerfLog.log("app-tag map first emission rows=${refs.size}")
                }
                // "packageName/className" -> 付与済み tagId 集合
                val map = refs
                    .groupBy({ "${it.packageName}/${it.className}" }, { it.tagId })
                    .mapValues { (_, ids) -> ids.toSet() }
                _uiState.update { it.copy(appTagMap = map) }
            }
        }
    }

    /** 入力中のタグ名を更新する。 */
    fun updateTagName(name: String) {
        _uiState.update { it.copy(tagName = name, message = null) }
    }

    /**
     * 入力中の名前でタグを作成する。
     * trim・空文字スキップ・重複IGNORE は TagRepository.createTag が担保。
     * 成功時は入力欄をクリアし、未挿入時は短いメッセージを出す。
     */
    fun createTag() {
        val name = _uiState.value.tagName
        viewModelScope.launch {
            try {
                val tagId = repository.createTag(name)
                _uiState.update {
                    when {
                        tagId >= 0 -> it.copy(tagName = "", message = null)
                        name.trim().isEmpty() -> it.copy(message = string(R.string.tag_name_required))
                        else -> it.copy(message = string(R.string.tag_name_duplicate))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "タグ作成に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.tag_create_failed)) }
            }
        }
    }

    /**
     * アプリをタグ割り当て対象として選択する。
     * 選択中アプリの付与済みタグIDを observeTagIdsForApp で購読し直す。
     */
    fun selectAppForTagging(app: LauncherApp) {
        _uiState.update { it.copy(selectedApp = app, selectedAppTagIds = emptySet()) }
        selectedAppTagJob?.cancel()
        selectedAppTagJob = viewModelScope.launch {
            repository.observeTagIdsForApp(app.packageName, app.className).collect { ids ->
                _uiState.update { it.copy(selectedAppTagIds = ids.toSet()) }
            }
        }
    }

    /** タグ割り当て対象の選択を解除する。 */
    fun clearSelectedApp() {
        selectedAppTagJob?.cancel()
        selectedAppTagJob = null
        _uiState.update { it.copy(selectedApp = null, selectedAppTagIds = emptySet()) }
    }

    /**
     * 選択中アプリに対してタグのON/OFFを切り替える。
     * ON で assignTag、OFF で removeTag。結果は observeTagIdsForApp 経由で反映。
     * 成功時は Undo 履歴に積み、Redo 履歴をクリアする。
     */
    fun setTagForSelectedApp(tagId: Long, checked: Boolean) {
        val app = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            try {
                applyTagEdit(app.packageName, app.className, tagId, assign = checked)
                undoStack.addLast(TagEditAction(app.packageName, app.className, tagId, assigned = checked))
                redoStack.clear()
                refreshUndoRedoFlags(message = null)
            } catch (e: Exception) {
                Log.w(TAG, "タグ割り当ての更新に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.tag_update_failed)) }
            }
        }
    }

    /** 直前のタグ付与/解除を元に戻す (逆操作を実行)。 */
    fun undoLastTagEdit() {
        val action = undoStack.lastOrNull() ?: return
        viewModelScope.launch {
            try {
                // assigned だった操作は解除、解除だった操作は付与で打ち消す
                applyTagEdit(action.packageName, action.className, action.tagId, assign = !action.assigned)
                undoStack.removeLast()
                redoStack.addLast(action)
                refreshUndoRedoFlags(message = string(R.string.tag_edit_undone))
            } catch (e: Exception) {
                Log.w(TAG, "Undo に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.tag_undo_failed)) }
            }
        }
    }

    /** Undo したタグ付与/解除をやり直す (元操作を再実行)。 */
    fun redoLastTagEdit() {
        val action = redoStack.lastOrNull() ?: return
        viewModelScope.launch {
            try {
                applyTagEdit(action.packageName, action.className, action.tagId, assign = action.assigned)
                redoStack.removeLast()
                undoStack.addLast(action)
                refreshUndoRedoFlags(message = string(R.string.tag_edit_redone))
            } catch (e: Exception) {
                Log.w(TAG, "Redo に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.tag_redo_failed)) }
            }
        }
    }

    /**
     * 選択中の複数アプリへ1つのタグを一括付与する。
     * 重複付与は IGNORE のため安全。一括操作は Undo/Redo 履歴に積まない。
     */
    fun bulkAssignTag(targets: List<Pair<String, String>>, tagId: Long) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            try {
                targets.forEach { (packageName, className) ->
                    repository.assignTag(packageName, className, tagId)
                }
                _uiState.update { it.copy(message = string(R.string.bulk_assign_success_format, targets.size)) }
            } catch (e: Exception) {
                Log.w(TAG, "一括付与に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.bulk_assign_failed)) }
            }
        }
    }

    /**
     * 選択中の複数アプリから1つのタグを一括解除する。
     * 付与されていないアプリがあってもクラッシュしない。一括操作は Undo/Redo 履歴に積まない。
     */
    fun bulkRemoveTag(targets: List<Pair<String, String>>, tagId: Long) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            try {
                targets.forEach { (packageName, className) ->
                    repository.removeTag(packageName, className, tagId)
                }
                _uiState.update { it.copy(message = string(R.string.bulk_remove_success_format, targets.size)) }
            } catch (e: Exception) {
                Log.w(TAG, "一括解除に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.bulk_remove_failed)) }
            }
        }
    }

    /** assign=true で付与、false で解除。重複/不在は DAO 側 IGNORE のため安全。 */
    private suspend fun applyTagEdit(packageName: String, className: String, tagId: Long, assign: Boolean) {
        if (assign) {
            repository.assignTag(packageName, className, tagId)
        } else {
            repository.removeTag(packageName, className, tagId)
        }
    }

    /** Undo/Redo 可否を反映する。message=null でメッセージをクリアする。 */
    private fun refreshUndoRedoFlags(message: String?) {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                message = message,
            )
        }
    }

    /**
     * 一覧絞り込みタグのクリック操作。
     * 複数選択モードON: 従来どおり ON/OFF トグル (AND条件)。
     * 複数選択モードOFF (既定): 単一選択切替 — 別タグで切替、同じタグ再クリックで全解除。
     * いずれも「タグなし」絞り込みは排他で OFF にする。
     */
    fun toggleFilterTag(tagId: Long) {
        _uiState.update {
            val next = if (it.multiSelectFilter) {
                it.selectedFilterTagIds.toMutableSet().apply { if (!add(tagId)) remove(tagId) }
            } else {
                if (it.selectedFilterTagIds == setOf(tagId)) emptySet() else setOf(tagId)
            }
            it.copy(selectedFilterTagIds = next, showUntaggedOnly = false)
        }
        prefs.saveFilterTagIds(_uiState.value.selectedFilterTagIds)
        prefs.showUntaggedOnly = false
    }

    /**
     * 単一選択中のタグを別タグへ「置き換える」(前/次ボタンによる前後遷移用)。
     * toggleFilterTag と異なり、同じ tagId を渡してもトグルオフせず単一選択として保持する。
     */
    fun selectSingleFilterTag(tagId: Long) {
        _uiState.update { it.copy(selectedFilterTagIds = setOf(tagId), showUntaggedOnly = false) }
        prefs.saveFilterTagIds(_uiState.value.selectedFilterTagIds)
        prefs.showUntaggedOnly = false
    }

    /** 複数タグAND絞り込みモードのON/OFFを切り替える。 */
    fun toggleMultiSelectFilter() {
        setMultiSelectFilter(!_uiState.value.multiSelectFilter)
    }

    /**
     * 複数選択モードを設定する。
     * OFF にした時に複数タグが選ばれていたら、分かりやすさ優先で全解除する。
     */
    fun setMultiSelectFilter(enabled: Boolean) {
        _uiState.update {
            val ids = if (!enabled && it.selectedFilterTagIds.size > 1) {
                emptySet()
            } else {
                it.selectedFilterTagIds
            }
            it.copy(multiSelectFilter = enabled, selectedFilterTagIds = ids)
        }
        prefs.multiSelectFilter = enabled
        prefs.saveFilterTagIds(_uiState.value.selectedFilterTagIds)
    }

    /** 一覧絞り込み (通常タグ・タグなし) をすべて解除する。 */
    fun clearFilterTags() {
        _uiState.update { it.copy(selectedFilterTagIds = emptySet(), showUntaggedOnly = false) }
        prefs.saveFilterTagIds(emptySet())
        prefs.showUntaggedOnly = false
    }

    /** 「タグなし」絞り込みのON/OFFを切り替える。 */
    fun toggleUntaggedFilter() {
        setUntaggedFilter(!_uiState.value.showUntaggedOnly)
    }

    /**
     * 「タグなし」絞り込みを設定する。
     * ON にすると通常タグ選択は排他でクリアする。
     */
    fun setUntaggedFilter(enabled: Boolean) {
        _uiState.update {
            it.copy(
                showUntaggedOnly = enabled,
                selectedFilterTagIds = if (enabled) emptySet() else it.selectedFilterTagIds,
            )
        }
        prefs.showUntaggedOnly = enabled
        if (enabled) prefs.saveFilterTagIds(emptySet())
    }

    /**
     * ショートカット起動指定の単一タグで絞り込む (ショートカット由来・非永続)。
     * 通常モードの手動フィルタ prefs は上書きしない (通常アイコン再起動で手動フィルタを失わないため)。
     * 存在しない tagId は observeTags の intersect で除外され、フィルタなしに戻る (クラッシュしない)。
     */
    fun applyShortcutFilterTag(tagId: Long) {
        PerfLog.log("VM applyShortcutFilterTag tagId=$tagId (transient)")
        _uiState.update { it.copy(selectedFilterTagIds = setOf(tagId), showUntaggedOnly = false) }
    }

    /** ショートカット起動指定で「タグなし」絞り込みを適用する (ショートカット由来・非永続)。 */
    fun applyShortcutUntaggedFilter() {
        PerfLog.log("VM applyShortcutUntaggedFilter (transient)")
        _uiState.update { it.copy(showUntaggedOnly = true, selectedFilterTagIds = emptySet()) }
    }

    /**
     * 通常アイコン起動時に、保存済みの手動フィルタ (prefs) を state へ復元する。
     * ショートカット由来の一時フィルタ (非永続) を上書きして消し、通常モードの手動フィルタだけを残す。
     * prefs は変更しない (手動フィルタの永続値を保持する)。復元ロジックは buildInitialFilterState と同等。
     */
    fun restoreManualFilters() {
        val multi = prefs.multiSelectFilter
        val saved = prefs.loadFilterTagIds()
        val ids = if (!multi && saved.size > 1) setOf(saved.first()) else saved
        PerfLog.log("VM restoreManualFilters tags=${ids.size} untagged=${prefs.showUntaggedOnly}")
        _uiState.update {
            it.copy(
                selectedFilterTagIds = ids,
                showUntaggedOnly = prefs.showUntaggedOnly,
                multiSelectFilter = multi,
            )
        }
    }

    /** タグの編集 (名前・表示名) を開始する。既存値を入力欄に入れる。「タグなし」編集は閉じる。 */
    fun startRenameTag(tag: TagEntity) {
        _uiState.update {
            it.copy(
                editingTag = tag,
                editingTagName = tag.name,
                editingTagDisplayLabel = tag.displayLabel.orEmpty(),
                editingUntagged = false,
                editingUntaggedLabel = "",
                message = null,
            )
        }
    }

    /** 「タグなし」の表示名編集を開始する。既存値を入力欄に入れる。通常タグの編集は閉じる。 */
    fun startEditUntaggedLabel() {
        _uiState.update {
            it.copy(
                editingUntagged = true,
                editingUntaggedLabel = it.untaggedDisplayLabel.orEmpty(),
                editingTag = null,
                editingTagName = "",
                editingTagDisplayLabel = "",
                message = null,
            )
        }
    }

    /** 編集中の「タグなし」表示名の入力値を更新する。 */
    fun updateEditingUntaggedLabel(label: String) {
        _uiState.update { it.copy(editingUntaggedLabel = label) }
    }

    /** 「タグなし」表示名の編集をキャンセルする。 */
    fun cancelEditUntaggedLabel() {
        _uiState.update { it.copy(editingUntagged = false, editingUntaggedLabel = "", message = null) }
    }

    /**
     * 「タグなし」表示名を確定する。保存先は prefs (DBには入れない)。
     * trim・「blank なら null (=既定の「タグなし」表示)」の正規化は AppPreferences 側が担保する。
     */
    fun confirmUntaggedLabel() {
        prefs.untaggedDisplayLabel = _uiState.value.editingUntaggedLabel
        _uiState.update {
            it.copy(
                untaggedDisplayLabel = prefs.untaggedDisplayLabel,
                editingUntagged = false,
                editingUntaggedLabel = "",
                message = null,
            )
        }
    }

    /** 名前変更中の入力値を更新する。 */
    fun updateEditingTagName(name: String) {
        _uiState.update { it.copy(editingTagName = name) }
    }

    /** 編集中の表示名 (チップ用) の入力値を更新する。 */
    fun updateEditingTagDisplayLabel(label: String) {
        _uiState.update { it.copy(editingTagDisplayLabel = label) }
    }

    /** 名前変更をキャンセルする。 */
    fun cancelRenameTag() {
        _uiState.update {
            it.copy(editingTag = null, editingTagName = "", editingTagDisplayLabel = "", message = null)
        }
    }

    /**
     * 編集 (名前・表示名) を確定する。
     * trim・空文字スキップ・重複IGNORE は TagRepository.renameTag が担保。
     * 表示名は blank なら null 保存 (=チップは name 表示) を TagRepository.updateDisplayLabel が担保。
     * 名前が重複で保存できなかった場合は表示名も保存せず、編集状態を維持する。
     */
    fun confirmRenameTag() {
        val tag = _uiState.value.editingTag ?: return
        val name = _uiState.value.editingTagName
        val displayLabel = _uiState.value.editingTagDisplayLabel
        viewModelScope.launch {
            try {
                if (name.trim().isEmpty()) {
                    _uiState.update { it.copy(message = string(R.string.tag_name_required)) }
                    return@launch
                }
                val rows = repository.renameTag(tag.tagId, name)
                if (rows > 0) {
                    repository.updateDisplayLabel(tag.tagId, displayLabel)
                }
                _uiState.update {
                    if (rows > 0) {
                        it.copy(editingTag = null, editingTagName = "", editingTagDisplayLabel = "", message = null)
                    } else {
                        it.copy(message = string(R.string.tag_name_duplicate))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "タグの編集に失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.tag_rename_failed)) }
            }
        }
    }

    /** タグを表示順で1つ上へ移動する (先頭なら何もしない)。 */
    fun moveTagUp(tag: TagEntity) = moveTag(tag, -1)

    /** タグを表示順で1つ下へ移動する (末尾なら何もしない)。 */
    fun moveTagDown(tag: TagEntity) = moveTag(tag, +1)

    /**
     * 表示順 (uiState.tags の並び) で隣のタグと sortOrder を交換する。
     * 更新後の並びは observeTags 経由でUIへ自然反映される。
     */
    private fun moveTag(tag: TagEntity, delta: Int) {
        val tags = _uiState.value.tags
        val index = tags.indexOfFirst { it.tagId == tag.tagId }
        if (index < 0) return
        val neighbor = tags.getOrNull(index + delta) ?: return
        viewModelScope.launch {
            try {
                repository.swapSortOrder(tags[index], neighbor)
            } catch (e: Exception) {
                Log.w(TAG, "タグの並び替えに失敗しました", e)
                _uiState.update { it.copy(message = string(R.string.tag_reorder_failed)) }
            }
        }
    }

    /** タグを削除する (UIは任意)。 */
    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            try {
                repository.deleteTag(tag)
            } catch (e: Exception) {
                Log.w(TAG, "タグ削除に失敗しました", e)
            }
        }
    }

    /**
     * Undo/Redo 対象のタグ編集操作。
     * assigned=true は「付与した操作」(Undo=解除)、false は「解除した操作」(Undo=付与)。
     */
    private data class TagEditAction(
        val packageName: String,
        val className: String,
        val tagId: Long,
        val assigned: Boolean,
    )

    private companion object {
        const val TAG = "TagViewModel"
    }
}
