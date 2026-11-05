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

package com.suanran.dreambox

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.retainedComponent
import com.suanran.dreambox.common.util.AppLanguageManager
import com.suanran.dreambox.core.util.PendingImportUrlHolder
import com.suanran.dreambox.core.util.path.APPLICATION_SCOPE_NAME
import com.suanran.dreambox.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.suanran.dreambox.presentation.component.dialog.ToastDialogHost
import com.suanran.dreambox.presentation.component.navigation.LocalTopBarHazeState
import com.suanran.dreambox.presentation.component.navigation.LocalTopBarHazeStyle
import com.suanran.dreambox.presentation.navigation.AppNavContainer
import com.suanran.dreambox.presentation.navigation.AppNavigationComponent
import com.suanran.dreambox.presentation.theme.ProvideAndroidPlatformTheme
import com.suanran.dreambox.presentation.theme.FlyTheme
import com.suanran.dreambox.presentation.theme.Radii
import com.suanran.dreambox.presentation.theme.Spacing
import com.suanran.dreambox.runtime.client.util.IntentController
import com.suanran.dreambox.runtime.client.util.extractPendingImportUrl
import com.tencent.mmkv.MMKV
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 高武门派七道氛围色（与插件页 SECTS 的 accent/accent2 一致）：种子色管全局配色，氛围管主界面的道统味。 */
private val sectAuraColors: Map<String, Pair<Color, Color>> =
    mapOf(
        "jian" to (Color(0xFF0284C7) to Color(0xFF38BDF8)),
        "fu" to (Color(0xFFD97706) to Color(0xFFF59E0B)),
        "zhen" to (Color(0xFF6366F1) to Color(0xFFA5B4FC)),
        "ti" to (Color(0xFFEA580C) to Color(0xFFFBBF24)),
        "kuilei" to (Color(0xFF0F766E) to Color(0xFF2DD4BF)),
        "gui" to (Color(0xFF16A34A) to Color(0xFF4ADE80)),
        "mo" to (Color(0xFFDC2626) to Color(0xFFA855F7)),
    )

/** 两团径向辉光：左上主色、右侧副色，只染界面边缘，alpha 压得很低不挡内容；Canvas 默认不拦截触摸。 */
@Composable
private fun SectAuraOverlay(colorA: Color, colorB: Color, dark: Boolean) {
    val a1 = if (dark) 0.15f else 0.10f
    val a2 = if (dark) 0.10f else 0.06f
    Canvas(Modifier.fillMaxSize()) {
        val c1 = Offset(size.width * 0.12f, -size.height * 0.05f)
        val r1 = size.width * 0.85f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(colorA.copy(alpha = a1), Color.Transparent),
                    center = c1,
                    radius = r1,
                ),
            center = c1,
            radius = r1,
        )
        val c2 = Offset(size.width * 1.02f, size.height * 0.32f)
        val r2 = size.width * 0.7f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(colorB.copy(alpha = a2), Color.Transparent),
                    center = c2,
                    radius = r2,
                ),
            center = c2,
            radius = r2,
        )
    }
}

class MainActivity : FragmentActivity() {
    companion object {
        private const val REQUEST_STARTUP_PERMISSIONS = 1001
        private const val MIUI_GET_INSTALLED_APPS_PERMISSION =
            "com.android.permission.GET_INSTALLED_APPS"
        private const val EXTRA_EXIT_UI_WHEN_BACKGROUND = "exit_ui_when_background"
        val pendingImportUrl: StateFlow<String?> = com.suanran.dreambox.core.util.PendingImportUrlHolder.pendingImportUrl

        fun clearPendingImportUrl() {
            com.suanran.dreambox.core.util.PendingImportUrlHolder.clear()
        }

        private val _pendingDeepLink = MutableStateFlow<String?>(null)
        val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()

        fun clearPendingDeepLink() {
            _pendingDeepLink.value = null
        }
    }

