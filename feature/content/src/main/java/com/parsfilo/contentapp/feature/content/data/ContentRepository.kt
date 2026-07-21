package com.parsfilo.contentapp.feature.content.data

import android.content.Context
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.model.Verse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

interface ContentRepository {
    suspend fun getVerses(): Result<List<Verse>>
}

class AssetContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : ContentRepository {

    // Cache: JSON içerik değişmediği için parse sonucunu bellekte tutuyoruz
    @Volatile
    private var cachedVerses: List<Verse>? = null
    private val cacheMutex = Mutex()

    override suspend fun getVerses(): Result<List<Verse>> {
        cachedVerses?.let { return Result.Success(it) }

        return withContext(ioDispatcher) {
            cacheMutex.withLock {
                // Double-check after acquiring lock
                cachedVerses?.let { return@withContext Result.Success(it) }

                try {
                    val jsonString = context.assets.open("data.json").bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val verses = mutableListOf<Verse>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        verses.add(
                            Verse(
                                id = obj.getInt("ayetID"),
                                arabic = obj.getString("ayetAR"),
                                latin = obj.getString("ayetLAT"),
                                turkish = obj.getString("ayetTR"),
                                english = obj.optString("ayetEN", ""),
                                german = obj.optString("ayetDE", ""),
                            )
                        )
                    }
                    cachedVerses = verses
                    Result.Success(verses)
                } catch (e: IOException) {
                    Timber.e(e, "data.json read error")
                    Result.Error(e)
                } catch (e: JSONException) {
                    Timber.e(e, "data.json parse hatası")
                    Result.Error(e)
                }
            }
        }
    }
}
