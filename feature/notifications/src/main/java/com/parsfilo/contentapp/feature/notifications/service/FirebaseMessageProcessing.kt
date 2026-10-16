package com.parsfilo.contentapp.feature.notifications.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// onMessageReceived runs on a Firebase background thread and has a limited time
// budget. Await short local work so service destruction cannot cancel it.
internal fun awaitFirebaseMessageProcessing(
    timeoutMs: Long = 10_000L,
    block: suspend () -> Unit,
) {
    runBlocking {
        withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) { block() }
        }
    }
}
