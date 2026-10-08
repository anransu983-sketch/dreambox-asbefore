/*
 * This file is part of DreamBox.
 *
 * DreamBox is free software: you can redistribute it and/or modify
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
 */

package com.suanran.dreambox.feature.home.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.Activity
import com.suanran.dreambox.presentation.icon.flycat.ChartColumn
import com.suanran.dreambox.presentation.icon.flycat.LayoutPanelLeft
import com.suanran.dreambox.presentation.icon.flycat.ListChevronsUpDown
import com.suanran.dreambox.presentation.icon.flycat.Play
import com.suanran.dreambox.presentation.icon.flycat.RedoDot
import com.suanran.dreambox.presentation.icon.flycat.Rocket
import com.suanran.dreambox.presentation.icon.flycat.ScrollText
import com.suanran.dreambox.presentation.icon.flycat.WifiCog
import com.suanran.dreambox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 首页快捷入口项。 */
data class HomeShortcut(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/**
 * Loon 风格的首页快捷宫格（2 列卡片）。
 */
@Composable
fun HomeShortcutGrid(
    shortcuts: List<HomeShortcut>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp12),
    ) {
        Text(
            text = FlyTxt.Home.Shortcut.Title,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        shortcuts.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
            ) {
                row.forEach { shortcut ->
                    Card(
                        modifier = Modifier.weight(1f).clickable(onClick = shortcut.onClick),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = UiDp.dp14, vertical = UiDp.dp12),
                            horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = shortcut.icon,
                                contentDescription = null,
                                modifier = Modifier.size(UiDp.dp28),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shortcut.title,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = shortcut.subtitle,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) {
                    // 补齐最后一行空位，保持两列对齐
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 构建默认的 9 个快捷入口。调用方传入导航动作。 */
@Composable
fun rememberHomeShortcuts(
    nodeCountText: String,
    onOpenNodes: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenPanel: () -> Unit,
    onOpenTraffic: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onOpenStreamUnlock: () -> Unit,
): List<HomeShortcut> = listOf(
    HomeShortcut(FlyCat.Rocket, FlyTxt.Home.Shortcut.Nodes, nodeCountText, onOpenNodes),
    HomeShortcut(FlyCat.Activity, FlyTxt.Home.Shortcut.Connection, FlyTxt.Home.Shortcut.ConnectionSub, onOpenConnection),
    HomeShortcut(FlyCat.WifiCog, FlyTxt.Home.Shortcut.Dns, FlyTxt.Home.Shortcut.DnsSub, onOpenDns),
    HomeShortcut(FlyCat.ListChevronsUpDown, FlyTxt.Home.Shortcut.Rules, FlyTxt.Home.Shortcut.RulesSub, onOpenRules),
    HomeShortcut(FlyCat.ScrollText, FlyTxt.Home.Shortcut.Log, FlyTxt.Home.Shortcut.LogSub, onOpenLog),
    HomeShortcut(FlyCat.LayoutPanelLeft, FlyTxt.Home.Shortcut.Panel, FlyTxt.Home.Shortcut.PanelSub, onOpenPanel),
    HomeShortcut(FlyCat.ChartColumn, FlyTxt.Home.Shortcut.Traffic, FlyTxt.Home.Shortcut.TrafficSub, onOpenTraffic),
    HomeShortcut(FlyCat.RedoDot, FlyTxt.Home.Shortcut.RefreshSub, FlyTxt.Home.Shortcut.RefreshSubSub, onRefreshSubscription),
    HomeShortcut(FlyCat.Play, FlyTxt.Home.Shortcut.StreamUnlock, FlyTxt.Home.Shortcut.StreamUnlockSub, onOpenStreamUnlock),
)
