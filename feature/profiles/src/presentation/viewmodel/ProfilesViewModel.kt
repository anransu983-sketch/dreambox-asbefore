/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.suanran.dreambox.feature.profiles.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.suanran.dreambox.core.contract.OverrideApplyExecutor
import com.suanran.dreambox.core.contract.OverrideConfigRepository
import com.suanran.dreambox.core.contract.ProfileBindingReader
import com.suanran.dreambox.core.model.override.OverrideConfig
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.model.profile.ProfileBinding
import com.suanran.dreambox.core.util.coroutine.safeRunSilent
import com.suanran.dreambox.feature.profiles.domain.ProfileCrudUseCase
import com.suanran.dreambox.feature.profiles.presentation.screen.copyProfileImport
import com.suanran.dreambox.feature.profiles.presentation.screen.writeProfileYaml
import com.suanran.dreambox.core.util.NodeLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.suanran.dreambox.presentation.viewmodel.AndroidContractStateViewModel
import com.suanran.dreambox.runtime.api.contract.ProfileRepositoryContract
import com.suanran.dreambox.runtime.api.contract.ProxyControlContract
import com.suanran.dreambox.runtime.api.remote.IFetchObserver
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.suanran.dreambox.locale.FlyTxt
import timber.log.Timber

class ProfilesViewModel(
    application: Application,
    private val profilesRepository: ProfileRepositoryContract,
    private val bindingProvider: ProfileBindingReader,
    private val OverrideApplyExecutor: OverrideApplyExecutor,
    private val proxyControl: ProxyControlContract,
    private val overrideConfigRepository: OverrideConfigRepository,
    private val profileCrud: ProfileCrudUseCase,
) :
    AndroidContractStateViewModel<ProfilesUiState, ProfilesUiEffect>(
        application,
        ProfilesUiState(),
    ) {

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    /** Whether the proxy is currently running. */
    val isRunning: StateFlow<Boolean> = proxyControl.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The currently active profile, if any. */
    val currentProfile: StateFlow<Profile?> = proxyControl.currentProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** User-defined override configs. */
    val userConfigs: StateFlow<List<OverrideConfig>> = overrideConfigRepository
        .getUserConfigsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Built-in override configs (loaded once). */
    val builtInConfigs: StateFlow<List<OverrideConfig>> = flow {
        emit(overrideConfigRepository.getBuiltInConfigs())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Stop the proxy. Delegates to ProxyControlContract. */
    suspend fun stopProxy() { proxyControl.stopProxy() }

    private var toggleJob: Job? = null

    init {
        refreshProfiles()
    }

    @Suppress("TooGenericExceptionCaught")
    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                setLoading(true)
                _profiles.value = profileCrud.queryAllProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to refresh profiles")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        interval: Long = 0L,
        fileUri: Uri? = null,
        ageSecretKey: String = "",
    ) {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value =
                    DownloadProgress(percent = 0, message = FlyTxt.ProfilesVM.Progress.Preparing)

                // 订阅链接可能是 URL 编码过的（如 %2F），先解码
                val decodedSource = try {
                    if (source.contains("%2F", ignoreCase = true) || source.contains("%3A", ignoreCase = true)) {
                        java.net.URLDecoder.decode(source, "UTF-8")
                    } else source
                } catch (_: Exception) { source }

                val uuid = try {
                    profileCrud.createProfile(
                        type = type, name = name, source = decodedSource, ageSecretKey = ageSecretKey, interval = interval,
                        onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                        onBeforeUpdate = { createdUuid ->
                            if (type == Profile.Type.File && fileUri != null) {
                                getApplication<Application>().copyProfileImport(fileUri, createdUuid)
                            }
                        },
                    )
                } catch (e: Exception) {
                    // 订阅返回的不是 Clash 配置而是节点链接时，转为节点导入（兜底）
                    val msg = e.message.orEmpty()
                    if (type == Profile.Type.Url && ("proxies" in msg || "proxy-providers" in msg)) {
                        importNodeSubscriptionInternal(name, decodedSource, msg)
                        return@launch
                    }
                    throw e
                }

                if (uuid == null) {
                    showError(FlyTxt.ProfilesVM.Message.AddFailed.format("Unknown"))
                    _downloadProgress.value = null
                    return@launch
                }
                _downloadProgress.value =
                    DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                showMessage(FlyTxt.ProfilesVM.Message.ProfileAdded.format(name))
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to create profile")
                refreshProfiles()
                showError(FlyTxt.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun cloneProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                val newUuid = profileCrud.cloneProfile(uuid)
                if (newUuid != null) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileAdded.format("Clone"))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.AddFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to clone profile")
                showError(FlyTxt.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun deleteProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                val profile = _profiles.value.find { it.uuid == uuid }
                if (profile?.active == true && isRunning.value) {
                    showError(FlyTxt.ProfilesVM.Message.CannotDeleteActiveProfile)
                    return@launch
                }
                if (profileCrud.deleteProfile(uuid)) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileDeleted)
                } else {
                    showError(FlyTxt.ProfilesVM.Message.DeleteFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to delete profile")
                showError(FlyTxt.ProfilesVM.Message.DeleteFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun activateProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                if (profileCrud.activateProfile(uuid)) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format("Active"))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to activate profile")
                showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    fun updateAllUrlProfiles() {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value = DownloadProgress(0, FlyTxt.ProfilesVM.Progress.Preparing)
                val count = profileCrud.updateAllUrlProfiles(
                    profiles = _profiles.value,
                    onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                )
                if (count > 0) {
                    _downloadProgress.value =
                        DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                    showMessage(FlyTxt.ProfilesPage.Action.UpdateAll)
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to update all url profiles")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    fun updateProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value = DownloadProgress(percent = 0, message = FlyTxt.ProfilesVM.Progress.Preparing)
                val success = profileCrud.updateProfile(
                    uuid = uuid,
                    onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                )
                if (success) {
                    _downloadProgress.value =
                        DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format(uuid.toString()))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format("Unknown"))
                    _downloadProgress.value = null
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to update profile")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun patchProfile(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String? = null,
    ) {
        viewModelScope.launch {
            try {
                setLoading(true)
                if (profileCrud.patchProfile(uuid, name, source, interval, ageSecretKey)) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format(name))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to patch profile")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    fun importProfileFromFile(uri: Uri, name: String) {
        createProfile(type = Profile.Type.File, name = name, fileUri = uri)
    }

    /**
     * 从节点 Map 列表创建配置（剪贴板导入/手动添加服务器用）。
     * @param onResult 回调 (成功创建的节点数, profile名称)
     */
    @Suppress("TooGenericExceptionCaught")
    /**
     * 节点订阅（V2）：抓取 URL 内容 → Base64 解码 → 解析节点链接 → 生成 Clash YAML → 存为 File Profile。
     * 供"配置类型=节点订阅"直接调用。
     */
    fun importNodeSubscription(name: String, url: String) {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value =
                    DownloadProgress(percent = 0, message = FlyTxt.ProfilesVM.Progress.Preparing)
                importNodeSubscriptionInternal(name, url, null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to import node subscription")
                refreshProfiles()
                showError(FlyTxt.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun importNodeSubscriptionInternal(name: String, url: String, originalError: String?) {
        val decodedUrl = try {
            if (url.contains("%2F", ignoreCase = true) || url.contains("%3A", ignoreCase = true)) {
                java.net.URLDecoder.decode(url, "UTF-8")
            } else url
        } catch (_: Exception) { url }
        val fetched = withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(decodedUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                conn.instanceFollowRedirects = true
                val text = conn.inputStream.bufferedReader().readText()
                try {
                    val b64 = android.util.Base64.decode(text.trim(), android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
                    if ("://" in b64) b64 else text
                } catch (_: Exception) { text }
            } catch (fe: Exception) { "FETCH_FAILED:${fe.message}" }
        }
        val orig = if (!originalError.isNullOrEmpty()) ", 原错误: $originalError" else ""
        if (fetched.startsWith("FETCH_FAILED")) {
            throw IllegalStateException("抓取订阅失败: ${fetched.removePrefix("FETCH_FAILED:")}$orig")
        }
        val (nodes, failCount) = NodeLinkParser.parseAll(fetched)
        if (nodes.isNotEmpty()) {
            val yaml = com.suanran.dreambox.feature.profiles.presentation.screen.buildNodesConfigYaml(nodes)
            val nodeUuid = profileCrud.createProfile(
                type = Profile.Type.File,
                name = name.ifBlank { FlyTxt.ProfilesPage.Input.NewProfile },
                source = "",
                onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                onBeforeUpdate = { createdUuid ->
                    getApplication<Application>().writeProfileYaml(yaml, createdUuid)
                },
            )
            if (nodeUuid == null) {
                throw IllegalStateException("解析到 ${nodes.size} 个节点但创建失败(失败${failCount}行)$orig")
            }
            _downloadProgress.value = DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
            showMessage(FlyTxt.ProfilesPage.Message.NodesAdded.format(nodes.size))
            refreshProfiles()
            return
        }
        throw IllegalStateException("订阅内容非节点链接(解析0个, 失败${failCount}行), 内容前100字: ${fetched.take(100)}$orig")
    }

    fun createProfileFromNodes(
        name: String,
        nodes: List<Map<String, Any?>>,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        if (nodes.isEmpty()) {
            showError(FlyTxt.ProfilesPage.Validation.NoValidNode)
            onResult(false, name)
            return
        }
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value =
                    DownloadProgress(percent = 0, message = FlyTxt.ProfilesVM.Progress.Preparing)
                val yaml = com.suanran.dreambox.feature.profiles.presentation.screen.buildNodesConfigYaml(nodes)
                val uuid = profileCrud.createProfile(
                    type = Profile.Type.File,
                    name = name.ifBlank { FlyTxt.ProfilesPage.Input.NewProfile },
                    source = "",
                    onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                    onBeforeUpdate = { createdUuid ->
                        val app = getApplication<Application>()
                        app.writeProfileYaml(yaml, createdUuid)
                    },
                )
                if (uuid == null) {
                    showError(FlyTxt.ProfilesVM.Message.AddFailed.format("Unknown"))
                    _downloadProgress.value = null
                    onResult(false, name)
                    return@launch
                }
                _downloadProgress.value =
                    DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                showMessage(FlyTxt.ProfilesPage.Message.NodesAdded.format(nodes.size))
                refreshProfiles()
                onResult(true, name)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to create profile from nodes")
                refreshProfiles()
                showError(FlyTxt.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
                onResult(false, name)
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun reorderProfiles(from: Int, to: Int) {
        viewModelScope.launch {
            try {
                val current = _profiles.value
                if (from !in current.indices || to !in current.indices || from == to) return@launch

                val reordered = current.toMutableList()
                val moved = reordered.removeAt(from)
                reordered.add(to, moved)

                _profiles.value = reordered
                profileCrud.reorderProfiles(reordered.map { it.uuid })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to reorder profiles")
                refreshProfiles()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun toggleProfileEnabled(uuid: UUID) {
        toggleJob?.cancel()
        toggleJob = viewModelScope.launch {
            try {
                val newState = profileCrud.toggleProfileEnabled(uuid)
                if (newState != null) {
                    val profile = profilesRepository.queryProfileByUUID(uuid)
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format(profile?.name ?: ""))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to toggle profile")
                showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format(error.message ?: "Unknown"))
            }
        }
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    fun clearError() {
        clearErrorState()
    }

    fun clearMessage() {
        clearMessageState()
    }

    suspend fun getBinding(profileId: String): ProfileBinding? =
        bindingProvider.getBinding(profileId)

    suspend fun saveOverrideBinding(
        profileId: String,
        overrideIds: List<String>,
        applyNow: Boolean,
    ): ProfileBinding? {
        val normalizedIds = overrideIds.distinct()
        val current = bindingProvider.getBinding(profileId)
        val updated = current?.copy(overrideIds = normalizedIds)
            ?: ProfileBinding(profileId = profileId, overrideIds = normalizedIds)
        bindingProvider.setBinding(updated)
        if (applyNow) OverrideApplyExecutor.applyOverride(profileId)
        return bindingProvider.getBinding(profileId)
    }

    private fun showError(message: String) {
        postError(message, ProfilesUiEffect.ShowError(message))
    }

    private fun showMessage(message: String) {
        postMessage(message, ProfilesUiEffect.ShowMessage(message))
    }
}
