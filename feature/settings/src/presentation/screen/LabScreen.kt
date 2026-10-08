package com.suanran.dreambox.feature.settings.presentation.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.misc.BocchiBlueBg
import com.suanran.dreambox.presentation.component.misc.BocchiBlueFg
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiPinkBg
import com.suanran.dreambox.presentation.component.misc.BocchiStar
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
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
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.Search
import com.suanran.dreambox.presentation.icon.flycat.Settings2
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class LabEntry(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: Route,
)

/**
 * 实验室：实验性/小众功能集合（主题、探针、IP 测试、音乐库、游戏）。
 */
@Composable
fun LabScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing
    val context = LocalContext.current

    // 实验室入口
    val mmkv: com.tencent.mmkv.MMKV = org.koin.compose.koinInject(org.koin.core.qualifier.named("settings"))

    val entries = listOf(
        LabEntry(
            title = FlyTxt.AppSettings.Interface.Theme.Title,
            summary = FlyTxt.Settings.Experimental.ThemeStoreSummary,
            icon = FlyCat.Settings2,
            route = Route.Theme,
        ),
        LabEntry(
            title = FlyTxt.Settings.Experimental.IpTestModule,
            summary = FlyTxt.Settings.Experimental.IpTestModuleSummary,
            icon = FlyCat.Search,
            route = Route.IpTest,
        ),
        LabEntry(
            title = FlyTxt.Settings.Experimental.MusicModule,
            summary = FlyTxt.Settings.Experimental.MusicModuleSummary,
            icon = Icons.Filled.LibraryMusic,
            route = Route.MusicLibrary,
        ),
        LabEntry(
            title = "已隐藏的模块",
            summary = "管理所有隐藏的功能模块，点击恢复",
            icon = Icons.Filled.VisibilityOff,
            route = Route.HiddenModules,
        ),
    )

    val defaultKeys = listOf("theme", "iptest", "music", "hidden")
    var order by remember { mutableStateOf(loadSectionOrder(mmkv, "lab", defaultKeys)) }
    var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "lab")) }
    val visibleOrder = order.filter { it !in hiddenKeys }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(listState) { from, to ->
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        saveSectionOrder(mmkv, "lab", order)
    }
    val entryByKey = entries.associateBy {
        when (it.route) {
            Route.Theme -> "theme"
            Route.IpTest -> "iptest"
            Route.MusicLibrary -> "music"
            Route.HiddenModules -> "hidden"
            else -> "theme"
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "实验室",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "实验性功能，稳定后会移到外面",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            itemsIndexed(visibleOrder, key = { _, k -> k }) { index, key ->
                val e = entryByKey[key] ?: return@itemsIndexed
                DraggableItem(state = dragState, index = index) { isDragging ->
                    SortableSectionCard(
                        isDragging = isDragging,
                        onHide = {
                            hiddenKeys = hiddenKeys + key
                            saveHiddenSections(mmkv, "lab", hiddenKeys + key)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { navigator.push(e.route) }
                                .padding(horizontal = spacing.space16, vertical = spacing.space14),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LabEntryIcon(icon = e.icon, key = key)
                            Spacer(modifier = Modifier.width(spacing.space12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = e.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BocchiTextDark,
                                    maxLines = 1,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = e.summary,
                                    fontSize = 11.sp,
                                    color = BocchiTextGray,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = "✦",
                                fontSize = 12.sp,
                                color = BocchiPink.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = BocchiTextGray,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            item {
                ResetOrderButton(onClick = {
                    clearSectionOrder(mmkv, "lab")
                    order = defaultKeys
                })
            }
        }
    }
}

@Composable
private fun LabEntryIcon(icon: ImageVector, key: String) {
    val bg = when (kotlin.math.abs(key.hashCode()) % 3) {
        0 -> BocchiPinkBg
        1 -> BocchiBlueBg
        else -> Color(0xFFE3F5E9)
    }
    val fg = when (kotlin.math.abs(key.hashCode()) % 3) {
        0 -> BocchiStar
        1 -> BocchiBlueFg
        else -> Color(0xFF43A047)
    }
    Box(
        modifier = Modifier.size(AppTheme.sizes.settingsIconContainerSize)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(24.dp),
        )
    }
}
