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

package com.suanran.dreambox.feature.proxy.presentation.screen

import com.suanran.dreambox.feature.proxy.presentation.screen.node.nodeLatencyLabel
import com.suanran.dreambox.feature.proxy.presentation.screen.node.displayName
import com.suanran.dreambox.presentation.component.misc.rememberDelayHistory
import com.suanran.dreambox.presentation.component.dialog.AppActionBottomSheet
import androidx.compose.foundation.layout.height
import com.suanran.dreambox.presentation.component.misc.DelayHistory
import androidx.compose.runtime.mutableStateMapOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.suanran.dreambox.presentation.component.misc.CustomIcons
import com.suanran.dreambox.presentation.util.toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.model.proxy.DelayTestMode
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.core.model.proxy.ProxyDisplayMode
import com.suanran.dreambox.core.model.proxy.ProxyGroupInfo
import com.suanran.dreambox.core.model.proxy.ProxySortMode
import com.suanran.dreambox.feature.proxy.presentation.screen.node.NodeSearchToolbar
import com.suanran.dreambox.feature.proxy.presentation.screen.node.NodeSortPopup
import com.suanran.dreambox.feature.proxy.presentation.screen.node.NodeTestPullToRefresh
import com.suanran.dreambox.feature.proxy.presentation.screen.node.nodeGroupItems
import com.suanran.dreambox.feature.proxy.presentation.screen.node.nodeListRows
import com.suanran.dreambox.feature.proxy.presentation.util.KeepLazyListTopAnchorOnReorder
import com.suanran.dreambox.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.misc.CenteredText
import com.suanran.dreambox.presentation.component.misc.EmojiAwareText
import com.suanran.dreambox.presentation.component.navigation.LocalDetailNavigator
import com.suanran.dreambox.presentation.component.navigation.LocalPagerState
import com.suanran.dreambox.presentation.component.navigation.LocalTopBarHazeState
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.component.navigation.isSplitShell
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.ListChevronsUpDown
import com.suanran.dreambox.presentation.icon.flycat.Search
import com.suanran.dreambox.presentation.icon.flycat.Folders
import com.suanran.dreambox.presentation.icon.flycat.Speed
import com.suanran.dreambox.presentation.theme.AnimationSpecs
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.theme.LocalSpacing
import com.suanran.dreambox.presentation.theme.UiDp
import com.suanran.dreambox.presentation.theme.rememberRowReveal
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

private fun LazyListState.isScrolledFromTop(): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

// 波奇酱风配色
private val BocchiPink = Color(0xFFFFB7C5)
private val BocchiBlue = Color(0xFFA8D8F0)
private val BocchiStar = Color(0xFFFF8FAB)
private val BocchiGreen = Color(0xFF66BB6A)

// animateScrollToItem races across arbitrary distances at full speed, so locating a far-away
// node reads as a blink. Cap the animated stretch at roughly one viewport: snap silently to
// just outside it, then glide the remainder in with a fixed-duration decelerating tween.
internal suspend fun LazyListState.animateLocateToItem(targetIndex: Int) {
    if (layoutInfo.visibleItemsInfo.isEmpty()) {
        scrollToItem(targetIndex)
        return
    }
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val visible = layoutInfo.visibleItemsInfo
    val avgItemSize =
        (visible.sumOf { it.size } / visible.size + layoutInfo.mainAxisItemSpacing).coerceAtLeast(1)
    val approachItems = viewport / avgItemSize + 1
    val distanceItems = targetIndex - firstVisibleItemIndex
    if (abs(distanceItems) > approachItems) {
        val preIndex =
            if (distanceItems > 0) targetIndex - approachItems else targetIndex + approachItems
        scrollToItem(preIndex.coerceAtLeast(0))
    }
    val remaining =
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset?.toFloat()
            ?: ((targetIndex - firstVisibleItemIndex) * avgItemSize.toFloat() -
                firstVisibleItemScrollOffset)
    animateScrollBy(
        value = remaining,
        animationSpec = tween(durationMillis = 650, easing = AnimationSpecs.EmphasizedDecelerate),
    )
    // Node cards are uniform so the estimate lands exactly; settle any residual drift quietly.
    val residual = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset
    when {
        residual == null -> scrollToItem(targetIndex)
        residual != 0 -> scrollBy(residual.toFloat())
    }
}

