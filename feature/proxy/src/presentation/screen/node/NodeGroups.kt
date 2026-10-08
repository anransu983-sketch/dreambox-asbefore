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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(group) },
            )
            .padding(horizontal = UiDp.dp12, vertical = UiDp.dp8)
            .heightIn(min = UiDp.dp48),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            EmojiAwareText(
                text = currentNodeName,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = UiDp.dp2),
            )
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
}
