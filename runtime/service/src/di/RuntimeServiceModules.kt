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

package com.suanran.dreambox.runtime.service.di

import com.suanran.dreambox.core.contract.LogRecordGateway
import com.suanran.dreambox.core.contract.PrivilegedAccessReader
import com.suanran.dreambox.core.contract.ProfileStoreReader
import com.suanran.dreambox.core.contract.RuntimeLogWriter
import com.suanran.dreambox.core.contract.ServiceStateReader
import com.suanran.dreambox.runtime.api.wifi.WifiAutomationController
import com.suanran.dreambox.runtime.api.wifi.WifiSsidProvider
import com.suanran.dreambox.runtime.service.LogRecordServiceGateway
import com.suanran.dreambox.runtime.service.android.LogRecordService
import com.suanran.dreambox.runtime.service.android.WifiAutomationService
import com.suanran.dreambox.runtime.service.config.ServiceStore
import com.suanran.dreambox.runtime.service.records.ProfileStore
import com.suanran.dreambox.runtime.service.shizuku.ShizukuAccess
import com.suanran.dreambox.runtime.service.wifi.WifiSsidProviderImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module that exposes runtime:service implementations through
 * core-defined interfaces, so that consuming modules only depend on
 * the interface layer and never import runtime:service internals.
 */
val runtimeServiceModule = module {
    single<ServiceStateReader> { ServiceStore() }
    single<ProfileStoreReader> { ProfileStore }
    single<LogRecordGateway> { LogRecordServiceGateway() }
    single<RuntimeLogWriter> { RuntimeLogWriter { line -> LogRecordService.writeLog(line) } }
    single<WifiSsidProvider> { WifiSsidProviderImpl(androidContext()) }
    single<PrivilegedAccessReader> { ShizukuAccess }
    single<WifiAutomationController> {
        val ctx = androidContext()
        object : WifiAutomationController {
            override fun start() = WifiAutomationService.start(ctx)
            override fun stop() = WifiAutomationService.stop(ctx)
        }
    }
}
