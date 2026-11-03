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

package com.suanran.dreambox.feature.proxy.presentation.screen.node

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.suanran.dreambox.presentation.component.dialog.AppDialog
import com.suanran.dreambox.presentation.component.misc.CustomIcons
import com.suanran.dreambox.presentation.component.misc.rememberCustomIconBitmap
import com.suanran.dreambox.feature.proxy.presentation.util.nodeFlagEmoji
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.core.model.proxy.ProxyDisplayMode
import com.suanran.dreambox.core.model.proxy.ProxyGroupInfo
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.misc.EmojiAwareText
import com.suanran.dreambox.presentation.component.state.LoadingDotsWave
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.Speed
import com.suanran.dreambox.presentation.icon.flycat.chevron
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.theme.RowRevealAll
import com.suanran.dreambox.presentation.theme.UiDp
import com.suanran.dreambox.presentation.theme.rememberRowShown
import com.suanran.dreambox.presentation.theme.rowReveal
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

// 波奇酱风配色
private val BocchiPink = Color(0xFFFFB7C5)
private val BocchiBlue = Color(0xFFA8D8F0)
private val BocchiPinkBg = Color(0xFFFFE9EE)
private val BocchiBlueBg = Color(0xFFE4F3FE)
private val BocchiStar = Color(0xFFFF8FAB)

