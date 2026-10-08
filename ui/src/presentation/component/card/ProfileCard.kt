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

package com.suanran.dreambox.presentation.component.card

import android.annotation.SuppressLint
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.CircleFadingArrowUp
import com.suanran.dreambox.presentation.icon.flycat.Delete
import com.suanran.dreambox.presentation.icon.flycat.Edit
import com.suanran.dreambox.presentation.icon.flycat.Share
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.core.util.format.ByteFormatter
import com.suanran.dreambox.presentation.util.enabled
import com.suanran.dreambox.presentation.util.expireAt
import com.suanran.dreambox.presentation.util.getDisplayProvider
import com.suanran.dreambox.presentation.util.isConfigSaved
import com.suanran.dreambox.presentation.util.shouldShowUpdateButton
import com.suanran.dreambox.presentation.util.totalBytes
import com.suanran.dreambox.presentation.util.usedBytes
import java.io.File
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
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

@Composable
fun ProfileCard(
    profile: Profile,
    workDir: File,
    isDownloading: Boolean = false,
    onExport: (Profile) -> Unit,
    onUpdate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onToggleEnabled: (Profile) -> Unit,
    onOverrideSettings: ((Profile) -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity
    val componentSizes = AppTheme.sizes

    val colorScheme = MiuixTheme.colorScheme

    val isDark = isSystemInDarkTheme()
    val secondaryContainer = colorScheme.secondaryContainer.copy(alpha = opacity.strong)
    val actionIconTint =
        remember(isDark, opacity) {
            colorScheme.onSurface.copy(
                alpha = if (isDark) opacity.subtleText else opacity.prominentText
            )
        }

    val isConfigSaved = remember(profile.uuid, profile.updatedAt) { profile.isConfigSaved(workDir) }

    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = spacing.space12).pressable(interactionSource = interactionSource, indication = SinkFeedback()),
        insideMargin = PaddingValues(spacing.space16),
    ) {
        // 标题行：名字 + 星星，右侧粉色开关
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = spacing.space4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight(600),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false).basicMarquee(),
                    )
                    Text(
                        text = "✦",
                        fontSize = 12.sp,
                        color = BocchiStar,
                        modifier = Modifier.padding(start = spacing.space4),
                    )
                }

                Text(
                    text = profile.getDisplayProvider(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = spacing.space2),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Switch(
                checked = profile.enabled,
                enabled = !isDownloading,
                onCheckedChange = { onToggleEnabled(profile) },
            )
        }

        // 流量进度条
        val totalBytesValue = profile.totalBytes
        val usedBytesValue = profile.usedBytes
        if (totalBytesValue != null && totalBytesValue > 0) {
            val percent = (usedBytesValue * 100 / totalBytesValue).toInt().coerceIn(0, 100)
            val barColor = if (percent < 50) BocchiPink else if (percent < 80) BocchiBlue else BocchiStar
            Column(modifier = Modifier.padding(top = spacing.space8)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent / 100f)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .background(barColor)
                                .padding(vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "$percent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = barColor,
                        modifier = Modifier.padding(start = spacing.space8),
                    )
                }
                Text(
                    text = "${ByteFormatter.format(usedBytesValue)} / ${ByteFormatter.format(totalBytesValue)}",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = spacing.space2),
                )
            }
        } else if (usedBytesValue > 0) {
            Text(
                text = FlyTxt.Component.ProfileCard.UsedTraffic.format(
                    ByteFormatter.format(usedBytesValue)
                ),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = spacing.space8),
            )
        }

        // 到期日贴纸徽章
        val expireAt = profile.expireAt
        if (expireAt != null) {
            val expireDate = java.time.Instant.ofEpochMilli(expireAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), expireDate).toInt()
            val expireLabel = when {
                daysLeft > 0 -> "📅 $expireDate · 剩余${daysLeft}天"
                daysLeft == 0L -> "📅 $expireDate · 今天到期"
                else -> "📅 $expireDate · 已过期"
            }
            val badgeBg = if (daysLeft > 30) BocchiBlueBg else BocchiPinkBg
            val badgeFg = if (daysLeft > 30) Color(0xFF4A90D9) else BocchiStar
            Text(
                text = expireLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = badgeFg,
                modifier = Modifier
                    .padding(top = spacing.space8)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(badgeBg)
                    .border(1.dp, badgeFg.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = spacing.space8, vertical = spacing.space4),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = spacing.space12),
            thickness = componentSizes.thinDividerThickness,
            color = colorScheme.outline.copy(alpha = opacity.medium),
        )

        // 操作按钮：贴纸风
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                backgroundColor = secondaryContainer,
                minHeight = componentSizes.compactActionButtonSize,
                minWidth = componentSizes.compactActionButtonSize,
                enabled = isConfigSaved && !isDownloading,
                onClick = { if (isConfigSaved && !isDownloading) onExport(profile) },
            ) {
                Icon(
                    modifier = Modifier.size(spacing.space20).alpha(if (isConfigSaved) 1f else opacity.disabledSecondary),
                    imageVector = FlyCat.Share,
                    tint = actionIconTint.copy(alpha = if (isConfigSaved) 1f else opacity.disabledSecondary),
                    contentDescription = FlyTxt.Component.Action.Export,
                )
            }

            Spacer(Modifier.width(spacing.space8))

            IconButton(
                backgroundColor = secondaryContainer,
                minHeight = componentSizes.compactActionButtonSize,
                minWidth = componentSizes.compactActionButtonSize,
                enabled = !isDownloading,
                onClick = { if (!isDownloading) onDelete(profile) },
            ) {
                Icon(
                    modifier = Modifier.size(spacing.space20),
                    imageVector = FlyCat.Delete,
                    tint = actionIconTint,
                    contentDescription = FlyTxt.Component.Action.Delete,
                )
            }

            Spacer(Modifier.weight(1f))

            if (profile.shouldShowUpdateButton()) {
                StickerButton(
                    text = FlyTxt.Component.ProfileCard.Update,
                    icon = {
                        Icon(
                            modifier = Modifier.size(14.dp),
                            imageVector = FlyCat.CircleFadingArrowUp,
                            tint = Color(0xFF4A90D9),
                            contentDescription = null,
                        )
                    },
                    bgColor = BocchiBlueBg,
                    fgColor = Color(0xFF4A90D9),
                    enabled = !isDownloading,
                    onClick = { if (!isDownloading) onUpdate(profile) },
                    modifier = Modifier.padding(end = spacing.space8),
                )
            }

            StickerButton(
                text = FlyTxt.Component.ProfileCard.Edit,
                icon = {
                    Icon(
                        modifier = Modifier.size(14.dp),
                        imageVector = FlyCat.Edit,
                        tint = BocchiStar,
                        contentDescription = null,
                    )
                },
                bgColor = BocchiPinkBg,
                fgColor = BocchiStar,
                enabled = !isDownloading,
                onClick = { if (!isDownloading) onEdit(profile) },
            )
        }
    }
}

/** 波奇酱风小贴纸按钮 */
@Composable
private fun StickerButton(
    text: String,
    icon: @Composable () -> Unit,
    bgColor: Color,
    fgColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, fgColor.copy(alpha = 0.3f), shape)
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = fgColor,
        )
    }
}
