package com.parsfilo.contentapp.feature.quran.ui.suradetail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Serialize Room marker writes in observation order. Conflate rapid viewport
 * changes so an older, slower IO write cannot finish after a newer marker.
 * A deliberate backward swipe remains valid: this is order, not max(ayah).
 */
internal class QuranProgressWriter(
    scope: CoroutineScope,
    private val save: suspend (Int) -> Unit,
) {
    private val pendingAyah = Channel<Int>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ayahNumber in pendingAyah) {
                try {
                    save(ayahNumber)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A transient Room failure must not kill every subsequent
                    // progress write or crash the host Activity.
                    Timber.e(error, "Failed to save Quran last-read ayah %d", ayahNumber)
                }
            }
        }
    }

    fun record(ayahNumber: Int) {
        pendingAyah.trySend(ayahNumber)
    }
}