internal fun LazyListScope.nodeGroupItems(
    groups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String> = emptySet(),
    onGroupDelayTestClick: ((ProxyGroupInfo) -> Unit)? = null,
    onGroupBoundsChanged: ((String, Rect) -> Unit)? = null,
    onGroupIconPick: ((ProxyGroupInfo) -> Unit)? = null,
    onGroupIconReset: ((ProxyGroupInfo) -> Unit)? = null,
    itemVerticalPadding: Dp = UiDp.dp6,
    revealCount: State<Int> = RowRevealAll,
) {
    // 策略组统一纵向列表样式，不再分单/双栏。
    itemsIndexed(
        items = groups,
        key = { _, group -> "${group.type}:${group.name}" },
        contentType = { _, _ -> "NodeGroupCard" },
    ) { index, group ->
        NodeGroupCard(
            group = group,
            isDelayTesting = testingGroupNames.contains(group.name),
            onClick = { onGroupClick(group) },
            onTestClick = onGroupDelayTestClick,
            onIconPick = onGroupIconPick?.let { cb -> { cb(group) } },
            onIconReset = onGroupIconReset?.let { cb -> { cb(group) } },
            modifier = Modifier.rowReveal(rememberRowShown(index, revealCount)).fillMaxWidth().padding(vertical = itemVerticalPadding),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun NodeGroupCard(
    group: ProxyGroupInfo,
    isDelayTesting: Boolean,
    onClick: (ProxyGroupInfo) -> Unit,
    onTestClick: ((ProxyGroupInfo) -> Unit)? = null,
    onIconPick: (() -> Unit)? = null,
    onIconReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val groupIconKey = remember(group.name) { CustomIcons.keyFor("group", group.name) }
    val customGroupIcon = rememberCustomIconBitmap(groupIconKey)
    var iconMenuOpen by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(AppTheme.radii.radius12)
    val interactionSource = remember { MutableInteractionSource() }
    val testInteractionSource = remember { MutableInteractionSource() }

    val proxiesByName = remember(group.proxies) { group.proxies.associateBy(Proxy::name) }
    val currentProxy = remember(group.now, proxiesByName) { proxiesByName[group.now] }
    val currentNodeName = remember(currentProxy?.name, group.now) {
        (currentProxy?.name ?: group.now).trim().ifBlank { FlyTxt.Proxy.Mode.Direct }
    }
    // 贴纸徽章配色：Selector 粉 / URLTest·Fallback 蓝
    val isSelector = group.type == Proxy.Type.Selector
    val badgeBg = if (isSelector) BocchiPinkBg else BocchiBlueBg
    val badgeFg = if (isSelector) BocchiStar else Color(0xFF4A90D9)

    Row(
        modifier = modifier
            .clip(cardShape)
            .background(Color.White)
            .border(
                width = 1.dp,
                color = if (isSelector) BocchiPink.copy(alpha = 0.5f) else BocchiBlue.copy(alpha = 0.5f),
                shape = cardShape,
            )
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(group) },
                onLongClick = if (onIconPick != null) {
                    { iconMenuOpen = true }
                } else {
                    null
                },
            )
            .padding(horizontal = UiDp.dp12, vertical = UiDp.dp8)
            .heightIn(min = UiDp.dp48),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (customGroupIcon != null) {
            Image(
                bitmap = customGroupIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = UiDp.dp10)
                    .size(UiDp.dp36)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiAwareText(
                    text = group.name,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "✦",
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                    color = badgeFg,
                    modifier = Modifier.padding(start = UiDp.dp2),
                )
                Text(
                    text = group.type,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                    color = badgeFg,
                    modifier = Modifier
                        .padding(start = UiDp.dp6)
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .padding(horizontal = UiDp.dp8, vertical = UiDp.dp2),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = UiDp.dp2),
            ) {
                val flagEmoji = remember(currentNodeName) { nodeFlagEmoji(currentNodeName) }
                if (flagEmoji != null) {
                    Text(
                        text = flagEmoji,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(end = UiDp.dp4),
                    )
                }
                EmojiAwareText(
                    text = currentNodeName,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 组内最快：已测速节点里最小的有效延迟，一眼看出这个组还有没有救。
                // 65535 是内核测速失败的哨兵值（uint16 最大），>5000 一律不算有效延迟，
                // 否则节点明明能用却显示「最快 65535ms」。口径与 nodeLatencyLabel 一致。
                val fastestDelay = remember(group.proxies) {
                    group.proxies.mapNotNull { it.delay }.filter { it in 1..5000 }.minOrNull()
                }
                if (fastestDelay != null && group.proxies.size > 1) {
                    Text(
                        text = "最快 ${fastestDelay}ms",
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                        color = badgeFg,
                        modifier = Modifier.padding(start = UiDp.dp6),
                    )
                }
            }
        }
        if (onTestClick != null) {
            Box(
                modifier = Modifier
                    .padding(start = UiDp.dp8)
                    .size(UiDp.dp28)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .clickable(
                        interactionSource = testInteractionSource,
                        indication = null,
                        enabled = !isDelayTesting,
                        onClick = { onTestClick(group) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isDelayTesting) {
                    LoadingDotsWave(
                        color = badgeFg,
                        modifier = Modifier.size(UiDp.dp14),
                    )
                } else {
                    Icon(
                        FlyCat.Speed,
                        contentDescription = FlyTxt.Proxy.Action.Test,
                        modifier = Modifier.size(UiDp.dp14),
                        tint = badgeFg,
                    )
                }
            }
        }
        Icon(
            FlyCat.chevron,
            contentDescription = null,
            modifier = Modifier.size(UiDp.dp18).padding(start = UiDp.dp4),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
    if (onIconPick != null) {
        AppDialog(
            show = iconMenuOpen,
            title = group.name,
            onDismissRequest = { iconMenuOpen = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "换一张图标",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            iconMenuOpen = false
                            onIconPick()
                        }
                        .padding(vertical = UiDp.dp10),
                )
                if (customGroupIcon != null && onIconReset != null) {
                    Text(
                        text = "恢复默认（去掉自定义图）",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                iconMenuOpen = false
                                onIconReset()
                            }
                            .padding(vertical = UiDp.dp10),
                    )
                }
                Text(
                    text = "自定义图会显示在组名左侧；所有配置里的同名组共用这张图。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = UiDp.dp6),
                )
            }
        }
    }
}
