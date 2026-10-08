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

import com.suanran.dreambox.BuildConfig
import com.suanran.dreambox.data.di.dataBackupModule
import com.suanran.dreambox.data.di.dataStoreModule
import com.suanran.dreambox.feature.about.UpdateBuildConfig
import com.suanran.dreambox.feature.about.di.featureUpdateModules
import com.suanran.dreambox.feature.dashboard.di.featureDashboardModules
import com.suanran.dreambox.feature.home.di.featureHomeModules
import com.suanran.dreambox.feature.log.di.featureLogModules
import com.suanran.dreambox.feature.override.di.featureOverrideModules
import com.suanran.dreambox.feature.profiles.di.featureProfilesModules
import com.suanran.dreambox.feature.proxy.di.featureProxyModules
import com.suanran.dreambox.feature.settings.di.featureSettingsModules
import com.suanran.dreambox.runtime.service.di.runtimeServiceModule
import org.koin.core.module.Module
import org.koin.dsl.module

val appUpdateModule = module {
    single {
        UpdateBuildConfig(
            versionName = BuildConfig.VERSION_NAME,
            updateSource = BuildConfig.UPDATE_SOURCE,
            uiBuildId = BuildConfig.UI_BUILD_ID,
            updateRepository = BuildConfig.UPDATE_REPOSITORY,
            updateMirrorTemplates = BuildConfig.UPDATE_MIRROR_TEMPLATES,
        )
    }
}

val appModule: List<Module> =
    coreDiModules +
        listOf(dataStoreModule, dataBackupModule, runtimeServiceModule, appUpdateModule) +
        featureUpdateModules +
        featureHomeModules +
        featureLogModules +
        featureProfilesModules +
        featureSettingsModules +
        featureProxyModules +
        featureOverrideModules +
        featureDashboardModules
