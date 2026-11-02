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

package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.kernel.KernelManager
import com.suanran.dreambox.core.model.AccessControlMode
import com.suanran.dreambox.core.model.tunnel.RunMode
import com.suanran.dreambox.core.model.tunnel.TunDnsMode
import com.suanran.dreambox.core.model.tunnel.TunStack
import com.suanran.dreambox.feature.settings.presentation.viewmodel.CommonTunOptionsUiState
import com.suanran.dreambox.feature.settings.presentation.viewmodel.NetworkSettingsUiState
import com.suanran.dreambox.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.RootTunServiceOptionsUiState
import com.suanran.dreambox.feature.settings.presentation.viewmodel.TunServiceOptionsUiState
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.dialog.AppFormDialog
import com.suanran.dreambox.presentation.component.dialog.AppTextFieldDialog
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiArrowItem
import com.suanran.dreambox.presentation.component.misc.BocchiEnumItem
import com.suanran.dreambox.presentation.component.misc.BocchiSwitchItem
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.CPU
import com.suanran.dreambox.presentation.icon.flycat.PlaneTakeoff
import com.suanran.dreambox.presentation.icon.flycat.Tun
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.component.sortable.SortableSectionCard
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun NetworkSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessControlMode by viewModel.accessControlMode.state.collectAsStateWithLifecycle()
    val mmkv: com.tencent.mmkv.MMKV = koinInject(named("settings"))

    // 「延迟测速」分节已删除（2026-11-02 用户拍板）：统一延迟/TCP 并发不再提供开关。
    val defaultKeys = listOf("runmode", "advanced", "access", "kernel")
    // 分节固定默认顺序（拖动排序已删，2026-11-02 用户拍板）；隐藏功能保留。
    var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "network_settings")) }
    val listState = rememberLazyListState()
    val visibleOrder = defaultKeys.filter { it !in hiddenKeys }
    val sectionTitles = mapOf(
        "runmode" to "运行模式", "advanced" to "高级选项", "access" to "访问控制", "kernel" to "内核管理",
    )

    Scaffold(
        topBar = { TopBar(title = FlyTxt.NetworkSettings.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            itemsIndexed(visibleOrder, key = { _, k -> k }) { _, key ->
                    SortableSectionCard(
                        isDragging = false,
                        onHide = {
                            hiddenKeys = hiddenKeys + key
                            saveHiddenSections(mmkv, "network_settings", hiddenKeys + key)
                        },
                    ) {
                        when (key) {
                            "runmode" -> NetworkRunModeSection(
                                viewModel = viewModel,
                                runMode = uiState.configuredMode,
                                rootAvailable = uiState.rootAvailable,
                                ebpfAvailable = uiState.ebpfAvailable,
                            )
                            "advanced" -> NetworkAdvancedSection(
                                navigator = navigator,
                                viewModel = viewModel,
                                runMode = uiState.configuredMode,
                            )
                            "access" -> NetworkProxyOptionsSection(
                                navigator = navigator,
                                accessControlMode = accessControlMode,
                                showAccessControlMode = uiState.showAccessControlMode,
                                onAccessControlModeChange = viewModel::onAccessControlModeChange,
                            )
                            "kernel" -> NetworkKernelSection(viewModel)
                        }
                    }
            }
        }
    }
}

