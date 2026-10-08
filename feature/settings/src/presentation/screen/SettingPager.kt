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

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.presentation.theme.horizontalPadding
import com.suanran.dreambox.presentation.component.sortable.DraggableItem
import com.suanran.dreambox.presentation.component.sortable.ModuleMenuButton
import com.suanran.dreambox.presentation.component.sortable.rememberDragDropState
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.navigation.LocalDetailNavigator
import com.suanran.dreambox.presentation.component.navigation.LocalNavigator
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.FlaskConical
import com.suanran.dreambox.presentation.icon.flycat.Github
import com.suanran.dreambox.presentation.icon.flycat.GitMerge
import com.suanran.dreambox.presentation.icon.flycat.Meta
import com.suanran.dreambox.presentation.icon.flycat.ScrollText
import com.suanran.dreambox.presentation.icon.flycat.Search
import com.suanran.dreambox.presentation.icon.flycat.Settings2
import com.suanran.dreambox.presentation.icon.flycat.WifiCog
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置磁吸模块描述。 */
private data class SettingsModule(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: Route?,
    val badge: String? = null,
)

private const val MMKV_KEY_MODULE_ORDER = "settings_module_order"

private fun defaultModules(): List<SettingsModule> = listOf(
    SettingsModule(
        key = "app",
        title = FlyTxt.Settings.UiSettings.App,
        summary = FlyTxt.Settings.UiSettings.AppSummary,
        icon = FlyCat.Settings2,
        route = Route.AppSettings,
    ),
    SettingsModule(
        key = "network",
        title = FlyTxt.Settings.UiSettings.Network,
        summary = FlyTxt.Settings.UiSettings.NetworkSummary,
        icon = FlyCat.WifiCog,
        route = Route.NetworkSettings,
    ),
    SettingsModule(
        key = "override",
        title = FlyTxt.Settings.UiSettings.Override,
        summary = FlyTxt.Settings.UiSettings.OverrideSummary,
        icon = FlyCat.GitMerge,
        route = Route.Override,
    ),
    SettingsModule(
        key = "meta",
        title = FlyTxt.Settings.UiSettings.MetaFeatures,
        summary = FlyTxt.Settings.UiSettings.MetaFeaturesSummary,
        icon = FlyCat.Meta,
        route = Route.MetaFeature,
    ),
    SettingsModule(
        key = "lab",
        title = "实验室",
        summary = "主题 · IP 测试 · 音乐库",
        icon = Icons.Filled.Science,
        route = Route.Lab,
    ),
    SettingsModule(
        key = "log",
        title = FlyTxt.Settings.More.Logs,
        summary = FlyTxt.Settings.More.LogsSummary,
        icon = FlyCat.ScrollText,
        route = Route.Log,
    ),
    SettingsModule(
        key = "about",
        title = FlyTxt.Settings.More.About,
        summary = FlyTxt.Settings.More.AboutSummary,
        icon = FlyCat.Github,
        route = Route.About,
        badge = com.suanran.dreambox.core.BuildConfigHolder.versionName,
    ),
)

