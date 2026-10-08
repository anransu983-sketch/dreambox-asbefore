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

package com.suanran.dreambox.feature.about.presentation.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.BuildConfigHolder
import com.suanran.dreambox.feature.about.GitHubUpdateViewModel
import com.suanran.dreambox.feature.about.UpdateCandidate
import com.suanran.dreambox.feature.about.UpdateDownloadProgress
import com.suanran.dreambox.feature.about.UpdateManifestPackage
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.dialog.DialogButtonRow
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.UiDp
import com.suanran.dreambox.presentation.util.toast
import com.suanran.dreambox.ui.platform.openUrl
import kotlinx.coroutines.flow.StateFlow
import com.suanran.dreambox.presentation.component.sortable.DraggableItem
import com.suanran.dreambox.presentation.component.sortable.ResetOrderButton
import com.suanran.dreambox.presentation.component.sortable.SortableSectionCard
import com.suanran.dreambox.presentation.component.sortable.clearSectionOrder
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import com.suanran.dreambox.presentation.component.sortable.hiddenSectionsRestore
import com.suanran.dreambox.presentation.component.sortable.loadSectionOrder
import com.suanran.dreambox.presentation.component.sortable.rememberDragDropState
import com.suanran.dreambox.presentation.component.sortable.saveSectionOrder
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val AppNameGradient =
    listOf(
        Color(0xFFEFC0D9),
        Color(0xFFD2C6F0),
        Color(0xFFC0D5F5),
    )

// Kernel version is read directly from BuildConfig (set at build time from mihomo git).
private fun formatKernelVersion(): String {
    val raw = BuildConfigHolder.kernelGitVersion.trimEnd('_')  // "Alpha_944e8e1_" → "Alpha_944e8e1"
    val parts = raw.split("_")
    return if (parts.size >= 2 && parts[0].isNotBlank()) {
        "${parts[0]}.${parts[1]}"
    } else {
        raw.ifBlank { "unknown" }
    }
}

