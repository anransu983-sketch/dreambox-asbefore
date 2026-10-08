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

package com.suanran.dreambox.runtime.service.android

import android.content.Intent
import android.net.VpnService
import com.suanran.dreambox.core.Global
import com.suanran.dreambox.core.appContextOrSelf
import com.suanran.dreambox.core.model.tunnel.RunMode
import com.suanran.dreambox.runtime.service.BaseService
import com.suanran.dreambox.runtime.service.RuntimeForegroundController
import com.suanran.dreambox.runtime.service.ServicePowerController
import com.suanran.dreambox.runtime.service.notification.ServiceNotificationManager
import com.suanran.dreambox.runtime.service.session.spec.SessionRuntimeSpecFactory
import com.suanran.dreambox.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.suanran.dreambox.runtime.service.session.transport.VpnTunTransport
import com.suanran.dreambox.runtime.service.util.cancelAndJoinBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TunService : VpnService(), CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private val controller by lazy {
        RuntimeForegroundController(
            service = this,
            scope = this,
            mode = RunMode.VpnService,
            notificationConfig = ServiceNotificationManager.vpnConfig,
            logScope = RuntimeStartupLogStore.Scope.LOCAL_TUN,
            transportFactory = { VpnTunTransport(this) },
            specFactory = { ctx -> SessionRuntimeSpecFactory(ctx).createTunSpec() },
            logTag = "LOCAL_TUN",
        )
    }

    override fun onCreate() {
        super.onCreate()
        Global.init(appContextOrSelf)
        controller.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.onStartCommand()
        return START_STICKY
    }

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
        cancelAndJoinBlocking()
    }

    override fun onRevoke() {
        controller.onVpnRevoked()
        super.onRevoke()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        controller.onTrimMemory()
    }
}
