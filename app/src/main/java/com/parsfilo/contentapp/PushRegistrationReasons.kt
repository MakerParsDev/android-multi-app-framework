package com.parsfilo.contentapp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Perform one initial sync per Activity and then only when notification
 * preferences change. Re-subscribing to STARTED lifecycle must not
 * create duplicate network requests or fabricated release-health attempts.
 */
internal fun Flow<Boolean>.pushRegistrationReasons(): Flow<String> =
    flow {
        var firstEmission = true
        this@pushRegistrationReasons.distinctUntilChanged().collect {
            emit(if (firstEmission) "app_start" else "notification_setting_changed")
            firstEmission = false
        }
    }
