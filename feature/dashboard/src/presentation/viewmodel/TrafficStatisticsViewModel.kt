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

package com.suanran.dreambox.feature.dashboard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suanran.dreambox.core.contract.TrafficStatisticsRepository
import com.suanran.dreambox.core.model.traffic.AppTrafficUsage
import com.suanran.dreambox.core.model.traffic.DailyTraffic
import com.suanran.dreambox.core.model.traffic.DailyTrafficSummary
import com.suanran.dreambox.core.model.traffic.FootprintDay
import com.suanran.dreambox.core.model.traffic.NodeTrafficUsage
import com.suanran.dreambox.core.model.traffic.StatisticsTimeRange
import com.suanran.dreambox.core.model.traffic.TimeSlotTraffic
import com.suanran.dreambox.presentation.component.chart.TimeSlotTrafficItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppSortMode { NAME, UPLOAD, DOWNLOAD, TOTAL }

class TrafficStatisticsViewModel(private val trafficStatisticsStore: TrafficStatisticsRepository) :
    ViewModel() {
    private val selectedTimeRange = MutableStateFlow(StatisticsTimeRange.DAY)
    private val appSortMode = MutableStateFlow(AppSortMode.NAME)
    private val appSortAscending = MutableStateFlow(true)

    fun setAppSortMode(mode: AppSortMode) {
        if (appSortMode.value == mode) {
            appSortAscending.value = !appSortAscending.value
        } else {
            appSortMode.value = mode
            appSortAscending.value = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sortedAppsFlow = selectedTimeRange.flatMapLatest { range ->
        combine(
            trafficStatisticsStore.getAppUsagesFlow(range),
            appSortMode,
            appSortAscending,
        ) { apps, mode, ascending ->
            sortApps(apps, mode, ascending)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val barChartFlow = selectedTimeRange.flatMapLatest { range ->
        when (range) {
            StatisticsTimeRange.DAY -> {
                trafficStatisticsStore.getTimeSlotTrafficFlow(range).map { slots ->
                    val allSlots = (0..23).map { s ->
                        slots.firstOrNull { it.slotIndex == s }
                            ?: com.suanran.dreambox.core.model.traffic.TimeSlotTraffic(s, 0L, 0L)
                    }
                    val items = allSlots.map {
                        TimeSlotTrafficItem(slotIndex = it.slotIndex, upload = it.totalUpload, download = it.totalDownload)
                    }
                    val labels = (0..23).map { "%02d".format(it) }
                    items to labels
                }
            }
            StatisticsTimeRange.WEEK, StatisticsTimeRange.MONTH -> {
                trafficStatisticsStore.getDailyTrafficFlow(range).map { days ->
                    val dayMap = days.associateBy { it.dateMillis }
                    val calendar = checkNotNull(dayStartCalendar.get())
                    val todayMillis = calendar.timeInMillis
                    val dayCount = range.days
                    val allDays = (dayCount - 1 downTo 0).map { offset ->
                        val millis = todayMillis - offset * DAY_MS
                        dayMap[millis] ?: DailyTraffic(millis, 0L, 0L)
                    }
                    val items = allDays.map { d ->
                        TimeSlotTrafficItem(slotIndex = 0, upload = d.totalUpload, download = d.totalDownload)
                    }
                    val labels = allDays.map { d ->
                        checkNotNull(dateFormat.get()).format(java.time.Instant.ofEpochMilli(d.dateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                    }
                    items to labels
                }
            }
        }
    }.distinctUntilChanged()

    /** 流量足迹：近30天每天的小结（倒序），每天取流量最高的应用。 */
    private val footprintFlow: StateFlow<List<FootprintDay>> =
        trafficStatisticsStore.getDailyAppBreakdownFlow(StatisticsTimeRange.MONTH)
            .map { rows ->
                rows.groupBy { it.dateMillis }
                    .map { (dateMillis, apps) ->
                        val topApp = apps.maxByOrNull { it.totalBytes }
                        FootprintDay(
                            dateMillis = dateMillis,
                            totalUpload = apps.sumOf { it.totalUpload },
                            totalDownload = apps.sumOf { it.totalDownload },
                            topAppName = topApp?.appName.orEmpty(),
                            topAppKey = topApp?.appKey.orEmpty(),
                        )
                    }
                    .filter { it.total > 0L }
                    .sortedByDescending { it.dateMillis }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )

    /**
     * 按节点（出口）聚合的流量：跟随当前所选时间范围，按总量倒序。
     * 注意：必须声明在 [uiState] 之前——Kotlin 属性按文本顺序初始化，
     * [uiState] 的 combine 参数会在其初始化器执行时求值，读到未初始化的 val 会得到 null
     * 从而在 kotlinx.coroutines 的参数非空检查处抛 IllegalArgumentException。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nodeUsages: StateFlow<List<NodeTrafficUsage>> =
        selectedTimeRange.flatMapLatest { range ->
            trafficStatisticsStore.getRouteUsagesFlow(range)
        }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrafficStatisticsUiState> =
        combine(
            selectedTimeRange,
            sortedAppsFlow,
            barChartFlow,
            footprintFlow,
            nodeUsages,
        ) { range, sortedApps, (barItems, barLabels), footprint, nodes ->
            buildUiState(range, sortedApps, barItems, barLabels, footprint, nodes)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TrafficStatisticsUiState(),
            )

    fun setTimeRange(range: StatisticsTimeRange) {
        selectedTimeRange.value = range
    }

    fun clearAllStatistics() {
        viewModelScope.launch { trafficStatisticsStore.clearAll() }
    }

    private fun sortApps(
        apps: List<AppTrafficUsage>,
        mode: AppSortMode,
        ascending: Boolean,
    ): List<AppTrafficUsage> {
        val sorted = when (mode) {
            AppSortMode.NAME -> apps.sortedBy { it.appName.lowercase() }
            AppSortMode.UPLOAD -> apps.sortedByDescending { it.totalUpload }
            AppSortMode.DOWNLOAD -> apps.sortedByDescending { it.totalDownload }
            AppSortMode.TOTAL -> apps.sortedByDescending { it.totalBytes }
        }
        return if (ascending) sorted else sorted.reversed()
    }

    private fun buildUiState(
        range: StatisticsTimeRange,
        sortedApps: List<AppTrafficUsage>,
        barItems: List<TimeSlotTrafficItem>,
        barLabels: List<String>,
        footprint: List<FootprintDay>,
        nodes: List<NodeTrafficUsage>,
    ): TrafficStatisticsUiState {
        val totalUpload = sortedApps.sumOf(AppTrafficUsage::totalUpload)
        val totalDownload = sortedApps.sumOf(AppTrafficUsage::totalDownload)

        return TrafficStatisticsUiState(
            selectedTimeRange = range,
            summary =
                DailyTrafficSummary(
                    dateMillis = range.days.toLong(),
                    totalUpload = totalUpload,
                    totalDownload = totalDownload,
                ),
            topApps = sortedApps,
            barChartItems = barItems,
            barChartLabels = barLabels,
            footprintDays = footprint,
            nodeUsages = nodes,
            appSortMode = appSortMode.value,
            appSortAscending = appSortAscending.value,
        )
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val dateFormat = ThreadLocal.withInitial {
            java.time.format.DateTimeFormatter.ofPattern("M/d")
        }
        private val dayStartCalendar = ThreadLocal.withInitial {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }
}

data class TrafficStatisticsUiState(
    val selectedTimeRange: StatisticsTimeRange = StatisticsTimeRange.DAY,
    val summary: DailyTrafficSummary = DailyTrafficSummary.empty,
    val topApps: List<AppTrafficUsage> = emptyList(),
    val barChartItems: List<TimeSlotTrafficItem> = emptyList(),
    val barChartLabels: List<String> = emptyList(),
    val footprintDays: List<FootprintDay> = emptyList(),
    val nodeUsages: List<NodeTrafficUsage> = emptyList(),
    val appSortMode: AppSortMode = AppSortMode.NAME,
    val appSortAscending: Boolean = true,
)
