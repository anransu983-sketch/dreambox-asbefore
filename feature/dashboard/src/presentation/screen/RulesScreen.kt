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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.model.RuntimeRule
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.RulesViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.CenteredText
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.component.state.SearchBarPadding
import com.suanran.dreambox.presentation.component.state.SearchPager
import com.suanran.dreambox.presentation.component.state.SearchStatus
import com.suanran.dreambox.presentation.component.state.TopAppBarAnim
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RulesScreen(navigator: Navigator) {
    val density = LocalDensity.current
    val viewModel = koinViewModel<RulesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filteredRules by viewModel.filteredRules.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing
    val mainListState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainLikePadding = rememberStandalonePageMainPadding()

    var searchStatus by remember {
        mutableStateOf(
            SearchStatus(
                label = FlyTxt.Rules.SearchHint,
                searchText = state.searchQuery,
            )
        )
    }
    val dynamicTopPadding by remember {
        derivedStateOf { spacing.space12 * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val listStartPadding = spacing.screenHorizontal
    val listEndPadding = spacing.screenHorizontal
    val currentSearchStatus =
        remember(searchStatus, filteredRules) {
            searchStatus.copy(
                resultStatus =
                    when {
                        searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                        filteredRules.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                        else -> SearchStatus.ResultStatus.SHOW
                    }
            )
        }

    LaunchedEffect(state.toggleError) {
        val error = state.toggleError ?: return@LaunchedEffect
        context.toast(FlyTxt.Rules.Message.ToggleFailed.format(error))
        viewModel.consumeToggleError()
    }

    LaunchedEffect(searchStatus.searchText) {
        if (searchStatus.searchText != state.searchQuery) {
            viewModel.setSearchQuery(searchStatus.searchText)
        }
    }

    LaunchedEffect(state.searchQuery) {
        if (searchStatus.searchText != state.searchQuery) {
            searchStatus = searchStatus.copy(searchText = state.searchQuery)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                currentSearchStatus.TopAppBarAnim {
                    TopBar(
                        title = FlyTxt.Rules.Title,
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 0.dp,
                        navigationIcon = { NavigationBackIcon(navigator = navigator) },
                        bottomContent = {
                            Box(
                                modifier =
                                    Modifier.alpha(if (currentSearchStatus.isCollapsed()) 1f else 0f)
                                        .onGloballyPositioned { coordinates ->
                                            with(density) {
                                                val collapsedBarOffset = coordinates.positionInWindow().y.toDp()
                                                if (currentSearchStatus.offsetY != collapsedBarOffset) {
                                                    searchStatus = currentSearchStatus.copy(offsetY = collapsedBarOffset)
                                                }
                                            }
                                        }
                                        .then(
                                            if (currentSearchStatus.isCollapsed()) {
                                                Modifier.pointerInput(currentSearchStatus.current) {
                                                    detectTapGestures {
                                                        searchStatus = currentSearchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                            ) {
                                RulesCollapsedSearchBar(
                                    label = currentSearchStatus.label,
                                    topPadding = dynamicTopPadding,
                                    startPadding = listStartPadding,
                                    endPadding = listEndPadding,
                                )
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            if (currentSearchStatus.shouldCollapse()) {
                when {
                    state.isLoading && state.rules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.Loading,
                            secondLine = FlyTxt.Rules.Empty.LoadingHint,
                        )
                    }

                    !state.isRunning && state.rules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.NotRunning,
                            secondLine = FlyTxt.Rules.Empty.NotRunningHint,
                        )
                    }

                    state.rules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.NoRules,
                            secondLine = FlyTxt.Rules.Empty.NoRulesHint,
                        )
                    }

                    else -> {
                        ScreenLazyColumn(
                            scrollBehavior = scrollBehavior,
                            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
                            lazyListState = mainListState,
                        ) {
                            item(key = "rule_test_card", contentType = "ruleTest") {
                                RuleTestCard(rules = state.rules)
                            }
                            items(items = filteredRules, key = { it.index }, contentType = { "rule" }) { rule ->
                                RuleCard(
                                    rule = rule,
                                    enabled = !rule.disabled,
                                    toggling = rule.index in state.togglingIndexes,
                                    onEnabledChange = { enabled ->
                                        viewModel.setRuleEnabled(rule.index, enabled)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        currentSearchStatus.SearchPager(
            onSearchStatusChange = { searchStatus = it },
            padding =
                SearchBarPadding(
                    top = dynamicTopPadding,
                    start = listStartPadding,
                    end = listEndPadding,
                ),
            emptyResult = {
                CenteredText(
                    firstLine = FlyTxt.Rules.Empty.NoResults,
                    secondLine = currentSearchStatus.searchText,
                )
            },
        ) {
            val searchListState = rememberLazyListState()
            val listBottomPadding =
                maxOf(
                    mainLikePadding.calculateBottomPadding(),
                    WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                )
            androidx.compose.foundation.lazy.LazyColumn(
                state = searchListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = spacing.space6,
                        bottom = listBottomPadding,
                    ),
            ) {
                items(items = filteredRules, key = { it.index }, contentType = { "rule" }) { rule ->
                    RuleCard(
                        rule = rule,
                        enabled = !rule.disabled,
                        toggling = rule.index in state.togglingIndexes,
                        onEnabledChange = { enabled -> viewModel.setRuleEnabled(rule.index, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RulesCollapsedSearchBar(
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    InputField(
        query = "",
        onQueryChange = {},
        label = label,
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = FlyTxt.Component.Editor.Action.Search,
                modifier =
                    Modifier.size(AppTheme.sizes.searchIconTouchTarget)
                        .padding(start = AppTheme.spacing.space16, end = AppTheme.spacing.space8),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = startPadding, end = endPadding)
                .padding(top = topPadding, bottom = AppTheme.sizes.searchBarBottomPadding),
        onSearch = {},
        enabled = false,
        expanded = false,
        onExpandedChange = {},
    )
}

@Composable
private fun RuleCard(
    rule: RuntimeRule,
    enabled: Boolean,
    toggling: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val spacing = AppTheme.spacing
    val totalCount = rule.hitCount + rule.missCount
    val hitRate =
        if (totalCount > 0L) {
            String.format(Locale.getDefault(), "%.1f%%", rule.hitCount * 100.0 / totalCount)
        } else {
            "-"
        }

    Card(modifier = Modifier.padding(vertical = spacing.space4)) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Text(
                    text = rule.payload.ifBlank { "#${rule.index}" },
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !toggling,
                )
            }
            Spacer(modifier = Modifier.size(spacing.space4))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Text(
                    text = "${rule.type.ifBlank { "-" }}  ·  ${rule.proxy.ifBlank { "-" }}",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = FlyTxt.Rules.Label.HitRate.format(hitRate),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}


// ── 规则测试（Surge 式）：离线按顺序算某个域名/IP 会命中哪条规则 ──────────────

@Composable
private fun RuleTestCard(rules: List<RuntimeRule>) {
    val spacing = AppTheme.spacing
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.padding(vertical = spacing.space4)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12),
        ) {
            Text(
                text = "规则测试",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "输入域名或 IP，按当前生效规则从上到下算它会走哪条。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = spacing.space2),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.space10),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space10),
            ) {
                top.yukonga.miuix.kmp.basic.TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = "如 example.com 或 1.2.3.4",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { result = evaluateRuleTest(input, rules) },
                ) {
                    Text(text = "测试", color = MiuixTheme.colorScheme.onPrimary)
                }
            }
            result?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = spacing.space10),
                )
            }
        }
    }
}

private fun evaluateRuleTest(rawInput: String, rules: List<RuntimeRule>): String {
    val host = normalizeTestTarget(rawInput)
    if (host.isBlank()) return "先输入一个域名或 IP。"
    if (rules.isEmpty()) return "当前没有生效中的规则（核心未运行或配置无规则）。"
    val isIp = isIpLiteralText(host)
    var deferredNote: String? = null
    for (rule in rules.sortedBy { it.index }) {
        if (rule.disabled) continue
        val type = rule.type.uppercase()
        val payload = rule.payload.trim().lowercase()
        val hit: Boolean? = when (type) {
            "DOMAIN" -> if (!isIp) host == payload else false
            "DOMAIN-SUFFIX" -> if (!isIp) (host == payload || host.endsWith(".$payload")) else false
            "DOMAIN-KEYWORD" -> if (!isIp) payload.isNotBlank() && host.contains(payload) else false
            "DOMAIN-WILDCARD" -> if (!isIp) wildcardMatch(payload, host) else false
            "DOMAIN-REGEX" -> if (!isIp) runCatching { Regex(rule.payload.trim()).containsMatchIn(host) }.getOrDefault(false) else false
            "IP-CIDR", "IP-CIDR6" -> if (isIp) cidrContains(rule.payload.trim(), host) else null
            "MATCH" -> true
            "GEOIP", "GEOSITE", "RULE-SET", "AND", "OR", "NOT", "SUB-RULE" -> null
            else -> false
        }
        when {
            hit == true -> {
                val base = "命中第 ${rule.index + 1} 条：${rule.type} ${rule.payload} → ${rule.proxy.ifBlank { "-" }}"
                return if (deferredNote != null) "$base（注意：$deferredNote，实际结果可能不同）" else base
            }
            hit == null && deferredNote == null -> {
                deferredNote = "它前面的第 ${rule.index + 1} 条 ${rule.type} 需要核心在线判定（解析/规则集）"
            }
        }
    }
    return if (deferredNote != null) {
        "没有离线命中的规则；但$deferredNote，实际可能先被它截走。"
    } else {
        "没有命中任何规则——检查一下配置末尾的 MATCH 兜底，或这个目标走了默认直连。"
    }
}

private fun normalizeTestTarget(raw: String): String {
    var v = raw.trim().lowercase()
    if (v.isBlank()) return ""
    v = v.substringAfter("://", v)
    v = v.substringBefore("/").substringBefore("?").substringBefore("#")
    // 去端口（域名:443 / [ipv6]:443）
    if (v.startsWith("[")) {
        v = v.substringBefore("]").removePrefix("[")
    } else if (v.count { it == ':' } == 1) {
        v = v.substringBefore(":")
    }
    return v.trimEnd('.')
}

private fun isIpLiteralText(host: String): Boolean =
    host.contains(':') || (host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' })

private fun wildcardMatch(pattern: String, host: String): Boolean {
    val regex = StringBuilder("^")
    for (c in pattern) {
        when (c) {
            '*' -> regex.append(".*")
            '?' -> regex.append(".")
            else -> regex.append(java.util.regex.Pattern.quote(c.toString()))
        }
    }
    regex.append("$")
    return runCatching { Regex(regex.toString()).matches(host) }.getOrDefault(false)
}

private fun cidrContains(cidr: String, ip: String): Boolean = runCatching {
    val parts = cidr.split("/")
    val network = java.net.InetAddress.getByName(parts[0].trim()).address
    val target = java.net.InetAddress.getByName(ip).address
    if (network.size != target.size) return@runCatching false
    val prefix = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: (network.size * 8)
    val fullBytes = prefix / 8
    val restBits = prefix % 8
    for (i in 0 until fullBytes) {
        if (network[i] != target[i]) return@runCatching false
    }
    if (restBits > 0) {
        val mask = (0xFF shl (8 - restBits)) and 0xFF
        if ((network[fullBytes].toInt() and mask) != (target[fullBytes].toInt() and mask)) return@runCatching false
    }
    true
}.getOrDefault(false)
