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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ViewModule
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
import com.suanran.dreambox.presentation.component.sortable.SortableSectionCard
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import com.suanran.dreambox.presentation.component.sortable.hiddenSectionsRestore
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
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color

private data class LabEntry(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: Route,
)

/**
 * 实验室：实验性/小众功能集合（主题、探针、IP 测试）。
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
            title = "插件",
            summary = "重型功能按需下载，删掉腾空间",
            icon = Icons.Filled.Extension,
            route = Route.FeaturePacks,
        ),
        LabEntry(
            title = "模块",
            summary = "各页面功能模块开关，关掉即隐藏",
            icon = Icons.Filled.ViewModule,
            route = Route.Modules,
        ),
    )

    var ageKeyHybrid by remember { mutableStateOf(false) }
    var ageKeyDialogVisible by remember { mutableStateOf(false) }
    var ageChooserVisible by remember { mutableStateOf(false) }

    val defaultKeys = listOf("packs", "modules")
    var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "lab")) }
    val visibleOrder = defaultKeys.filter { it !in hiddenKeys }
    val listState = rememberLazyListState()
    val entryByKey = entries.associateBy {
        when (it.route) {
            Route.FeaturePacks -> "packs"
            Route.Modules -> "modules"
            else -> "packs"
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "实验室",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = { com.suanran.dreambox.presentation.component.help.ModuleHelpAction("lab") },
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
                SortableSectionCard(
                        isDragging = false,
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
            item {
                // 与上面插件/模块完全同款的入口行（原先标题在卡外、两行裸文字，和整页风格不一致，用户指为突兀）
                BocchiCard {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { ageChooserVisible = true }
                            .padding(horizontal = spacing.space16, vertical = spacing.space14),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabEntryIcon(icon = androidx.compose.material.icons.Icons.Filled.VpnKey, key = "agekey")
                        Spacer(modifier = Modifier.width(spacing.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = FlyTxt.MetaFeature.AgeKey.Section,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BocchiTextDark,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "生成 X25519 或混合后量子密钥",
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
        com.suanran.dreambox.presentation.component.dialog.AppDialog(
            show = ageChooserVisible,
            title = FlyTxt.MetaFeature.AgeKey.Section,
            onDismissRequest = { ageChooserVisible = false },
        ) {
            Column {
                ArrowPreference(
                    title = FlyTxt.MetaFeature.AgeKey.X25519Title,
                    onClick = {
                        ageChooserVisible = false
                        ageKeyHybrid = false
                        ageKeyDialogVisible = true
                    },
                )
                ArrowPreference(
                    title = FlyTxt.MetaFeature.AgeKey.HybridTitle,
                    onClick = {
                        ageChooserVisible = false
                        ageKeyHybrid = true
                        ageKeyDialogVisible = true
                    },
                )
            }
        }
        AgeKeyGeneratorDialog(
            show = ageKeyDialogVisible,
            hybrid = ageKeyHybrid,
            onDismiss = { ageKeyDialogVisible = false },
            onDismissFinished = {},
        )
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
