package com.parsfilo.contentapp.feature.content.data

import android.content.Context
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.core.model.PrayerVerse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

interface PrayerRepository {
    suspend fun getPrayers(): List<Prayer>

    suspend fun getPrayerById(id: Int): Prayer?
}

class AssetPrayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : PrayerRepository {

    override suspend fun getPrayers(): List<Prayer> = withContext(ioDispatcher) {
        try {
            val jsonString = context.assets.open("data.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val prayers = mutableListOf<Prayer>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val ayetlerArray = obj.getJSONArray("ayetler")
                val verses = mutableListOf<PrayerVerse>()

                for (j in 0 until ayetlerArray.length()) {
                    val ayetObj = ayetlerArray.getJSONObject(j)
                    verses.add(
                        PrayerVerse(
                            ayetID = ayetObj.getInt("ayetID"),
                            ayetAR = ayetObj.getString("ayetAR"),
                            ayetLAT = ayetObj.getString("ayetLAT"),
                            ayetTR = ayetObj.getString("ayetTR"),
                            ayetEN = ayetObj.optString("ayetEN", ""),
                            ayetDE = ayetObj.optString("ayetDE", ""),
                        )
                    )
                }

                prayers.add(
                    Prayer(
                        sureID = obj.getInt("sureID"),
                        sureAdiAR = obj.getString("sureAdiAR"),
                        sureAdiEN = obj.getString("sureAdiEN"),
                        sureAdiTR = obj.getString("sureAdiTR"),
                        sureAdiDE = obj.optString("sureAdiDE", ""),
                        ayetSayisi = obj.getInt("ayetSayisi"),
                        sureMedya = obj.getString("sureMedya"),
                        ayetler = verses
                    )
                )
            }
            prayers
        } catch (e: IOException) {
            Timber.e(e, "data.json (prayers) read error")
            emptyList()
        } catch (e: JSONException) {
            Timber.e(e, "data.json (prayers) parse hatası")
            emptyList()
        }
    }

    override suspend fun getPrayerById(id: Int): Prayer? = withContext(ioDispatcher) {
        getPrayers().find { it.sureID == id }
    }
}
