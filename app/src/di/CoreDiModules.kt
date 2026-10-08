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

package com.suanran.dreambox.di

import com.suanran.dreambox.common.util.AppLanguageManager
import com.suanran.dreambox.core.contract.AccessControlControllerContract
import com.suanran.dreambox.core.contract.AppIdentityReader
import com.suanran.dreambox.core.contract.AppLogSettings
import com.suanran.dreambox.core.contract.AppSettingsControllerContract
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.core.contract.AppShutdownHandler
import com.suanran.dreambox.core.contract.BroadcastNotifier
import com.suanran.dreambox.core.contract.BulkStoreReset
import com.suanran.dreambox.core.contract.ConnectionRepository
import com.suanran.dreambox.core.contract.FeatureStoreReader
import com.suanran.dreambox.core.contract.LanguageApplier
import com.suanran.dreambox.core.contract.LogStoreReader
import com.suanran.dreambox.core.contract.NetworkInfoReader
import com.suanran.dreambox.core.contract.NetworkSettingsControllerContract
import com.suanran.dreambox.core.contract.NetworkSettingsReader
import com.suanran.dreambox.core.contract.OverrideApplier
import com.suanran.dreambox.core.contract.OverrideApplyExecutor
import com.suanran.dreambox.core.contract.OverrideConfigRepository
import com.suanran.dreambox.core.contract.ProfileBindingReader
import com.suanran.dreambox.core.contract.ProvidersRepository
import com.suanran.dreambox.core.contract.ProxyDisplaySettingsReader
import com.suanran.dreambox.core.contract.ProxyGroupRepository
import com.suanran.dreambox.core.contract.RemoteControllerStoreReader
import com.suanran.dreambox.core.contract.RuntimeLifecycleCommand
import com.suanran.dreambox.core.contract.RuntimeRuleRepository
import com.suanran.dreambox.core.contract.ServiceBootstrapReader
import com.suanran.dreambox.core.contract.StoreSynchronizer
import com.suanran.dreambox.core.contract.SubStoreSettings
import com.suanran.dreambox.core.contract.TrafficStatisticsRepository
import com.suanran.dreambox.core.contract.UpdateSettings
import com.suanran.dreambox.core.model.tunnel.RunMode
import com.suanran.dreambox.core.util.path.APPLICATION_SCOPE_NAME
import com.suanran.dreambox.core.util.AssetDownloader
import com.suanran.dreambox.core.util.HttpAssetDownloader
import com.suanran.dreambox.data.collector.AppTrafficStatisticsCollector
import com.suanran.dreambox.data.controller.AccessControlCommandExecutor
import com.suanran.dreambox.data.controller.AccessControlController
import com.suanran.dreambox.data.controller.ActiveProfileOverrideApplier
import com.suanran.dreambox.data.controller.AppSettingsController
import com.suanran.dreambox.data.controller.NetworkSettingsCommandExecutor
import com.suanran.dreambox.data.controller.NetworkSettingsController
import com.suanran.dreambox.data.controller.OverrideApplicator
import com.suanran.dreambox.data.controller.ProvidersController
import com.suanran.dreambox.data.datasource.NetworkInfoService
import com.suanran.dreambox.data.logging.AppLogBuffer
import com.suanran.dreambox.data.repository.AppIdentityResolver
import com.suanran.dreambox.data.repository.OverrideBindingRepository
import com.suanran.dreambox.data.store.AppSettingsStore
import com.suanran.dreambox.data.store.AppStateManager
import com.suanran.dreambox.data.store.BuiltInOverrideFileStore
import com.suanran.dreambox.data.store.FeatureStore
import com.suanran.dreambox.data.store.LogStore
import com.suanran.dreambox.data.store.MetadataIndexStore
import com.suanran.dreambox.data.store.MMKVProvider
import com.suanran.dreambox.data.store.NetworkSettingsStore
import com.suanran.dreambox.data.store.OverrideConfigStore
import com.suanran.dreambox.data.store.ProfileBindingProvider
import com.suanran.dreambox.data.store.ProfileBindingStore
import com.suanran.dreambox.data.store.ProxyDisplaySettingsStore
import com.suanran.dreambox.data.store.RemoteControllerStore
import com.suanran.dreambox.data.store.TrafficStatisticsStore
import com.suanran.dreambox.data.store.room.createTrafficStatisticsDao
import com.suanran.dreambox.runtime.api.constants.Intents.actionOverrideChanged
import com.suanran.dreambox.runtime.api.constants.Intents.actionProfileChanged
import com.suanran.dreambox.runtime.api.contract.AutoStartExecutionGate
import com.suanran.dreambox.runtime.api.contract.ProfileRepositoryContract
import com.suanran.dreambox.runtime.api.contract.ProxyControlContract
import com.suanran.dreambox.runtime.api.contract.RuntimeStateMapper
import com.suanran.dreambox.runtime.client.ProfilesRepository
import com.suanran.dreambox.runtime.client.ProxyFacade
import com.suanran.dreambox.runtime.client.remote.ServiceClient
import com.suanran.dreambox.runtime.client.root.RootTunReloadScheduler
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// ─────────────────────────────────────────────────────────────────────────────
// ServiceBootstrapReaderImpl (merged from ServiceBootstrapReaderImpl.kt)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * [ServiceBootstrapReader] implementation backed by concrete data stores.
 *
 * Created once in [App] and registered in [ServiceBootstrapHolder] so that Android framework-instantiated Services can read settings without directly depending on data-store implementations.
 */
