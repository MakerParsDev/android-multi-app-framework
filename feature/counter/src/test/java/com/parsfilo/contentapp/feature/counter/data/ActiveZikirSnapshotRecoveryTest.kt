package com.parsfilo.contentapp.feature.counter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.ActiveZikirSessionSnapshot
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActiveZikirSnapshotRecoveryTest {
    private val sample = ActiveZikirSessionSnapshot(
        zikirKey = "subhanallah",
        sessionId = 27381L,
        count = 17,
        target = 33,
        startedAt = 100_000L,
        updatedAt = 110_000L,
    )

    @Test
    fun partialProgressSurvivesDataStoreCloseAndDiskReopen() = runBlocking {
        val dir = Files.createTempDirectory("amf486-recovery-").toFile()
        val file = dir.resolve("temporary-active-count.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var prefs = PreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
            prefs.setActiveZikirSessionSnapshot(sample)
            assertThat(file.exists()).isTrue()
            assertThat(file.length()).isGreaterThan(0L)

            scope.cancel()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            prefs = PreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
            assertThat(prefs.activeZikirSessionSnapshot.first()).isEqualTo(sample)
            prefs.clearActiveZikirSessionIfId(sample.sessionId)
            assertThat(prefs.activeZikirSessionSnapshot.first()).isNull()
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun staleCompletionCleanupDoesNotEraseNewSession() = runBlocking {
        val store = FakePreferenceStore()
        val prefs = PreferencesDataSource(store)
        val newer = sample.copy(sessionId = 93640L, count = 8, updatedAt = 112_000L)
        prefs.setActiveZikirSessionSnapshot(sample)
        prefs.setActiveZikirSessionSnapshot(newer)
        prefs.clearActiveZikirSessionIfId(sample.sessionId)
        assertThat(prefs.activeZikirSessionSnapshot.first()).isEqualTo(newer)
        prefs.clearActiveZikirSessionIfId(newer.sessionId)
        assertThat(prefs.activeZikirSessionSnapshot.first()).isNull()
    }

    @Test
    fun malformedAndUnknownVersionSnapshotsAreRejectedSafely() {
        assertThat(ActiveZikirSessionSnapshot.fromJson("{ broken json")).isNull()
        assertThat(ActiveZikirSessionSnapshot.fromJson(sample.toJson().replace("\"version\":1", "\"version\":99"))).isNull()
        assertThat(ActiveZikirSessionSnapshot.fromJson(sample.copy(count = -1).toJson())).isNull()
    }

    private class FakePreferenceStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val result = transform(data.value)
            data.value = result
            return result
        }
    }
}
