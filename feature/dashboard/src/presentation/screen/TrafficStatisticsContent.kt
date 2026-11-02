package com.suanran.dreambox.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.model.traffic.AppTrafficUsage
import com.suanran.dreambox.core.model.traffic.FootprintDay
import com.suanran.dreambox.core.model.traffic.NodeTrafficUsage
import com.suanran.dreambox.core.model.traffic.StatisticsTimeRange
import com.suanran.dreambox.core.model.traffic.TrafficStatisticsBuckets
import com.suanran.dreambox.core.util.format.formatBytes
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.AppSortMode
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.TrafficStatisticsUiState
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.TrafficStatisticsViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.chart.TimeSlotBarChart
import com.suanran.dreambox.presentation.component.chart.TrafficDonutChart
import com.suanran.dreambox.presentation.component.chart.TrafficDonutSlice
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.misc.BocchiSegmentedTabs
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiBlue
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.component.rememberAppIconBitmap
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import org.koin.androidx.compose.koinViewModel
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

/** 流量统计三种看法（参考 Trakt 统计页：仪表盘 / 故事 / 海报墙），2026-11-03 用户拍板。 */
private enum class StatsViewMode(val label: String) {
    DASHBOARD("仪表盘"),
    STORY("故事"),
    WALL("海报墙"),
}

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
    var viewMode by remember { mutableStateOf(StatsViewMode.DASHBOARD) }

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
                BocchiSegmentedTabs(
                    tabs = tabLabels,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { index ->
                        timeRanges.getOrNull(index)?.let(viewModel::setTimeRange)
                    },
                    modifier = Modifier.padding(horizontal = spacing.space16),
                )
            }

            item {
                BocchiSegmentedTabs(
                    tabs = StatsViewMode.entries.map { it.label },
                    selectedTabIndex = viewMode.ordinal,
                    onTabSelected = { index ->
                        StatsViewMode.entries.getOrNull(index)?.let { viewMode = it }
                    },
                    modifier = Modifier.padding(horizontal = spacing.space16),
                )
            }

            when (viewMode) {
                StatsViewMode.DASHBOARD -> {
                    item {
                        TrafficDonutHero(uiState = uiState)
                    }

                    item {
                        BocchiCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.space16),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = spacing.space18, vertical = spacing.space18),
                                verticalArrangement = Arrangement.spacedBy(spacing.space16),
                            ) {
                                TimeSlotBarChart(
                                    items = uiState.barChartItems,
                                    labels = uiState.barChartLabels,
                                    downloadColor = BocchiPink,
                                    uploadColor = BocchiBlue,
                                    averageLineColor = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BocchiSectionTitle(FlyTxt.TrafficStatistics.Section.PerAppTraffic)
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
                            BocchiCard(
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

                    // 按节点（出口）流量：DIRECT 与各节点分开列
                    item {
                        BocchiSectionTitle(FlyTxt.TrafficStatistics.Section.PerNodeTraffic)
                    }
                    if (uiState.nodeUsages.isEmpty()) {
                        item {
                            BocchiCard(
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
                        BocchiSectionTitle(FlyTxt.TrafficStatistics.Section.Footprint)
                    }
                    if (uiState.footprintDays.isEmpty()) {
                        item {
                            BocchiCard(
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

                StatsViewMode.STORY -> {
                    val stories = buildStories(uiState)
                    if (stories.isEmpty()) {
                        item {
                            BocchiCard(
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
                                        text = "这段时间还没有流量记录。",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    } else {
                        items(items = stories) { story ->
                            BocchiCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.space16),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.space14, vertical = spacing.space14),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(text = story.emoji, fontSize = 20.sp)
                                    Text(
                                        text = story.text,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                StatsViewMode.WALL -> {
                    item {
                        BocchiSectionTitle("应用墙")
                    }
                    if (uiState.topApps.isEmpty()) {
                        item {
                            BocchiCard(
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
                        items(items = uiState.topApps.chunked(3)) { rowApps ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.space16),
                                horizontalArrangement = Arrangement.spacedBy(spacing.space10),
                            ) {
                                rowApps.forEach { usage ->
                                    AppPosterCell(
                                        usage = usage,
                                        total = activeSummary.total,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - rowApps.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    item {
                        BocchiSectionTitle("足迹墙")
                    }
                    if (uiState.footprintDays.isEmpty()) {
                        item {
                            BocchiCard(
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
                        item {
                            FootprintHeatCard(days = uiState.footprintDays)
                        }
                    }
                }
            }
        }
    }
}

// ---------- 仪表盘：环图主视觉 ----------

private val DaypartMorning = Color(0xFF7EC8F7)
private val DaypartAfternoon = Color(0xFFB79CFF)
private val DaypartEvening = Color(0xFFFFB35C)

private data class DonutData(
    val slices: List<TrafficDonutSlice>,
    val peakLabel: String,
    val peakValue: Long,
)

private fun trafficPct(part: Long, total: Long): Int =
    if (total > 0L) (part * 100L / total).toInt() else 0

private fun buildDonutData(state: TrafficStatisticsUiState): DonutData {
    val items = state.barChartItems
    val peakIndex = items.indices.maxByOrNull { items[it].upload + items[it].download } ?: -1
    val peakValue = if (peakIndex >= 0) items[peakIndex].upload + items[peakIndex].download else 0L
    val rawPeakLabel = if (peakIndex >= 0) state.barChartLabels.getOrNull(peakIndex).orEmpty() else ""
    if (state.selectedTimeRange == StatisticsTimeRange.DAY) {
        fun sumHours(from: Int, to: Int): Long =
            items.filter { it.slotIndex in from..to }.sumOf { it.upload + it.download }
        return DonutData(
            slices = listOf(
                TrafficDonutSlice("morning", "上午", sumHours(6, 11), DaypartMorning),
                TrafficDonutSlice("afternoon", "下午", sumHours(12, 17), DaypartAfternoon),
                TrafficDonutSlice("evening", "晚上", sumHours(18, 23), DaypartEvening),
                TrafficDonutSlice("night", "夜间", sumHours(0, 5), BocchiPink),
            ),
            peakLabel = if (rawPeakLabel.isBlank()) "" else "$rawPeakLabel 时",
            peakValue = peakValue,
        )
    }
    return DonutData(
        slices = listOf(
            TrafficDonutSlice("download", "下行", state.summary.totalDownload, BocchiPink),
            TrafficDonutSlice("upload", "上行", state.summary.totalUpload, BocchiBlue),
        ),
        peakLabel = rawPeakLabel,
        peakValue = peakValue,
    )
}

@Composable
private fun TrafficDonutHero(uiState: TrafficStatisticsUiState) {
    val spacing = AppTheme.spacing
    val donut = remember(uiState) { buildDonutData(uiState) }
    var selectedKey by remember(uiState.selectedTimeRange) { mutableStateOf<String?>(null) }
    val total = uiState.summary.total
    val selected = donut.slices.firstOrNull { it.key == selectedKey }

    BocchiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.space18, vertical = spacing.space18),
            verticalArrangement = Arrangement.spacedBy(spacing.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TrafficDonutChart(
                total = total,
                slices = donut.slices,
                selectedKey = selectedKey,
                onSliceClick = { selectedKey = it },
                modifier = Modifier.size(220.dp),
                animationKey = uiState.selectedTimeRange,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selected?.label ?: "总流量",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = formatBytes(selected?.value ?: total),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BocchiTextDark,
                        maxLines = 1,
                    )
                    if (selected != null && total > 0L) {
                        Text(
                            text = "占 ${trafficPct(selected.value, total)}%",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.space10),
            ) {
                donut.slices.forEach { slice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = if (selectedKey == slice.key) null else slice.key },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.space10),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(slice.color),
                            )
                            Text(
                                text = slice.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = BocchiTextDark,
                            )
                        }
                        Text(
                            text = formatBytes(slice.value),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedKey == slice.key) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                TrioCell(
                    modifier = Modifier.weight(1f),
                    value = formatBytes(uiState.summary.totalDownload),
                    caption = "下行",
                )
                TrioCell(
                    modifier = Modifier.weight(1f),
                    value = formatBytes(uiState.summary.totalUpload),
                    caption = "上行",
                )
                TrioCell(
                    modifier = Modifier.weight(1f),
                    value = donut.peakLabel.ifBlank { "—" },
                    caption = "峰值 · ${formatBytes(donut.peakValue)}",
                )
            }
        }
    }
}

@Composable
private fun TrioCell(
    modifier: Modifier = Modifier,
    value: String,
    caption: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BocchiTextDark,
            maxLines = 1,
        )
        Text(
            text = caption,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

// ---------- 故事 ----------

private data class StoryCardData(
    val emoji: String,
    val text: String,
)

private fun buildStories(state: TrafficStatisticsUiState): List<StoryCardData> {
    val total = state.summary.total
    if (total <= 0L) return emptyList()
    val cards = mutableListOf<StoryCardData>()
    val rangeWord = when (state.selectedTimeRange) {
        StatisticsTimeRange.DAY -> "今天"
        StatisticsTimeRange.WEEK -> "本周"
        StatisticsTimeRange.MONTH -> "本月"
    }
    cards += StoryCardData(
        "📊",
        "${rangeWord}一共用了 ${formatBytes(total)} 流量，下行占 ${trafficPct(state.summary.totalDownload, total)}%。",
    )
    val donut = buildDonutData(state)
    if (donut.peakValue > 0L && donut.peakLabel.isNotBlank()) {
        cards += StoryCardData(
            "🔥",
            if (state.selectedTimeRange == StatisticsTimeRange.DAY) {
                "最忙的是 ${donut.peakLabel}，一个小时就跑了 ${formatBytes(donut.peakValue)}。"
            } else {
                "最忙的一天是 ${donut.peakLabel}，单日用了 ${formatBytes(donut.peakValue)}。"
            },
        )
    }
    state.topApps.maxByOrNull { it.totalBytes }?.let { app ->
        cards += StoryCardData(
            "🏆",
            "最耗流量的是 ${app.appName}，用了 ${formatBytes(app.totalBytes)}，占总量的 ${trafficPct(app.totalBytes, total)}%。",
        )
    }
    state.nodeUsages.maxByOrNull { it.totalBytes }?.let { node ->
        cards += StoryCardData(
            "🚀",
            "出口主要走 ${node.nodeName}，占总量的 ${trafficPct(node.totalBytes, total)}%。",
        )
    }
    if (state.selectedTimeRange == StatisticsTimeRange.DAY) {
        val night = donut.slices.firstOrNull { it.key == "night" }?.value ?: 0L
        val nightPct = trafficPct(night, total)
        val comment = when {
            nightPct >= 40 -> "夜里比白天还要忙。"
            nightPct >= 15 -> "夜里也挺活跃。"
            else -> "基本都花在白天。"
        }
        cards += StoryCardData("🌙", "夜间（0–6 点）流量占 ${nightPct}%，$comment")
    }
    val up = state.summary.totalUpload
    val down = state.summary.totalDownload
    if (up > 0L && down > up * 3L) {
        cards += StoryCardData(
            "⬇️",
            "下行是上行的 ${"%.1f".format(down.toDouble() / up.toDouble())} 倍，主要是下载和看片的用法。",
        )
    }
    return cards
}

// ---------- 海报墙 ----------

@Composable
private fun AppPosterCell(
    usage: AppTrafficUsage,
    total: Long,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    BocchiCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space10, vertical = spacing.space12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIconBadge(
                appKey = usage.appKey,
                packageName = usage.packageName,
                appName = usage.appName,
            )
            Text(
                text = usage.appName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = BocchiTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(usage.totalBytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "${trafficPct(usage.totalBytes, total)}%",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FootprintHeatCard(days: List<FootprintDay>) {
    val spacing = AppTheme.spacing
    val sorted = remember(days) { days.sortedBy { it.dateMillis }.takeLast(84) }
    val maxTotal = sorted.maxOfOrNull { it.total } ?: 0L
    val activeDays = sorted.count { it.total > 0L }
    BocchiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.space14, vertical = spacing.space14),
            verticalArrangement = Arrangement.spacedBy(spacing.space10),
        ) {
            sorted.chunked(14).forEach { rowDays ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowDays.forEach { day ->
                        val cellColor = if (day.total <= 0L || maxTotal <= 0L) {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        } else {
                            BocchiPink.copy(alpha = 0.18f + 0.82f * (day.total.toFloat() / maxTotal.toFloat()))
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(cellColor),
                        )
                    }
                }
            }
            Text(
                text = "近 ${sorted.size} 天 · 活跃 $activeDays 天",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

// ---------- 列表行（仪表盘沿用） ----------

@Composable
private fun AppTrafficRow(
    usage: AppTrafficUsage,
    total: Long,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val share = if (total > 0L) usage.totalBytes.toDouble() / total.toDouble() else 0.0
    BocchiCard(
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
                    fontSize = 13.sp,
                    color = BocchiTextDark,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
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
    BocchiCard(
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
                    fontSize = 13.sp,
                    color = BocchiTextDark,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
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
        BocchiCard(modifier = Modifier.weight(1f)) {
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
