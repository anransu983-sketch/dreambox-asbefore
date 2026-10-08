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

package com.suanran.dreambox.feature.settings.data.vps

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import timber.log.Timber

/**
 * SSH 脚本执行器（sshj，密码认证）。
 *
 * 连接超时 15 秒；脚本执行超时 10 分钟。输出通过 [onOutput] 实时回调
 * （stdout/stderr 合并，保留 ANSI 颜色码，由 UI 层决定是否剥离）。
 * 密码不打日志。
 */
class SshScriptRunner {

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val EXEC_TIMEOUT_MINUTES = 10L
    }

    /**
     * 在 [server] 上执行 [script]，输出块实时回调。
     * @return 退出码；抛异常表示连接/认证/执行失败。
     */
    suspend fun run(
        server: VpsServer,
        script: VpsTestScript,
        onOutput: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val ssh = SSHClient()
        // 接受所有 host key（用户自己的机器，简化处理）
        ssh.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.timeout = CONNECT_TIMEOUT_MS
        try {
            ssh.connect(server.host, server.port)
            ssh.authPassword(server.username, server.password)
            val session: Session = ssh.startSession()
            try {
                // 用 login shell 跑，支持 <() 进程替换
                val cmd = session.exec("bash -lc ${quoteForBash(script.command)}")
                val out = ByteArrayOutputStream()
                // 实时 pump 输出
                val pump = thread(isDaemon = true, name = "vps-test-pump") {
                    val buf = ByteArray(8192)
                    try {
                        val input = cmd.inputStream
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            val chunk = String(buf, 0, n, Charsets.UTF_8)
                            out.write(buf, 0, n)
                            onOutput(chunk)
                        }
                    } catch (_: Exception) {
                        // 流关闭即结束
                    }
                }
                // err 合并到 out
                val errPump = thread(isDaemon = true, name = "vps-test-err-pump") {
                    val buf = ByteArray(4096)
                    try {
                        val err = cmd.errorStream
                        while (true) {
                            val n = err.read(buf)
                            if (n < 0) break
                            onOutput(String(buf, 0, n, Charsets.UTF_8))
                        }
                    } catch (_: Exception) {
                    }
                }
                cmd.join(EXEC_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                pump.join(5_000)
                errPump.join(2_000)
                cmd.exitStatus ?: -1
            } finally {
                runCatching { session.close() }
            }
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    private fun quoteForBash(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * 在 [server] 上执行任意 shell 命令并捕获完整输出。
     * @return Pair(退出码, 合并后的 stdout+stderr 文本)。
     */
    suspend fun runAndCapture(
        server: VpsServer,
        command: String,
        timeoutMinutes: Long = 2L,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.timeout = CONNECT_TIMEOUT_MS
        try {
            ssh.connect(server.host, server.port)
            ssh.authPassword(server.username, server.password)
            val session: Session = ssh.startSession()
            try {
                val cmd = session.exec("bash -lc ${quoteForBash(command)}")
                val outBuf = ByteArrayOutputStream()
                val errBuf = ByteArrayOutputStream()
                val outPump = thread(isDaemon = true) {
                    val buf = ByteArray(8192)
                    try {
                        while (true) {
                            val n = cmd.inputStream.read(buf)
                            if (n < 0) break
                            outBuf.write(buf, 0, n)
                        }
                    } catch (_: Exception) { }
                }
                val errPump = thread(isDaemon = true) {
                    val buf = ByteArray(4096)
                    try {
                        while (true) {
                            val n = cmd.errorStream.read(buf)
                            if (n < 0) break
                            errBuf.write(buf, 0, n)
                        }
                    } catch (_: Exception) { }
                }
                cmd.join(timeoutMinutes, TimeUnit.MINUTES)
                outPump.join(5_000)
                errPump.join(2_000)
                val exit = cmd.exitStatus ?: -1
                val combined = outBuf.toString(Charsets.UTF_8.name()) +
                    errBuf.toString(Charsets.UTF_8.name()).let { if (it.isNotBlank()) "\n$it" else "" }
                exit to combined.trim()
            } finally {
                runCatching { session.close() }
            }
        } finally {
            runCatching { ssh.disconnect() }
        }
    }
}