@Composable
private fun NetworkRunModeSection(
    viewModel: NetworkSettingsViewModel,
    runMode: RunMode,
    rootAvailable: Boolean,
    ebpfAvailable: Boolean,
) {
    BocchiSectionTitle(FlyTxt.NetworkSettings.RunMode.SectionTitle)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeCard(
            icon = FlyCat.PlaneTakeoff,
            title = FlyTxt.NetworkSettings.RunMode.VpnServiceTitle,
            summary = FlyTxt.NetworkSettings.RunMode.VpnServiceSummary,
            selected = runMode == RunMode.VpnService,
            enabled = true,
            onSelect = { viewModel.onRunModeChange(RunMode.VpnService) },
        )
        ModeCard(
            icon = FlyCat.Tun,
            title = FlyTxt.NetworkSettings.RunMode.TunTitle,
            summary = FlyTxt.NetworkSettings.RunMode.TunSummary,
            selected = runMode == RunMode.Tun,
            enabled = rootAvailable,
            onSelect = { viewModel.onRunModeChange(RunMode.Tun) },
        )
        ModeCard(
            icon = FlyCat.CPU,
            title = FlyTxt.NetworkSettings.RunMode.EbpfTitle,
            summary = FlyTxt.NetworkSettings.RunMode.EbpfSummary,
            selected = runMode == RunMode.Ebpf,
            enabled = ebpfAvailable,
            onSelect = { viewModel.onRunModeChange(RunMode.Ebpf) },
        )
    }
}

@Composable
private fun NetworkAdvancedSection(
    navigator: Navigator,
    viewModel: NetworkSettingsViewModel,
    runMode: RunMode,
) {
    BocchiSectionTitle(FlyTxt.NetworkSettings.Section.Advanced)
    BocchiCard {
        BocchiArrowItem(
            title = FlyTxt.NetworkSettings.Section.VpnOptions,
            onClick = {
                when (runMode) {
                    RunMode.VpnService -> navigator.push(Route.VpnServiceOptions)
                    RunMode.Tun -> navigator.push(Route.TunServiceOptions)
                    RunMode.Ebpf -> navigator.push(Route.EbpfServiceOptions)
                }
            },
        )
        BocchiSwitchItem(
            title = FlyTxt.NetworkSettings.Advanced.DisableOverrideTitle,
            checked = viewModel.disableAllOverride.value,
            onCheckedChange = viewModel::onDisableAllOverrideChange,
        )
    }
}

@Composable
private fun NetworkProxyOptionsSection(
    navigator: Navigator,
    accessControlMode: AccessControlMode,
    showAccessControlMode: Boolean,
    onAccessControlModeChange: (AccessControlMode) -> Unit,
) {
    BocchiSectionTitle(FlyTxt.NetworkSettings.Section.ProxyOptions)
    BocchiCard {
        if (showAccessControlMode) {
            BocchiEnumItem(
                title = FlyTxt.NetworkSettings.ProxyOptions.AccessControlModeTitle,
                currentValue = accessControlMode,
                items =
                    listOf(
                        FlyTxt.NetworkSettings.ProxyOptions.AllowAll,
                        FlyTxt.NetworkSettings.ProxyOptions.AllowSelected,
                        FlyTxt.NetworkSettings.ProxyOptions.RejectSelected,
                    ),
                values =
                    listOf(
                        AccessControlMode.ALLOW_ALL,
                        AccessControlMode.ALLOW_SPECIFIC,
                        AccessControlMode.DENY_SPECIFIC
                    ),
                onValueChange = onAccessControlModeChange,
            )
        }
        BocchiArrowItem(
            title = FlyTxt.NetworkSettings.ProxyOptions.ManageAccessControlTitle,
            onClick = { navigator.push(Route.AccessControl) },
        )
    }
}

/**
 * A run-mode option: its own card with an icon on the leading side and a trailing
 * selection radio. A disabled mode greys its contents and can't be selected.
 */
/** 内核管理新布局的小件：图标贴片行 / 状态胶囊 / 分隔线（与功能包页同一套语言）。 */
@Composable
private fun KernelIconRow(
    icon: String,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            top.yukonga.miuix.kmp.basic.Text(text = icon, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            top.yukonga.miuix.kmp.basic.Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                top.yukonga.miuix.kmp.basic.Text(
                    text = summary,
                    fontSize = 12.5.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun KernelChip(text: String, filled: Boolean) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (filled) Modifier.background(colorScheme.primary)
                else Modifier.border(1.dp, colorScheme.primary.copy(alpha = 0.55f), shape)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (filled) Color.White else colorScheme.primary,
        )
    }
}