@Composable
fun ProxyPager(
    mainInnerPadding: PaddingValues,
    onNavigateToProviders: (() -> Unit)?,
    isActive: Boolean,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    val proxyControl = koinInject<com.suanran.dreambox.runtime.api.contract.ProxyControlContract>()
    val networkSettings = koinInject<com.suanran.dreambox.core.contract.NetworkSettingsReader>()
    val isProxyRunning by proxyControl.isRunning.collectAsStateWithLifecycle()
    val primaryNode by proxyControl.resolvedPrimaryNode.collectAsStateWithLifecycle()
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsStateWithLifecycle()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsStateWithLifecycle()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsStateWithLifecycle()
    val delayTestProgress by proxyViewModel.delayTestProgress.collectAsStateWithLifecycle()
    val sortMode by proxyViewModel.sortMode.collectAsStateWithLifecycle()
    val delayTestMode by proxyViewModel.delayTestMode.collectAsStateWithLifecycle()
    val displayMode by proxyViewModel.displayMode.collectAsStateWithLifecycle()
    val groupScrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)
    val pagerState = LocalPagerState.current
    val topBarHazeState = LocalTopBarHazeState.current
    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    val inSplitShell = LocalDetailNavigator.current.isSplitShell
    val uiSelectedGroupName by proxyViewModel.uiSelectedGroupName.collectAsStateWithLifecycle()
    val groupSelection = rememberProxyGroupSelectionState(
        proxyGroups = proxyGroups,
        onRefreshGroup = proxyViewModel::refreshGroup,
        retainLastKnownGroup = !inSplitShell,
        controlledSelectedGroupName = if (inSplitShell) uiSelectedGroupName else null,
        onControlledSelectedGroupNameChange = if (inSplitShell) proxyViewModel::selectUiGroup else null,
    )
    val selectedGroupName = groupSelection.selectedGroupName
    // 策略组自定义图标：长按组卡片换图（CustomIcons 机制，与导航栏/模块图标同一套）。
    val groupIconContext = LocalContext.current
    var pendingGroupIconKey by remember { mutableStateOf<String?>(null) }
    val groupIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val key = pendingGroupIconKey
        pendingGroupIconKey = null
        if (uri != null && key != null) {
            if (CustomIcons.saveFromUri(groupIconContext, key, uri)) {
                groupIconContext.toast("策略组图标已更新")
            } else {
                groupIconContext.toast("图片读取失败，换一张试试")
            }
        }
    }
    val onGroupIconPick: (ProxyGroupInfo) -> Unit = { group ->
        pendingGroupIconKey = CustomIcons.keyFor("group", group.name)
        groupIconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val onGroupIconReset: (ProxyGroupInfo) -> Unit = { group ->
        CustomIcons.clear(groupIconContext, CustomIcons.keyFor("group", group.name))
    }
    val displayGroup = groupSelection.displayGroup
    val coroutineScope = rememberCoroutineScope()
    val onToggleProxyRunning: (Boolean) -> Unit = remember(coroutineScope, proxyControl, networkSettings) {
        { enable ->
            coroutineScope.launch {
                try {
                    if (enable) {
                        proxyControl.startProxy(networkSettings.runMode.value)
                    } else {
                        proxyControl.stopProxy()
                    }
                } catch (_: Exception) {
                    // 状态以 isRunning 流为准，失败时开关会自动回弹
                }
            }
        }
    }
    val statusBarContent: @Composable () -> Unit = {
        val nodeName = remember(primaryNode, proxyGroups) {
            (primaryNode?.name ?: proxyGroups.firstOrNull()?.now.orEmpty()).trim()
                .ifBlank { FlyTxt.Proxy.Mode.Direct }
        }
        ProxyStatusBar(
            isRunning = isProxyRunning,
            nodeName = nodeName,
            onToggle = onToggleProxyRunning,
        )
    }
    val groupListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val nodeListState = rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }
    var nodeSearchQuery by rememberSaveable(selectedGroupName) { mutableStateOf("") }
    var nodeSearchVisible by rememberSaveable(selectedGroupName) { mutableStateOf(false) }
    val requestSelectedGroupDelayTest = remember(coroutineScope, nodeListState, selectedGroupName, proxyViewModel) {
        {
            val groupName = selectedGroupName ?: return@remember
            coroutineScope.launch {
                if (nodeListState.isScrolledFromTop()) {
                    nodeListState.scrollToItem(0)
                }
                proxyViewModel.testDelay(groupName)
            }
        }
    }
    val locateCurrentProxy = remember(coroutineScope, displayGroup, nodeListState, selectedGroupName, displayMode) {
        if (selectedGroupName == null) {
            null
        } else {
            displayGroup?.takeIf { group -> group.name == selectedGroupName }?.let { group ->
                fun() {
                    val proxyIndex =
                        group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                    if (proxyIndex < 0) return
                    // 搜索栏固定在滚动列表外，列表无头部偏移。
                    // 在双栏模式中，每行容纳 2 个代理，因此除以 2。
                    val listItemIndex =
                        if (displayMode.isSingleColumn) proxyIndex
                        else proxyIndex / 2
                    coroutineScope.launch {
                        nodeListState.animateLocateToItem(listItemIndex)
                    }
                }
            }
        }
    }
    BackHandler(enabled = nodeSearchVisible) {
        nodeSearchVisible = false
        nodeSearchQuery = ""
    }
    BackHandler(enabled = selectedGroupName != null && !inSplitShell) { groupSelection.clearSelection() }
    // Tablet: auto-select the first group so the right pane is never empty.
    LaunchedEffect(inSplitShell, proxyGroups, selectedGroupName) {
        if (!inSplitShell || proxyGroups.isEmpty()) return@LaunchedEffect
        if (selectedGroupName == null || proxyGroups.none { it.name == selectedGroupName }) {
            groupSelection.selectGroup(proxyGroups.first())
        }
    }

    // 自建单节点配置（全配置只有一个真实节点）：代理页直接落到主策略组的节点列表，
    // 只显示节点；策略组列表点返回/面包屑才进。用户手动返回后本次不再强拉回去，
    // 换配置（叶子节点名变化）后重新生效。多节点配置不受影响，仍落在策略组列表。
    val singleLeafNodeName = remember(proxyGroups) {
        proxyGroups.flatMap { it.proxies }.filter { !it.isGroup }.map { it.name }.distinct().singleOrNull()
    }
    var autoEnteredLeaf by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(singleLeafNodeName, inSplitShell, selectedGroupName) {
        if (inSplitShell || selectedGroupName != null) return@LaunchedEffect
        val leaf = singleLeafNodeName ?: return@LaunchedEffect
        if (autoEnteredLeaf == leaf) return@LaunchedEffect
        val mainGroup = proxyGroups.firstOrNull { it.type in Proxy.Type.manuallySelectable }
            ?: proxyGroups.firstOrNull() ?: return@LaunchedEffect
        autoEnteredLeaf = leaf
        groupSelection.selectGroup(mainGroup)
    }

    LaunchedEffect(isActive) { proxyViewModel.ensureCoreLoaded(isActive, source = "proxy_page") }
    LaunchedEffect(isActive, inSplitShell) { if (!isActive && !inSplitShell) nodeSearchQuery = "" }

    // 应用在长时间后台运行后返回前台时强制刷新，因为WhileSubscribed(5000)会停止上游收集，且同步循环可能已被限流或阻塞在旧互斥锁后面。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isActive) proxyViewModel.onForegroundResume()
    }

    DisposableEffect(proxyViewModel) {
        onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_page") }
    }

    Scaffold(
        floatingActionButton = {},
        topBar = {
            if (!inSplitShell && SKY_PROXY_PAGE) {
                Box(Modifier.fillMaxWidth().height(0.dp))
            } else {
            PagerTopBar(
                scrollBehavior = groupScrollBehavior,
                onNavigateToProviders = if (inSplitShell) null else onNavigateToProviders,
                onTestAllDelay = { proxyViewModel.testDelay() },
                locateCurrentProxy = if (inSplitShell) null else locateCurrentProxy,
                showSortPopup = showSortPopup,
                onShowSortPopupChange = { showSortPopup = it },
                displayMode = displayMode,
                sortMode = sortMode,
                onDisplayModeSelected = proxyViewModel::setDisplayMode,
                onSortSelected = proxyViewModel::setSortMode,
                delayTestMode = delayTestMode,
                onDelayTestModeSelected = proxyViewModel::setDelayTestMode,
                selectedGroupName = if (inSplitShell) null else selectedGroupName,
                singleGroup = proxyGroups.size == 1,
                onBackToGroups = groupSelection.clearSelection,
                searchVisible = nodeSearchVisible,
                // 搜索只挂在节点列表页：组列表/双栏左栏不放搜索入口。
                onSearchToggle = if (inSplitShell || selectedGroupName == null) {
                    null
                } else {
                    {
                        nodeSearchVisible = !nodeSearchVisible
                        if (!nodeSearchVisible) nodeSearchQuery = ""
                    }
                },
                onTitleScrollTop = {
                    // 双栏左栏只挂 groupListState（nodeListState 未上屏）；单栏在组列表/节点列表间切换。
                    val listState = if (inSplitShell || selectedGroupName == null) groupListState else nodeListState
                    coroutineScope.launch {
                        if (listState.isScrolledFromTop()) {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
            )
            }
        },
    ) {
        if (!inSplitShell && SKY_PROXY_PAGE) {
            SkyProxyScene(
                isActive = isActive,
                groups = proxyGroups,
                selectedGroup = selectedGroupName?.let { groupSelection.selectedGroup ?: displayGroup },
                testingGroupNames = testingGroupNames,
                testingProxyNames = testingProxyNames,
                talkState = HarborTalkState(
                    running = isProxyRunning,
                    nodeName = primaryNode?.name,
                    pingMs = primaryNode?.let { pn -> proxyGroups.flatMap { it.proxies }.firstOrNull { it.name == pn.name }?.delay } ?: -1,
                    groupName = null,
                    groupCount = proxyGroups.size,
                    nodeCount = 0,
                ),
                searchQuery = nodeSearchQuery,
                onSearchQueryChange = { nodeSearchQuery = it },
                onSelectGroup = groupSelection.selectGroup,
                onBackToGroups = groupSelection.clearSelection,
                onSelectProxy = { g, p -> proxyViewModel.selectProxy(g, p) },
                onForceSelectProxy = { g, p -> proxyViewModel.forceSelectProxy(g, p) },
                onTestAll = { proxyViewModel.testDelay() },
                onTestGroup = { proxyViewModel.testDelay(it) },
                onNavigateToProviders = onNavigateToProviders,
                searchVisible = nodeSearchVisible,
                onSearchVisibleChange = { nodeSearchVisible = it },
            )
        } else {
        Box(
            modifier = Modifier.fillMaxSize().let { mod -> if (topBarHazeState != null) mod.hazeSource(state = topBarHazeState) else mod }
        ) {
            if (inSplitShell) {
                // Tablet: only show group list; node detail is in the right pane.
                if (proxyGroups.isEmpty()) {
                    CenteredText(
                        firstLine = FlyTxt.Proxy.Empty.NoNodes,
                        secondLine = FlyTxt.Proxy.Empty.Hint,
                        showEmptyResourceIllustration = true,
                    )
                } else {
                    ProxyContent(
                        proxyGroups = proxyGroups,
                        displayMode = displayMode,
                        scrollBehavior = groupScrollBehavior,
                        listState = groupListState,
                        innerPadding = it,
                        mainInnerPadding = mainInnerPadding,
                        testingGroupNames = testingGroupNames,
                        onGroupClick = groupSelection.selectGroup,
                        onGroupDelayTestClick = { group -> proxyViewModel.testDelay(group.name) },
                        onGroupBoundsChanged = { _, _ -> },
                        onGroupIconPick = onGroupIconPick,
                        onGroupIconReset = onGroupIconReset,
                        statusBar = statusBarContent,
                    )
                }
            } else {
                AnimatedContent(
                    targetState = selectedGroupName,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetSlideInDuration, easing = AnimationSpecs.Legacy),
                                initialOffsetX = { it },
                            ) + fadeIn(animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetFadeInDuration))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetSlideOutDuration, easing = AnimationSpecs.Legacy),
                                    targetOffsetX = { -it / 3 },
                                ) + fadeOut(animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetFadeOutDuration)))
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetSlideOutDuration, easing = AnimationSpecs.Legacy),
                                initialOffsetX = { -it / 3 },
                            ) + fadeIn(animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetFadeInDuration))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetSlideInDuration, easing = AnimationSpecs.Legacy),
                                    targetOffsetX = { it },
                                ) + fadeOut(animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetFadeOutDuration)))
                        }
                    },
                    label = "proxy_content_slide",
                ) { targetGroupName ->
                    if (targetGroupName == null) {
                        if (proxyGroups.isEmpty()) {
                            CenteredText(
                                firstLine = FlyTxt.Proxy.Empty.NoNodes,
                                secondLine = FlyTxt.Proxy.Empty.Hint,
                                showEmptyResourceIllustration = true,
                            )
                        } else {
                            ProxyContent(
                                proxyGroups = proxyGroups,
                                displayMode = displayMode,
                                scrollBehavior = groupScrollBehavior,
                                listState = groupListState,
                                innerPadding = it,
                                mainInnerPadding = mainInnerPadding,
                                testingGroupNames = testingGroupNames,
                                onGroupClick = groupSelection.selectGroup,
                                onGroupDelayTestClick = { group -> proxyViewModel.testDelay(group.name) },
                                onGroupBoundsChanged = { _, _ -> },
                                onGroupIconPick = onGroupIconPick,
                                onGroupIconReset = onGroupIconReset,
                                statusBar = statusBarContent,
                            )
                        }
                    } else {
                        val currentGroup = groupSelection.selectedGroup ?: displayGroup
                        NodeListPage(
                            group = currentGroup,
                            allGroups = proxyGroups,
                            displayMode = displayMode,
                            sortMode = sortMode,
                            testingGroupNames = testingGroupNames,
                            testingProxyNames = testingProxyNames,
                            mainInnerPadding = mainInnerPadding,
                            outerInnerPadding = it,
                            scrollBehavior = groupScrollBehavior,
                            listState = nodeListState,
                            onSelectProxy = { groupName, proxyName -> proxyViewModel.selectProxy(groupName, proxyName) },
                            onForceSelectProxy = { groupName, proxyName -> proxyViewModel.forceSelectProxy(groupName, proxyName) },
                            onTestDelay = requestSelectedGroupDelayTest,
                            onTestProxyDelay = { proxyName -> currentGroup?.name?.let { groupName -> proxyViewModel.testProxyDelay(groupName, proxyName) } },
                            onScrollDirectionChanged = {},
                            searchQuery = nodeSearchQuery,
                            onSearchQueryChange = { nodeSearchQuery = it },
                            searchVisible = nodeSearchVisible,
                            onSearchVisibleChange = { nodeSearchVisible = it },
                            testProgress = delayTestProgress,
                        )
                    }
                }
            } // else (phone mode)
        }
        }
    }
}

