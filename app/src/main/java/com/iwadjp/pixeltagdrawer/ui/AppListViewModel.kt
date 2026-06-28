package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow(AppListUiState(isLoading = true))
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repository.loadLaunchableApps() }
                _uiState.update { it.copy(isLoading = false, apps = list, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "アプリ一覧の読み込みに失敗しました")
                }
            }
        }
    }

    /**
     * 対象アプリを起動する。失敗してもクラッシュさせず、エラーメッセージで通知する。
     */
    fun launch(app: LauncherApp) {
        val context = getApplication<Application>()
        try {
            context.startActivity(repository.buildLaunchIntent(app))
            _uiState.update { it.copy(errorMessage = null) }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "起動に失敗しました: ${app.label} (${app.packageName})")
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