@Composable
private fun KernelChevron() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun KernelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 69.dp, end = 16.dp)
            .height(1.dp)
            .background(colorScheme.onSurfaceVariantSummary.copy(alpha = 0.10f)),
    )
}

@Composable
private fun KernelListRow(
    label: String,
    summary: String?,
    active: Boolean,
    installed: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !downloading, onClick = onClick)
            .padding(start = 69.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active -> colorScheme.primary
                        installed -> Color(0xFF43A047)
                        else -> colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                    }
                ),
        )
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            top.yukonga.miuix.kmp.basic.Text(
                text = label,
                fontSize = 14.5.sp,
                color = colorScheme.onSurface,
            )
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                top.yukonga.miuix.kmp.basic.Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        when {
            downloading -> top.yukonga.miuix.kmp.basic.Text(
                text = FlyTxt.NetworkSettings.Kernel.DownloadingTag,
                fontSize = 12.5.sp,
                color = colorScheme.primary,
            )
            active -> KernelChip(text = FlyTxt.NetworkSettings.Kernel.ActiveTag, filled = true)
            installed -> KernelChip(text = "切换", filled = false)
            else -> KernelChip(text = "下载", filled = false)
        }
    }
}

@Composable
private fun NetworkKernelSection(viewModel: NetworkSettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val kernels by viewModel.kernels.collectAsStateWithLifecycle()
    val activeKernelId by viewModel.activeKernelId.collectAsStateWithLifecycle()
    val kernelBusy by viewModel.kernelBusy.collectAsStateWithLifecycle()
    val downloadingIds by viewModel.downloadingKernelIds.collectAsStateWithLifecycle()
    val installedCommits by viewModel.installedKernelCommits.collectAsStateWithLifecycle()
    var showCustomDialog by remember { mutableStateOf(false) }
    val restartRequired by viewModel.restartRequired.collectAsStateWithLifecycle()
    var kernelsExpanded by remember { mutableStateOf(false) }

    BocchiSectionTitle(FlyTxt.NetworkSettings.Section.Kernel)
    BocchiCard {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            // 当前内核（展示行，切换在下方可用内核列表里点）
            KernelIconRow(
                icon = "🧠",
                title = FlyTxt.NetworkSettings.Kernel.ActiveTitle,
                summary = kernelLabel(activeKernelId, installedCommits[activeKernelId]),
                trailing = { KernelChip(text = FlyTxt.NetworkSettings.Kernel.ActiveTag, filled = true) },
            )
            KernelDivider()
            // 检查更新
            KernelIconRow(
                icon = "🔄",
                title = FlyTxt.NetworkSettings.Kernel.RefreshTitle,
                summary = if (kernels.isNotEmpty()) "共 ${kernels.size} 个可用内核" else "点按获取最新内核列表",
                onClick = { viewModel.refreshKernels() },
                trailing = { KernelChevron() },
            )
            // 可用内核：内置 + 索引目录 + 已装的自定义内核（保证任何已装内核都能切回）
            val displayKernels = remember(kernels, installedCommits) {
                val list = mutableListOf<NetworkSettingsViewModel.KernelInfo>()
                if (kernels.none { it.id == KernelManager.BUNDLED_ALPHA_ID }) {
                    list += NetworkSettingsViewModel.KernelInfo(
                        id = KernelManager.BUNDLED_ALPHA_ID, name = "", version = "内置",
                        commit = installedCommits[KernelManager.BUNDLED_ALPHA_ID] ?: "",
                    )
                }
                list += kernels
                val known = list.map { it.id }.toSet()
                installedCommits.keys.filter { it !in known }.sorted().forEach { id ->
                    list += NetworkSettingsViewModel.KernelInfo(
                        id = id, name = "", version = "已安装", commit = installedCommits[id] ?: "",
                    )
                }
                list
            }
            KernelDivider()
            KernelIconRow(
                icon = "📦",
                title = "可用内核（${displayKernels.size}）",
                summary = if (kernelsExpanded) "点按收起" else "点按展开，选内核切换或下载",
                onClick = { kernelsExpanded = !kernelsExpanded },
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.graphicsLayer { rotationZ = if (kernelsExpanded) 180f else 0f },
                    )
                },
            )
            AnimatedVisibility(
                visible = kernelsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    for (kernel in displayKernels) {
                        val isInstalled = kernel.id == KernelManager.BUNDLED_ALPHA_ID || KernelManager.isInstalled(context, kernel.id)
                        val isActive = activeKernelId == kernel.id
                        val isDownloading = kernel.id in downloadingIds
                        KernelListRow(
                            label = kernelLabel(kernel.id, kernel.commit),
                            summary = when {
                                isDownloading -> FlyTxt.NetworkSettings.Kernel.DownloadingTag
                                isActive -> "当前使用中"
                                isInstalled -> FlyTxt.NetworkSettings.Kernel.InstalledTag
                                else -> kernel.version
                            },
                            active = isActive,
                            installed = isInstalled,
                            downloading = isDownloading,
                            onClick = {
                                if (!kernelBusy) {
                                    if (isInstalled) {
                                        viewModel.selectKernel(kernel.id)
                                    } else {
                                        viewModel.downloadKernels(setOf(kernel.id)) {}
                                    }
                                }
                            },
                        )
                    }
                }
            }
            KernelDivider()
            // 自定义内核
            KernelIconRow(
                icon = "🧩",
                title = FlyTxt.NetworkSettings.Kernel.CustomTitle,
                summary = "粘贴链接安装第三方内核",
                onClick = { showCustomDialog = true },
                trailing = { KernelChevron() },
            )
        }
    }

    if (showCustomDialog) {
        var customUrl by remember { mutableStateOf("") }
        var showFormatHint by remember { mutableStateOf(false) }
        AppFormDialog(
            show = showCustomDialog,
            title = FlyTxt.NetworkSettings.Kernel.CustomTitle,
            onDismissRequest = { showCustomDialog = false },
            onConfirm = {
                if (customUrl.isNotBlank()) {
                    viewModel.installCustomPluginUrl(customUrl.trim()) { success ->
                        if (success) showCustomDialog = false
                    }
                }
            },
        ) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = "https://example.com/kernel-plugin.zip",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(
                        onClick = { showFormatHint = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
        if (showFormatHint) {
            val annotatedText = androidx.compose.ui.text.buildAnnotatedString {
                val body = FlyTxt.NetworkSettings.Kernel.PluginFormatBody
                val url = "https://github.com/LM-Firefly/Kernel-Builder"
                val start = body.indexOf(url)
                if (start >= 0) {
                    append(body.substring(0, start))
                    val linkStart = length
                    append(url)
                    val linkEnd = length
                    addLink(
                        androidx.compose.ui.text.LinkAnnotation.Url(url = url),
                        start = linkStart,
                        end = linkEnd,
                    )
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            color = colorScheme.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        ),
                        start = linkStart,
                        end = linkEnd,
                    )
                    append(body.substring(start + url.length))
                } else {
                    append(body)
                }
            }
            AppFormDialog(
                show = true,
                title = FlyTxt.NetworkSettings.Kernel.PluginFormatTitle,
                onDismissRequest = { showFormatHint = false },
                onConfirm = { showFormatHint = false },
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = annotatedText,
                    style = androidx.compose.ui.text.TextStyle(
                        color = colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }

    if (restartRequired) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AppFormDialog(
            show = true,
            title = FlyTxt.NetworkSettings.Kernel.RestartDialogTitle,
            onDismissRequest = { viewModel.dismissRestartPrompt() },
            onConfirm = {
                viewModel.dismissRestartPrompt()
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            },
        ) {
            top.yukonga.miuix.kmp.basic.Text(
                text = FlyTxt.NetworkSettings.Kernel.RestartDialogMessage,
            )
        }
    }
}

private fun kernelLabel(id: String, commit: String? = null): String {
    val name = when (id) {
        KernelManager.BUNDLED_ALPHA_ID -> FlyTxt.NetworkSettings.Kernel.BundledAlpha
        "alpha" -> "Alpha"
        "meta" -> "Meta"
        "smart" -> "Smart"
        "ebpf" -> "eBPF"
        else -> id
    }
    return if (id == KernelManager.BUNDLED_ALPHA_ID || commit.isNullOrBlank()) {
        name
    } else {
        "$name-${commit.take(6)}"
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    BocchiCard {
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = if (enabled) onSelect else null,
            startAction = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (enabled) {
                            colorScheme.onSurface
                        } else {
                            colorScheme.disabledOnSecondaryVariant
                        },
                    modifier = Modifier
                        .padding(start = 4.dp, end = 12.dp)
                        .size(24.dp),
                )
            },
            endActions = {
                RadioButton(
                    selected = selected,
                    onClick = if (enabled) onSelect else null,
                    enabled = enabled,
                )
            },
        )
    }
}

