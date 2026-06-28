package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // 前回の絞り込み選択を復元する。削除済みIDは observeTags の intersect で除外される。
    private val _uiState = MutableStateFlow(TagUiState(selectedFilterTagIds = prefs.loadFilterTagIds()))
    val uiState: StateFlow<TagUiState> = _uiState.asStateFlow()

    // 選択中アプリの付与済みタグID購読。選択切替時に張り替える。
    private var selectedAppTagJob: Job? = null

    // タグ付与/解除の Undo/Redo 履歴 (メモリ上のみ・永続化しない)。
    private val undoStack = ArrayDeque<TagEditAction>()
    private val redoStack = ArrayDeque<TagEditAction>()

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
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
                        name.trim().isEmpty() -> it.copy(message = "タグ名を入力してください")
                        else -> it.copy(message = "同名のタグが既にあります")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "タグ作成に失敗しました", e)
                _uiState.update { it.copy(message = "タグの作成に失敗しました") }
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
                _uiState.update { it.copy(message = "タグの更新に失敗しました") }
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
                refreshUndoRedoFlags(message = "タグ操作を元に戻しました")
            } catch (e: Exception) {
                Log.w(TAG, "Undo に失敗しました", e)
                _uiState.update { it.copy(message = "元に戻せませんでした") }
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
                refreshUndoRedoFlags(message = "タグ操作をやり直しました")
            } catch (e: Exception) {
                Log.w(TAG, "Redo に失敗しました", e)
                _uiState.update { it.copy(message = "やり直せませんでした") }
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
                _uiState.update { it.copy(message = "${targets.size}件に一括付与しました") }
            } catch (e: Exception) {
                Log.w(TAG, "一括付与に失敗しました", e)
                _uiState.update { it.copy(message = "一括付与に失敗しました") }
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
                _uiState.update { it.copy(message = "${targets.size}件から一括解除しました") }
            } catch (e: Exception) {
                Log.w(TAG, "一括解除に失敗しました", e)
                _uiState.update { it.copy(message = "一括解除に失敗しました") }
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

    /** 一覧絞り込みタグのON/OFFを切り替える。複数選択時はAND条件で扱う。 */
    fun toggleFilterTag(tagId: Long) {
        _uiState.update {
            val next = it.selectedFilterTagIds.toMutableSet()
            if (!next.add(tagId)) next.remove(tagId)
            it.copy(selectedFilterTagIds = next)
        }
        prefs.saveFilterTagIds(_uiState.value.selectedFilterTagIds)
    }

    /** 一覧絞り込みタグの選択をすべて解除する。 */
    fun clearFilterTags() {
        _uiState.update { it.copy(selectedFilterTagIds = emptySet()) }
        prefs.saveFilterTagIds(emptySet())
    }

    /** タグの名前変更を開始する。既存名を入力欄に入れる。 */
    fun startRenameTag(tag: TagEntity) {
        _uiState.update { it.copy(editingTag = tag, editingTagName = tag.name, message = null) }
    }

    /** 名前変更中の入力値を更新する。 */
    fun updateEditingTagName(name: String) {
        _uiState.update { it.copy(editingTagName = name) }
    }

    /** 名前変更をキャンセルする。 */
    fun cancelRenameTag() {
        _uiState.update { it.copy(editingTag = null, editingTagName = "", message = null) }
    }

    /**
     * 名前変更を確定する。
     * trim・空文字スキップ・重複IGNORE は TagRepository.renameTag が担保。
     * 成功時は編集状態を解除し、未更新時は短いメッセージを出す。
     */
    fun confirmRenameTag() {
        val tag = _uiState.value.editingTag ?: return
        val name = _uiState.value.editingTagName
        viewModelScope.launch {
            try {
                if (name.trim().isEmpty()) {
                    _uiState.update { it.copy(message = "タグ名を入力してください") }
                    return@launch
                }
                val rows = repository.renameTag(tag.tagId, name)
                _uiState.update {
                    if (rows > 0) {
                        it.copy(editingTag = null, editingTagName = "", message = null)
                    } else {
                        it.copy(message = "同名のタグが既にあります")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "タグ名の変更に失敗しました", e)
                _uiState.update { it.copy(message = "タグ名の変更に失敗しました") }
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