class ServiceBootstrapReaderImpl(
    private val appSettingsStore: AppSettingsStore,
    private val featureStore: FeatureStore,
    private val networkSettingsStore: NetworkSettingsStore,
    mmkvProvider: MMKVProvider,
) : ServiceBootstrapReader {
    private val serviceCache = mmkvProvider.getMMKV("service_cache")
    override val automaticRestart: Boolean
        get() = appSettingsStore.automaticRestart.value
    override val autoUpdateCurrentProfileOnStart: Boolean
        get() = appSettingsStore.autoUpdateCurrentProfileOnStart.value
    override val runMode: RunMode
        get() = networkSettingsStore.runMode.value
    override fun isRemoteControllerActive(): Boolean = RemoteControllerStore.isActive()
    override fun consumePostUpdateColdStartPending(): Boolean = featureStore.consumePostUpdateColdStartPending()
    override fun markAutoStartStarted() = AutoStartExecutionGate.markStarted(serviceCache)
    override fun clearAutoStart() = AutoStartExecutionGate.clear(serviceCache)
    override fun isAutoStartInFlight(): Boolean = AutoStartExecutionGate.isExecuting(serviceCache)
}

// ─────────────────────────────────────────────────────────────────────────────
// appFoundationModule (merged from FoundationModule.kt)
// ─────────────────────────────────────────────────────────────────────────────