// ── Reusable composables for service options pages ──

@Composable
internal fun CommonTunServiceOptions(
    state: CommonTunOptionsUiState,
    actions: CommonTunOptionActions,
    extraOptions: @Composable () -> Unit,
) {
    BocchiSwitchItem(
        title = FlyTxt.NetworkSettings.VpnOptions.BypassPrivateTitle,
        checked = state.bypassPrivateNetwork,
        onCheckedChange = actions.onBypassPrivateNetworkChange,
    )
    BocchiEnumItem(
        title = FlyTxt.NetworkSettings.VpnOptions.TunStackTitle,
        currentValue = state.tunStack,
        items =
            listOf(
                FlyTxt.NetworkSettings.VpnOptions.TunStackSystem,
                FlyTxt.NetworkSettings.VpnOptions.TunStackGVisor,
                FlyTxt.NetworkSettings.VpnOptions.TunStackMixed,
                FlyTxt.NetworkSettings.VpnOptions.TunStackMips,
            ),
        values = TunStack.entries,
        onValueChange = actions.onTunStackChange,
    )
    BocchiSwitchItem(
        title = FlyTxt.NetworkSettings.VpnOptions.DnsHijackTitle,
        checked = state.dnsHijack,
        onCheckedChange = actions.onDnsHijackChange,
    )
    BocchiSwitchItem(
        title = FlyTxt.NetworkSettings.VpnOptions.EnableIpv6Title,
        checked = state.enableIPv6,
        onCheckedChange = actions.onEnableIPv6Change,
    )
    extraOptions()
}

