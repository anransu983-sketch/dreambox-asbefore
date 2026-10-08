package com.suanran.dreambox.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suanran.dreambox.core.model.UpdateSource
import com.suanran.dreambox.locale.FlyTxt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 更新通道已固定为 Smart：直接按构建 ID（APK 文件名中的时间戳）比较，
 * 不再让用户选择版本通道，也不再读取旧的通道设置。
 */
private val FixedUpdateSource = UpdateSource.Smart

data class GitHubUpdateUiState(
    val isChecking: Boolean = false,
    val candidate: UpdateCandidate? = null,
    val message: String? = null,
)

class GitHubUpdateViewModel(
    private val updateManager: GitHubUpdateManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubUpdateUiState())
    val uiState: StateFlow<GitHubUpdateUiState> = _uiState.asStateFlow()
    val downloadProgress: StateFlow<UpdateDownloadProgress> = updateManager.downloadProgress
    private var downloadJob: Job? = null
    fun checkForUpdate() {
        if (_uiState.value.isChecking) return
        val source = FixedUpdateSource
        Timber.i("Manual update check requested: source=%s", source.key)
        Timber.i("Manual update check bypass cache: source=%s", source.key)
        _uiState.value = _uiState.value.copy(isChecking = true, message = null)
        viewModelScope.launch {
            updateManager.checkForUpdate(source = source, isManualCheck = true)
                .onSuccess { candidate ->
                    Timber.i(
                        "Manual update check finished: source=%s hasCandidate=%s tag=%s version=%s",
                        source.key,
                        candidate != null,
                        candidate?.tag.orEmpty(),
                        candidate?.versionName.orEmpty(),
                    )
                    _uiState.value = GitHubUpdateUiState(
                        isChecking = false,
                        candidate = candidate,
                        message = if (candidate == null) FlyTxt.Component.Update.Message.NoUpdate else null,
                    )
                }
                .onFailure { throwable ->
                    Timber.w(throwable, "Manual update check failed: source=%s", source.key)
                    _uiState.value = GitHubUpdateUiState(
                        isChecking = false,
                        message = FlyTxt.Component.Update.Message.CheckFailed.format(
                            throwable.message ?: FlyTxt.Util.Error.UnknownError,
                        ),
                    )
                }
        }
    }
    fun downloadAndInstall(candidate: UpdateCandidate, selectedPackage: UpdateManifestPackage? = null) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            updateManager.downloadAndInstall(candidate, selectedPackage)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        candidate = null,
                        message = FlyTxt.Component.Update.Message.InstallPromptOpened,
                    )
                }
                .onFailure { throwable ->
                    val message = if (throwable.isUpdateDownloadCancelled()) {
                        FlyTxt.Component.Button.Cancel
                    } else {
                        FlyTxt.Component.Update.Message.InstallFailed.format(
                            throwable.message ?: FlyTxt.Util.Error.UnknownError,
                        )
                    }
                    _uiState.value = _uiState.value.copy(message = message)
                }
        }
    }
    fun cancelDownload() {
        updateManager.cancelDownload()
    }
    fun dismissCandidate() {
        _uiState.value = _uiState.value.copy(candidate = null)
    }
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
