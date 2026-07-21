package com.parsfilo.contentapp.core.database.dao.prayer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.database.model.prayer.PrayerSyncStateEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrayerTimesDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PrayerTimesDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.prayerTimesDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observePrayerTimes returns only selected range ordered by date`() = runBlocking {
        dao.upsertPrayerTimes(
            listOf(
                testTime(date = "2026-02-20"),
                testTime(date = "2026-02-18"),
                testTime(date = "2026-02-19"),
            )
        )

        val result = dao.observePrayerTimes(
            districtId = DISTRICT_ID,
            fromDate = "2026-02-18",
            toDate = "2026-02-19",
        ).first()

        assertThat(result.map { it.localDate })
            .containsExactly("2026-02-18", "2026-02-19")
            .inOrder()
    }

    @Test
    fun `deletePrayerTimesOutsideRange prunes old and far-future rows`() = runBlocking {
        dao.upsertPrayerTimes(
            listOf(
                testTime(date = "2026-02-15"),
                testTime(date = "2026-02-18"),
                testTime(date = "2026-03-10"),
            )
        )

        dao.deletePrayerTimesOutsideRange(
            districtId = DISTRICT_ID,
            minDate = "2026-02-18",
            maxDate = "2026-02-28",
        )

        assertThat(dao.countByDistrictAndDate(DISTRICT_ID, "2026-02-15")).isEqualTo(0)
        assertThat(dao.countByDistrictAndDate(DISTRICT_ID, "2026-02-18")).isEqualTo(1)
        assertThat(dao.countByDistrictAndDate(DISTRICT_ID, "2026-03-10")).isEqualTo(0)
    }

    @Test
    fun `touchDistrictAccess updates LRU ordering`() = runBlocking {
        dao.upsertSyncState(
            PrayerSyncStateEntity(
                districtId = 1,
                lastSyncAt = 100L,
                coverageStart = "2026-02-01",
                coverageEnd = "2026-03-01",
                lastAccessAt = 100L,
            )
        )
        dao.upsertSyncState(
            PrayerSyncStateEntity(
                districtId = 2,
                lastSyncAt = 200L,
                coverageStart = "2026-02-01",
                coverageEnd = "2026-03-01",
                lastAccessAt = 200L,
            )
        )
        dao.upsertSyncState(
            PrayerSyncStateEntity(
                districtId = 3,
                lastSyncAt = 150L,
                coverageStart = "2026-02-01",
                coverageEnd = "2026-03-01",
                lastAccessAt = 150L,
            )
        )

        assertThat(dao.getDistrictIdsByRecentAccess()).containsExactly(2, 3, 1).inOrder()

        dao.touchDistrictAccess(districtId = 1, lastAccessAt = 300L)

        assertThat(dao.getDistrictIdsByRecentAccess()).containsExactly(1, 2, 3).inOrder()
    }

    private fun testTime(date: String): PrayerTimeEntity {
        return PrayerTimeEntity(
            districtId = DISTRICT_ID,
            localDate = date,
            imsak = "05:30",
            gunes = "07:00",
            ogle = "13:00",
            ikindi = "16:00",
            aksam = "18:00",
            yatsi = "19:30",
            fetchedAt = 1_000L,
        )
    }

    private companion object {
        private const val DISTRICT_ID = 34
    }
}