internal data class CommonTunOptionActions(
    val onBypassPrivateNetworkChange: (Boolean) -> Unit,
    val onDnsHijackChange: (Boolean) -> Unit,
    val onEnableIPv6Change: (Boolean) -> Unit,
    val onTunStackChange: (TunStack) -> Unit,
)

internal data class TunServiceOptionActions(
    val common: CommonTunOptionActions,
    val onAllowBypassChange: (Boolean) -> Unit,
    val onSystemProxyChange: (Boolean) -> Unit,
)

internal data class RootTunServiceOptionActions(
    val common: CommonTunOptionActions,
    val onTunAutoRouteChange: (Boolean) -> Unit,
    val onTunStrictRouteChange: (Boolean) -> Unit,
    val ontunAutoRedirectChange: (Boolean) -> Unit,
    val ontunDnsModeChange: (TunDnsMode) -> Unit,
    val ontunIfNameDraftChange: (String) -> Unit,
    val ontunMtuDraftChange: (String) -> Unit,
    val ontunFakeIpRangeDraftChange: (String) -> Unit,
    val ontunFakeIpRange6DraftChange: (String) -> Unit,
    val committunIfName: () -> Unit,
    val committunMtu: () -> Unit,
    val committunFakeIpRange: () -> Unit,
    val committunFakeIpRange6: () -> Unit,
)

