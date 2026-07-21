package com.parsfilo.contentapp.core.database.model.prayer

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prayer_district",
    indices = [Index(value = ["city_id"])],
)
data class PrayerDistrictEntity(
    @PrimaryKey
    @ColumnInfo(name = "district_id")
    val districtId: Int,
    @ColumnInfo(name = "city_id")
    val cityId: Int,
    @ColumnInfo(name = "name_tr")
    val nameTr: String,
    @ColumnInfo(name = "name_en")
    val nameEn: String,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
