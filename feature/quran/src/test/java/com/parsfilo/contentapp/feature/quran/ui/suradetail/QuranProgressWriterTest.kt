package com.parsfilo.contentapp.feature.quran.ui.suradetail

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuranProgressWriterTest {
    @Test
    fun rapidViewportUpdatesConflateToLastActuallyReportedVerse() = runTest {
        val persisted = mutableListOf<Int>()
        val writer = QuranProgressWriter(backgroundScope) { persisted.add(it) }
        writer.record(3)
        writer.record(4)
        writer.record(5)
        runCurrent()
        assertEquals(listOf(5), persisted)
    }

    @Test
    fun failedRoomWriteDoesNotDisableLaterProgressUpdates() = runTest {
        val saved = mutableListOf<Int>()
        val writer = QuranProgressWriter(backgroundScope) { verse ->
            if (verse == 3) throw IllegalStateException("synthetic write failure")
            saved.add(verse)
        }
        writer.record(3)
        runCurrent()
        writer.record(4)
        runCurrent()
        assertEquals(listOf(4), saved)
    }

    @Test
    fun blockedOlderWriteCannotFinishAfterNewerWrite() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val persisted = mutableListOf<Int>()
        val writer = QuranProgressWriter(backgroundScope) { verse ->
            if (verse == 3) {
                entered.complete(Unit)
                release.await()
            }
            persisted.add(verse)
        }
        writer.record(3)
        runCurrent()
        entered.await()
        writer.record(4)
        writer.record(5)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(3, 5), persisted)
        // A real backward scroll is not discarded as though progress were monotonic.
        writer.record(2)
        runCurrent()
        assertEquals(listOf(3, 5, 2), persisted)
    }
}