@Composable
internal fun TunServiceOptions(state: TunServiceOptionsUiState, actions: TunServiceOptionActions) {
    CommonTunServiceOptions(
        state = state.common,
        actions = actions.common,
        extraOptions = {
            BocchiSwitchItem(
                title = FlyTxt.NetworkSettings.VpnOptions.AllowBypassTitle,
                checked = state.allowBypass,
                onCheckedChange = actions.onAllowBypassChange,
            )
            BocchiSwitchItem(
                title = FlyTxt.NetworkSettings.VpnOptions.SystemProxyTitle,
                checked = state.systemProxy,
                onCheckedChange = actions.onSystemProxyChange,
            )
        },
    )
}

@Composable
internal fun RootTunServiceOptions(
    state: RootTunServiceOptionsUiState,
    showFakeIpRange: Boolean,
    actions: RootTunServiceOptionActions,
) {
    CommonTunServiceOptions(
        state = state.common,
        actions = actions.common,
        extraOptions = {
            RootTunAdvancedOptions(
                state = state,
                showFakeIpRange = showFakeIpRange,
                actions = actions,
            )
        },
    )
}

@Composable
private fun RootTunAdvancedOptions(
    state: RootTunServiceOptionsUiState,
    showFakeIpRange: Boolean,
    actions: RootTunServiceOptionActions,
) {
    var editDialog by remember { mutableStateOf<RootTunEditDialogState?>(null) }

    RootTunIdentityOptions(
        tunIfNameDraft = state.tunIfNameDraft,
        tunMtuDraft = state.tunMtuDraft,
        onEditIfName = { editDialog = RootTunEditDialogState.IfName },
        onEditMtu = { editDialog = RootTunEditDialogState.Mtu },
    )
    RootTunRoutingOptions(
        tunAutoRoute = state.tunAutoRoute,
        tunStrictRoute = state.tunStrictRoute,
        tunAutoRedirect = state.tunAutoRedirect,
        tunDnsMode = state.tunDnsMode,
        onTunAutoRouteChange = actions.onTunAutoRouteChange,
        onTunStrictRouteChange = actions.onTunStrictRouteChange,
        ontunAutoRedirectChange = actions.ontunAutoRedirectChange,
        ontunDnsModeChange = actions.ontunDnsModeChange,
    )
    RootTunFakeIpOptions(
        showFakeIpRange = showFakeIpRange,
        tunFakeIpRangeDraft = state.tunFakeIpRangeDraft,
        tunFakeIpRange6Draft = state.tunFakeIpRange6Draft,
        onEditFakeIpRange = { editDialog = RootTunEditDialogState.FakeIpRange },
        onEditFakeIpRange6 = { editDialog = RootTunEditDialogState.FakeIpRange6 },
    )

    RootTunEditDialogs(
        editDialog = editDialog,
        state = state,
        actions = actions,
        onDismiss = { editDialog = null },
    )
}

@Composable
private fun RootTunIdentityOptions(
    tunIfNameDraft: String,
    tunMtuDraft: String,
    onEditIfName: () -> Unit,
    onEditMtu: () -> Unit,
) {
    BocchiArrowItem(
        title = FlyTxt.NetworkSettings.RootTun.IfNameTitle,
        summary = tunIfNameDraft.ifBlank { FlyTxt.NetworkSettings.RootTun.IfNameSummary },
        onClick = onEditIfName,
    )
    BocchiArrowItem(
        title = FlyTxt.NetworkSettings.RootTun.MtuTitle,
        summary = tunMtuDraft.ifBlank { FlyTxt.NetworkSettings.RootTun.MtuSummary },
        onClick = onEditMtu,
    )
}

