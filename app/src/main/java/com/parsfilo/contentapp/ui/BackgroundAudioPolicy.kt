package com.parsfilo.contentapp.ui

import com.parsfilo.contentapp.product.AppProductDefinition
import com.parsfilo.contentapp.product.ContentFamily

/**
 * Preserve a user-started Media3 session when an audio app leaves the foreground.
 * The MediaSessionService owns playback and its notification; only utility flavors
 * without an audio feature should retain the former stop-and-reset policy.
 *
 * Quran streams recitation on demand, so it must remain enabled even though it
 * does not define a single bundled audioFileName.
 */
internal fun shouldStopPlaybackOnAppBackground(
    product: AppProductDefinition,
    isChangingConfigurations: Boolean,
): Boolean =
    !isChangingConfigurations &&
        product.audioFileName.isNullOrBlank() &&
        product.contentFamily != ContentFamily.QURAN
