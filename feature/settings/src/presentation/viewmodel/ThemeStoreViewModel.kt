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

package com.suanran.dreambox.feature.settings.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.core.contract.Preference
import com.suanran.dreambox.core.model.ThemeMode
import com.suanran.dreambox.feature.settings.data.ThemeInstallError
import com.suanran.dreambox.feature.settings.data.ThemeRepository
import com.suanran.dreambox.feature.settings.presentation.theme.DreamTheme
import com.suanran.dreambox.feature.settings.presentation.theme.ThemeCatalogEntry
import com.suanran.dreambox.feature.settings.presentation.theme.ThemeParseError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 主题商店 UI 事件（toast / 对话框提示）。 */
sealed interface ThemeStoreEvent {
    data class Message(val text: String) : ThemeStoreEvent
    /** 需要本地化的错误：由 UI 层按 key 取文案。 */
    data class ErrorKey(val key: String, val arg: String? = null) : ThemeStoreEvent
}

/** 主题市场加载状态。 */
enum class MarketState { Idle, Loading, Loaded, Failed }

/**
 * 主题商店 ViewModel。
 *
 * 主题启用沿用现有设置键（themeMode + themeAccentColorArgb），不另起存储；
 * 「使用中」判定为主题模式与主题色同时命中。
 */
class ThemeStoreViewModel(
    private val settings: AppSettingsReader,
    private val repository: ThemeRepository,
) : ViewModel() {

    val themeMode: Preference<ThemeMode> = settings.themeMode
    val themeSeedColorArgb: Preference<Long> = settings.themeAccentColorArgb

    private val _userThemes = MutableStateFlow<List<DreamTheme>>(emptyList())
    val userThemes: StateFlow<List<DreamTheme>> = _userThemes.asStateFlow()

    private val _marketThemes = MutableStateFlow<List<ThemeCatalogEntry>>(emptyList())
    val marketThemes: StateFlow<List<ThemeCatalogEntry>> = _marketThemes.asStateFlow()

    private val _marketState = MutableStateFlow(MarketState.Idle)
    val marketState: StateFlow<MarketState> = _marketState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<ThemeStoreEvent>()
    val events: SharedFlow<ThemeStoreEvent> = _events.asSharedFlow()

    init {
        refreshThemes()
    }

    fun refreshThemes() {
        viewModelScope.launch {
            _userThemes.value = repository.listUserThemes()
        }
    }

    /** 拉取主题市场目录。 */
    fun refreshMarket() {
        viewModelScope.launch {
            _marketState.value = MarketState.Loading
            repository.fetchMarketCatalog()
                .onSuccess { entries ->
                    _marketThemes.value = entries
                    _marketState.value = MarketState.Loaded
                }
                .onFailure { e ->
                    _marketState.value = MarketState.Failed
                    emitInstallError(e)
                }
        }
    }

    /** 从市场安装主题（一键下载安装）。 */
    fun installMarketTheme(entry: ThemeCatalogEntry) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.installFromUrl(entry.download)
                    .onSuccess { theme ->
                        refreshThemes()
                        _events.emit(ThemeStoreEvent.ErrorKey("install_success", theme.name))
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    /** 启用主题：写入现有主题设置键。 */
    fun applyTheme(theme: DreamTheme) {
        themeMode.set(theme.themeMode)
        themeSeedColorArgb.set(theme.seedColorArgb)
        viewModelScope.launch { _events.emit(ThemeStoreEvent.ErrorKey("applied", theme.name)) }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.installFromUrl(url)
                    .onSuccess { theme ->
                        refreshThemes()
                        _events.emit(ThemeStoreEvent.ErrorKey("install_success", theme.name))
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun installFromFile(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.installFromFile(uri)
                    .onSuccess { theme ->
                        refreshThemes()
                        _events.emit(ThemeStoreEvent.ErrorKey("install_success", theme.name))
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun installFromZip(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.installFromZip(uri)
                    .onSuccess { theme ->
                        refreshThemes()
                        _events.emit(ThemeStoreEvent.ErrorKey("install_success", theme.name))
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteTheme(theme: DreamTheme) {
        viewModelScope.launch {
            repository.deleteTheme(theme.id)
                .onSuccess {
                    refreshThemes()
                    _events.emit(ThemeStoreEvent.ErrorKey("delete_success", theme.name))
                }
                .onFailure { e -> emitInstallError(e) }
        }
    }

    fun checkUpdate(theme: DreamTheme) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.checkForUpdate(theme)
                    .onSuccess { updated ->
                        if (updated == null) {
                            _events.emit(ThemeStoreEvent.ErrorKey("no_update"))
                        } else {
                            refreshThemes()
                            _events.emit(ThemeStoreEvent.ErrorKey("update_success", updated.version))
                        }
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun emitInstallError(e: Throwable) {
        val (key, arg) = when (e) {
            is ThemeInstallError.EmptyUrl -> "err_empty_url" to null
            is ThemeInstallError.DownloadFailed -> "err_download" to e.detail
            is ThemeInstallError.ReadFailed -> "err_read" to e.detail
            is ThemeInstallError.SaveFailed -> "err_save" to e.detail
            is ThemeInstallError.AlreadyExists -> "err_exists" to e.id
            is ThemeInstallError.IsBuiltin -> "err_builtin" to e.id
            is ThemeInstallError.NotFound -> "err_not_found" to e.id
            is ThemeInstallError.NoSourceUrl -> "err_no_source" to null
            is ThemeInstallError.ParseError -> when (val p = e.parseError) {
                is ThemeParseError.NotJson -> "err_not_json" to null
                is ThemeParseError.MissingId -> "err_missing_id" to null
                is ThemeParseError.InvalidId -> "err_invalid_id" to p.id
                is ThemeParseError.MissingName -> "err_missing_name" to null
                is ThemeParseError.InvalidThemeMode -> "err_invalid_mode" to p.value
                is ThemeParseError.InvalidSeedColor -> "err_invalid_color" to p.value
                is ThemeParseError.BuiltinConflict -> "err_builtin_conflict" to p.id
            }
            else -> "err_unknown" to (e.message ?: "unknown")
        }
        _events.emit(ThemeStoreEvent.ErrorKey(key, arg))
    }
}
