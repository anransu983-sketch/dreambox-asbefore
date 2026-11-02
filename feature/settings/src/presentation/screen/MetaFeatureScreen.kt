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
import com.suanran.dreambox.feature.settings.presentation.viewmodel.MetaFeatureViewModel
import com.suanran.dreambox.presentation.component.help.ModuleHelpAction
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.dialog.AppConfirmDialog
import com.suanran.dreambox.presentation.component.dialog.AppDialog
import com.suanran.dreambox.presentation.component.dialog.AppFormDialog
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import com.suanran.dreambox.presentation.component.sortable.SortableSectionCard
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import com.suanran.dreambox.presentation.component.sortable.hiddenSectionsRestore
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

    val ageKeyHybrid = remember { mutableStateOf(false) }
    val ageKeyDialogVisible = remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBar(title = FlyTxt.MetaFeature.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }, actions = { ModuleHelpAction("meta") }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        val mmkv: com.tencent.mmkv.MMKV = koinInject(named("settings"))
        val defaultKeys = listOf("routing")
        var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "meta_feature")) }
        val visibleOrder = defaultKeys.filter { it !in hiddenKeys }
        val sectionTitles = mapOf("routing" to "分流相关")
        val listState = rememberLazyListState()
    val sectionContent: Map<String, @Composable () -> Unit> = mapOf(
        "routing" to {
                BocchiSectionTitle(FlyTxt.MetaFeature.Section.Routing)
                BocchiCard {
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
        }
    )

        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            itemsIndexed(visibleOrder, key = { _, k -> k }) { index, key ->
                SortableSectionCard(
                        isDragging = false,
                        onHide = {
                            hiddenKeys = hiddenKeys + key
                            saveHiddenSections(mmkv, "meta_feature", hiddenKeys + key)
                        },
                    ) {
                        sectionContent[key]?.invoke()
                    }
            }
        }

        GeoXDownloadDialog(
            show = showGeoXDownloadSheet,
            context = context,
            viewModel = viewModel,
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
