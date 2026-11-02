package com.suanran.dreambox.feature.home.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.ProxyGroupRepository
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import com.suanran.dreambox.runtime.api.contract.ProfileRepositoryContract
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import com.suanran.dreambox.presentation.component.navigation.TopBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

/** 全局搜索（Stash/Loon 式）：一个框通搜功能入口、策略组、节点、配置。 */
private data class SearchDestination(
    val title: String,
    val keywords: String,
    val route: Route,
)

private val DESTINATIONS: List<SearchDestination> = listOf(
    SearchDestination("连接", "连接 connections 实时", Route.Connection),
    SearchDestination("规则", "规则 rules 命中 测试", Route.Rules),
    SearchDestination("客户端测速", "测速 speedtest 速度", Route.SpeedTest),
    SearchDestination("流量统计", "流量 统计 traffic 域名 应用 节点", Route.TrafficStatistics),
    SearchDestination("日志", "日志 log 诊断", Route.Log),
    SearchDestination("覆写", "覆写 override 快速规则", Route.Override),
    SearchDestination("自定义路由", "路由 分流 routing 预设", Route.CustomRouting),
    SearchDestination("核心功能", "核心 meta 控制器 面板 geox", Route.MetaFeature),
    SearchDestination("网络设置", "网络 dns tun 网络", Route.NetworkSettings),
    SearchDestination("应用设置", "设置 外观 行为 settings", Route.AppSettings),
    SearchDestination("个性化", "个性化 图标 面板 导航栏 模块", Route.Theme),
    SearchDestination("插件", "插件 功能包 packs 终端 节点部署 流媒体 解锁 webdav 云备份", Route.FeaturePacks),
    SearchDestination("提供商", "提供商 providers 远程节点", Route.Providers),
    SearchDestination("关于", "关于 版本 about", Route.About),
)

@Composable
fun GlobalSearchScreen(navigator: Navigator) {
    val spacing = AppTheme.spacing
    val scrollBehavior = MiuixScrollBehavior()
    val groupRepo = koinInject<ProxyGroupRepository>()
    val profileRepo = koinInject<ProfileRepositoryContract>()
    val groups by groupRepo.proxyGroups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        profiles = runCatching { profileRepo.queryAllProfiles() }.getOrDefault(emptyList())
        runCatching { focusRequester.requestFocus() }
    }

    val q = query.trim().lowercase()
    val matchedDestinations = if (q.isBlank()) emptyList() else DESTINATIONS.filter {
        it.title.lowercase().contains(q) || it.keywords.lowercase().contains(q)
    }
    val matchedGroups = if (q.isBlank()) emptyList() else groups.filter { it.name.lowercase().contains(q) }
    val matchedNodes = if (q.isBlank()) {
        emptyList()
    } else {
        groups.flatMap { group -> group.proxies.filter { !it.isGroup }.map { group to it } }
            .filter { (_, node) -> node.name.lowercase().contains(q) }
            .take(30)
    }
    val matchedProfiles = if (q.isBlank()) emptyList() else profiles.filter { it.name.lowercase().contains(q) }

    Scaffold(
        topBar = {
            TopBar(
                title = "全局搜索",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜节点、配置、功能…",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space12).focusRequester(focusRequester),
                )
            }
            if (q.isBlank()) {
                item {
                    Text(
                        text = "节点可以直接点选切换（手动选择组）；配置点一下设为当前配置。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = spacing.space16),
                    )
                }
            } else if (matchedDestinations.isEmpty() && matchedGroups.isEmpty() && matchedNodes.isEmpty() && matchedProfiles.isEmpty()) {
                item {
                    Text(
                        text = "没有找到「$query」。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = spacing.space16),
                    )
                }
            }
            if (matchedDestinations.isNotEmpty()) {
                item { BocchiSectionTitle("功能") }
                items(matchedDestinations.size) { index ->
                    val dest = matchedDestinations[index]
                    SearchRow(
                        title = dest.title,
                        subtitle = "功能入口",
                        onClick = { navigator.push(dest.route) },
                    )
                }
            }
            if (matchedGroups.isNotEmpty()) {
                item { BocchiSectionTitle("策略组") }
                items(matchedGroups.size) { index ->
                    val group = matchedGroups[index]
                    SearchRow(
                        title = group.name,
                        subtitle = "当前：${group.now.ifBlank { "-" }}",
                        onClick = { context.toast("在代理页点开这个组可以换节点") },
                    )
                }
            }
            if (matchedNodes.isNotEmpty()) {
                item { BocchiSectionTitle("节点") }
                items(matchedNodes.size) { index ->
                    val (group, node) = matchedNodes[index]
                    SearchRow(
                        title = node.name,
                        subtitle = "${group.name} · ${if (node.delay > 0) "${node.delay}ms" else "未测速"}",
                        onClick = {
                            if (group.type == Proxy.Type.Selector) {
                                scope.launch {
                                    val ok = groupRepo.selectProxy(group.name, node.name)
                                    context.toast(if (ok) "已切换到 ${node.name}" else "切换失败，看看核心是不是在运行")
                                }
                            } else {
                                context.toast("这个组是自动选速，节点由核心决定")
                            }
                        },
                    )
                }
            }
            if (matchedProfiles.isNotEmpty()) {
                item { BocchiSectionTitle("配置") }
                items(matchedProfiles.size) { index ->
                    val profile = matchedProfiles[index]
                    SearchRow(
                        title = profile.name,
                        subtitle = "点按设为当前配置",
                        onClick = {
                            scope.launch {
                                runCatching { profileRepo.setActiveProfile(profile.uuid) }
                                    .onSuccess { context.toast("当前配置已切换为 ${profile.name}") }
                                    .onFailure { context.toast("切换失败：${it.message ?: "未知错误"}") }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val spacing = AppTheme.spacing
    Card(modifier = Modifier.padding(vertical = spacing.space4)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.space16, vertical = spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