@Composable
fun AboutScreen(navigator: Navigator, appIconResId: Int) {
    val context = LocalContext.current
    val updateViewModel = koinViewModel<GitHubUpdateViewModel>()
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val downloadProgress by updateViewModel.downloadProgress.collectAsStateWithLifecycle()
    var dialogCandidate by remember { mutableStateOf<UpdateCandidate?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val coreVersion = remember { formatKernelVersion() }
    LaunchedEffect(updateUiState.message) {
        updateUiState.message?.let { message ->
            context.toast(message)
            updateViewModel.consumeMessage()
        }
    }
    LaunchedEffect(updateUiState.candidate) {
        updateUiState.candidate?.let {
            Timber.i("AboutScreen received update candidate: tag=%s version=%s", it.tag, it.versionName)
            dialogCandidate = it
        }
    }
    // Close dialog when download finishes (isDownloading transitions from true to false)
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(downloadProgress.isDownloading) {
        if (wasDownloading && !downloadProgress.isDownloading) { dialogCandidate = null }
        wasDownloading = downloadProgress.isDownloading
    }

    Scaffold(topBar = { TopBar(title = FlyTxt.About.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }) {
        innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        val mmkv: com.tencent.mmkv.MMKV = koinInject(named("settings"))
        val defaultKeys = listOf("info", "links", "license")
        var order by remember { mutableStateOf(loadSectionOrder(mmkv, "about", defaultKeys)) }
        var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "about")) }
        val visibleOrder = order.filter { it !in hiddenKeys }
        val sectionTitles = mapOf("info" to "应用信息", "links" to "项目链接", "license" to "许可证")
        val listState = rememberLazyListState()
        val dragState = rememberDragDropState(listState) { from, to ->
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            saveSectionOrder(mmkv, "about", order)
        }
        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(UiDp.dp24))
                    Icon(painter = painterResource(id = appIconResId), contentDescription = "App Icon", modifier = Modifier.size(UiDp.dp120).clip(RoundedCornerShape(UiDp.dp24)), tint = Color.Unspecified)
                    Spacer(modifier = Modifier.height(UiDp.dp24))
                    Text(text = "梦盒", style = MiuixTheme.textStyles.title1.copy(brush = Brush.linearGradient(AppNameGradient)))
                    Spacer(modifier = Modifier.height(UiDp.dp8))
                    Text(text = "${BuildConfigHolder.versionName} ($coreVersion)", style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(UiDp.dp4))
                    Text(text = "UI Build: ${BuildConfigHolder.uiBuildId.ifBlank { "000000000000-000000" }}", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(UiDp.dp32))
                }
            }

            // 可拖拽的功能卡片
            val sectionContent: Map<String, @Composable () -> Unit> = mapOf(
                "info" to {
BocchiCard {
                    BasicComponent(
                        title = "梦盒",
                        summary = "基于 Mihomo 的开源安卓客户端",
                    )
                    ArrowPreference(
                        title = FlyTxt.About.License.CheckUpdate,
                        summary = if (updateUiState.isChecking) { FlyTxt.Component.Update.Message.Checking } else { FlyTxt.About.License.CheckUpdateSummary },
                        enabled = !updateUiState.isChecking && !downloadProgress.isDownloading,
                        onClick = updateViewModel::checkForUpdate,
                    )
                }
                },
                "links" to {
BocchiSectionTitle(FlyTxt.About.Section.ProjectLinks)
                BocchiCard {
                    AboutLinkItem(
                        title = "梦盒",
                        url = "https://github.com/anransu983-sketch/dreambox-asbefore",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                    AboutLinkItem(
                        title = "Mihomo",
                        url = "https://github.com/MetaCubeX/mihomo",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                }
                },
                "license" to {
BocchiSectionTitle(FlyTxt.About.Section.License)
                BocchiCard {
                    ArrowPreference(
                        title = FlyTxt.About.License.Libraries,
                        summary = FlyTxt.About.License.LibrariesSummary,
                        onClick = { navigator.push(Route.OpenSourceLicenses) },
                    )
                    BasicComponent(
                        title = FlyTxt.About.License.AgplName,
                        summary = FlyTxt.About.License.AgplDescription,
                    )
                }
                },
            )
            itemsIndexed(visibleOrder, key = { _, k -> k }) { index, key ->
                DraggableItem(state = dragState, index = index) { isDragging ->
                    SortableSectionCard(
                        isDragging = isDragging,
                        onHide = {
                            hiddenKeys = hiddenKeys + key
                            saveHiddenSections(mmkv, "about", hiddenKeys + key)
                        },
                    ) {
                        sectionContent[key]?.invoke()
                    }
                }
            }
            item {
                ResetOrderButton(onClick = {
                    clearSectionOrder(mmkv, "about")
                    order = defaultKeys
                })
            }
            item {
                // 重置主页模块位置
                com.suanran.dreambox.presentation.component.card.Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            mmkv.remove("settings_module_order")
                            context.toast("已重置主页模块位置")
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "重置主页模块位置",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = UiDp.dp32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = FlyTxt.About.Copyright, style = MiuixTheme.textStyles.footnote1)
                }
                Spacer(modifier = Modifier.height(UiDp.dp32))
            }
        }
    }
    UpdateCandidateDialog(
        candidate = dialogCandidate,
        downloadProgressFlow = updateViewModel.downloadProgress,
        onDismiss = {
            dialogCandidate = null
            updateViewModel.dismissCandidate()
        },
        onDownload = updateViewModel::downloadAndInstall,
        onCancelDownload = updateViewModel::cancelDownload,
    )
}

@Composable
private fun AboutLinkItem(
    title: String,
    url: String,
    onOpenUrl: (String) -> Unit,
    showArrow: Boolean,
) {
    if (showArrow) {
        ArrowPreference(title = title, summary = url, onClick = { onOpenUrl(url) })
    } else {
        BasicComponent(title = title, summary = url, onClick = { onOpenUrl(url) })
    }
}

