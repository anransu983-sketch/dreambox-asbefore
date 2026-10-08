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

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.core.contract.StoreSynchronizer
import com.suanran.dreambox.core.model.GeoFileType
import com.suanran.dreambox.core.model.geoXItems
import com.suanran.dreambox.feature.settings.presentation.backup.BackupRestoreSection
import com.suanran.dreambox.feature.settings.presentation.util.MetaWebDavConfig
import com.suanran.dreambox.feature.settings.presentation.viewmodel.MetaFeatureViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.component.dialog.AppConfirmDialog
import com.suanran.dreambox.presentation.component.dialog.AppDialog
import com.suanran.dreambox.presentation.component.dialog.AppFormDialog
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.Title
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
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
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
fun MetaFeatureScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: MetaFeatureViewModel = koinViewModel()
    val storeSynchronizer = koinInject<StoreSynchronizer>()
    val selectedPanelType by viewModel.panelSettings.selectedPanelType.state.collectAsStateWithLifecycle()

    val showGeoXDownloadSheet = remember { mutableStateOf(false) }
    var showWebDavRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showWebDavConfigDialog by remember { mutableStateOf(false) }
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartMessage by remember { mutableStateOf("") }
    var loadingTitle by remember { mutableStateOf("") }
    var loadingMessage by remember { mutableStateOf("") }
    var webDavError by remember { mutableStateOf<String?>(null) }
    var webDavUrlField by remember { mutableStateOf(TextFieldValue("")) }
    var webDavDirField by remember { mutableStateOf(TextFieldValue("")) }
    var webDavAccountField by remember { mutableStateOf(TextFieldValue("")) }
    var webDavPasswordField by remember { mutableStateOf(TextFieldValue("")) }

    val ageKeyHybrid = remember { mutableStateOf(false) }
    val ageKeyDialogVisible = remember { mutableStateOf(false) }

    fun openWebDavDialog() {
        val config = viewModel.getWebDavConfig()
        webDavUrlField = TextFieldValue(config.url, TextRange(config.url.length))
        webDavDirField = TextFieldValue(config.directory, TextRange(config.directory.length))
        webDavAccountField = TextFieldValue(config.account, TextRange(config.account.length))
        webDavPasswordField = TextFieldValue(config.password, TextRange(config.password.length))
        webDavError = null
        showWebDavConfigDialog = true
    }

    fun currentDraftConfig(): MetaWebDavConfig {
        return MetaWebDavConfig(
            url = webDavUrlField.text.trim(),
            directory = webDavDirField.text.trim(),
            account = webDavAccountField.text.trim(),
            password = webDavPasswordField.text,
        )
    }

    Scaffold(
        topBar = { TopBar(title = FlyTxt.MetaFeature.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        val mmkv: com.tencent.mmkv.MMKV = koinInject(named("settings"))
        val defaultKeys = listOf("conn", "routing", "panel", "backup", "webdav", "agekey")
        var order by remember { mutableStateOf(loadSectionOrder(mmkv, "meta_feature", defaultKeys)) }
        var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "meta_feature")) }
        val visibleOrder = order.filter { it !in hiddenKeys }
        val sectionTitles = mapOf(
            "conn" to "连接与流量", "routing" to "分流相关", "panel" to "面板",
            "backup" to "备份恢复", "webdav" to "WebDAV", "agekey" to "Age 密钥",
        )
        val listState = rememberLazyListState()
        val dragState = rememberDragDropState(listState) { from, to ->
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            saveSectionOrder(mmkv, "meta_feature", order)
        }
    val sectionContent: Map<String, @Composable () -> Unit> = mapOf(
        "conn" to {
                Title(FlyTxt.MetaFeature.Section.ConnectionAndTraffic)
                Card {
                    ArrowPreference(
                        title = FlyTxt.Connection.Title,
                        summary = FlyTxt.Connection.Summary,
                        onClick = { navigator.push(Route.Connection) },
                    )
                    ArrowPreference(
                        title = FlyTxt.TrafficStatistics.Title,
                        summary = FlyTxt.TrafficStatistics.EntrySummary,
                        onClick = { navigator.push(Route.TrafficStatistics) },
                    )
                }
        },
        "routing" to {
                Title(FlyTxt.MetaFeature.Section.Routing)
                Card {
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.RuntimeRules.Title,
                        summary = FlyTxt.MetaFeature.RuntimeRules.Summary,
                        onClick = { navigator.push(Route.Rules) },
                    )
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.CustomRouting.Title,
                        summary = FlyTxt.MetaFeature.CustomRouting.Summary,
                        onClick = { navigator.push(Route.CustomRouting) },
                    )
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.GeoX.OnlineUpdateTitle,
                        summary = FlyTxt.MetaFeature.GeoX.OnlineUpdateSummary,
                        onClick = { showGeoXDownloadSheet.value = true },
                    )
                }
        },
        "panel" to {
                Title(FlyTxt.MetaFeature.Section.Panel)
                Card {
                    WindowDropdownPreference(
                        title = FlyTxt.MetaFeature.Panel.SelectPanel,
                        summary = MetaFeatureViewModel.PANEL_NAMES.getOrElse(selectedPanelType) { MetaFeatureViewModel.PANEL_NAMES[0] },
                        items = MetaFeatureViewModel.PANEL_NAMES,
                        selectedIndex = selectedPanelType.coerceIn(MetaFeatureViewModel.PANEL_NAMES.indices),
                        onSelectedIndexChange = { index -> viewModel.setPanelType(index) },
                    )
                }
        },
        "backup" to {
                BackupRestoreSection()
        },
        "webdav" to {
                Title(FlyTxt.MetaFeature.Section.BackupRestore + " — WebDAV")
                Card {
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.WebDav.ConfigTitle,
                        summary = FlyTxt.MetaFeature.WebDav.ConfigSummary,
                        onClick = { openWebDavDialog() },
                    )
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.WebDav.TestTitle,
                        summary = FlyTxt.MetaFeature.WebDav.TestSummary,
                        onClick = {
                            val config = viewModel.getWebDavConfig()
                            if (!config.isValid()) {
                                context.toast(FlyTxt.MetaFeature.WebDav.ConfigRequired)
                                return@ArrowPreference
                            }
                            scope.launch {
                                showLoadingDialog = true
                                loadingTitle = FlyTxt.MetaFeature.WebDav.TestLoadingTitle
                                loadingMessage = FlyTxt.MetaFeature.WebDav.TestLoadingMessage
                                try {
                                    viewModel.testWebDavConfig(config)
                                        .onSuccess {
                                            context.toast(FlyTxt.MetaFeature.WebDav.TestSuccess)
                                        }
                                        .onFailure { error ->
                                            context.toast(FlyTxt.MetaFeature.WebDav.TestFailed.format(error.localizedMessage ?: FlyTxt.Util.Error.UnknownError))
                                        }
                                } finally {
                                    showLoadingDialog = false
                                }
                            }
                        },
                    )
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.WebDav.BackupTitle,
                        summary = FlyTxt.MetaFeature.WebDav.BackupSummary,
                        onClick = {
                            scope.launch {
                                showLoadingDialog = true
                                loadingTitle = FlyTxt.MetaFeature.WebDav.BackupLoadingTitle
                                loadingMessage = FlyTxt.MetaFeature.WebDav.BackupLoadingMessage
                                try {
                                    viewModel.backupToWebDav(context)
                                        .onSuccess { remoteName ->
                                            context.toast(FlyTxt.MetaFeature.WebDav.BackupSuccess.format(remoteName))
                                        }
                                        .onFailure { error ->
                                            context.toast(FlyTxt.MetaFeature.WebDav.BackupFailed.format(error.localizedMessage ?: FlyTxt.Util.Error.UnknownError))
                                        }
                                } finally {
                                    showLoadingDialog = false
                                }
                            }
                        },
                    )
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.WebDav.RestoreTitle,
                        summary = FlyTxt.MetaFeature.WebDav.RestoreSummary,
                        onClick = {
                            showWebDavRestoreConfirmDialog = true
                        },
                    )
                }
        },
        "agekey" to {
                Title(FlyTxt.MetaFeature.AgeKey.Section)
                Card {
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.AgeKey.X25519Title,
                        onClick = {
                            ageKeyHybrid.value = false
                            ageKeyDialogVisible.value = true
                        },
                    )
                    ArrowPreference(
                        title = FlyTxt.MetaFeature.AgeKey.HybridTitle,
                        onClick = {
                            ageKeyHybrid.value = true
                            ageKeyDialogVisible.value = true
                        },
                    )
                }
        }
    )

        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            itemsIndexed(visibleOrder, key = { _, k -> k }) { index, key ->
                DraggableItem(state = dragState, index = index) { isDragging ->
                    SortableSectionCard(
                        isDragging = isDragging,
                        onHide = {
                            hiddenKeys = hiddenKeys + key
                            saveHiddenSections(mmkv, "meta_feature", hiddenKeys + key)
                        },
                    ) {
                        sectionContent[key]?.invoke()
                    }
                }
            }
            item {
                ResetOrderButton(onClick = {
                    clearSectionOrder(mmkv, "meta_feature")
                    order = defaultKeys
                })
            }
        }

        GeoXDownloadDialog(
            show = showGeoXDownloadSheet,
            context = context,
            viewModel = viewModel,
        )
        AppConfirmDialog(
            show = showWebDavRestoreConfirmDialog,
            title = FlyTxt.MetaFeature.WebDav.RestoreConfirmTitle,
            message = FlyTxt.MetaFeature.WebDav.RestoreConfirmMessage,
            onDismissRequest = { showWebDavRestoreConfirmDialog = false },
            onConfirm = {
                showWebDavRestoreConfirmDialog = false
                scope.launch {
                    showLoadingDialog = true
                    loadingTitle = FlyTxt.MetaFeature.WebDav.RestoreLoadingTitle
                    loadingMessage = FlyTxt.MetaFeature.WebDav.RestoreLoadingMessage
                    try {
                        viewModel.restoreLatestFromWebDav(context)
                            .onSuccess { remoteName ->
                                restartMessage = FlyTxt.MetaFeature.Backup.WebDavRestoreSuccess.format(remoteName)
                                showRestartDialog = true
                            }
                            .onFailure { error ->
                                context.toast(FlyTxt.MetaFeature.WebDav.RestoreFailed.format(error.localizedMessage ?: FlyTxt.Util.Error.UnknownError))
                            }
                    } finally {
                        showLoadingDialog = false
                    }
                }
            },
        )
        AppFormDialog(
            show = showWebDavConfigDialog,
            title = FlyTxt.MetaFeature.WebDav.ConfigTitle,
            summary = FlyTxt.MetaFeature.WebDav.ConfigUrlExample,
            onDismissRequest = { showWebDavConfigDialog = false },
            onConfirm = {
                val config = currentDraftConfig()
                webDavError = when {
                    config.url.isBlank() -> FlyTxt.MetaFeature.WebDav.ValidationUrlRequired
                    config.account.isBlank() -> FlyTxt.MetaFeature.WebDav.ValidationAccountRequired
                    config.password.isBlank() -> FlyTxt.MetaFeature.WebDav.ValidationPasswordRequired
                    else -> null
                }
                if (webDavError == null) {
                    viewModel.updateWebDavConfig(config)
                    showWebDavConfigDialog = false
                    context.toast(FlyTxt.MetaFeature.WebDav.ConfigSaved)
                }
            },
            error = webDavError,
        ) {
            TextField(
                value = webDavUrlField,
                onValueChange = {
                    webDavUrlField = it
                    webDavError = null
                },
                label = FlyTxt.MetaFeature.WebDav.LabelUrl,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = webDavDirField,
                onValueChange = {
                    webDavDirField = it
                    webDavError = null
                },
                label = FlyTxt.MetaFeature.WebDav.LabelDir,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = webDavAccountField,
                onValueChange = {
                    webDavAccountField = it
                    webDavError = null
                },
                label = FlyTxt.MetaFeature.WebDav.LabelAccount,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = webDavPasswordField,
                onValueChange = {
                    webDavPasswordField = it
                    webDavError = null
                },
                label = FlyTxt.MetaFeature.WebDav.LabelPassword,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppDialog(
            show = showLoadingDialog,
            title = loadingTitle,
            onDismissRequest = {},
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = loadingMessage)
            }
        }

        AppConfirmDialog(
            show = showRestartDialog,
            title = FlyTxt.MetaFeature.Backup.RestoreSuccessTitle,
            message = restartMessage,
            onDismissRequest = { showRestartDialog = false },
            onConfirm = {
                showRestartDialog = false
                restartApplication(context, storeSynchronizer)
            },
            confirmText = FlyTxt.MetaFeature.Backup.RestartNow,
        )

        AgeKeyGeneratorDialog(
            show = ageKeyDialogVisible.value,
            hybrid = ageKeyHybrid.value,
            onDismiss = { ageKeyDialogVisible.value = false },
            onDismissFinished = {},
        )
    }
}

