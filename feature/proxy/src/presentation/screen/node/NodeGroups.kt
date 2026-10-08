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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.core.model.proxy.ProxyDisplayMode
import com.suanran.dreambox.core.model.proxy.ProxyGroupInfo
import com.suanran.dreambox.feature.proxy.presentation.util.NodeFlagBadge
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

internal fun LazyListScope.nodeGroupItems(
    groups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String> = emptySet(),
    onGroupDelayTestClick: ((ProxyGroupInfo) -> Unit)? = null,
    onGroupBoundsChanged: ((String, Rect) -> Unit)? = null,
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
            modifier = Modifier.rowReveal(rememberRowShown(index, revealCount)).fillMaxWidth().padding(vertical = itemVerticalPadding),
        )
    }
}

@Composable
internal fun NodeGroupCard(
    group: ProxyGroupInfo,
    isDelayTesting: Boolean,
    onClick: (ProxyGroupInfo) -> Unit,
    onTestClick: ((ProxyGroupInfo) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(AppTheme.radii.radius12)
    val interactionSource = remember { MutableInteractionSource() }
    val testInteractionSource = remember { MutableInteractionSource() }
    val primary = MiuixTheme.colorScheme.primary

    val proxiesByName = remember(group.proxies) { group.proxies.associateBy(Proxy::name) }
    val currentProxy = remember(group.now, proxiesByName) { proxiesByName[group.now] }
    val currentNodeName = remember(currentProxy?.name, group.now) {
        (currentProxy?.name ?: group.now).trim().ifBlank { FlyTxt.Proxy.Mode.Direct }
    }

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = UiDp.dp16, vertical = UiDp.dp14),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp10),
    ) {
        // 第一行：组名 + 类型徽章 + 测速按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiAwareText(
                text = group.name,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = group.type,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                color = primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primary.copy(alpha = 0.1f))
                    .padding(horizontal = UiDp.dp8, vertical = UiDp.dp3),
            )
            if (onTestClick != null) {
                Box(
                    modifier = Modifier
                        .padding(start = UiDp.dp8)
                        .size(UiDp.dp28)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.1f))
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
                            color = primary,
                            modifier = Modifier.size(UiDp.dp14),
                        )
                    } else {
                        Icon(
                            FlyCat.Speed,
                            contentDescription = FlyTxt.Proxy.Action.Test,
                            modifier = Modifier.size(UiDp.dp14),
                            tint = primary,
                        )
                    }
                }
            }
        }
        // 第二行：当前节点条，点击进入节点列表
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UiDp.dp12))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .pressable(interactionSource = interactionSource, indication = SinkFeedback())
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onClick(group) },
                )
                .padding(horizontal = UiDp.dp12, vertical = UiDp.dp10),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp10),
        ) {
            NodeFlagBadge(nodeName = currentNodeName, size = UiDp.dp36, emojiSize = 20.sp)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                EmojiAwareText(
                    text = currentNodeName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
            Icon(
                FlyCat.chevron,
                contentDescription = null,
                modifier = Modifier.size(UiDp.dp18),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
