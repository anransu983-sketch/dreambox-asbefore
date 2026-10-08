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

import com.tencent.mmkv.MMKV
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * s-ui API Token 自动注入器。
 *
 * s-ui（alireza0/s-ui）的 /apiv2 API 使用 token 认证，token 存在 SQLite 数据库中：
 * - 数据库文件：`/usr/local/s-ui/db/s-ui.db`
 * - 表名：`tokens`
 * - 字段：`id`（自增）、`desc`、`token`（32 位字母数字）、`expiry`（0=永不过期）、`user_id`
 *
 * 注入后需重启 s-ui 服务（token 在启动时加载到内存）。
 * 生成的 token 存于独立 MMKV（key: sui_token_<serverId>），不打日志。
 */
class SuiTokenInjector(
    private val ssh: SshScriptRunner,
) {
    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("vps_test") }

    companion object {
        const val SUI_DB_PATH = "/usr/local/s-ui/db/s-ui.db"
        private const val TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val TOKEN_LENGTH = 32
    }

    sealed interface Result {
        data class Ok(val token: String) : Result
        data class Err(val message: String) : Result
    }

    /**
     * 为 [server] 上的 s-ui 生成并注入 API token。
     * @return Ok(token) 或 Err(原因)。
     */
    suspend fun injectToken(server: VpsServer): Result = withContext(Dispatchers.IO) {
        // 1. 检查数据库文件是否存在
        val (checkExit, checkOut) = ssh.runAndCapture(
            server,
            "test -f $SUI_DB_PATH && echo EXISTS || echo MISSING",
        )
        if (checkExit != 0 || !checkOut.contains("EXISTS")) {
            return@withContext Result.Err("s-ui 数据库不存在（$SUI_DB_PATH），请先安装 s-ui")
        }

        // 2. 生成 32 位随机 token（格式与 s-ui 一致：A-Za-z0-9）
        val token = generateToken()

        // 3. 确保有可用的 SQLite 工具（优先 sqlite3 CLI，否则用 python3）
        val (toolExit, toolOut) = ssh.runAndCapture(
            server,
            "command -v sqlite3 >/dev/null 2>&1 && echo SQLITE3 || (python3 -c 'import sqlite3; print(\"PYSQLITE\")' 2>/dev/null || echo NONE)",
        )
        if (toolExit != 0) {
            return@withContext Result.Err("检查 SQLite 工具失败")
        }
        val insertCmd = when {
            toolOut.contains("SQLITE3") -> buildSqlite3Command(token)
            toolOut.contains("PYSQLITE") -> buildPythonCommand(token)
            else -> {
                // 尝试安装 sqlite3
                val (aptExit, _) = ssh.runAndCapture(
                    server,
                    "apt-get update -qq && apt-get install -y -qq sqlite3",
                    timeoutMinutes = 5L,
                )
                if (aptExit != 0) {
                    return@withContext Result.Err("服务器缺少 sqlite3 且自动安装失败，请手动安装后重试")
                }
                buildSqlite3Command(token)
            }
        }

        // 4. 插入 token 记录（expiry=0 永不过期，user_id 取第一个用户）
        val (insertExit, insertOut) = ssh.runAndCapture(server, insertCmd)
        if (insertExit != 0) {
            Timber.w("SuiTokenInjector: insert failed: $insertOut")
            return@withContext Result.Err("写入 token 失败：${insertOut.take(200)}")
        }

        // 5. 验证 token 已写入
        val verifyCmd = if (toolOut.contains("SQLITE3") || insertCmd.startsWith("sqlite3")) {
            "sqlite3 $SUI_DB_PATH \"SELECT COUNT(*) FROM tokens WHERE token='$token';\""
        } else {
            "python3 -c \"import sqlite3; print(sqlite3.connect('$SUI_DB_PATH').execute(\\\"SELECT COUNT(*) FROM tokens WHERE token='$token'\\\").fetchone()[0])\""
        }
        val (verifyExit, verifyOut) = ssh.runAndCapture(server, verifyCmd)
        if (verifyExit != 0 || verifyOut.trim() != "1") {
            return@withContext Result.Err("token 写入验证失败")
        }

        // 6. 重启 s-ui 使 token 生效（token 在启动时加载到内存）
        val (restartExit, _) = ssh.runAndCapture(
            server,
            "systemctl restart s-ui 2>/dev/null || service s-ui restart 2>/dev/null || (pkill -f 's-ui' ; sleep 2 ; /usr/local/s-ui/s-ui >/dev/null 2>&1 &)",
            timeoutMinutes = 1L,
        )
        // 重启命令的退出码不可靠（服务重启时连接可能断开），不强制检查
        if (restartExit != 0) {
            Timber.w("SuiTokenInjector: restart command exit=$restartExit (may be normal)")
        }

        // 7. 保存 token 到 MMKV
        saveToken(server.id, token)

        Result.Ok(token)
    }

    /** 从 MMKV 获取某服务器的 token（可能为空）。 */
    fun getToken(serverId: String): String =
        mmkv.decodeString("sui_token_$serverId").orEmpty()

    /** 保存 token 到 MMKV。 */
    fun saveToken(serverId: String, token: String) {
        runCatching {
            mmkv.encode("sui_token_$serverId", token)
        }.onFailure { Timber.w(it, "SuiTokenInjector: save token failed") }
    }

    private fun generateToken(): String {
        val random = SecureRandom()
        return (1..TOKEN_LENGTH)
            .map { TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)] }
            .joinToString("")
    }

    private fun buildSqlite3Command(token: String): String {
        // 使用子查询获取第一个用户的 id；desc 标记来源
        val sql = "INSERT INTO tokens (desc, token, expiry, user_id) " +
            "VALUES ('dreambox-auto', '$token', 0, (SELECT id FROM users LIMIT 1));"
        return "sqlite3 $SUI_DB_PATH \"$sql\""
    }

    private fun buildPythonCommand(token: String): String {
        return "python3 -c \"" +
            "import sqlite3; " +
            "db=sqlite3.connect('$SUI_DB_PATH'); " +
            "uid=db.execute('SELECT id FROM users LIMIT 1').fetchone()[0]; " +
            "db.execute(\\\"INSERT INTO tokens (desc, token, expiry, user_id) VALUES ('dreambox-auto', '$token', 0, ?)\\\", (uid,)); " +
            "db.commit()\""
    }
}