@Composable
private fun GeoXDownloadDialog(
    show: MutableState<Boolean>,
    context: android.content.Context,
    viewModel: MetaFeatureViewModel,
) {
    val spacing = AppTheme.spacing
    val scope = rememberCoroutineScope()
    val selectedItems = remember { mutableStateMapOf<GeoFileType, Boolean>() }
    val canConfirm = selectedItems.values.any { it }

    AppDialog(
        show = show.value,
        title = FlyTxt.MetaFeature.Download.DialogTitle,
        onDismissRequest = { show.value = false },
    ) {
        Column {
            geoXItems.forEach { item ->
                BasicComponent(
                    title = item.title,
                    endActions = {
                        Checkbox(
                            state = ToggleableState(selectedItems[item.type] ?: false),
                            onClick = {
                                selectedItems[item.type] = !(selectedItems[item.type] ?: false)
                            },
                        )
                    },
                    onClick = {
                        selectedItems[item.type] = !(selectedItems[item.type] ?: false)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space16),
            ) {
                TextButton(
                    text = FlyTxt.Component.Button.Cancel,
                    onClick = { show.value = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = FlyTxt.Component.Button.Confirm,
                    onClick = {
                        val itemsToDownload = geoXItems.filter { selectedItems[it.type] == true }
                        if (itemsToDownload.isEmpty()) {
                            return@TextButton
                        }
                        show.value = false
                        scope.launch(CoroutineExceptionHandler { _, throwable -> if (!throwable.isOkHttpCancellationBug()) { throw throwable } }) {
                            val count = viewModel.downloadGeoXFiles(context, itemsToDownload)
                            context.toast(FlyTxt.MetaFeature.Download.DownloadComplete.format(count, itemsToDownload.size))
                        }
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

private fun restartApplication(context: android.content.Context, storeSynchronizer: StoreSynchronizer) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    if (launchIntent == null) {
        context.toast(FlyTxt.MetaFeature.Backup.RestartLaunchNotFound)
        return
    }
    context.startActivity(launchIntent)
    (context as? Activity)?.finishAffinity()
    storeSynchronizer.syncAll()
    exitProcess(0)
}

private fun Throwable.isOkHttpCancellationBug(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is IllegalStateException &&
            current.message?.contains("Unbalanced enter/exit") == true
        ) { return true }
        current = current.cause
    }
    return false
}
