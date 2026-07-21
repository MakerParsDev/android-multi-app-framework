package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.appset.AppSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentSyncIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedSyncId: String? = null

    @Volatile
    private var fetchAttempted = false

    suspend fun getConsentSyncIdOrNull(): String? {
        cachedSyncId?.let { return it }
        if (fetchAttempted) return null

        return mutex.withLock {
            cachedSyncId?.let { return it }
            if (fetchAttempted) return null

            fetchAttempted = true
            runCatching {
                val info = AppSet.getClient(context).appSetIdInfo.await()
                info.id.trim()
            }
                .onSuccess { id ->
                    if (ConsentSyncIdValidator.isValid(id)) {
                        cachedSyncId = id
                        Timber.d("Consent sync ID available via App Set ID")
                    } else if (id.isBlank()) {
                        Timber.w("App Set ID returned blank consent sync id")
                    } else {
                        Timber.w(
                            "App Set ID did not meet consent sync ID format requirements length=%d",
                            id.length,
                        )
                    }
                }
                .onFailure { error ->
                    Timber.w(error, "Failed to fetch App Set ID for consent sync")
                }

            cachedSyncId
        }
    }
}

internal object ConsentSyncIdValidator {
    private const val MIN_LENGTH = 22
    private const val MAX_LENGTH = 150
    private val allowedPunctuation = setOf('+', '.', '=', '/', '_', '-', '$', ',', '{', '}')

    fun isValid(rawId: String?): Boolean {
        val id = rawId?.trim().orEmpty()
        if (id.length !in MIN_LENGTH..MAX_LENGTH) return false
        return id.all { character ->
            character in '0'..'9' ||
                character in 'a'..'z' ||
                character in 'A'..'Z' ||
                character in allowedPunctuation
        }
    }
}
