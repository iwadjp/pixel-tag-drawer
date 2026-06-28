package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow(TagUiState())
    val uiState: StateFlow<TagUiState> = _uiState.asStateFlow()

    // 選択中アプリの付与済みタグID購読。選択切替時に張り替える。
    private var selectedAppTagJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
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
     */
    fun setTagForSelectedApp(tagId: Long, checked: Boolean) {
        val app = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            try {
                if (checked) {
                    repository.assignTag(app.packageName, app.className, tagId)
                } else {
                    repository.removeTag(app.packageName, app.className, tagId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "タグ割り当ての更新に失敗しました", e)
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

    private companion object {
        const val TAG = "TagViewModel"
    }
}