@Composable
private fun RootTunRoutingOptions(
    tunAutoRoute: Boolean,
    tunStrictRoute: Boolean,
    tunAutoRedirect: Boolean,
    tunDnsMode: TunDnsMode,
    onTunAutoRouteChange: (Boolean) -> Unit,
    onTunStrictRouteChange: (Boolean) -> Unit,
    ontunAutoRedirectChange: (Boolean) -> Unit,
    ontunDnsModeChange: (TunDnsMode) -> Unit,
) {
    BocchiSwitchItem(
        title = FlyTxt.NetworkSettings.RootTun.AutoRouteTitle,
        checked = tunAutoRoute,
        onCheckedChange = onTunAutoRouteChange,
    )
    BocchiSwitchItem(
        title = FlyTxt.NetworkSettings.RootTun.StrictRouteTitle,
        checked = tunStrictRoute,
        onCheckedChange = onTunStrictRouteChange,
    )
    BocchiSwitchItem(
        title = FlyTxt.NetworkSettings.RootTun.AutoRedirectTitle,
        checked = tunAutoRedirect,
        onCheckedChange = ontunAutoRedirectChange,
    )
    BocchiEnumItem(
        title = FlyTxt.NetworkSettings.RootTun.DnsModeTitle,
        currentValue = tunDnsMode,
        items =
            listOf(
                FlyTxt.NetworkSettings.RootTun.DnsModeRedirHost,
                FlyTxt.NetworkSettings.RootTun.DnsModeFakeIp,
            ),
        values = TunDnsMode.entries,
        onValueChange = ontunDnsModeChange,
    )
}

@Composable
private fun RootTunFakeIpOptions(
    showFakeIpRange: Boolean,
    tunFakeIpRangeDraft: String,
    tunFakeIpRange6Draft: String,
    onEditFakeIpRange: () -> Unit,
    onEditFakeIpRange6: () -> Unit,
) {
    AnimatedVisibility(
        visible = showFakeIpRange,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            BocchiArrowItem(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRangeTitle,
                summary =
                    tunFakeIpRangeDraft.ifBlank {
                        FlyTxt.NetworkSettings.RootTun.FakeIpRangeSummary
                    },
                onClick = onEditFakeIpRange,
            )
            BocchiArrowItem(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRange6Title,
                summary =
                    tunFakeIpRange6Draft.ifBlank {
                        FlyTxt.NetworkSettings.RootTun.FakeIpRange6Summary
                    },
                onClick = onEditFakeIpRange6,
            )
        }
    }
}

@Composable
private fun RootTunEditDialogs(
    editDialog: RootTunEditDialogState?,
    state: RootTunServiceOptionsUiState,
    actions: RootTunServiceOptionActions,
    onDismiss: () -> Unit,
) {
    when (editDialog) {
        RootTunEditDialogState.IfName ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.IfNameTitle,
                value = state.tunIfNameDraft,
                onValueChange = actions.ontunIfNameDraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunIfName,
            )

        RootTunEditDialogState.Mtu ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.MtuTitle,
                value = state.tunMtuDraft,
                onValueChange = actions.ontunMtuDraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunMtu,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )

        RootTunEditDialogState.FakeIpRange ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRangeTitle,
                value = state.tunFakeIpRangeDraft,
                onValueChange = actions.ontunFakeIpRangeDraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunFakeIpRange,
            )

        RootTunEditDialogState.FakeIpRange6 ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRange6Title,
                value = state.tunFakeIpRange6Draft,
                onValueChange = actions.ontunFakeIpRange6DraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunFakeIpRange6,
            )

        null -> {}
    }
}

@Composable
private fun RootTunTextEditDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
) {
    val focusManager = LocalFocusManager.current

    AppTextFieldDialog(
        show = true,
        title = title,
        value = value,
        onValueChange = onValueChange,
        onDismissRequest = {
            onDismiss()
            focusManager.clearFocus()
        },
        onConfirm = {
            onCommit()
            onDismiss()
            focusManager.clearFocus()
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(
            onDone = {
                onCommit()
                onDismiss()
                focusManager.clearFocus()
            }
        ),
    )
}

private enum class RootTunEditDialogState { IfName, Mtu, FakeIpRange, FakeIpRange6 }
