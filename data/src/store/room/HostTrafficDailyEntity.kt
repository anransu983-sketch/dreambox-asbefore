package com.suanran.dreambox.data.store.room

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/** 按目标域名/IP 的日流量（与 app/route 同构：天桶 + 小时槽）。 */
@Entity(
    tableName = "host_traffic_daily",
    primaryKeys = ["date_millis", "host_key", "slot_index"],
)
data class HostTrafficDailyEntity(
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    @ColumnInfo(name = "host_key") val hostKey: String,
    @ColumnInfo(name = "slot_index") val slotIndex: Int = 0,
    @ColumnInfo(name = "total_upload") val totalUpload: Long = 0L,
    @ColumnInfo(name = "total_download") val totalDownload: Long = 0L,
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long = 0L,
)
