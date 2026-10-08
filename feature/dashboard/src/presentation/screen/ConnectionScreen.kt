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

package com.suanran.dreambox.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.feature.dashboard.presentation.component.ConnectionDetailSheet
import com.suanran.dreambox.feature.dashboard.presentation.component.ConnectionLeadingIcon
import com.suanran.dreambox.feature.dashboard.presentation.component.getProtocolColor
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.ConnectionCardItem
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.ConnectionSort
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.ConnectionTab
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.ConnectionViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TabRowWithContour
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import com.suanran.dreambox.presentation.component.misc.BocchiBlueFg
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiStar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

private val SortModes =
    listOf(ConnectionSort.Time, ConnectionSort.Upload, ConnectionSort.Download, ConnectionSort.Host)

private fun ConnectionSort.getDisplayName(): String =
    when (this) {
        ConnectionSort.Time -> FlyTxt.Connection.Sort.Time
        ConnectionSort.Upload -> FlyTxt.Connection.Sort.Upload
        ConnectionSort.Download -> FlyTxt.Connection.Sort.Download
        ConnectionSort.Host -> FlyTxt.Connection.Sort.Host
    }

@Composable
fun ConnectionScreen(navigator: Navigator) {
    val viewModel = koinViewModel<ConnectionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filteredConnections by viewModel.filteredConnections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val spacing = AppTheme.spacing

    val scrollBehavior = MiuixScrollBehavior()
    var searchText by remember { mutableStateOf(state.searchQuery) }
    var showSortPopup by remember { mutableStateOf(false) }

    var selectedConnection by remember {
        mutableStateOf<com.suanran.dreambox.core.model.ConnectionInfo?>(null)
    }
    var showDetailSheet by remember { mutableStateOf(false) }

    val tabs = listOf(FlyTxt.Connection.Tab.Active, FlyTxt.Connection.Tab.Closed)
    var selectedTabIndex by
        rememberSaveable(state.selectedTab) {
            mutableIntStateOf(
                when (state.selectedTab) {
                    ConnectionTab.ACTIVE -> 0
                    ConnectionTab.CLOSED -> 1
                }
            )
        }
    val selectedSortIndex =
        remember(state.sortBy) { SortModes.indexOf(state.sortBy).coerceAtLeast(0) }
    val emptyStateText =
        when {
            state.isLoading -> FlyTxt.Connection.Loading
            state.searchQuery.isNotEmpty() -> FlyTxt.Connection.NoResults
            else -> FlyTxt.Connection.Empty
        }

    LaunchedEffect(selectedTabIndex) {
        val tab = if (selectedTabIndex == 0) ConnectionTab.ACTIVE else ConnectionTab.CLOSED
        viewModel.setTab(tab)
    }

    LaunchedEffect(searchText) {
        if (searchText != state.searchQuery) {
            viewModel.setSearchQuery(searchText)
        }
    }

    LaunchedEffect(state.searchQuery) {
        if (searchText != state.searchQuery) {
            searchText = state.searchQuery
        }
    }

    LaunchedEffect(showDetailSheet, selectedConnection) {
        if (showDetailSheet && selectedConnection == null) {
            showDetailSheet = false
        }
    }
    LaunchedEffect(state.snapshot, state.selectedTab, showDetailSheet, selectedConnection?.id) {
        if (!showDetailSheet || state.selectedTab != ConnectionTab.ACTIVE) return@LaunchedEffect
        val selectedId = selectedConnection?.id ?: return@LaunchedEffect
        val updated = state.snapshot?.connections?.firstOrNull { it.id == selectedId }
        when {
            updated != null && updated != selectedConnection -> selectedConnection = updated
            updated == null -> showDetailSheet = false
        }
    }
    DisposableEffect(Unit) {
        viewModel.resetHistory()
        viewModel.startPolling()

        onDispose {
            viewModel.stopPolling()
        }
    }
    // Stop polling when app goes to background, resume on foreground (unless detail sheet is open)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, showDetailSheet) {
        var wasPollingBeforePause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPollingBeforePause = !showDetailSheet
                    viewModel.pausePolling()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPollingBeforePause) {
                        viewModel.resumePolling()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(showDetailSheet) {
        if (showDetailSheet) {
            viewModel.pausePolling()
        } else {
            viewModel.resumePolling()
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.Connection.Title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = {
                    Box {
                        IconButton(
                            modifier = Modifier.padding(end = spacing.space12),
                            onClick = { showSortPopup = true },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Sort,
                                contentDescription = FlyTxt.Connection.SortBy.trimEnd(':', '：'),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        OverlayListPopup(
                            show = showSortPopup,
                            alignment = PopupPositionProvider.Align.BottomEnd,
                            onDismissRequest = { showSortPopup = false },
                        ) {
                            ListPopupColumn {
                                SortModes.forEachIndexed { index, mode ->
                                    DropdownImpl(
                                        text = mode.getDisplayName(),
                                        optionSize = SortModes.size,
                                        isSelected = selectedSortIndex == index,
                                        onSelectedIndexChange = {
                                            if (mode != state.sortBy) viewModel.setSortBy(mode)
                                            showSortPopup = false
                                        },
                                        index = index,
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val closed = viewModel.closeAllConnections()
                            if (closed) {
                                showDetailSheet = false
                                selectedConnection = null
                            }
                        }
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = FlyTxt.Connection.CloseAll,
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
            contentPadding =
                PaddingValues(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = innerPadding.calculateTopPadding(),
                    bottom = mainLikePadding.calculateBottomPadding() + spacing.space12,
                ),
        ) {
            item {
                TextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space20),
                    label = FlyTxt.Connection.SearchHint,
                    singleLine = true,
                )
            }

            item {
                TabRowWithContour(
                    modifier = Modifier.padding(top = spacing.space12),
                    tabs = tabs,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                )
            }

            if (filteredConnections.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(spacing.space32),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyStateText,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                items(
                    items = filteredConnections,
                    key = { it.connectionInfo.id },
                    contentType = { "connection" },
                ) { connection ->
                    StashConnectionCard(
                        item = connection,
                        showCloseAction = state.selectedTab == ConnectionTab.ACTIVE,
                        onClick = {
                            val isSameConnection = selectedConnection?.id == connection.connectionInfo.id
                            if (!isSameConnection || !showDetailSheet) {
                                selectedConnection = connection.connectionInfo
                                showDetailSheet = true
                            }
                        },
                        onClose = {
                            scope.launch {
                                val closed = viewModel.closeConnection(connection.connectionInfo.id)
                                if (closed && selectedConnection?.id == connection.connectionInfo.id) {
                                    showDetailSheet = false
                                    selectedConnection = null
                                }
                            }
                        },
                        modifier = Modifier.padding(vertical = spacing.space6),
                    )
                }
            }
        }

        ConnectionDetailSheet(
            show = showDetailSheet,
            connectionInfo = selectedConnection,
            canInterrupt = state.selectedTab == ConnectionTab.ACTIVE,
            onInterruptConnection = { id -> viewModel.closeConnection(id) },
            onDismiss = { showDetailSheet = false },
            onDismissFinished = { selectedConnection = null },
        )
    }
}

/**
 * Stash 风格的连接卡片：三行布局
 * 第一行：开始时间（本地 HH:mm:ss）+ 域名:端口
 * 第二行：走的代理名（chains 最后一个，无则为 DIRECT）+ 匹配规则
 * 第三行：↑ 上传速度 ↓ 下载速度
 */
@Composable
private fun StashConnectionCard(
    item: ConnectionCardItem,
    showCloseAction: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val sizes = AppTheme.sizes
    val shape = RoundedCornerShape(radii.radius24)
    val interactionSource = remember { MutableInteractionSource() }
    val info = item.connectionInfo

    val hostPort = item.displayHost
    val startTimeText = remember(info.start) { formatStartTimeHms(info.start) }
    val proxyName = info.chains.lastOrNull().orEmpty().ifEmpty { FlyTxt.Connection.Direct }
    val ruleText = remember(info.rule, info.rulePayload) {
        when {
            info.rule.isNotBlank() && info.rulePayload.isNotBlank() ->
                "${info.rule} · ${info.rulePayload}"
            info.rule.isNotBlank() -> info.rule
            else -> info.rulePayload
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clip(shape)
            .background(Color.White)
            .border(1.dp, BocchiPink.copy(alpha = 0.45f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = spacing.space16, vertical = spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        ConnectionLeadingIcon(
            metadata = info.metadata,
            network = item.network,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            // 第一行：时间 + 域名:端口
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                if (startTimeText.isNotBlank()) {
                    Text(
                        text = startTimeText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = hostPort,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(),
                )
            }
            // 第二行：代理名 + 匹配规则
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Text(
                    text = proxyName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = getProtocolColor(item.network),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(),
                )
                if (ruleText.isNotBlank()) {
                    Text(
                        text = ruleText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                    )
                }
            }
            // 第三行：↑ 上传速度 ↓ 下载速度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                SpeedText(
                    label = "↑",
                    speedText = item.uploadSpeedText,
                    accentColor = BocchiBlueFg,
                )
                SpeedText(
                    label = "↓",
                    speedText = item.downloadSpeedText,
                    accentColor = BocchiStar,
                )
            }
        }
        if (showCloseAction) {
            Box(
                modifier = Modifier.heightIn(min = sizes.connectionLeadingIconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = FlyTxt.Connection.Detail.Action.Interrupt,
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClose)
                        .padding(horizontal = spacing.space12, vertical = spacing.space8),
                )
            }
        }
    }
}

@Composable
private fun SpeedText(
    label: String,
    speedText: String,
    accentColor: Color,
) {
    val spacing = AppTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space6),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = accentColor,
            maxLines = 1,
        )
        Text(
            text = speedText,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private val HmsTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

/** 把连接 start（ISO 时间）转成本地 HH:mm:ss，解析失败返回空串。 */
private fun formatStartTimeHms(start: String): String {
    if (start.isBlank()) return ""
    return try {
        java.time.OffsetDateTime.parse(start)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(HmsTimeFormatter)
    } catch (_: Exception) {
        ""
    }
}
