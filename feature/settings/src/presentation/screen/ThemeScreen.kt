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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.feature.settings.presentation.screen.component.ThemeColorPickerItem
import com.suanran.dreambox.feature.settings.presentation.screen.component.ThemeModeSelectorItem
import com.suanran.dreambox.feature.settings.presentation.theme.DreamTheme
import com.suanran.dreambox.feature.settings.presentation.theme.ThemeCatalogEntry
import com.suanran.dreambox.feature.settings.presentation.theme.rememberBuiltinDreamThemes
import com.suanran.dreambox.feature.settings.presentation.theme.toPreviewTheme
import com.suanran.dreambox.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.suanran.dreambox.feature.settings.presentation.viewmodel.MarketState
import com.suanran.dreambox.feature.settings.presentation.viewmodel.ThemeStoreEvent
import com.suanran.dreambox.feature.settings.presentation.viewmodel.ThemeStoreViewModel
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
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.theme.colorFromArgb
import com.suanran.dreambox.presentation.util.toast
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主题商店：内置主题 + 用户安装主题（URL/文件），对标 Komari 主题管理
 * （卡片 + 颜色预览 + 作者/版本/描述/来源，可启用/删除/更新）。
 */
@Composable
fun ThemeScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val viewModel = koinViewModel<ThemeStoreViewModel>()
    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val themeMode by viewModel.themeMode.state.collectAsStateWithLifecycle()
    val themeSeedColorArgb by viewModel.themeSeedColorArgb.state.collectAsStateWithLifecycle()
    val userThemes by viewModel.userThemes.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val marketThemes by viewModel.marketThemes.collectAsStateWithLifecycle()
    val marketState by viewModel.marketState.collectAsStateWithLifecycle()
    val builtinThemes = rememberBuiltinDreamThemes()
    val themeTxt = FlyTxt.AppSettings.Interface.Theme

    var showUrlDialog by remember { mutableStateOf(false) }
    var urlField by remember { mutableStateOf(TextFieldValue("")) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<DreamTheme?>(null) }
    var pendingEvent by remember { mutableStateOf<ThemeStoreEvent?>(null) }

    val zipLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) viewModel.installFromZip(uri)
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            pendingEvent = event
        }
    }

    // 事件转文案需在 @Composable 上下文中解析（FlyTxt 依赖 CompositionLocal）
    pendingEvent?.let { event ->
        val message =
            when (event) {
                is ThemeStoreEvent.Message -> event.text
                is ThemeStoreEvent.ErrorKey -> event.key.toThemeStoreMessage(event.arg)
            }
        LaunchedEffect(event) {
            context.toast(message)
            pendingEvent = null
        }
    }

    val allThemes = remember(builtinThemes, userThemes) { builtinThemes + userThemes }

    Scaffold(
        topBar = {
            TopBar(
                title = themeTxt.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.space12),
        ) {
            item {
                BocchiSectionTitle(themeTxt.StoreTitle)
                Card {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    urlField = TextFieldValue("")
                                    urlError = null
                                    showUrlDialog = true
                                }
                                .padding(
                                    horizontal = AppTheme.spacing.space16,
                                    vertical = AppTheme.spacing.space12,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = themeTxt.InstallFromUrl,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppTheme.spacing.space16))
                Card {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { zipLauncher.launch("application/zip") }
                                .padding(
                                    horizontal = AppTheme.spacing.space16,
                                    vertical = AppTheme.spacing.space12,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = themeTxt.InstallFromZip,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            item {
                BocchiSectionTitle(themeTxt.MarketTitle)
                ThemeMarketSection(
                    marketState = marketState,
                    marketThemes = marketThemes,
                    installedIds = remember(userThemes) { userThemes.map { it.id }.toSet() },
                    busy = busy,
                    onBrowse = { viewModel.refreshMarket() },
                    onInstall = { viewModel.installMarketTheme(it) },
                )
            }
            items(
                count = allThemes.size,
                key = { index -> allThemes[index].id },
            ) { index ->
                val theme = allThemes[index]
                val inUse =
                    themeMode == theme.themeMode && themeSeedColorArgb == theme.seedColorArgb
                ThemeStoreCard(
                    theme = theme,
                    inUse = inUse,
                    busy = busy,
                    onEnable = { viewModel.applyTheme(theme) },
                    onDelete = { deleteTarget = theme },
                    onUpdate = { viewModel.checkUpdate(theme) },
                )
            }
            item {
                BocchiSectionTitle(themeTxt.CustomTitle)
                Card {
                    ThemeModeSelectorItem(
                        themeMode = themeMode,
                        onThemeModeChange = appSettingsViewModel::onThemeModeChange,
                    )
                    ThemeColorPickerItem(
                        themeSeedColorArgb = themeSeedColorArgb,
                        onThemeSeedColorChange = appSettingsViewModel::onThemeSeedColorChange,
                    )
                }
                Spacer(modifier = Modifier.height(AppTheme.spacing.space16))
                Text(
                    text = themeTxt.StoreHint,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.space16),
                )
            }
        }

        AppFormDialog(
            show = showUrlDialog,
            title = themeTxt.UrlDialogTitle,
            summary = themeTxt.UrlDialogSummary,
            onDismissRequest = { showUrlDialog = false },
            onConfirm = {
                val url = urlField.text.trim()
                if (url.isEmpty()) {
                    urlError = themeTxt.ErrEmptyUrl
                } else {
                    urlError = null
                    showUrlDialog = false
                    viewModel.installFromUrl(url)
                }
            },
            error = urlError,
        ) {
            TextField(
                value = urlField,
                onValueChange = {
                    urlField = it
                    urlError = null
                },
                label = themeTxt.UrlLabel,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        AppConfirmDialog(
            show = deleteTarget != null,
            title = themeTxt.DeleteConfirmTitle,
            message = themeTxt.DeleteConfirmMessage.format(deleteTarget?.name ?: ""),
            onDismissRequest = { deleteTarget = null },
            onConfirm = {
                deleteTarget?.let { viewModel.deleteTheme(it) }
                deleteTarget = null
            },
        )

        AppDialog(
            show = busy,
            title = themeTxt.WorkingTitle,
            onDismissRequest = {},
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** 将事件 key 映射为本地化文案。 */
@Composable
private fun String.toThemeStoreMessage(arg: String?): String {
    val t = FlyTxt.AppSettings.Interface.Theme
    return when (this) {
        "applied" -> t.Applied.format(arg ?: "")
        "install_success" -> t.InstallSuccess.format(arg ?: "")
        "delete_success" -> t.DeleteSuccess.format(arg ?: "")
        "update_success" -> t.UpdateSuccess.format(arg ?: "")
        "no_update" -> t.NoUpdate
        "err_empty_url" -> t.ErrEmptyUrl
        "err_download" -> t.ErrDownload.format(arg ?: "")
        "err_read" -> t.ErrRead.format(arg ?: "")
        "err_save" -> t.ErrSave.format(arg ?: "")
        "err_exists" -> t.ErrExists.format(arg ?: "")
        "err_builtin" -> t.ErrBuiltin
        "err_not_found" -> t.ErrNotFound.format(arg ?: "")
        "err_no_source" -> t.ErrNoSource
        "err_not_json" -> t.ErrNotJson
        "err_missing_id" -> t.ErrMissingId
        "err_invalid_id" -> t.ErrInvalidId.format(arg ?: "")
        "err_missing_name" -> t.ErrMissingName
        "err_invalid_mode" -> t.ErrInvalidMode.format(arg ?: "")
        "err_invalid_color" -> t.ErrInvalidColor.format(arg ?: "")
        "err_builtin_conflict" -> t.ErrBuiltinConflict.format(arg ?: "")
        else -> t.ErrUnknown.format(arg ?: "")
    }
}

@Composable
private fun ThemeStoreCard(
    theme: DreamTheme,
    inUse: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
) {
    val themeTxt = FlyTxt.AppSettings.Interface.Theme
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity

    Card {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.space16, vertical = spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeColorPreview(seedColorArgb = theme.seedColorArgb)
                Spacer(modifier = Modifier.width(spacing.space12))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = theme.name,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(spacing.space8))
                        Surface(
                            color = MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = theme.versionBadge(themeTxt.BuiltIn),
                                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = spacing.space8, vertical = 2.dp),
                            )
                        }
                    }
                    if (theme.author.isNotBlank()) {
                        Text(
                            text = themeTxt.AuthorFormat.format(theme.author),
                            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            if (theme.description.isNotBlank()) {
                Text(
                    text = theme.description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (!theme.sourceUrl.isNullOrBlank()) {
                Text(
                    text = theme.sourceUrl,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!theme.builtin) {
                    TextButton(
                        text = themeTxt.Delete,
                        onClick = onDelete,
                        enabled = !busy,
                    )
                }
                if (!theme.sourceUrl.isNullOrBlank()) {
                    TextButton(
                        text = themeTxt.Update,
                        onClick = onUpdate,
                        enabled = !busy,
                    )
                }
                if (inUse) {
                    Surface(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = spacing.space12),
                        ) {
                            Text(
                                text = themeTxt.InUse,
                                style =
                                    MiuixTheme.textStyles.footnote1.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onEnable,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.height(32.dp),
                        enabled = !busy,
                    ) {
                        Text(
                            text = themeTxt.Enable,
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** 主题颜色预览：一排由 seedColor 派生的色块。 */
@Composable
private fun ThemeColorPreview(seedColorArgb: Long) {
    val seed = colorFromArgb(seedColorArgb)
    val swatches =
        remember(seed) {
            listOf(
                lerp(seed, Color.Black, 0.45f),
                lerp(seed, Color.Black, 0.22f),
                seed,
                lerp(seed, Color.White, 0.35f),
                lerp(seed, Color.White, 0.65f),
            )
        }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        swatches.forEach { color ->
            Box(
                modifier =
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}

/** 主题市场区：浏览在线主题目录，一键安装（对标 Komari 主题市场）。 */
@Composable
private fun ThemeMarketSection(
    marketState: MarketState,
    marketThemes: List<ThemeCatalogEntry>,
    installedIds: Set<String>,
    busy: Boolean,
    onBrowse: () -> Unit,
    onInstall: (ThemeCatalogEntry) -> Unit,
) {
    val themeTxt = FlyTxt.AppSettings.Interface.Theme
    val spacing = AppTheme.spacing
    when (marketState) {
        MarketState.Idle -> {
            Card {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onBrowse)
                            .padding(horizontal = spacing.space16, vertical = spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = themeTxt.MarketBrowse,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(spacing.space4))
                        Text(
                            text = themeTxt.MarketHint,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        MarketState.Loading -> {
            Card {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16, vertical = spacing.space16),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(spacing.space8))
                    Text(
                        text = themeTxt.MarketLoading,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        MarketState.Failed -> {
            Card {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16, vertical = spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = themeTxt.MarketLoadFailed,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(text = themeTxt.MarketRetry, onClick = onBrowse)
                }
            }
        }
        MarketState.Loaded -> {
            if (marketThemes.isEmpty()) {
                Card {
                    Text(
                        text = themeTxt.MarketEmpty,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.space16, vertical = spacing.space16),
                    )
                }
            } else {
                marketThemes.forEach { entry ->
                    val preview = remember(entry) { entry.toPreviewTheme() }
                    if (preview != null) {
                        MarketThemeCard(
                            entry = entry,
                            preview = preview,
                            installed = entry.id in installedIds,
                            busy = busy,
                            onInstall = { onInstall(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** 市场主题卡片：颜色预览 + 名称/版本/作者/描述 + 安装按钮。 */
@Composable
private fun MarketThemeCard(
    entry: ThemeCatalogEntry,
    preview: DreamTheme,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    val themeTxt = FlyTxt.AppSettings.Interface.Theme
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity
    Card {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.space16, vertical = spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeColorPreview(seedColorArgb = preview.seedColorArgb)
                Spacer(modifier = Modifier.width(spacing.space12))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = preview.name,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(spacing.space8))
                        Surface(
                            color = MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "v${preview.version}",
                                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = spacing.space8, vertical = 2.dp),
                            )
                        }
                    }
                    if (preview.author.isNotBlank()) {
                        Text(
                            text = themeTxt.AuthorFormat.format(preview.author),
                            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            if (preview.description.isNotBlank()) {
                Text(
                    text = preview.description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installed) {
                    Text(
                        text = themeTxt.MarketInstalled,
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.height(32.dp),
                        enabled = !busy,
                    ) {
                        Text(
                            text = themeTxt.MarketInstall,
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
