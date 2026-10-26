package com.parsfilo.contentapp.feature.quran.ui.suradetail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
                save(ayahNumber)
            }
        }
    }

    fun record(ayahNumber: Int) {
        pendingAyah.trySend(ayahNumber)
    }
}