/** 代理页大标题面包屑：根段“代理”可点，[segmentLabel] 非空时显示“代理 > 组名”分段。 */
@Composable
internal fun ProxyTitleBreadcrumb(rootLabel: String, onRootClick: () -> Unit, segmentLabel: String? = null, onSegmentClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TopAppBarDefaults.TitlePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProxyCrumbText(label = rootLabel, onClick = onRootClick)
        if (segmentLabel != null) {
            Text(
                text = " > ",
                style = MiuixTheme.textStyles.title1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            ProxyCrumbText(
                label = segmentLabel,
                onClick = onSegmentClick,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * 面包屑可点段。
 * Press 时序是按压动效能被看见的前提：clickable 会把 Press/Release 折叠到同一拍，collectIsPressedAsState 观察不到 pressed=true，animate*AsState 就永远不会离开静止值。
 * 这里用 pressable(delay=null) 在 down 瞬间 emit Press，短按也有完整的按压帧。
 * 缩放走 SinkFeedback 节点内 Animatable（不依赖 recomposition）；降透明/底色按下 snap、松手弹簧回弹。
 */
@Composable
private fun ProxyCrumbText(label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val pressSource = remember { MutableInteractionSource() }
    val clickSource = remember { MutableInteractionSource() }
    val sinkFeedback = remember { SinkFeedback(sinkAmount = 0.88f, animationSpec = AnimationSpecs.ButtonPressSpring) }
    val pressed by pressSource.collectIsPressedAsState()
    val pressTransition = updateTransition(targetState = pressed, label = "crumb_press")
    val textAlpha by pressTransition.animateFloat(
        transitionSpec = {
            if (targetState) snap()
            else AnimationSpecs.ButtonPressSpring
        },
        label = "crumb_press_alpha",
    ) { if (it) 0.4f else 1f }
    val pressBg by pressTransition.animateColor(
        transitionSpec = {
            if (targetState) snap()
            else tween(AnimationSpecs.DURATION_FAST)
        },
        label = "crumb_press_bg",
    ) {
        if (it) {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(UiDp.dp8))
            .background(pressBg)
            .pressable(
                interactionSource = pressSource,
                indication = sinkFeedback,
                enabled = onClick != null,
                delay = null,
            )
            .clickable(
                interactionSource = clickSource,
                indication = null,
                enabled = onClick != null,
                role = Role.Button,
            ) { onClick?.invoke() },
        contentAlignment = Alignment.CenterStart,
    ) {
        EmojiAwareText(
            text = label,
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer { this.alpha = textAlpha }
                .padding(horizontal = UiDp.dp8, vertical = UiDp.dp4),
        )
    }
}

/** Stable TopBar sub-composable for [ProxyPager] — only recomposes when sort/display params change. */
@Composable
private fun PagerTopBar(
    scrollBehavior: ScrollBehavior,
    onNavigateToProviders: (() -> Unit)?,
    onTestAllDelay: () -> Unit,
    locateCurrentProxy: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    onDisplayModeSelected: (ProxyDisplayMode) -> Unit,
    onSortSelected: (ProxySortMode) -> Unit,
    delayTestMode: DelayTestMode = DelayTestMode.PANEL,
    onDelayTestModeSelected: (DelayTestMode) -> Unit = {},
    selectedGroupName: String?,
    singleGroup: Boolean = false,
    onBackToGroups: () -> Unit,
    onTitleScrollTop: () -> Unit,
    searchVisible: Boolean = false,
    onSearchToggle: (() -> Unit)? = null,
) {
    ProxyTopBar(
        // 只有一个策略组时面包屑只剩根段（「代理 > Proxy」里的组名纯属重复，2026-11-03 用户点单）；
        // 点标题回组列表的行为不变。
        title = if (selectedGroupName == null || singleGroup) {
            FlyTxt.Component.BottomBar.Proxy
        } else {
            "${FlyTxt.Component.BottomBar.Proxy} > $selectedGroupName"
        },
        groupName = if (singleGroup) null else selectedGroupName,
        onTitleRootClick = { if (selectedGroupName == null) onTitleScrollTop() else onBackToGroups() },
        onTitleGroupClick = onTitleScrollTop,
        scrollBehavior = scrollBehavior,
        showBack = false,
        onBack = {},
        onNavigateToProviders = onNavigateToProviders,
        onTestAllDelay = onTestAllDelay,
        onLocateCurrentProxy = locateCurrentProxy,
        showSortPopup = showSortPopup,
        onShowSortPopupChange = onShowSortPopupChange,
        displayMode = displayMode,
        sortMode = sortMode,
        onDisplayModeSelected = onDisplayModeSelected,
        onSortSelected = onSortSelected,
        delayTestMode = delayTestMode,
        onDelayTestModeSelected = onDelayTestModeSelected,
        searchVisible = searchVisible,
        onSearchToggle = onSearchToggle,
    )
}

@Composable
internal fun ProxyTopBar(
    title: String,
    groupName: String? = null,
    /** 面包屑根段文案；双栏右栏用组名作单段可点标题时传入。 */
    titleRootLabel: String = FlyTxt.Component.BottomBar.Proxy,
    onTitleRootClick: (() -> Unit)? = null,
    onTitleGroupClick: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior,
    showBack: Boolean,
    onBack: () -> Unit,
    onNavigateToProviders: (() -> Unit)?,
    onTestAllDelay: () -> Unit = {},
    onLocateCurrentProxy: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    onDisplayModeSelected: (ProxyDisplayMode) -> Unit,
    onSortSelected: (ProxySortMode) -> Unit,
    delayTestMode: DelayTestMode = DelayTestMode.PANEL,
    onDelayTestModeSelected: (DelayTestMode) -> Unit = {},
    searchVisible: Boolean = false,
    onSearchToggle: (() -> Unit)? = null,
) {
    val titleContent: (@Composable () -> Unit)? = if (onTitleRootClick == null) {
        null
    } else {
        {
            ProxyTitleBreadcrumb(
                rootLabel = titleRootLabel,
                onRootClick = onTitleRootClick,
                segmentLabel = groupName,
                onSegmentClick = onTitleGroupClick,
            )
        }
    }

    TopBar(
        title = title,
        titleContent = titleContent,
        scrollBehavior = scrollBehavior,
        navigationIconPadding = UiDp.dp24,
        actionIconPadding = UiDp.dp24,
        navigationIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = FlyTxt.Component.Navigation.Back)
                    }
                } else {
                    if (onNavigateToProviders != null) {
                        IconButton(onClick = onNavigateToProviders) {
                            Icon(FlyCat.Folders, contentDescription = FlyTxt.Providers.Title)
                        }
                    }
                }
            }
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                if (onSearchToggle != null) {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            imageVector = FlyCat.Search,
                            contentDescription = FlyTxt.Component.Editor.Action.Search,
                        )
                    }
                }
                IconButton(onClick = onTestAllDelay) {
                    Icon(FlyCat.Speed, contentDescription = FlyTxt.Proxy.Action.Test)
                }
                Box {
                    IconButton(onClick = { onShowSortPopupChange(true) }) {
                        Icon(FlyCat.ListChevronsUpDown, contentDescription = FlyTxt.Proxy.Action.Sort)
                    }
                    NodeSortPopup(
                        show = showSortPopup,
                        onDismiss = { onShowSortPopupChange(false) },
                        displayMode = displayMode,
                        sortMode = sortMode,
                        delayTestMode = delayTestMode,
                        alignment = PopupPositionProvider.Align.BottomEnd,
                        onDisplayModeSelected = onDisplayModeSelected,
                        onSortSelected = onSortSelected,
                        onDelayTestModeSelected = onDelayTestModeSelected,
                        onLocateCurrentProxy = onLocateCurrentProxy,
                    )
                }
            }
        },
    )
}

