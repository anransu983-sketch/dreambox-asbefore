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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * VPS 服务器仓库：服务器列表以 JSON 存于独立 MMKV。
 *
 * 密码以明文存于本机，不打日志、不上报。
 */
class VpsTestRepository {
    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("vps_test") }
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_SERVERS = "servers"
    }

    suspend fun listServers(): List<VpsServer> = withContext(Dispatchers.IO) {
        runCatching {
            mmkv.decodeString(KEY_SERVERS)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<VpsServer>>(it) }
                .orEmpty()
        }.onFailure { Timber.w(it, "VpsTest: decode servers failed") }.getOrDefault(emptyList())
    }

    suspend fun saveServer(server: VpsServer) = withContext(Dispatchers.IO) {
        val list = listServers().toMutableList()
        val idx = list.indexOfFirst { it.id == server.id }
        if (idx >= 0) list[idx] = server else list.add(server)
        persist(list)
    }

    suspend fun deleteServer(id: String) = withContext(Dispatchers.IO) {
        persist(listServers().filterNot { it.id == id })
    }

    private fun persist(list: List<VpsServer>) {
        runCatching {
            mmkv.encode(KEY_SERVERS, json.encodeToString(list))
        }.onFailure { Timber.w(it, "VpsTest: persist servers failed") }
    }
}
