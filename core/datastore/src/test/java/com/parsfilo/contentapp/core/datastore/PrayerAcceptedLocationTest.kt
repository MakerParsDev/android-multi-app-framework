package com.parsfilo.contentapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PrayerAcceptedLocationTest {
    @Test
    fun acceptedGpsLocationIsOneAtomicPreferencesTransaction() {
        runBlocking {
            val store = FakePreferenceStore()
            val repository = PrayerPreferencesDataSource(store)
            repository.setMode(PrayerTimesMode.MANUAL)
            repository.setManualSelection(countryId = 90, cityId = 35, districtId = 3502)
            val before = repository.preferences.first()
            assertThat(before.mode).isEqualTo(PrayerTimesMode.MANUAL.name)
            assertThat(before.selectedDistrictId).isEqualTo(3502)

            val editCount = store.writeCount
            repository.setAcceptedAutoSelection(countryId = 90, cityId = 35, districtId = 3501)
            assertThat(store.writeCount).isEqualTo(editCount + 1)

            val accepted = repository.preferences.first()
            assertThat(accepted.mode).isEqualTo(PrayerTimesMode.AUTO.name)
            assertThat(accepted.selectedCountryId).isEqualTo(90)
            assertThat(accepted.selectedCityId).isEqualTo(35)
            assertThat(accepted.selectedDistrictId).isEqualTo(3501)
            assertThat(accepted.lastAutoDistrictId).isEqualTo(3501)
            assertThat(accepted.alarmEnabled).isEqualTo(before.alarmEnabled)
            assertThat(accepted.selectedAlarmPrayerKeys)
                .containsExactlyElementsIn(before.selectedAlarmPrayerKeys)
        }
    }

    private class FakePreferenceStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())
        var writeCount = 0
            private set

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(data.value)
            data.value = next
            writeCount++
            return next
        }
    }
}
