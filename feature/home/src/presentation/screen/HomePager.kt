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

package com.suanran.dreambox.feature.home.presentation.screen

import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.geometry.Offset
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.core.util.format.formatBytesForDisplay
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.model.traffic.TrafficData
import com.suanran.dreambox.core.model.tunnel.RunMode
import com.suanran.dreambox.core.model.tunnel.TunnelState.Mode
import com.suanran.dreambox.feature.home.presentation.viewmodel.HomeProxyControlState
import com.suanran.dreambox.feature.home.presentation.viewmodel.HomeViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.navigation.LocalDetailNavigator
import com.suanran.dreambox.presentation.component.navigation.LocalMainPagerState
import com.suanran.dreambox.presentation.component.navigation.LocalNavigator
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.UiDp
import com.suanran.dreambox.presentation.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomePager(mainInnerPadding: PaddingValues, onOpenDashboard: (() -> Unit)? = null, isActive: Boolean) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val navigator = LocalNavigator.current
    val detailNavigator = LocalDetailNavigator.current
    val mainPagerState = LocalMainPagerState.current
    val openSecondary: (Route) -> Unit = { route -> if (detailNavigator != null) { detailNavigator.replaceAll(listOf(route)) } else { navigator.push(route) } }
    val controlState by homeViewModel.controlState.collectAsStateWithLifecycle()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val homeProfileState by homeViewModel.homeProfileState.collectAsStateWithLifecycle()
    val isRemoteController by homeViewModel.isRemoteController.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { homeViewModel.refreshRunMode() }

    LaunchedEffect(isActive) { homeViewModel.setHomeScreenActive(isActive) }

    DisposableEffect(homeViewModel) { onDispose { homeViewModel.setHomeScreenActive(false) } }

    DisposableEffect(lifecycleOwner, homeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.reconcileRuntimeState()
                homeViewModel.refreshRunMode()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            context.toast(it, Toast.LENGTH_LONG)
            homeViewModel.consumeError()
        }
    }

    LaunchedEffect(uiState.message) { uiState.message?.let { homeViewModel.consumeMessage() } }

    val isRunning = controlState == HomeProxyControlState.Running
    val isBusy =
        controlState == HomeProxyControlState.Connecting ||
            controlState == HomeProxyControlState.Disconnecting

    // 城堡点击 = 启停：忙时（连接中/断开中）忽略，防连点重复启停。
    val onHomeClick: () -> Unit =
        click@{
            if (isRemoteController || isBusy) return@click
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            if (isRunning) {
                coroutineScope.launch { homeViewModel.stopProxy() }
            } else {
                val target = homeProfileState.recommendedProfile
                if (!homeProfileState.hasEnabledProfile || target == null) {
                    context.toast(FlyTxt.ProfilesVM.Error.ProfileNotExist)
                } else {
                    homeViewModel.startProxy(profileId = target.uuid.toString(), mode = null)
                }
            }
        }

    HomeSkyScene(
        homeViewModel = homeViewModel,
        isRunning = isRunning,
        isActive = isActive,
        statusText =
            when (controlState) {
                HomeProxyControlState.Running -> FlyTxt.Home.Status.Connected
                HomeProxyControlState.Connecting -> FlyTxt.Home.Status.Connecting
                HomeProxyControlState.Disconnecting -> FlyTxt.Home.Status.Disconnecting
                HomeProxyControlState.Lost -> FlyTxt.Home.Status.Lost
                HomeProxyControlState.Idle -> FlyTxt.Home.Status.Disconnected
            },
        idleHint =
            FlyTxt.Home.Status.TapToConnect.takeIf { controlState == HomeProxyControlState.Idle },
        onHomeClick = onHomeClick,
        onOpenPacks = { openSecondary(Route.FeaturePacks) },
        onOpenTraffic = { openSecondary(Route.TrafficStatistics) },
        onOpenDashboard = onOpenDashboard,
    )
}

/**
 * 天空之城场景的接线层：1Hz 的 flows（节点/延迟/流量/运行时长）只收在这里，
 * 每秒重组不碰 HomePager 本体和场景底图。
 */
@Composable
private fun HomeSkyScene(
    homeViewModel: HomeViewModel,
    isRunning: Boolean,
    isActive: Boolean,
    statusText: String,
    idleHint: String?,
    onHomeClick: () -> Unit,
    onOpenPacks: () -> Unit,
    onOpenTraffic: () -> Unit,
    onOpenDashboard: (() -> Unit)? = null,
) {
    val name by homeViewModel.selectedServerName.collectAsStateWithLifecycle()
    val ping by homeViewModel.selectedServerPing.collectAsStateWithLifecycle()
    val traffic by homeViewModel.trafficData.collectAsStateWithLifecycle()
    val todayBytes by homeViewModel.todayTrafficBytes.collectAsStateWithLifecycle()
    val currentProfile by homeViewModel.currentProfile.collectAsStateWithLifecycle()
    val snapshot by homeViewModel.runtimeSnapshot.collectAsStateWithLifecycle()

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRunning, isActive) {
        while (isRunning && isActive) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uptime =
        remember(snapshot.startedAt, nowMs, isRunning) {
            val s = snapshot.startedAt
            if (!isRunning || s == null) {
                null
            } else {
                ((nowMs - s) / 1000).coerceAtLeast(0).let {
                    "%02d:%02d:%02d".format(it / 3600, it % 3600 / 60, it % 60)
                }
            }
        }
    fun speed(b: Long): String? =
        if (!isRunning || b <= 0) null else formatBytesForDisplay(b).let { (v, u) -> "$v $u/s" }

    // 浮石入口：画里三块发光浮石，左石=插件、右石=流量统计、中下石=面板（核心面板，1.183 加回：天空之城改版后首页丢了面板入口，onOpenDashboard 接进来却没地方点）
    val packSpots =
        remember {
            listOf(
                SkyPackSpot("packs", "插件", Offset(0.220f, 0.545f)),
                SkyPackSpot("traffic", "流量统计", Offset(0.789f, 0.539f)),
                SkyPackSpot("panel", "面板", Offset(0.505f, 0.615f)),
            )
        }

    SkyIslandScene(
        state =
            SkyHomeState(
                connected = isRunning,
                statusText = statusText,
                detailText =
                    if (isRunning) {
                        listOfNotNull(name, uptime).joinToString(" · ").ifBlank { null }
                    } else {
                        idleHint
                    },
                // 65535 是内核测速失败哨兵、>5000 不算有效延迟（口径同 NodeGroups），别让它冒充延迟
                latencyMs = ping?.takeIf { isRunning && it in 1..5000 },
                downText = speed(traffic.download),
                upText = speed(traffic.upload),
                nodeName = if (isRunning) name else null,
                uptimeText = uptime,
                todayTrafficText = todayBytes?.let { formatBytesForDisplay(it).let { (v, u) -> "$v $u" } },
                profileName = if (isRunning) currentProfile?.name else null,
                quotaLeftText =
                    (if (isRunning) currentProfile else null)?.let { p ->
                        if (p.total > 0) {
                            formatBytesForDisplay((p.total - p.upload - p.download).coerceAtLeast(0)).let { (v, u) -> "$v $u" }
                        } else {
                            null
                        }
                    },
            ),
        art = remember { skyNightArt("file:///android_asset/sky_island_night.webp") },
        onHomeClick = onHomeClick,
        packSpots = packSpots,
        onOpenPack = { id ->
            when (id) {
                "traffic" -> onOpenTraffic()
                "panel" -> onOpenDashboard?.invoke()
                else -> onOpenPacks()
            }
        },
    )
}