@SuppressLint("LocalContextResourcesRead")
@Composable
fun SettingPager(mainInnerPadding: PaddingValues) {
    val scrollBehavior = MiuixScrollBehavior()
    val rootNavigator = LocalNavigator.current
    val detailNavigator = LocalDetailNavigator.current
    val openSecondary: (Route) -> Unit = { route ->
        if (detailNavigator != null) {
            detailNavigator.replaceAll(listOf(route))
        } else {
            rootNavigator.push(route)
        }
    }

    val mmkv: MMKV = koinInject(named("settings"))
    val settingsReader: AppSettingsReader = koinInject()
    val isAnime = true
    var modules by remember {
        mutableStateOf(loadModuleOrder(mmkv))
    }
    var hiddenKeys by remember { mutableStateOf(loadHiddenModules(mmkv)) }

    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(listState) { from, to ->
        modules = modules.toMutableList().apply { add(to, removeAt(from)) }
        saveModuleOrder(mmkv, modules)
    }
    val visibleModules = modules.filter { it.key !in hiddenKeys }
    val hiddenModules = modules.filter { it.key in hiddenKeys }

    Scaffold(topBar = { TopBar(title = FlyTxt.Settings.Title, scrollBehavior = scrollBehavior) }) { innerPadding ->
        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
            modifier = if (isAnime) Modifier.background(animeBackgroundBrush) else Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(visibleModules, key = { _, m -> m.key }) { index, module ->
                DraggableItem(state = dragState, index = index) { isDragging ->
                    if (isAnime) {
                        AnimeModuleCard(
                            module = module,
                            isDragging = isDragging,
                            onHide = {
                                hiddenKeys = hiddenKeys + module.key
                                saveHiddenModules(mmkv, hiddenKeys + module.key)
                            },
                            onClick = { module.route?.let { openSecondary(it) } },
                        )
                    } else {
                        MaterialModuleCard(
                            module = module,
                            isDragging = isDragging,
                            onHide = {
                                hiddenKeys = hiddenKeys + module.key
                                saveHiddenModules(mmkv, hiddenKeys + module.key)
                            },
                            onClick = { module.route?.let { openSecondary(it) } },
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "长按拖动可排序",
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (isAnime) animeOnGlass.copy(alpha = 0.6f) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** 动漫风背景：波奇酱粉纵向渐变。 */
private val animeBackgroundBrush = Brush.verticalGradient(
    listOf(Color(0xFFFF7BAC), Color(0xFFFFB9D4), Color(0xFFFFE7F0)),
)

/** 动漫风玻璃卡片上的深色文字。 */
private val animeOnGlass = Color(0xFF4A1E2C)

/** 动漫风磁吸模块卡片：粉色玻璃拟态 + 大圆角，右侧只留菜单和箭头。 */
@Composable
private fun AnimeModuleCard(
    module: SettingsModule,
    isDragging: Boolean,
    onHide: () -> Unit,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .horizontalPadding()
            .then(if (isDragging) Modifier.shadow(12.dp, cardShape) else Modifier)
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.65f), cardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleIcon(icon = module.icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                    color = animeOnGlass,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (module.badge != null) "${module.summary} · ${module.badge}" else module.summary,
                    style = MiuixTheme.textStyles.body2,
                    color = animeOnGlass.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ModuleMenuButton(onHide = onHide)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = animeOnGlass.copy(alpha = 0.45f),
            )
        }
    }
}

/** 简约风磁吸模块卡片：Material tonal 卡片 + 小单色图标，行高收紧。 */
@Composable
private fun MaterialModuleCard(
    module: SettingsModule,
    isDragging: Boolean,
    onHide: () -> Unit,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .horizontalPadding()
            .then(if (isDragging) Modifier.shadow(8.dp, cardShape) else Modifier)
            .clip(cardShape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = module.icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Medium),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (module.badge != null) "${module.summary} · ${module.badge}" else module.summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ModuleMenuButton(onHide = onHide)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ModuleIcon(icon: ImageVector) {
    val componentSizes = AppTheme.sizes
    val radii = AppTheme.radii
    Box(
        modifier = Modifier
            .size(componentSizes.settingsIconContainerSize)
            .clip(RoundedCornerShape(radii.radius16))
            .background(MiuixTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onPrimary,
            modifier = Modifier.size(componentSizes.settingsIconGlyphSize),
        )
    }
}

private fun loadModuleOrder(mmkv: MMKV): List<SettingsModule> {
    val defaults = defaultModules()
    val saved = mmkv.decodeString(MMKV_KEY_MODULE_ORDER, "").orEmpty()
    if (saved.isBlank()) return defaults
    val order = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val byKey = defaults.associateBy { it.key }
    val ordered = order.mapNotNull { byKey[it] }
    val missing = defaults.filter { d -> d.key !in order }
    return (ordered + missing).distinctBy { it.key }
}

private fun saveModuleOrder(mmkv: MMKV, modules: List<SettingsModule>) {
    mmkv.encode(MMKV_KEY_MODULE_ORDER, modules.joinToString(",") { it.key })
}

private const val MMKV_KEY_MODULE_HIDDEN = "settings_module_hidden"

/** 加载被隐藏的模块 key 集合。 */
private fun loadHiddenModules(mmkv: MMKV): Set<String> {
    return mmkv.decodeString(MMKV_KEY_MODULE_HIDDEN, "").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** 保存被隐藏的模块 key 集合。 */
private fun saveHiddenModules(mmkv: MMKV, hidden: Set<String>) {
    mmkv.encode(MMKV_KEY_MODULE_HIDDEN, hidden.joinToString(","))
}
