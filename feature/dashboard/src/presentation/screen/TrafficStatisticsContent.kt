package com.suanran.dreambox.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.model.traffic.AppTrafficUsage
import com.suanran.dreambox.core.model.traffic.FootprintDay
import com.suanran.dreambox.core.model.traffic.NodeTrafficUsage
import com.suanran.dreambox.core.model.traffic.StatisticsTimeRange
import com.suanran.dreambox.core.model.traffic.TrafficStatisticsBuckets
import com.suanran.dreambox.core.util.format.formatBytes
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.AppSortMode
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.TrafficStatisticsViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.chart.TimeSlotBarChart
import com.suanran.dreambox.presentation.component.chart.TrafficDonutChart
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.Title
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TabRowWithContour
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.component.rememberAppIconBitmap
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private const val APP_ICON_BADGE_BITMAP_SIZE = 84

@Composable
fun TrafficStatisticsContent(onBack: () -> Unit) {
    val viewModel = koinViewModel<TrafficStatisticsViewModel>()
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeRanges = StatisticsTimeRange.entries
    val selectedTabIndex = timeRanges.indexOf(uiState.selectedTimeRange).coerceAtLeast(0)
    val tabLabels = remember { timeRanges.map { it.toLabel() } }
    val activeSummary = uiState.summary

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.TrafficStatistics.Title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(onNavigateBack = onBack) },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.clearAllStatistics()
                            context.toast(FlyTxt.TrafficStatistics.Action.ClearSuccess)
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = FlyTxt.TrafficStatistics.Action.Clear,
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                TabRowWithContour(
                    tabs = tabLabels,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { index ->
                        timeRanges.getOrNull(index)?.let(viewModel::setTimeRange)
                    },
                    modifier = Modifier.padding(horizontal = spacing.space16),
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.space16),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.space18, vertical = spacing.space18),
                        verticalArrangement = Arrangement.spacedBy(spacing.space16),
                    ) {
                        val semanticColors = AppTheme.colors
                        TimeSlotBarChart(
                            items = uiState.barChartItems,
                            labels = uiState.barChartLabels,
                            downloadColor = semanticColors.traffic.download,
                            uploadColor = semanticColors.traffic.upload,
                            averageLineColor = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                Title(FlyTxt.TrafficStatistics.Section.Traffic)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.space16),
                    verticalArrangement = Arrangement.spacedBy(spacing.space10),
                ) {


                    TrafficMetricCard(
                        downloadValue = formatBytes(activeSummary.totalDownload),
                        uploadValue = formatBytes(activeSummary.totalUpload),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Title(FlyTxt.TrafficStatistics.Section.PerAppTraffic)
                    Box(modifier = Modifier.padding(end = spacing.space16)) {
                        var sortExpanded by remember { mutableStateOf(false) }
                        val currentMode = uiState.appSortMode
                        val currentAscending = uiState.appSortAscending
                        val sortOptions = remember {
                            listOf(
                                AppSortMode.NAME to FlyTxt.TrafficStatistics.Sort.ByName,
                                AppSortMode.UPLOAD to FlyTxt.TrafficStatistics.Sort.ByUpload,
                                AppSortMode.DOWNLOAD to FlyTxt.TrafficStatistics.Sort.ByDownload,
                                AppSortMode.TOTAL to FlyTxt.TrafficStatistics.Sort.ByTotal,
                            )
                        }
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(
                                imageVector = MiuixIcons.Sort,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        WindowListPopup(
                            show = sortExpanded,
                            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            ListPopupColumn {
                                sortOptions.forEachIndexed { index, (mode, label) ->
                                    val arrow = if (currentMode == mode) {
                                        if (currentAscending) " ↑" else " ↓"
                                    } else ""
                                    DropdownImpl(
                                        text = label + arrow,
                                        optionSize = sortOptions.size,
                                        isSelected = currentMode == mode,
                                        onSelectedIndexChange = {
                                            viewModel.setAppSortMode(mode)
                                            sortExpanded = false
                                        },
                                        index = index,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.topApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.space14, vertical = spacing.space18),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = FlyTxt.TrafficStatistics.Section.EmptyApps,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.topApps,
                    key = AppTrafficUsage::appKey,
                ) { usage ->
                    Box(
                        modifier = Modifier.padding(
                            horizontal = spacing.space16,
                            vertical = componentSizes.listItemVerticalMinimal,
                        ),
                    ) {
                        AppTrafficRow(
                            usage = usage,
                            total = activeSummary.total,
                        )
                    }
                }
            }

            // 按节点（出口）流量：参考 Shadowrocket Traffic Usage 页，DIRECT 与各节点分开列
            item {
                Title(FlyTxt.TrafficStatistics.Section.PerNodeTraffic)
            }
            if (uiState.nodeUsages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.space14, vertical = spacing.space18),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = FlyTxt.TrafficStatistics.Section.EmptyNodes,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.nodeUsages,
                    key = NodeTrafficUsage::routeKey,
                ) { usage ->
                    Box(
                        modifier = Modifier.padding(
                            horizontal = spacing.space16,
                            vertical = componentSizes.listItemVerticalMinimal,
                        ),
                    ) {
                        NodeTrafficRow(
                            usage = usage,
                            total = activeSummary.total,
                        )
                    }
                }
            }

            // 流量足迹时间线
            item {
                Title(FlyTxt.TrafficStatistics.Section.Footprint)
            }
            if (uiState.footprintDays.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.space14, vertical = spacing.space18),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = FlyTxt.TrafficStatistics.Section.EmptyFootprint,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.footprintDays,
                    key = { it.dateMillis },
                ) { day ->
                    FootprintTimelineRow(day = day)
                }
            }
        }
    }
}