@Composable
private fun UpdateCandidateDialog(candidate: UpdateCandidate?, downloadProgressFlow: StateFlow<UpdateDownloadProgress>, onDismiss: () -> Unit, onDownload: (UpdateCandidate, UpdateManifestPackage?) -> Unit, onCancelDownload: () -> Unit) {
    if (candidate == null) return
    // 直接按本机 ABI 自动匹配安装包，不再让用户选择
    val autoPackage = remember(candidate) { candidate.selectablePackages().firstOrNull() }
    val releaseNotes = candidate.releaseNotes.ifBlank { FlyTxt.Component.Update.Message.Available }
    val message = buildString {
        appendLine("${FlyTxt.Component.Update.Message.CurrentVersion}: ${BuildConfigHolder.versionName}")
        appendLine("${FlyTxt.Component.Update.Message.RemoteVersion}: ${candidate.versionName}")
        if (candidate.tag.isNotBlank()) { appendLine("${FlyTxt.Component.Update.Message.Tag}: ${candidate.tag}") }
        appendLine()
        append(releaseNotes)
        if (autoPackage != null) {
            appendLine()
            appendLine("${FlyTxt.Component.Update.Message.DownloadFile}: ${autoPackage.fileName}")
        } else {
            appendLine()
            append(FlyTxt.Component.Update.Message.NoCompatibleAsset)
        }
    }
    val downloadProgress by downloadProgressFlow.collectAsStateWithLifecycle()
    val isDownloading = downloadProgress.isDownloading
    WindowDialog(
        show = true,
        title = if (isDownloading) FlyTxt.Component.Update.Message.Downloading else FlyTxt.Component.Update.Title.Available,
        titleColor = DialogDefaults.titleColor(),
        summary = null,
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = { if (isDownloading) { onCancelDownload() } else { onDismiss() } },
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        defaultWindowInsetsPadding = true,
    ) {
        val dp by downloadProgressFlow.collectAsStateWithLifecycle()
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.75).dp
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight)) {
            if (dp.isDownloading) {
                UpdateDownloadContent(progress = dp, onCancelDownload = onCancelDownload)
            } else {
                // Fixed header
                UpdateDialogMessage(message)
                // Fixed buttons
                Spacer(modifier = Modifier.height(UiDp.dp12))
                DialogButtonRow(
                    onCancel = onDismiss,
                    onConfirm = { autoPackage?.let { onDownload(candidate, it) } },
                    confirmText = FlyTxt.Component.Update.Action.DownloadNow,
                    confirmEnabled = autoPackage != null,
                )
            }
        }
    }
}

private fun UpdateCandidate.selectablePackages(): List<UpdateManifestPackage> {
    val available = manifest.packages.filter {
        it.downloadUrl.isNotBlank() &&
            !it.fileName.contains("standalone", ignoreCase = true)
    }
    if (available.isEmpty()) return emptyList()
    val primaryAbi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
    val exactMatches = if (primaryAbi.isNotBlank()) { available.filter { it.abi.equals(primaryAbi, ignoreCase = true) } } else { emptyList() }
    val universalMatches = available.filter { it.isUniversal || it.abi.equals("universal", ignoreCase = true) }
    val compatible = (exactMatches + universalMatches).distinctBy { it.fileName.ifBlank { it.downloadUrl } }
    val pool = if (compatible.isNotEmpty()) compatible else available.distinctBy { it.fileName.ifBlank { it.downloadUrl } }
    // Release 里堆了大量历史包，GitHub API 按上传顺序（旧在前）返回，必须按构建时间戳倒序，否则永远选中旧包
    return pool.sortedByDescending { BUILD_STAMP_REGEX.find(it.fileName)?.groupValues?.getOrNull(1).orEmpty() }
}

private val BUILD_STAMP_REGEX = Regex("([0-9]{12})-[0-9a-fA-F]{5,8}\\.apk$")

@Composable
private fun UpdateDownloadContent(progress: UpdateDownloadProgress, onCancelDownload: () -> Unit) {
    val progressValue = (progress.progress.coerceIn(0, 100) / 100f)
    Column {
        UpdateDialogMessage(progress.message.ifBlank { FlyTxt.Component.Update.Message.Downloading })
        Spacer(modifier = Modifier.height(UiDp.dp12))
        Box(modifier = Modifier.fillMaxWidth().height(UiDp.dp8).clip(RoundedCornerShape(UiDp.dp100)).background(MiuixTheme.colorScheme.secondaryVariant.copy(alpha = 0.24f))) {
            if (progressValue > 0f) {
                Box(modifier = Modifier.fillMaxWidth(progressValue).fillMaxHeight().clip(RoundedCornerShape(UiDp.dp100)).background(MiuixTheme.colorScheme.primary))
            }
        }
        Spacer(modifier = Modifier.height(UiDp.dp12))
        Button(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp8), verticalAlignment = Alignment.CenterVertically) {
                Text(text = FlyTxt.Component.Button.Cancel, color = MiuixTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun UpdateDialogMessage(message: String) {
    Text(text = message, style = MiuixTheme.textStyles.body1)
}
