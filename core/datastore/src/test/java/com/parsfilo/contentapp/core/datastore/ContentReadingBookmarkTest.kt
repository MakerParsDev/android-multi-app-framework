package com.parsfilo.contentapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ContentReadingBookmarkTest {
    @Test
    fun explicitSavePersistsByCanonicalIdWithoutChangingOtherSettings() = runBlocking {
        val store = FakeStore()
        val preferences = PreferencesDataSource(store)
        preferences.setDisplayMode("LATIN")
        preferences.setPremium(true)

        assertThat(preferences.contentSavedVerseId.first()).isNull()
        preferences.setContentSavedVerseId(96)
        assertThat(preferences.contentSavedVerseId.first()).isEqualTo(96)

        // New repository instance reads the same persisted app preference.
        val restarted = PreferencesDataSource(store)
        assertThat(restarted.contentSavedVerseId.first()).isEqualTo(96)
        assertThat(restarted.userData.first().displayMode).isEqualTo("LATIN")
        assertThat(restarted.userData.first().isPremium).isTrue()
    }

    @Test
    fun clearingDoesNotResetUnrelatedSettingsOrOtherApplicationStore() = runBlocking {
        val firstApp = PreferencesDataSource(FakeStore())
        val secondApp = PreferencesDataSource(FakeStore())
        firstApp.setContentSavedVerseId(40)
        secondApp.setContentSavedVerseId(29)
        firstApp.setDisplayMode("TURKISH")

        firstApp.setContentSavedVerseId(null)
        assertThat(firstApp.contentSavedVerseId.first()).isNull()
        assertThat(secondApp.contentSavedVerseId.first()).isEqualTo(29)
        assertThat(firstApp.userData.first().displayMode).isEqualTo("TURKISH")
        firstApp.setContentSavedVerseId(-1)
        assertThat(firstApp.contentSavedVerseId.first()).isNull()
    }

    private class FakeStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(data.value)
            data.value = next
            return next
        }
    }
}
