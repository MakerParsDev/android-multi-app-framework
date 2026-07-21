package com.parsfilo.contentapp.core.database.model.prayer

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "prayer_times",
    primaryKeys = ["district_id", "local_date"],
)
data class PrayerTimeEntity(
    @ColumnInfo(name = "district_id")
    val districtId: Int,
    @ColumnInfo(name = "local_date")
    val localDate: String,
    val imsak: String,
    val gunes: String,
    val ogle: String,
    val ikindi: String,
    val aksam: String,
    val yatsi: String,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