@Composable
private fun TrafficMetricCard(
    downloadValue: String,
    uploadValue: String,
) {
    val spacing = AppTheme.spacing
    val semanticColors = AppTheme.colors

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space14, vertical = spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space18),
        ) {
            TrafficMetricLine(
                label = FlyTxt.TrafficStatistics.Metric.Download,
                value = downloadValue,
                valueColor = semanticColors.traffic.download,
            )
            TrafficMetricLine(
                label = FlyTxt.TrafficStatistics.Metric.Upload,
                value = uploadValue,
                valueColor = semanticColors.traffic.upload,
            )
        }
    }
}

@Composable
private fun TrafficMetricLine(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppTrafficRow(
    usage: AppTrafficUsage,
    total: Long,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val share = if (total > 0L) usage.totalBytes.toDouble() / total.toDouble() else 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space14, vertical = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconBadge(
                appKey = usage.appKey,
                packageName = usage.packageName,
                appName = usage.appName,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(componentSizes.textLineCompactSpacing),
            ) {
                Text(
                    text = usage.appName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = FlyTxt.TrafficStatistics.Metric.UsageLine.format(
                        formatBytes(usage.totalDownload),
                        formatBytes(usage.totalUpload),
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(componentSizes.textLineCompactSpacing),
            ) {
                Text(
                    text = formatBytes(usage.totalBytes),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "%.1f%%".format(share * 100),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AppIconBadge(
    appKey: String,
    packageName: String?,
    appName: String,
) {
    val componentSizes = AppTheme.sizes
    val semanticColors = AppTheme.colors
    val opacity = AppTheme.opacity
    val radii = AppTheme.radii

    if (appKey == TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY) {
        Box(
            modifier = Modifier
                .size(componentSizes.iconBadgeMedium)
                .clip(RoundedCornerShape(radii.radius12))
                .background(semanticColors.traffic.unattributed.copy(alpha = opacity.softOverlay)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                style = MiuixTheme.textStyles.body1,
                color = semanticColors.traffic.unattributed,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val bitmap = rememberAppIconBitmap(packageName = packageName, bitmapSize = APP_ICON_BADGE_BITMAP_SIZE)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(componentSizes.iconBadgeMedium)
                .clip(RoundedCornerShape(radii.radius12)),
        )
        return
    }

    Box(
        modifier = Modifier
            .size(componentSizes.iconBadgeMedium)
            .clip(RoundedCornerShape(radii.radius12))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtleStrong)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appName.take(1).ifBlank { "?" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NodeTrafficRow(
    usage: NodeTrafficUsage,
    total: Long,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val share = if (total > 0L) usage.totalBytes.toDouble() / total.toDouble() else 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space14, vertical = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeIconBadge(nodeName = usage.nodeName)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(componentSizes.textLineCompactSpacing),
            ) {
                Text(
                    text = usage.nodeName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = FlyTxt.TrafficStatistics.Metric.UsageLine.format(
                        formatBytes(usage.totalDownload),
                        formatBytes(usage.totalUpload),
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(componentSizes.textLineCompactSpacing),
            ) {
                Text(
                    text = formatBytes(usage.totalBytes),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "%.1f%%".format(share * 100),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun NodeIconBadge(nodeName: String) {
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity
    val radii = AppTheme.radii

    Box(
        modifier = Modifier
            .size(componentSizes.iconBadgeMedium)
            .clip(RoundedCornerShape(radii.radius12))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtleStrong)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = nodeName.take(1).ifBlank { "?" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FootprintTimelineRow(day: FootprintDay) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val dateLabel = remember(day.dateMillis) {
        java.time.Instant.ofEpochMilli(day.dateMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("M月d日 E", java.util.Locale.CHINESE))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16, vertical = componentSizes.listItemVerticalMinimal),
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        // 时间线竖线+圆点
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = spacing.space14),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MiuixTheme.colorScheme.primary),
            )
        }
        Card(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.space14, vertical = spacing.space12),
                verticalArrangement = Arrangement.spacedBy(componentSizes.textLineCompactSpacing),
            ) {
                Text(
                    text = dateLabel,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = FlyTxt.TrafficStatistics.Metric.UsageLine.format(
                        formatBytes(day.totalDownload),
                        formatBytes(day.totalUpload),
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (day.topAppName.isNotBlank()) {
                    Text(
                        text = FlyTxt.TrafficStatistics.Footprint.TopApp.format(day.topAppName),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun StatisticsTimeRange.toLabel(): String = when (this) {
    StatisticsTimeRange.DAY -> FlyTxt.TrafficStatistics.TimeRange.Day
    StatisticsTimeRange.WEEK -> FlyTxt.TrafficStatistics.TimeRange.Week
    StatisticsTimeRange.MONTH -> FlyTxt.TrafficStatistics.TimeRange.Month
}
