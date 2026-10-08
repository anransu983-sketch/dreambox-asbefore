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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suanran.dreambox.feature.settings.data.vps.SshScriptRunner
import com.suanran.dreambox.feature.settings.data.vps.SuiTokenInjector
import com.suanran.dreambox.feature.settings.data.vps.VpsServer
import com.suanran.dreambox.feature.settings.data.vps.VpsTestRepository
import com.suanran.dreambox.feature.settings.data.vps.VpsTestScript
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** VPS 测试 UI 事件（toast 提示，key 由 UI 层本地化）。 */
sealed interface VpsTestEvent {
    data class ErrorKey(val key: String, val arg: String? = null) : VpsTestEvent
}

/** 脚本运行状态。 */
enum class VpsRunState {
    Idle, Running, Done, Failed,
}

class VpsTestViewModel(
    private val repository: VpsTestRepository,
    private val sshRunner: SshScriptRunner,
    private val tokenInjector: SuiTokenInjector,
) : ViewModel() {

    private val _servers = MutableStateFlow<List<VpsServer>>(emptyList())
    val servers: StateFlow<List<VpsServer>> = _servers.asStateFlow()

    private val _events = MutableSharedFlow<VpsTestEvent>()
    val events: SharedFlow<VpsTestEvent> = _events.asSharedFlow()

    // 运行状态
    private val _runState = MutableStateFlow(VpsRunState.Idle)
    val runState: StateFlow<VpsRunState> = _runState.asStateFlow()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    // s-ui 安装后自动注入的 API token
    private val _suiToken = MutableStateFlow<String?>(null)
    val suiToken: StateFlow<String?> = _suiToken.asStateFlow()

    private var runJob: Job? = null
    private val cancelled = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _servers.value = repository.listServers()
        }
    }

    fun saveServer(server: VpsServer) {
        viewModelScope.launch {
            repository.saveServer(server)
            refresh()
            _events.emit(VpsTestEvent.ErrorKey("server_saved", server.name))
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            repository.deleteServer(id)
            refresh()
            _events.emit(VpsTestEvent.ErrorKey("server_deleted", null))
        }
    }

    /** 开始在指定服务器上跑脚本。输出实时追加到 [output]。 */
    fun runScript(server: VpsServer, script: VpsTestScript) {
        if (_runState.value == VpsRunState.Running) return
        cancelled.set(false)
        _output.value = ""
        _exitCode.value = null
        _suiToken.value = null
        _runState.value = VpsRunState.Running
        runJob = viewModelScope.launch {
            try {
                val code = sshRunner.run(server, script) { chunk ->
                    // 回调在 IO 线程，追加输出（主线程更新 StateFlow 更稳）
                    _output.value += chunk
                }
                _exitCode.value = code
                if (code == 0 && script == VpsTestScript.SuiInstall) {
                    // s-ui 安装成功：自动注入 API token
                    _output.value += "\n[INFO] s-ui 安装完成，正在注入 API token…\n"
                    when (val r = tokenInjector.injectToken(server)) {
                        is SuiTokenInjector.Result.Ok -> {
                            _suiToken.value = r.token
                            _output.value += "[INFO] API token 已注入并保存\n"
                        }
                        is SuiTokenInjector.Result.Err -> {
                            _output.value += "[WARN] API token 注入失败：${r.message}\n"
                        }
                    }
                }
                _runState.value = if (code == 0) VpsRunState.Done else VpsRunState.Failed
                _events.emit(VpsTestEvent.ErrorKey(if (code == 0) "run_done" else "run_failed_code", code.toString()))
            } catch (e: Exception) {
                if (cancelled.get()) {
                    _runState.value = VpsRunState.Idle
                } else {
                    _runState.value = VpsRunState.Failed
                    // 不暴露密码：只取异常类名+简短 message
                    val msg = (e.message ?: e.javaClass.simpleName).take(200)
                    _output.value += "\n[ERROR] $msg\n"
                    _events.emit(VpsTestEvent.ErrorKey("run_failed", msg))
                }
            }
        }
    }

    fun cancelRun() {
        cancelled.set(true)
        runJob?.cancel()
        _runState.value = VpsRunState.Idle
    }

    fun clearOutput() {
        _output.value = ""
        _exitCode.value = null
        _runState.value = VpsRunState.Idle
    }
}