private fun ProxyGroupInfo.filterNodes(query: String): List<Proxy> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return proxies
    return proxies.filter { proxy ->
        proxy.name.contains(normalizedQuery, ignoreCase = true) ||
            proxy.title.contains(normalizedQuery, ignoreCase = true) ||
            proxy.subtitle.contains(normalizedQuery, ignoreCase = true)
    }
}

@Composable
internal fun NodeListPage(
    group: ProxyGroupInfo?,
    allGroups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    testingGroupNames: Set<String>,
    testingProxyNames: Set<String>,
    mainInnerPadding: PaddingValues,
    outerInnerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    onSelectProxy: (groupName: String, proxyName: String) -> Unit,
    onForceSelectProxy: (groupName: String, proxyName: String) -> Unit,
    onTestDelay: () -> Unit,
    onTestProxyDelay: (proxyName: String) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchVisible: Boolean = false,
    onSearchVisibleChange: (Boolean) -> Unit = {},
    testProgress: ProxyViewModel.DelayTestProgress? = null,
) {
    if (group == null) {
        CenteredText(
            firstLine = FlyTxt.Proxy.Empty.NoNodes,
            secondLine = FlyTxt.Proxy.Empty.Hint,
            showEmptyResourceIllustration = true,
        )
        return
    }
    val spacing = LocalSpacing.current
    val isTesting = testingGroupNames.contains(group.name)
    val listItemKeys = remember(group.proxies) { group.proxies.map { it.name } }
    // 延迟历史落盘：每次测速结果变化时记一笔（供节点行走势线用）。
    val delayRecordContext = LocalContext.current
    val lastRecordedDelays = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(group.proxies) {
        group.proxies.forEach { proxy ->
            val d = proxy.delay ?: 0
            if (d > 0 && lastRecordedDelays[proxy.name] != d) {
                lastRecordedDelays[proxy.name] = d
                DelayHistory.record(delayRecordContext, proxy.name, d)
            }
        }
    }
    var detailProxy by remember { mutableStateOf<Proxy?>(null) }
    // 节点自定义图标（与策略组同一套 CustomIcons 机制，key=node_<md5>）。
    val nodeIconContext = LocalContext.current
    var pendingNodeIconKey by remember { mutableStateOf<String?>(null) }
    val nodeIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val key = pendingNodeIconKey
        pendingNodeIconKey = null
        if (uri != null && key != null) {
            if (CustomIcons.saveFromUri(nodeIconContext, key, uri)) {
                nodeIconContext.toast("节点图标已更新")
            } else {
                nodeIconContext.toast("图片读取失败，换一张试试")
            }
        }
    }
    fun performNodePrimaryAction(proxyName: String) {
        if (group.type == Proxy.Type.Selector) {
            onSelectProxy(group.name, proxyName)
        } else if (group.type == Proxy.Type.URLTest || group.type == Proxy.Type.Fallback) {
            val target = if (proxyName == group.fixed) "" else proxyName
            onForceSelectProxy(group.name, target)
        } else {
            onTestDelay()
        }
        detailProxy = null
    }

    val visibleProxies = remember(group.proxies, searchQuery) { group.filterNodes(searchQuery) }
    val revealCount = rememberRowReveal(itemCount = visibleProxies.size, replayKey = group.name, listState = listState)
    // 滚动列表即收起搜索框：搜索是低频操作，避免常驻占位。
    if (searchVisible) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling -> if (scrolling) onSearchVisibleChange(false) }
        }
    }

    KeepLazyListTopAnchorOnReorder(
        listState = listState,
        itemKeys = listItemKeys,
        enabled = sortMode == ProxySortMode.BY_LATENCY,
        scrollToTopOnEnabled = true,
    )

    LaunchedEffect(isTesting) {
        if (isTesting && listState.isScrolledFromTop()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = outerInnerPadding.calculateTopPadding()),
    ) {
        if (group.chainPath.isNotEmpty()) {
            ProxyChainIndicator(
                chain = group.chainPath,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(visible = searchVisible) {
            NodeSearchToolbar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(
                    start = UiDp.dp12,
                    end = UiDp.dp12,
                    // Column 已扣除 topBar 高度，这里不要再加 outerInnerPadding.top。
                    top = if (group.chainPath.isNotEmpty()) UiDp.dp10 else UiDp.dp12,
                    bottom = UiDp.dp8,
                ),
            )
        }
        NodeTestPullToRefresh(
            isRefreshing = isTesting,
            onRefresh = onTestDelay,
            tested = testProgress?.tested ?: 0,
            total = testProgress?.total ?: 0,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            ScreenLazyColumn(
                modifier = Modifier.fillMaxSize(),
                lazyListState = listState,
                scrollBehavior = scrollBehavior,
                innerPadding = outerInnerPadding,
                enableGlobalScroll = true,
                onScrollDirectionChanged = onScrollDirectionChanged,
                contentPadding =
                    PaddingValues(
                        start = UiDp.dp12,
                        end = UiDp.dp12,
                        bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
                    ),
            ) {
                nodeListRows(
                    proxies = visibleProxies,
                    selectedProxyName = group.now,
                    onProxyClick = { proxyName ->
                        // 点节点先出详情抽屉（Loon 式），选择动作在抽屉里完成。
                        detailProxy = visibleProxies.firstOrNull { it.name == proxyName }
                    },
                    isDelayTesting = isTesting,
                    testingProxyNames = testingProxyNames,
                    onSingleNodeTestClick = { proxyName ->
                        onTestProxyDelay(proxyName)
                    },
                    itemVerticalPadding = UiDp.dp6,
                    revealCount = revealCount,
                )
            }
        }
    }
    NodeDetailSheet(
        proxy = detailProxy,
        group = group,
        onDismiss = { detailProxy = null },
        onPrimaryAction = { name -> performNodePrimaryAction(name) },
        onTestSingle = { name -> onTestProxyDelay(name) },
        onIconPick = { p ->
            pendingNodeIconKey = CustomIcons.keyFor("node", p.name)
            nodeIconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onIconReset = { p ->
            CustomIcons.clear(nodeIconContext, CustomIcons.keyFor("node", p.name))
            nodeIconContext.toast("已恢复默认图标")
        },
    )
}

/** 代理页顶部细状态条：连接圆点 + 状态文案，右侧小电源开关。波奇酱风。 */
@Composable
private fun ProxyStatusBar(
    isRunning: Boolean,
    nodeName: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppTheme.radii.radius12)
    // 深色跟随主题卡面（旧版恒白卡配主题浅字，深色下状态文案看不清，1.77 改）
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) MiuixTheme.colorScheme.surfaceContainer else Color.White)
            .border(1.dp, BocchiPink.copy(alpha = if (dark) 0.3f else 0.5f), shape)
            .padding(horizontal = UiDp.dp12, vertical = UiDp.dp10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UiDp.dp8)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (isRunning) BocchiGreen else MiuixTheme.colorScheme.onSurfaceVariantSummary),
        )
        Text(
            text = if (isRunning) "已连接 · $nodeName" else "未连接",
            style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = UiDp.dp8),
        )
        Text(
            text = "✦",
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
            color = BocchiStar,
            modifier = Modifier.padding(end = UiDp.dp6),
        )
        Switch(
            checked = isRunning,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun ProxyContent(
    proxyGroups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    innerPadding: PaddingValues,
    mainInnerPadding: PaddingValues,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    onGroupDelayTestClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String>,
    onGroupBoundsChanged: ((String, Rect) -> Unit)? = null,
    onGroupIconPick: ((ProxyGroupInfo) -> Unit)? = null,
    onGroupIconReset: ((ProxyGroupInfo) -> Unit)? = null,
    statusBar: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val revealCount = rememberRowReveal(itemCount = proxyGroups.size, listState = listState)
    ScreenLazyColumn(
        scrollBehavior = scrollBehavior,
        lazyListState = listState,
        innerPadding = innerPadding,
        enableGlobalScroll = true,
        contentPadding =
            PaddingValues(
                start = UiDp.dp12,
                end = UiDp.dp12,
                top = innerPadding.calculateTopPadding() + UiDp.dp2,
                bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
            ),
    ) {
        if (statusBar != null) {
            item(key = "proxy_status_bar", contentType = "ProxyStatusBar") {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = UiDp.dp8)) {
                    statusBar()
                }
            }
        }
        nodeGroupItems(
            groups = proxyGroups,
            displayMode = displayMode,
            onGroupClick = onGroupClick,
            testingGroupNames = testingGroupNames,
            onGroupDelayTestClick = onGroupDelayTestClick,
            onGroupBoundsChanged = onGroupBoundsChanged,
            onGroupIconPick = onGroupIconPick,
            onGroupIconReset = onGroupIconReset,
            itemVerticalPadding = UiDp.dp6,
            revealCount = revealCount,
        )
    }
}


