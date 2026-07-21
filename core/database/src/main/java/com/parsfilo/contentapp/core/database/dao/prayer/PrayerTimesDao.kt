package com.parsfilo.contentapp.core.database.dao.prayer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCityEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCountryEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerDistrictEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerSyncStateEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerTimesDao {
    @Query(
        "SELECT * FROM prayer_times WHERE district_id = :districtId " +
            "AND local_date BETWEEN :fromDate AND :toDate ORDER BY local_date ASC"
    )
    fun observePrayerTimes(
        districtId: Int,
        fromDate: String,
        toDate: String,
    ): Flow<List<PrayerTimeEntity>>

    @Query(
        "SELECT * FROM prayer_times WHERE district_id = :districtId " +
            "AND local_date BETWEEN :fromDate AND :toDate ORDER BY local_date ASC"
    )
    suspend fun getPrayerTimes(
        districtId: Int,
        fromDate: String,
        toDate: String,
    ): List<PrayerTimeEntity>

    @Query(
        "SELECT COUNT(*) FROM prayer_times WHERE district_id = :districtId " +
            "AND local_date = :date"
    )
    suspend fun countByDistrictAndDate(
        districtId: Int,
        date: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrayerTimes(items: List<PrayerTimeEntity>)

    @Query(
        "DELETE FROM prayer_times WHERE district_id = :districtId " +
            "AND (local_date < :minDate OR local_date > :maxDate)"
    )
    suspend fun deletePrayerTimesOutsideRange(
        districtId: Int,
        minDate: String,
        maxDate: String,
    )

    @Query("DELETE FROM prayer_times WHERE district_id = :districtId")
    suspend fun deletePrayerTimesByDistrict(districtId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: PrayerSyncStateEntity)

    @Query("SELECT * FROM prayer_sync_state WHERE district_id = :districtId LIMIT 1")
    suspend fun getSyncState(districtId: Int): PrayerSyncStateEntity?

    @Query("DELETE FROM prayer_sync_state WHERE district_id = :districtId")
    suspend fun deleteSyncStateByDistrict(districtId: Int)

    @Query("SELECT district_id FROM prayer_sync_state ORDER BY last_access_at DESC")
    suspend fun getDistrictIdsByRecentAccess(): List<Int>

    @Query(
        "UPDATE prayer_sync_state SET last_access_at = :lastAccessAt " +
            "WHERE district_id = :districtId"
    )
    suspend fun touchDistrictAccess(
        districtId: Int,
        lastAccessAt: Long,
    )

    @Query("SELECT * FROM prayer_country ORDER BY name_tr ASC")
    suspend fun getCountries(): List<PrayerCountryEntity>

    @Query("SELECT MIN(fetched_at) FROM prayer_country")
    suspend fun getMinCountryFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCountries(items: List<PrayerCountryEntity>)

    @Query("DELETE FROM prayer_country")
    suspend fun deleteCountries()

    @Query("SELECT * FROM prayer_city WHERE country_id = :countryId ORDER BY name_tr ASC")
    suspend fun getCities(countryId: Int): List<PrayerCityEntity>

    @Query("SELECT MIN(fetched_at) FROM prayer_city WHERE country_id = :countryId")
    suspend fun getMinCityFetchedAt(countryId: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCities(items: List<PrayerCityEntity>)

    @Query("DELETE FROM prayer_city WHERE country_id = :countryId")
    suspend fun deleteCitiesByCountry(countryId: Int)

    @Query("SELECT * FROM prayer_district WHERE city_id = :cityId ORDER BY name_tr ASC")
    suspend fun getDistricts(cityId: Int): List<PrayerDistrictEntity>

    @Query("SELECT MIN(fetched_at) FROM prayer_district WHERE city_id = :cityId")
    suspend fun getMinDistrictFetchedAt(cityId: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDistricts(items: List<PrayerDistrictEntity>)

    @Query("DELETE FROM prayer_district WHERE city_id = :cityId")
    suspend fun deleteDistrictsByCity(cityId: Int)

    @Query("SELECT * FROM prayer_district WHERE district_id = :districtId LIMIT 1")
    suspend fun getDistrictById(districtId: Int): PrayerDistrictEntity?

    @Query("SELECT * FROM prayer_city WHERE city_id = :cityId LIMIT 1")
    suspend fun getCityById(cityId: Int): PrayerCityEntity?

    @Query("SELECT * FROM prayer_country WHERE country_id = :countryId LIMIT 1")
    suspend fun getCountryById(countryId: Int): PrayerCountryEntity?
}
