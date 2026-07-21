package com.parsfilo.contentapp.core.common.broadcast

import android.content.Intent
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System actions that are allowed to trigger alarm rescheduling.
 *
 * Receivers must reject every other action before calling goAsync() so an
 * explicit intent from another app cannot create background work.
 */
object SystemBroadcastRescheduleActions {
    private val supportedActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
    )

    fun supports(action: String?): Boolean = action in supportedActions
}

/**
 * Coalesces duplicate system broadcasts while a reschedule is already active.
 * Alarm and WorkManager scheduling remain idempotent, and a broadcast storm
 * cannot create parallel reads or scheduling side effects.
 */
@Singleton
class SystemBroadcastRescheduleGate @Inject constructor() {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun runIfIdle(key: String, block: suspend () -> Unit): Boolean {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        if (!mutex.tryLock()) return false
        return try {
            block()
            true
        } finally {
            mutex.unlock()
        }
    }
}
