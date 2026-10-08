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

package com.suanran.dreambox.runtime.service.root

import android.content.Context
import com.suanran.dreambox.core.appContextOrSelf
import com.suanran.dreambox.core.model.ConnectionOverviewSnapshot
import com.suanran.dreambox.core.model.ConnectionSnapshot
import com.suanran.dreambox.core.model.proxy.ProxyGroup
import com.suanran.dreambox.core.model.proxy.ProxySort
import com.suanran.dreambox.runtime.api.root.RootTunOperationResult
import com.suanran.dreambox.runtime.api.root.RootTunStartRequest
import com.suanran.dreambox.runtime.api.root.RootTunStatus
import com.suanran.dreambox.runtime.api.root.rootTunDecode
import com.suanran.dreambox.runtime.api.root.rootTunEncode
import com.suanran.dreambox.runtime.service.android.RootTunService
import com.suanran.dreambox.service.root.IRootTunService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RootTunServiceBridge {
    private val binding = RootTunBinding.shared

    private suspend fun <T> remoteCall(
        context: Context,
        onBinderFailure: (() -> T)? = null,
        block: (IRootTunService) -> T,
    ): T = binding.remoteCall(context, onBinderFailure, block)

    private suspend fun bind(context: Context): IRootTunService = binding.bind(context)

    private suspend fun disconnect() = binding.disconnect()

    suspend fun start(context: Context): RootTunOperationResult {
        val appContext = context.appContextOrSelf
        val request = RootTunStartRequest(source = "service.bridge.start")
        val result =
            withContext(Dispatchers.IO) {
                val service = bind(context)
                val resultJson = service.startRootTun(rootTunEncode(request))
                rootTunDecode<RootTunOperationResult>(resultJson)
            }
        if (result.success) {
            RootTunService.start(appContext)
        }
        return result
    }

    suspend fun stop(context: Context): RootTunOperationResult {
        val result =
            remoteCall(
                context = context,
                onBinderFailure = { RootTunOperationResult(success = true) },
            ) { service ->
                val resultJson = service.stopRootTun()
                rootTunDecode<RootTunOperationResult>(resultJson)
            }
        disconnect()
        return result
    }

    suspend fun queryStatus(context: Context): RootTunStatus =
        remoteCall(context) { service ->
            val statusJson = service.queryStatus()
            rootTunDecode<RootTunStatus>(statusJson)
        }

    suspend fun queryTrafficNow(context: Context): Long =
        remoteCall(context) { service -> service.queryTrafficNow() }

    suspend fun queryTrafficTotal(context: Context): Long =
        remoteCall(context) { service -> service.queryTrafficTotal() }

    suspend fun queryProxyGroupNames(
        context: Context,
        excludeNotSelectable: Boolean = false,
    ): List<String> =
        remoteCall(context) { service ->
            rootTunDecode<List<String>>(service.queryProxyGroupNamesJson(excludeNotSelectable))
        }

    suspend fun queryProxyGroup(
        context: Context,
        name: String,
        sort: ProxySort = ProxySort.Default,
    ): ProxyGroup? =
        remoteCall(context) { service ->
            service.queryProxyGroupJson(name, sort.name)?.let {
                rootTunDecode<ProxyGroup>(it)
            }
        }

    suspend fun queryAllProxyGroups(
        context: Context,
        excludeNotSelectable: Boolean = false,
    ): List<ProxyGroup> =
        remoteCall(context) { service ->
            rootTunDecode<List<ProxyGroup>>(service.queryAllProxyGroupsJson(excludeNotSelectable))
        }

    suspend fun queryConnections(context: Context): ConnectionSnapshot =
        remoteCall(context) { service ->
            rootTunDecode<ConnectionSnapshot>(service.queryConnectionsJson())
        }

    suspend fun queryConnectionsOverview(context: Context): ConnectionOverviewSnapshot =
        remoteCall(context) { service ->
            rootTunDecode<ConnectionOverviewSnapshot>(service.queryConnectionsOverviewJson())
        }

    suspend fun closeConnection(context: Context, id: String): Boolean =
        remoteCall(context) { service -> service.closeConnection(id) }

    suspend fun closeAllConnections(context: Context) {
        remoteCall(context) { service -> service.closeAllConnections() }
    }
}