val appFoundationModule = module {
    // ── Infrastructure ────────────────────────────────────────────────────────
    single<CoroutineScope>(named(APPLICATION_SCOPE_NAME)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single { MMKVProvider() }
    single<BulkStoreReset> { get<MMKVProvider>() }
    single<StoreSynchronizer> { get<MMKVProvider>() }
    // ── Context-dependent stores (need androidApplication) ────────────────────
    single { RemoteControllerStore(get(named(MMKVProvider.ID_REMOTE_CONTROLLER))).also { ServiceClient.configure(it) } }
    single<RemoteControllerStoreReader> { get<RemoteControllerStore>() }
    single { createTrafficStatisticsDao(androidApplication()) }
    single { LogStore(androidApplication(), get()) }
    single<LogStoreReader> { get<LogStore>() }
    single { NetworkInfoService() }
    single<NetworkInfoReader> { get<NetworkInfoService>() }
    single<AssetDownloader> { HttpAssetDownloader() }
    single {
        AppStateManager(
            appSettingsStore = get(),
            networkSettingsStore = get(),
            featureStore = get(),
            proxyDisplaySettingsStore = get(),
            trafficStatisticsStore = get(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// appDataModule — data-layer bindings (stores, controllers, repositories)
// ─────────────────────────────────────────────────────────────────────────────

val appDataModule = module {
    // ── Settings Controllers ──────────────────────────────────────────────────
    single { AppSettingsController(get(), languageApplier = LanguageApplier(AppLanguageManager::apply)) }
    single<AppSettingsControllerContract> { get<AppSettingsController>() }
    single {
        NetworkSettingsCommandExecutor(
            store = get(),
            restartProxy = { mode -> get<ProxyFacade>().startProxy(mode) },
        )
    }
    single {
        val proxyFacade = get<ProxyFacade>()
        NetworkSettingsController(
            store = get(),
            isRunning = { RuntimeStateMapper.isActuallyRunning(proxyFacade.runtimeSnapshot.value) },
            commandExecutor = get(),
        )
    }
    single<NetworkSettingsControllerContract> { get<NetworkSettingsController>() }
    single {
        val proxyFacade = get<ProxyFacade>()
        AccessControlCommandExecutor(
            restartProxy = { mode -> proxyFacade.startProxy(mode) },
        )
    }
    single {
        val proxyFacade = get<ProxyFacade>()
        AccessControlController(
            store = get(),
            isRunning = { proxyFacade.isRunning.value },
            resolveActiveMode = {
                RuntimeStateMapper.modeForOwner(proxyFacade.runtimeSnapshot.value.owner)
            },
            commandExecutor = get(),
        )
    }
    single<AccessControlControllerContract> { get<AccessControlController>() }
    // ── Data Services ─────────────────────────────────────────────────────────
    single {
        val appContext = androidContext()
        ProvidersController(
            context = appContext,
            queryProvidersAction = {
                ServiceClient.connect(appContext)
                ServiceClient.clash().queryProviders()
            },
            updateProviderAction = { type, name ->
                ServiceClient.connect(appContext)
                ServiceClient.clash().updateProvider(type, name)
            },
        )
    }
    // ── Override & Profile Binding Stores ─────────────────────────────────────
    single { MetadataIndexStore(androidContext()) }
    single { ProfileBindingStore(androidContext(), get()) }
    single<ProfileBindingProvider> { get<ProfileBindingStore>() }
    single { BuiltInOverrideFileStore(androidContext()) }
    single { OverrideConfigStore(androidContext(), get(), get()) }
    single { OverrideBindingRepository(get(), get()) }
    single {
        val appContext = androidContext()
        OverrideApplicator(get()) {
            appContext.sendBroadcast(
                android.content.Intent(
                    actionOverrideChanged(appContext.packageName)
                ).setPackage(appContext.packageName)
            )
            RootTunReloadScheduler.schedule(
                appContext,
                RootTunReloadScheduler.Reason.PROFILE_OVERRIDE_CHANGED,
            )
        }
    }
    single {
        val profilesRepository = get<ProfilesRepository>()
        ActiveProfileOverrideApplier(
            queryActiveProfile = { profilesRepository.queryActiveProfile() },
            bindingProvider = get(),
            overrideApplicator = get(),
        )
    }
    // Bind controller reader interfaces
    single<OverrideApplier> { get<ActiveProfileOverrideApplier>() }
    single<OverrideApplyExecutor> { get<OverrideApplicator>() }
    single<OverrideConfigRepository> { get<OverrideConfigStore>() }
    single<ProfileBindingReader> { get<OverrideBindingRepository>() }
    single<ProvidersRepository> { get<ProvidersController>() }
    single<AppIdentityReader> { get<AppIdentityResolver>() }
    single { AppIdentityResolver(androidContext()) }
}

// ─────────────────────────────────────────────────────────────────────────────
// appRuntimeModule — runtime-layer bindings (proxy facade, profiles, traffic)
// ─────────────────────────────────────────────────────────────────────────────

val appRuntimeModule = module {
    // ── Proxy Runtime ─────────────────────────────────────────────────────────
    single { ProxyFacade(androidContext(), get(), get()) }
    single<ProxyGroupRepository> { get<ProxyFacade>() }
    single<ConnectionRepository> { get<ProxyFacade>() }
    single<RuntimeRuleRepository> { get<ProxyFacade>() }
    single<ProxyControlContract> { get<ProxyFacade>() }
    single<RuntimeLifecycleCommand> {
        val facade = get<ProxyFacade>()
        object : RuntimeLifecycleCommand {
            override suspend fun stopProxy() = facade.stopProxy()
            override suspend fun reconcileRuntimeState() = facade.reconcileRuntimeState()
            override suspend fun applyRemoteControllerState() = facade.applyRemoteControllerState()
        }
    }
    single<BroadcastNotifier> {
        val ctx = androidContext()
        object : BroadcastNotifier {
            override fun notifyProfileChanged() {
                ctx.sendBroadcast(
                    android.content.Intent(
                        actionProfileChanged(ctx.packageName)
                    ).setPackage(ctx.packageName)
                )
            }
            override fun notifyOverrideChanged() {
                ctx.sendBroadcast(
                    android.content.Intent(
                        actionOverrideChanged(ctx.packageName)
                    ).setPackage(ctx.packageName)
                )
            }
        }
    }
    single { ProfilesRepository(androidContext()) }
    single<ProfileRepositoryContract> { get<ProfilesRepository>() }
    single {
        val facade = get<ProxyFacade>()
        AppTrafficStatisticsCollector(
            isRunningFlow = facade.isRunning,
            currentProfileId = { facade.currentProfile.value?.uuid?.toString() },
            trafficStatisticsStore = get(),
            appIdentityResolver = get(),
            trafficTotalFlow = facade.trafficTotal,
            connectionCloseFlow = facade.reliableConnectionCloseEvents,
            queryActiveProfileId = {
                facade.refreshCurrentProfile()
                facade.currentProfile.value?.uuid?.toString()
            },
        )
    }
    // ── Shutdown Handlers ─────────────────────────────────────────────────────
    single<AppShutdownHandler>(qualifier = named("proxy_facade_shutdown")) {
        AppShutdownHandler { get<ProxyFacade>().shutdown() }
    }
    single<AppShutdownHandler>(qualifier = named("identity_resolver_shutdown")) {
        AppShutdownHandler { get<AppIdentityResolver>().close() }
    }
    single<AppShutdownHandler>(qualifier = named("network_info_shutdown")) {
        AppShutdownHandler { get<NetworkInfoService>().close() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Combined module list
// ─────────────────────────────────────────────────────────────────────────────

val coreDiModules: List<Module> = listOf(appFoundationModule, appDataModule, appRuntimeModule)
