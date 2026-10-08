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

package com.suanran.dreambox.core.contract

import com.suanran.dreambox.core.model.AccessControlMode
import com.suanran.dreambox.core.model.AppColorTheme
import com.suanran.dreambox.core.model.AppLanguage
import com.suanran.dreambox.core.model.SettingsPageStyle
import com.suanran.dreambox.core.model.ThemeMode
import com.suanran.dreambox.core.model.WifiAutomationFallbackAction
import com.suanran.dreambox.core.model.WifiAutomationRule
import com.suanran.dreambox.core.model.tunnel.RunMode
import com.suanran.dreambox.core.model.tunnel.TunDnsMode
import com.suanran.dreambox.core.model.tunnel.TunStack

/** Contract for network settings consumed by runtime and feature modules. */
interface NetworkSettingsReader {
    val runMode: Preference<RunMode>
    val bypassPrivateNetwork: Preference<Boolean>
    val dnsHijack: Preference<Boolean>
    val allowBypass: Preference<Boolean>
    val enableIPv6: Preference<Boolean>
    val systemProxy: Preference<Boolean>
    val disableAllOverride: Preference<Boolean>
    val tunStack: Preference<TunStack>
    val tunRouteExcludeAddress: Preference<List<String>>
    val tunIfName: Preference<String>
    val tunMtu: Preference<Int>
    val tunAutoRoute: Preference<Boolean>
    val tunStrictRoute: Preference<Boolean>
    val tunAutoRedirect: Preference<Boolean>
    val tunIncludeAndroidUser: Preference<List<Int>>
    val tunDnsMode: Preference<TunDnsMode>
    val tunFakeIpRange: Preference<String>
    val tunFakeIpRange6: Preference<String>
    val accessControlMode: Preference<AccessControlMode>
    val accessControlPackages: Preference<Set<String>>
    val accessControlShowSystemApps: Preference<Boolean>
    val accessControlSelectedFirst: Preference<Boolean>
    val wifiAutomationEnabled: Preference<Boolean>
    val wifiAutomationLocationRequested: Preference<Boolean>
    val wifiAutomationRules: Preference<List<WifiAutomationRule>>
    val ebpfBypassCn: Preference<Boolean>
    val wifiAutomationOtherWifiAction: Preference<WifiAutomationFallbackAction>
    val wifiAutomationNoWifiAction: Preference<WifiAutomationFallbackAction>
    // Empty string means "do not switch" when the corresponding fallback action is Start.
    val wifiAutomationOtherWifiProfileUuid: Preference<String>
    val wifiAutomationNoWifiProfileUuid: Preference<String>
}

/** Contract for app settings consumed by runtime and feature modules. */
interface AppSettingsReader {
    val automaticRestart: Preference<Boolean>
    val autoUpdateCurrentProfileOnStart: Preference<Boolean>
    val customUserAgent: Preference<String>
    val initialSetupCompleted: Preference<Boolean>
    val excludeFromRecents: Preference<Boolean>
    val privacyPolicyAccepted: Preference<Boolean>
    val themeMode: Preference<ThemeMode>
    val settingsPageStyle: Preference<SettingsPageStyle>
    val appLanguage: Preference<AppLanguage>
    val colorTheme: Preference<AppColorTheme>
    val themeAccentColorArgb: Preference<Long>
    val invertOnPrimaryColors: Preference<Boolean>
    val homePreviewGuideShown: Preference<Boolean>
    val hideAppIcon: Preference<Boolean>
    val showTrafficNotification: Preference<Boolean>
    val superIslandEnabled: Preference<Boolean>
    val bottomBarAutoHide: Preference<Boolean>
    val topBarBlurEnabled: Preference<Boolean>
    val classicHomeEnabled: Preference<Boolean>
    val homeHitokotoEnabled: Preference<Boolean>
    val moeWallpaperUri: Preference<String>
    val moeWallpaperVideoMuted: Preference<Boolean>
    val moeWallpaperSourceUri: Preference<String>
    val moeWallpaperZoom: Preference<Float>
    val moeWallpaperBiasX: Preference<Float>
    val moeWallpaperBiasY: Preference<Float>
    val moeHomeQuote: Preference<String>
    val moeHomeQuoteAuthor: Preference<String>
    val moeSidebarExpanded: Preference<Boolean>
    val moeWallpaperScrimEnabled: Preference<Boolean>
    val pageScale: Preference<Float>
    val predictiveBackEnabled: Preference<Boolean>
    val predictiveBackMaxProgress: Preference<Float>
    val logLevel: Preference<Int>
    val autoCheckAppUpdate: Preference<Boolean>
    val updateSourceKey: Preference<String>
    val webDav: WebDavSettingsReader
    // 首页氛围动画（动画商店）
    val animationEnabled: Preference<Boolean>
    /** 当前选中的动画 id；空表示未选择。 */
    val activeAnimationId: Preference<String>
    /** 参数覆盖：0 = 使用动画 JSON 默认值。 */
    val animCountOverride: Preference<Int>
    val animSpeedOverride: Preference<Float>
    /** 探针面板服务器地址。 */
    val probeServerUrl: Preference<String>
    /** s-ui 面板地址（用户自建，如 http://38.209.71.17:2095/app）。 */
    val suiPanelUrl: Preference<String>
    val animOpacityOverride: Preference<Float>
    /** 音乐库：音乐服地址（Navidrome / Subsonic 协议）。 */
    val musicServerUrl: Preference<String>
    /** 音乐库：音乐服账号。 */
    val musicServerUser: Preference<String>
    /** 音乐库：音乐服密码。 */
    val musicServerPassword: Preference<String>
    /** 首页：在签名处显示当前播放的歌词。 */
    val musicLyricInSignature: Preference<Boolean>
    /** 首页：在侧边栏显示音乐播放器按钮。 */
    val musicPlayerInSidebar: Preference<Boolean>
    /** 播放页：歌词字号（sp）。 */
    val musicLyricFontSize: Preference<Float>
    /** 播放页：歌词颜色（ARGB Int）。 */
    val musicLyricColor: Preference<Int>
}

/** Read/write contract for WebDAV backup settings. */
interface WebDavSettingsReader {
    val webDavUrl: Preference<String>
    val webDavAccount: Preference<String>
    val webDavPassword: Preference<String>
    val webDavDir: Preference<String>
}

/** Read/write contract for feature store settings. */
interface FeatureStoreReader {
    fun consumePostUpdateColdStartPending(): Boolean
    val exitUiWhenBackground: Preference<Boolean>
    val showWebControlInProxy: Preference<Boolean>
    val isFirstOpen: Boolean
}

/** Write contract for access control management with side effects. */
interface AccessControlControllerContract {
    fun setAccessControlMode(mode: AccessControlMode)
    fun applyPackages(packages: Set<String>)
}

/** Write contract for app settings with side effects. */
interface AppSettingsControllerContract {
    fun applyAppLanguage(language: AppLanguage)
    fun applyCustomUserAgent(userAgent: String)
}

/** Write contract for network settings with restart orchestration. */
interface NetworkSettingsControllerContract {
    fun setRunMode(mode: RunMode)
    fun <T> setAndRestartIfNeeded(preference: Preference<T>, value: T)
    suspend fun startService(mode: RunMode): Result<Unit>
    fun requestRestartIfRunning()
}

/** Contract for bulk MMKV store reset operations used during backup restore. */
interface BulkStoreReset {
    fun clearStore(id: String)
}

/** Contract for synchronizing all key-value store data to disk before process exit. */
interface StoreSynchronizer {
    fun syncAll()
}
