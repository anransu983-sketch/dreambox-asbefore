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
import com.suanran.dreambox.core.model.animation.AnimationParseError
import com.suanran.dreambox.core.model.animation.DreamAnimation
import com.suanran.dreambox.feature.settings.data.AnimationInstallError
import com.suanran.dreambox.feature.settings.data.AnimationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 动画商店 UI 事件（toast 提示）。 */
sealed interface AnimationStoreEvent {
    data class Message(val text: String) : AnimationStoreEvent
    /** 需要本地化的错误：由 UI 层按 key 取文案。 */
    data class ErrorKey(val key: String, val arg: String? = null) : AnimationStoreEvent
}

/**
 * 动画商店 ViewModel（对标 ThemeStoreViewModel）。
 *
 * 动画启用沿用现有设置键（animationEnabled + activeAnimationId）。
 */
class AnimationStoreViewModel(
    private val settings: AppSettingsReader,
    private val repository: AnimationRepository,
) : ViewModel() {

    val animationEnabled: Preference<Boolean> = settings.animationEnabled
    val activeAnimationId: Preference<String> = settings.activeAnimationId
    val animCountOverride: Preference<Int> = settings.animCountOverride
    val animSpeedOverride: Preference<Float> = settings.animSpeedOverride
    val animOpacityOverride: Preference<Float> = settings.animOpacityOverride

    private val _userAnimations = MutableStateFlow<List<DreamAnimation>>(emptyList())
    val userAnimations: StateFlow<List<DreamAnimation>> = _userAnimations.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<AnimationStoreEvent>()
    val events: SharedFlow<AnimationStoreEvent> = _events.asSharedFlow()

    init {
        refreshAnimations()
    }

    fun refreshAnimations() {
        viewModelScope.launch {
            _userAnimations.value = repository.listUserAnimations()
        }
    }

    /** 启用动画：写入选中 id 并打开总开关。 */
    fun applyAnimation(animation: DreamAnimation) {
        activeAnimationId.set(animation.id)
        animationEnabled.set(true)
        viewModelScope.launch { _events.emit(AnimationStoreEvent.ErrorKey("applied", animation.name)) }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.installFromUrl(url)
                    .onSuccess { animation ->
                        refreshAnimations()
                        _events.emit(AnimationStoreEvent.ErrorKey("install_success", animation.name))
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
                    .onSuccess { animation ->
                        refreshAnimations()
                        _events.emit(AnimationStoreEvent.ErrorKey("install_success", animation.name))
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteAnimation(animation: DreamAnimation) {
        viewModelScope.launch {
            repository.deleteAnimation(animation.id)
                .onSuccess {
                    // 若删的是当前启用动画，一并关闭
                    if (activeAnimationId.value == animation.id) {
                        animationEnabled.set(false)
                        activeAnimationId.set("")
                    }
                    refreshAnimations()
                    _events.emit(AnimationStoreEvent.ErrorKey("delete_success", animation.name))
                }
                .onFailure { e -> emitInstallError(e) }
        }
    }

    fun checkUpdate(animation: DreamAnimation) {
        viewModelScope.launch {
            _busy.value = true
            try {
                repository.checkForUpdate(animation)
                    .onSuccess { updated ->
                        if (updated == null) {
                            _events.emit(AnimationStoreEvent.ErrorKey("no_update"))
                        } else {
                            refreshAnimations()
                            _events.emit(AnimationStoreEvent.ErrorKey("update_success", updated.version))
                        }
                    }
                    .onFailure { e -> emitInstallError(e) }
            } finally {
                _busy.value = false
            }
        }
    }

    /** 参数覆盖：0 表示恢复 JSON 默认值。 */
    fun onCountOverrideChange(value: Int) = animCountOverride.set(value.coerceIn(0, 150))
    fun onSpeedOverrideChange(value: Float) = animSpeedOverride.set(value.coerceIn(0f, 5f))
    fun onOpacityOverrideChange(value: Float) = animOpacityOverride.set(value.coerceIn(0f, 1f))

    private suspend fun emitInstallError(e: Throwable) {
        val (key, arg) = when (e) {
            is AnimationInstallError.EmptyUrl -> "err_empty_url" to null
            is AnimationInstallError.DownloadFailed -> "err_download" to e.detail
            is AnimationInstallError.ReadFailed -> "err_read" to e.detail
            is AnimationInstallError.SaveFailed -> "err_save" to e.detail
            is AnimationInstallError.AlreadyExists -> "err_exists" to e.id
            is AnimationInstallError.IsBuiltin -> "err_builtin" to e.id
            is AnimationInstallError.NotFound -> "err_not_found" to e.id
            is AnimationInstallError.NoSourceUrl -> "err_no_source" to null
            is AnimationInstallError.ParseError -> when (val p = e.parseError) {
                is AnimationParseError.NotJson -> "err_not_json" to null
                is AnimationParseError.MissingId -> "err_missing_id" to null
                is AnimationParseError.InvalidId -> "err_invalid_id" to p.id
                is AnimationParseError.MissingName -> "err_missing_name" to null
                is AnimationParseError.InvalidParticleType -> "err_invalid_type" to p.value
                is AnimationParseError.InvalidSpawn -> "err_invalid_spawn" to p.value
                is AnimationParseError.InvalidBehavior -> "err_invalid_behavior" to p.value
                is AnimationParseError.InvalidColor -> "err_invalid_color" to p.value
                is AnimationParseError.BuiltinConflict -> "err_builtin_conflict" to p.id
            }
            else -> "err_unknown" to (e.message ?: "unknown")
        }
        _events.emit(AnimationStoreEvent.ErrorKey(key, arg))
    }
}