/**
 * 节点详情抽屉（Loon 式）：点节点先看详情——延迟大字 + 历史走势 + 统计，
 * 选择/固定动作在抽屉主按钮完成；延迟数字的小测速入口仍在行内（点数字测速不开抽屉）。
 */
@Composable
private fun NodeDetailSheet(
    proxy: Proxy?,
    group: ProxyGroupInfo,
    onDismiss: () -> Unit,
    onPrimaryAction: (String) -> Unit,
    onTestSingle: (String) -> Unit,
    onIconPick: (Proxy) -> Unit,
    onIconReset: (Proxy) -> Unit,
) {
    val spacing = LocalSpacing.current
    AppActionBottomSheet(
        show = proxy != null,
        title = proxy?.name ?: "",
        onDismissRequest = onDismiss,
    ) {
        val history = rememberDelayHistory(proxy?.name ?: "")
        if (proxy == null) return@AppActionBottomSheet
        val delayLabel = nodeLatencyLabel(proxy.delay, withUnit = true)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.suanran.dreambox.feature.proxy.presentation.util.NodeFlagBadge(
                    nodeName = proxy.name,
                    size = UiDp.dp48,
                    emojiSize = 26.sp,
                )
                Column(modifier = Modifier.padding(start = spacing.space12).weight(1f)) {
                    Text(
                        text = displayName(proxy.type),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = "所属策略组：${group.name}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (proxy.subtitle.isNotBlank()) {
                        Text(
                            text = proxy.subtitle,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (delayLabel != null) {
                    Text(
                        text = delayLabel.first,
                        style = MiuixTheme.textStyles.title4,
                        color = delayLabel.second,
                    )
                } else {
                    Text(
                        text = "未测速",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (history.size >= 2) {
                DelayBigSparkline(
                    history = history,
                    color = delayLabel?.second ?: MiuixTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space14),
                )
                val minV = history.minOrNull() ?: 0
                val maxV = history.maxOrNull() ?: 0
                val avgV = history.average().toInt()
                Text(
                    text = "最近 ${history.size} 次测速 · 最低 ${minV}ms · 平均 ${avgV}ms · 最高 ${maxV}ms",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = spacing.space6),
                )
            } else {
                Text(
                    text = "延迟走势会在测速两次后出现在这里。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = spacing.space12),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.space16, bottom = spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(spacing.space10),
            ) {
                val primaryLabel = when {
                    group.type == Proxy.Type.Selector -> if (proxy.name == group.now) "当前已选" else "选择此节点"
                    group.type == Proxy.Type.URLTest || group.type == Proxy.Type.Fallback ->
                        if (proxy.name == group.fixed) "取消固定" else "固定到此节点"
                    else -> "测速整组"
                }
                val primaryEnabled = !(group.type == Proxy.Type.Selector && proxy.name == group.now)
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { onPrimaryAction(proxy.name) },
                    enabled = primaryEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = primaryLabel)
                }
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "单独测速",
                    onClick = { onTestSingle(proxy.name) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(spacing.space10),
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "换图标",
                    onClick = { onIconPick(proxy) },
                    modifier = Modifier.weight(1f),
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "恢复默认图标",
                    onClick = { onIconReset(proxy) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 详情抽屉里的大号延迟走势（与行内小曲线同一数据源）；形状/颜色读 SparklinePrefs。 */
@Composable
private fun DelayBigSparkline(history: List<Int>, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefVersion = com.suanran.dreambox.presentation.component.misc.SparklinePrefs.version
    val shape = remember(prefVersion) { com.suanran.dreambox.presentation.component.misc.SparklinePrefs.shape(context) }
    val colorMode = remember(prefVersion) { com.suanran.dreambox.presentation.component.misc.SparklinePrefs.colorMode(context) }
    val drawColor = if (colorMode == com.suanran.dreambox.presentation.component.misc.SparklinePrefs.COLOR_THEME) {
        MiuixTheme.colorScheme.primary
    } else {
        color
    }
    androidx.compose.foundation.Canvas(modifier = modifier.height(UiDp.dp40)) {
        val maxV = history.maxOrNull() ?: 1
        val minV = history.minOrNull() ?: 0
        val range = (maxV - minV).coerceAtLeast(20).toFloat()
        val padY = 3.dp.toPx()
        val stepX = if (history.size > 1) size.width / (history.size - 1) else size.width
        fun yOf(v: Int): Float = size.height - padY - ((v - minV).toFloat() / range) * (size.height - padY * 2)
        if (shape == com.suanran.dreambox.presentation.component.misc.SparklinePrefs.SHAPE_BARS) {
            val barW = (stepX * 0.5f).coerceAtLeast(2.dp.toPx())
            history.forEachIndexed { i, v ->
                val x = stepX * i
                drawLine(
                    color = drawColor.copy(alpha = 0.65f),
                    start = androidx.compose.ui.geometry.Offset(x, size.height - padY),
                    end = androidx.compose.ui.geometry.Offset(x, yOf(v)),
                    strokeWidth = barW,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            return@Canvas
        }
        val linePath = androidx.compose.ui.graphics.Path()
        val fillPath = androidx.compose.ui.graphics.Path()
        history.forEachIndexed { i, v ->
            val x = stepX * i
            val y = yOf(v)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else if (shape == com.suanran.dreambox.presentation.component.misc.SparklinePrefs.SHAPE_SMOOTH) {
                val prevX = stepX * (i - 1)
                val prevY = yOf(history[i - 1])
                linePath.quadraticBezierTo(prevX, prevY, (prevX + x) / 2f, (prevY + y) / 2f)
                fillPath.lineTo((prevX + x) / 2f, (prevY + y) / 2f)
                if (i == history.lastIndex) {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(stepX * (history.size - 1), size.height)
        fillPath.close()
        drawPath(fillPath, color = drawColor.copy(alpha = 0.12f))
        drawPath(
            linePath,
            color = drawColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

private const val SKY_PROXY_PAGE = true
