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

package com.suanran.dreambox.core.model.traffic

import kotlinx.serialization.Serializable
import com.suanran.dreambox.locale.FlyTxt

@Serializable
data class DailyTrafficSummary(
    val dateMillis: Long,
    val totalUpload: Long,
    val totalDownload: Long,
) {
    val total: Long
        get() = totalUpload + totalDownload

    companion object {
        val empty = DailyTrafficSummary(0L, 0L, 0L)
    }
}

@Serializable
data class AppTrafficUsage(
    val appKey: String,
    val packageName: String? = null,
    val appName: String,
    val totalUpload: Long = 0L,
    val totalDownload: Long = 0L,
    val lastActiveAt: Long = 0L,
) {
    val totalBytes: Long
        get() = totalUpload + totalDownload
}

data class AppTrafficDeltaRecord(
    val appKey: String,
    val packageName: String? = null,
    val appName: String,
    val uploadDelta: Long,
    val downloadDelta: Long,
    val routeKey: String? = null,
    val routeLabel: String? = null,
)

object TrafficStatisticsBuckets {
    const val UNKNOWN_APP_KEY = "unknown"
    val UNKNOWN_APP_NAME get() = FlyTxt.TrafficStatistics.Buckets.UnknownAppName
    const val UNATTRIBUTED_APP_KEY = "system:unattributed"
    val UNATTRIBUTED_APP_NAME get() = FlyTxt.TrafficStatistics.Buckets.UnattributedTrafficName
    const val UNATTRIBUTED_ROUTE_KEY = "route:unattributed"
    val UNATTRIBUTED_ROUTE_NAME get() = FlyTxt.TrafficStatistics.Buckets.UnattributedRouteName

    fun buildUnattributedRecord(uploadDelta: Long, downloadDelta: Long) =
        AppTrafficDeltaRecord(
            appKey = UNATTRIBUTED_APP_KEY,
            packageName = null,
            appName = UNATTRIBUTED_APP_NAME,
            uploadDelta = uploadDelta,
            downloadDelta = downloadDelta,
            routeKey = UNATTRIBUTED_ROUTE_KEY,
            routeLabel = UNATTRIBUTED_ROUTE_NAME,
        )
}

@Serializable
data class AppRouteTrafficUsage(
    val appKey: String,
    val routeKey: String,
    val routeLabel: String,
    val totalUpload: Long = 0L,
    val totalDownload: Long = 0L,
    val lastActiveAt: Long = 0L,
) {
    val totalBytes: Long
        get() = totalUpload + totalDownload
}

@Serializable
data class NodeTrafficUsage(
    val routeKey: String,
    val routeLabel: String,
    val totalUpload: Long = 0L,
    val totalDownload: Long = 0L,
    val lastActiveAt: Long = 0L,
) {
    val totalBytes: Long
        get() = totalUpload + totalDownload

    /**
     * 展示用节点名：直连流量（routeLabel 为 "DIRECT"，大小写不敏感）统一显示为 "DIRECT"，
     * 空标签回退到 routeKey。
     */
    val nodeName: String
        get() =
            when {
                routeLabel.equals("direct", ignoreCase = true) -> "DIRECT"
                routeLabel.isNotBlank() -> routeLabel
                else -> routeKey
            }
}

@Serializable
data class DailyAppTrafficSummary(val dateMillis: Long, val appUsages: Map<String, AppTrafficUsage> = emptyMap()) {
    val totalUpload: Long
        get() = appUsages.values.sumOf(AppTrafficUsage::totalUpload)
    val totalDownload: Long
        get() = appUsages.values.sumOf(AppTrafficUsage::totalDownload)
    val total: Long
        get() = totalUpload + totalDownload
}

@Serializable
data class ConnectionTrafficBaseline(
    val id: String,
    val upload: Long,
    val download: Long,
    val appKey: String,
    val packageName: String? = null,
    val appName: String,
)

enum class StatisticsTimeRange(val days: Int) {
    DAY(1),
    WEEK(7),
    MONTH(30),
}

data class TimeSlotTraffic(
    val slotIndex: Int,
    val totalUpload: Long,
    val totalDownload: Long,
)

data class DailyTraffic(
    val dateMillis: Long,
    val totalUpload: Long,
    val totalDownload: Long,
)

/** 单日单应用的流量明细，用于流量足迹时间线。 */
data class DailyAppBreakdown(
    val dateMillis: Long,
    val appKey: String,
    val appName: String,
    val totalUpload: Long,
    val totalDownload: Long,
    val lastActiveAt: Long,
) {
    val totalBytes: Long
        get() = totalUpload + totalDownload
}

/** 流量足迹中的一天小结。 */
data class FootprintDay(
    val dateMillis: Long,
    val totalUpload: Long,
    val totalDownload: Long,
    val topAppName: String,
    val topAppKey: String,
) {
    val total: Long
        get() = totalUpload + totalDownload
}
