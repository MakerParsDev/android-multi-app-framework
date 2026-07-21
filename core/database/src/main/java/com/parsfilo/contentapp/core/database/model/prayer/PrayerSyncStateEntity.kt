package com.parsfilo.contentapp.core.database.model.prayer

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_sync_state")
data class PrayerSyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "district_id")
    val districtId: Int,
    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long,
    @ColumnInfo(name = "coverage_start")
    val coverageStart: String,
    @ColumnInfo(name = "coverage_end")
    val coverageEnd: String,
    @ColumnInfo(name = "last_access_at")
    val lastAccessAt: Long,
)