    private val appSettingsReader: com.suanran.dreambox.core.contract.AppSettingsReader by
        inject()
    private val appSettingsStore: com.suanran.dreambox.data.store.AppSettingsStore by inject()
    private val navigationComponent: AppNavigationComponent by lazy {
        retainedComponent { componentContext ->
            AppNavigationComponent(
                componentContext = componentContext,
                predictiveBackEnabledAtLaunch = appSettingsStore.predictiveBackEnabled.value,
            )
        }
    }
    private val featureStoreReader: com.suanran.dreambox.core.contract.FeatureStoreReader by inject()
    private val networkSettingsStorage:
        com.suanran.dreambox.data.store.NetworkSettingsStore by
        inject()
    private val profilesRepository: com.suanran.dreambox.runtime.client.ProfilesRepository by
        inject()
    private val proxyFacade: com.suanran.dreambox.runtime.client.ProxyFacade by inject()
    private val serviceCache: MMKV by inject(qualifier = named("service_cache"))
    private val applicationScope: CoroutineScope by
        inject(qualifier = named(APPLICATION_SCOPE_NAME))

    private lateinit var intentController: IntentController

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        applyExcludeFromRecents(appSettingsReader.excludeFromRecents.value)
        intentController = IntentController(lifecycleScope, packageName)
        if (intent?.action?.endsWith(".action.START_CLASH") == true ||
            intent?.action?.endsWith(".action.STOP_CLASH") == true) {
            // Use application scope so the coroutine survives finish()
            IntentController(applicationScope, packageName).handleIntent(intent)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
            finish()
            return
        }

        handleIntent(intent)

        requestStartupPermissions()

