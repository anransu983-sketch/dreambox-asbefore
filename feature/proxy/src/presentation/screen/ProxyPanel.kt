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

package com.suanran.dreambox.feature.proxy.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.model.traffic.TrafficData
import com.suanran.dreambox.core.util.format.formatBytesForDisplay
import com.suanran.dreambox.presentation.component.chart.TrafficChartConfig
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PanelUploadGreen = Color(0xFF52C41A)
private const val PANEL_SPEED_SAMPLE_LIMIT = 24

/**
 * 代理页仪表盘头卡（面板模式专用，纯展示）：
 * 下载/上传实时速率大数字 + 24 采样速率柱状图 + 组数。
 * 速率来自内核实时流量（ProxyControlContract.trafficNow），未连接时显示为 0，
 * 不编造任何数字；采样由调用方每秒喂一次真实值。
 */
@Composable
internal fun PanelDashboardHeader(
    trafficNow: TrafficData,
    isRunning: Boolean,
    groupCount: Int,
    speedHistory: List<TrafficData>,
    onTogglePanelMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppTheme.radii.radius12)
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val downloadColor = MiuixTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) MiuixTheme.colorScheme.surfaceContainer else Color.White)
            .border(1.dp, Color(0xFFFFB7C5).copy(alpha = if (dark) 0.3f else 0.5f), shape)
            .padding(horizontal = UiDp.dp14, vertical = UiDp.dp12),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp10),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
        ) {
            PanelSpeedCell(
                label = "下载 Download",
                speed = trafficNow.download,
                color = downloadColor,
                modifier = Modifier.weight(1f),
            )
            PanelSpeedCell(
                label = "上传 Upload",
                speed = trafficNow.upload,
                color = PanelUploadGreen,
                modifier = Modifier.weight(1f),
            )
        }
        PanelSpeedChart(
            speedHistory = speedHistory,
            downloadColor = downloadColor,
            uploadColor = PanelUploadGreen,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isRunning) "代理组 · $groupCount 组" else "未连接 · 代理组 $groupCount 组",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onTogglePanelMode != null) {
                Text(
                    text = "切回列表",
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onTogglePanelMode),
                )
            }
        }
    }
}

@Composable
private fun PanelSpeedCell(
    label: String,
    speed: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val (value, unit) = formatBytesForDisplay(speed)
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = UiDp.dp2),
        ) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.title2.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
            )
            Text(
                text = unit,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 14.sp),
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = UiDp.dp4, bottom = UiDp.dp2),
            )
        }
    }
}

/**
 * 速率柱状图：与首页 SpeedChart 同一套分档口径（TrafficChartConfig），
 * 下行在下、上行叠在上；无数据时只有最小可见高度，不画假曲线。
 */
@Composable
private fun PanelSpeedChart(
    speedHistory: List<TrafficData>,
    downloadColor: Color,
    uploadColor: Color,
    modifier: Modifier = Modifier,
) {
    val fractions = remember(speedHistory) {
        val download = FloatArray(PANEL_SPEED_SAMPLE_LIMIT) { TrafficChartConfig.minimumVisibleFraction }
        val upload = FloatArray(PANEL_SPEED_SAMPLE_LIMIT) { TrafficChartConfig.minimumVisibleFraction }
        val recent = speedHistory.takeLast(PANEL_SPEED_SAMPLE_LIMIT)
        val offset = (PANEL_SPEED_SAMPLE_LIMIT - recent.size).coerceAtLeast(0)
        recent.forEachIndexed { index, sample ->
            download[offset + index] = TrafficChartConfig.calculateBarFraction(sample.download)
            upload[offset + index] = TrafficChartConfig.calculateBarFraction(sample.upload)
        }
        download to upload
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val barCount = PANEL_SPEED_SAMPLE_LIMIT
        val barGapPx = 4.dp.toPx()
        val totalGapWidth = barGapPx * (barCount - 1)
        val barWidthPx = ((size.width - totalGapWidth) / barCount).coerceAtLeast(0f)
        if (barWidthPx <= 0f) return@Canvas
        val (downloadFractions, uploadFractions) = fractions
        for (index in 0 until barCount) {
            val leftPx = index * (barWidthPx + barGapPx)
            val downloadHeightPx = size.height * downloadFractions[index].coerceIn(
                TrafficChartConfig.minimumVisibleFraction, 1f,
            )
            var uploadHeightPx = size.height * uploadFractions[index].coerceIn(
                TrafficChartConfig.minimumVisibleFraction, 1f,
            )
            if (downloadHeightPx + uploadHeightPx > size.height) {
                uploadHeightPx = (size.height - downloadHeightPx).coerceAtLeast(0f)
            }
            drawRect(
                color = downloadColor.copy(alpha = 0.75f),
                topLeft = Offset(leftPx, size.height - downloadHeightPx),
                size = Size(barWidthPx, downloadHeightPx),
            )
            drawRoundRect(
                color = uploadColor.copy(alpha = 0.75f),
                topLeft = Offset(leftPx, size.height - downloadHeightPx - uploadHeightPx),
                size = Size(barWidthPx, uploadHeightPx),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f),
            )
        }
    }
}
