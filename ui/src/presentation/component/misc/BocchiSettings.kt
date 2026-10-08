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

package com.suanran.dreambox.presentation.component.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.theme.horizontalPadding
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import kotlin.math.roundToInt

/** 波奇酱配色（与代理页/配置页保持一致）。 */
val BocchiPink = Color(0xFFFFB7C5)
val BocchiBlue = Color(0xFFA8D8F0)
val BocchiPinkBg = Color(0xFFFFE9EE)
val BocchiBlueBg = Color(0xFFE4F3FE)
val BocchiStar = Color(0xFFFF8FAB)
val BocchiBlueFg = Color(0xFF4A90D9)
val BocchiTextDark = Color(0xFF4A2E35)
val BocchiTextGray = Color(0xFF9A7B83)

/** 波奇酱分组小标题：12sp 粉字。 */
@Composable
fun BocchiSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = BocchiStar,
        modifier = Modifier.horizontalPadding().padding(top = 12.dp, bottom = 6.dp),
    )
}

/** 波奇酱卡片：白色圆角 + 1dp 粉/蓝细描边。签名与 Card 保持一致方便替换。 */
@Composable
fun BocchiCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    applyHorizontalPadding: Boolean = true,
    colors: CardColors = CardDefaults.defaultColors(),
    borderColor: Color = BocchiPink.copy(alpha = 0.45f),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier =
            (if (applyHorizontalPadding) modifier.horizontalPadding() else modifier)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, borderColor, shape)
                .padding(insideMargin),
    ) {
        content()
    }
}

/** 波奇酱粉色小开关（纯 Compose 实现，不依赖主题色）。 */
@Composable
fun BocchiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (checked) BocchiStar else Color(0xFFE0D5D8)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .background(trackColor.copy(alpha = if (enabled) 1f else 0.5f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onClick = { onCheckedChange(!checked) },
                )
                .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(if (checked) (18.dp.toPx()).roundToInt() else 0, 0) }
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
        )
    }
}

@Composable
private fun BocchiRowTexts(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = BocchiTextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!summary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                fontSize = 11.sp,
                color = BocchiTextGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 波奇酱开关行：13sp 标题 + 11sp 灰字 + 粉色小开关。 */
@Composable
fun BocchiSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BocchiRowTexts(title = title, summary = summary, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        BocchiSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 波奇酱箭头行：13sp 标题 + 11sp 灰字 + 右箭头。 */
@Composable
fun BocchiArrowItem(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    holdDownState: Boolean = false,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BocchiRowTexts(title = title, summary = summary, modifier = Modifier.weight(1f))
            if (endActions != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { endActions() }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = BocchiTextGray,
                modifier = Modifier.size(18.dp),
            )
        }
        bottomAction?.invoke()
    }
}

/** 波奇酱数值行：13sp 标题 + 右侧自定义内容。 */
@Composable
fun BocchiValueItem(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
) {
    BocchiArrowItem(title = title, onClick = onClick, summary = summary, endActions = endActions)
}

/** 波奇酱枚举选择行：13sp 标题 + 11sp 灰字，点开选值。 */
@Composable
fun <T> BocchiEnumItem(
    title: String,
    currentValue: T,
    items: List<String>,
    values: List<T>,
    onValueChange: (T) -> Unit,
    summary: String? = null,
) {
    val selectedIndex = values.indexOf(currentValue).coerceAtLeast(0)
    val dropdownItems = remember(items) { items.map { DropdownItem(title = it) } }
    WindowSpinnerPreference(
        title = title,
        summary = summary,
        items = dropdownItems,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            values.getOrNull(index)?.let(onValueChange)
        },
    )
}

/** 波奇酱小圆形按钮：48dp，粉/蓝。 */
@Composable
fun BocchiCircleButton(
    pink: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = if (pink) BocchiStar else BocchiBlueFg
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 波奇酱小贴纸按钮：粉/蓝底 + 描边，13sp。 */
@Composable
fun BocchiStickerButton(
    text: String,
    onClick: () -> Unit,
    pink: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bg = if (pink) BocchiPinkBg else BocchiBlueBg
    val fg = if (pink) BocchiStar else BocchiBlueFg
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(bg)
                .border(1.dp, fg.copy(alpha = 0.4f), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/** 波奇酱小贴纸徽章。 */
@Composable
fun BocchiStickerBadge(
    text: String,
    pink: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bg = if (pink) BocchiPinkBg else BocchiBlueBg
    val fg = if (pink) BocchiStar else BocchiBlueFg
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(bg)
                .border(1.dp, fg.copy(alpha = 0.35f), shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/**
 * 波奇酱枚举行：沿用 Miuix 的 WindowDropdownPreference（功能不变），
 * 外层包一层小字号标题行，点击展开系统下拉。
 */
@Composable
fun <T> BocchiEnumItem(
    title: String,
    currentValue: T,
    items: List<String>,
    values: List<T>,
    onValueChange: (T) -> Unit,
    summary: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedIndex = values.indexOf(currentValue).coerceAtLeast(0)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BocchiRowTexts(title = title, summary = summary, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = items.getOrElse(selectedIndex) { "" },
            fontSize = 12.sp,
            color = BocchiBlueFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = BocchiTextGray,
            modifier = Modifier.size(18.dp),
        )
    }
    if (showDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDialog = false }) {
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier =
                    Modifier
                        .clip(shape)
                        .background(Color.White)
                        .border(1.dp, BocchiPink.copy(alpha = 0.45f), shape)
                        .padding(16.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BocchiTextDark,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { index, label ->
                        val selected = index == selectedIndex
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) BocchiPinkBg else Color.Transparent)
                                    .clickable {
                                        onValueChange(values[index])
                                        showDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (selected) BocchiStar else BocchiTextDark,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 波奇酱小 pastel 分段按钮：日/周/月切换用。 */
@Composable
fun BocchiSegmentedTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(Color.White)
                .border(1.dp, BocchiPink.copy(alpha = 0.4f), shape)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            val bg = if (selected) BocchiPink else Color.Transparent
            val fg = if (selected) Color.White else BocchiTextGray
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = fg,
                )
            }
        }
    }
}
