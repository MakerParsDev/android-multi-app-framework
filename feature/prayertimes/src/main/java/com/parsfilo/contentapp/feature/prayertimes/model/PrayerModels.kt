package com.parsfilo.contentapp.feature.prayertimes.model

data class PrayerTimesDay(
    val localDate: String,
    val imsak: String,
    val gunes: String,
    val ogle: String,
    val ikindi: String,
    val aksam: String,
    val yatsi: String,
)

data class PrayerLocationSelection(
    val countryId: Int,
    val cityId: Int,
    val districtId: Int,
    val displayName: String,
)

data class PrayerLocationSuggestion(
    val countryId: Int,
    val cityId: Int?,
)

data class PrayerCountry(
    val id: Int,
    val nameTr: String,
    val nameEn: String,
)

data class PrayerCity(
    val id: Int,
    val countryId: Int,
    val nameTr: String,
    val nameEn: String,
)

data class PrayerDistrict(
    val id: Int,
    val cityId: Int,
    val nameTr: String,
    val nameEn: String,
)