        setContent {
            val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
            val activitySettings by appSettingsViewModel.activitySettings.collectAsStateWithLifecycle()
            // 门派主题实时订阅：选道/叛门后主界面立刻换装，不用重启
            val sectId by appSettingsStore.sectPathId.state.collectAsStateWithLifecycle()
            val sectSpec = sectThemeSpecs[sectId]
            LaunchedEffect(activitySettings.excludeFromRecents) {
                this@MainActivity.applyExcludeFromRecents(activitySettings.excludeFromRecents)
            }

            ProvideAndroidPlatformTheme {
                val systemDensity = LocalDensity.current
                val scaledDensity =
                    remember(systemDensity, activitySettings.pageScale) {
                        Density(systemDensity.density * activitySettings.pageScale, systemDensity.fontScale)
                    }
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    FlyTheme(
                        themeMode = sectSpec?.mode ?: activitySettings.themeMode,
                        themeSeedColorArgb = sectSpec?.seedArgb ?: activitySettings.themeSeedColorArgb,
                        invertOnPrimaryColors = activitySettings.invertOnPrimaryColors,
                        radii = sectSpec?.radii ?: Radii(),
                        spacing = sectSpec?.spacing ?: Spacing(),
                    ) {
                        val topBarHazeState = remember { HazeState() }
                        val topBarBackground = MiuixTheme.colorScheme.surface
                        val topBarHazeStyle =
                            remember(topBarBackground) {
                                HazeBlurStyle {
                                    backgroundColor(topBarBackground)
                                    colorEffects(listOf(HazeColorEffect.tint(topBarBackground.copy(0.8f))))
                                }
                            }
                        CompositionLocalProvider(
                            LocalTopBarHazeState provides
                                if (activitySettings.topBarBlurEnabled) topBarHazeState else null,
                            LocalTopBarHazeStyle provides
                                if (activitySettings.topBarBlurEnabled) topBarHazeStyle else null,
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MiuixTheme.colorScheme.surface,
                            ) {
                              Box(Modifier.fillMaxSize()) {
                                AppNavContainer(navigationComponent)
                                // 高武门派道统氛围：选道后主界面罩一层该道的双色辉光（插件页同款氛围带进本体）
                                val aura = sectAuraColors[sectId]
                                if (aura != null) {
                                    val auraDark =
                                        when (sectSpec?.mode ?: activitySettings.themeMode) {
                                            com.suanran.dreambox.core.model.ThemeMode.Dark -> true
                                            com.suanran.dreambox.core.model.ThemeMode.Light -> false
                                            else -> isSystemInDarkTheme()
                                        }
                                    SectAuraOverlay(aura.first, aura.second, auraDark)
                                }
                                ToastDialogHost()

                              }
                            }
                        }
                    }
                }
            }
        }
        // Auto-start is handled exclusively by App.scheduleDeferredStartupTasks() to avoid duplicate invocations.
        // The AutoStartSessionGate ensures only one call site succeeds, but removing the duplicate here eliminates the redundant warmup await and clarifies the single responsibility: App owns cold-start auto-start, MainActivity owns UI.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_UI_HIDDEN || isFinishing) {
            return
        }
        if (!featureStoreReader.exitUiWhenBackground.value) {
            return
        }
        if (proxyFacade.runtimeSnapshot.value.running) {
            finishAndRemoveTask()
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let { safeIntent ->
            if (safeIntent.getBooleanExtra(EXTRA_EXIT_UI_WHEN_BACKGROUND, false)) {
                finishAndRemoveTask()
                return
            }
            extractPendingImportUrl(safeIntent)?.let { com.suanran.dreambox.core.util.PendingImportUrlHolder.set(it) }
            safeIntent.data?.let { uri ->
                val scheme = uri.scheme
                if (scheme == "clash" || scheme == "clashmeta") {
                    val host = uri.host
                    if (host == "install-config") {
                        val configUrl = uri.getQueryParameter("url")
                        if (!configUrl.isNullOrBlank()) {
                            com.suanran.dreambox.core.util.PendingImportUrlHolder.set(configUrl)
                        }
                    }
                } else if (scheme == "dreambox" && uri.host == "import-file") {
                    val fileUri = uri.getQueryParameter("uri")
                    if (!fileUri.isNullOrBlank()) {
                        com.suanran.dreambox.core.util.PendingImportFileHolder.set(
                            fileUri,
                            uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "node",
                        )
                    }
                } else if (scheme == "flycat") {
                    _pendingDeepLink.value = uri.toString()
                }
            }
            intentController.handleIntent(safeIntent)
        }
    }

    /**
     * On launch, auto-request the two runtime permissions the app needs: notifications (Android 13+)
     * and the MIUI dynamic "get installed apps" permission. Both are fired in a single system dialog
     * sequence; permissions that aren't runtime-requestable on this device/OS are simply skipped.
     */
    private fun requestStartupPermissions() {
        val permissions =
            buildList {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                if (
                    isMiuiGetInstalledAppsDynamicSupported() &&
                        checkSelfPermission(MIUI_GET_INSTALLED_APPS_PERMISSION) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    add(MIUI_GET_INSTALLED_APPS_PERMISSION)
                }
            }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_STARTUP_PERMISSIONS)
        }
    }

    private fun isMiuiGetInstalledAppsDynamicSupported(): Boolean =
        runCatching {
                packageManager
                    .getPermissionInfo(MIUI_GET_INSTALLED_APPS_PERMISSION, 0)
                    .packageName == "com.lbe.security.miui"
            }
            .getOrDefault(false)

    private fun applyExcludeFromRecents(exclude: Boolean) {
        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return@runCatching
            val currentTaskId = getTaskId()
            val task =
                am.appTasks.firstOrNull { appTask: ActivityManager.AppTask ->
                    val taskInfo = appTask.taskInfo ?: return@firstOrNull false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        taskInfo.taskId == currentTaskId
                    } else {
                        @Suppress("DEPRECATION")
                        taskInfo.id == currentTaskId
                    }
                }
            task?.setExcludeFromRecents(exclude)
        }
    }
}
