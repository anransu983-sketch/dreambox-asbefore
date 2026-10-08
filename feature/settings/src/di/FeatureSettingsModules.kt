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

package com.suanran.dreambox.feature.settings.di

import com.suanran.dreambox.core.music.MusicPlayer
import com.suanran.dreambox.feature.settings.data.ThemeRepository
import com.suanran.dreambox.feature.settings.data.AnimationRepository
import com.suanran.dreambox.feature.settings.data.vps.SshScriptRunner
import com.suanran.dreambox.feature.settings.data.vps.SuiApiClient
import com.suanran.dreambox.feature.settings.data.vps.SuiTokenInjector
import com.suanran.dreambox.feature.settings.data.vps.VpsTestRepository
import com.suanran.dreambox.feature.settings.domain.InstalledAppsUseCase
import com.suanran.dreambox.feature.settings.presentation.backup.BackupRestoreViewModel
import com.suanran.dreambox.feature.settings.presentation.music.MusicPlayerManager
import com.suanran.dreambox.feature.settings.presentation.util.ChinaAppDetector
import com.suanran.dreambox.feature.settings.presentation.viewmodel.AccessControlViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.AnimationStoreViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.MetaFeatureViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.RemoteControllerViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.ThemeStoreViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.VpsTestViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.WifiAutomationViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureSettingsDomainModule = module {
    single { InstalledAppsUseCase(androidApplication(), get()) }
    single { ChinaAppDetector(androidApplication(), get(named("chinaAppCache"))) }
    single { ThemeRepository(androidApplication()) }
    single { AnimationRepository(androidApplication()) }
    single { VpsTestRepository() }
    single { SshScriptRunner() }
    single { SuiApiClient() }
    single { SuiTokenInjector(get()) }
    // 注意：这里用字符串 "settings" 而不是 MMKVProvider.ID_SETTINGS，避免 feature 层直接依赖 :data 模块。
    single<MusicPlayer> { MusicPlayerManager(androidContext(), get(named("settings")), get()) }
}

val featureSettingsViewModelModule = module {
    viewModel { AppSettingsViewModel(androidApplication(), get(), get(), get(), get(), get(), get()) }
    viewModel { NetworkSettingsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { WifiAutomationViewModel(androidApplication(), get(), get(), get(), get()) }
    viewModel { RemoteControllerViewModel(androidApplication(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { MetaFeatureViewModel(get(), get(), get(), get()) }
    viewModel { BackupRestoreViewModel(androidApplication(), get()) }
    viewModel { ThemeStoreViewModel(get(), get()) }
    viewModel { AnimationStoreViewModel(get(), get()) }
    viewModel { VpsTestViewModel(get(), get(), get()) }
}

val featureSettingsModules = listOf(featureSettingsDomainModule, featureSettingsViewModelModule)
