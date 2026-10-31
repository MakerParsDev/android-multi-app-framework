package com.parsfilo.contentapp.feature.content.data

import android.content.Context
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.model.MiraclesOriginalVerse
import com.parsfilo.contentapp.core.model.MiraclesOriginalSupplication
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

interface MiraclesPrayerRepository {
    suspend fun getPrayers(): List<MiraclesPrayer>

    suspend fun getPrayerByIndex(index: Int): MiraclesPrayer?
}

class AssetMiraclesPrayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : MiraclesPrayerRepository {

    override suspend fun getPrayers(): List<MiraclesPrayer> = withContext(ioDispatcher) {
        try {
            val jsonString = context.assets.open("data.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val verifiedArabic = loadVerifiedArabic()
            val verifiedHadith = loadVerifiedHadith()
            val prayers = mutableListOf<MiraclesPrayer>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                prayers.add(
                    MiraclesPrayer(
                        duaIsim = obj.getString("duaIsim"),
                        duaAciklama = obj.getString("duaAciklama"),
                        duaBesmele = obj.getString("duaBesmele"),
                        duaArapca = obj.getString("duaArapca"),
                        duaLatinOkunus = obj.optString("duaLatinOkunus", obj.optString("duaBesmele", "")),
                        originalArabicVerses = verifiedArabic[obj.getString("duaIsim")].orEmpty(),
                        originalArabicSupplication = verifiedHadith[obj.getString("duaIsim")],
                    )
                )
            }
            prayers
        } catch (e: IOException) {
            Timber.e(e, "data.json (miracles) read error")
            emptyList()
        } catch (e: JSONException) {
            Timber.e(e, "data.json (miracles) parse hatası")
            emptyList()
        }
    }

    // Optional: only Mucize Dualar bundles these unchanged licensed source verses.
    // Other flavors continue working with the original legacy data.json alone.
    private fun loadVerifiedArabic(): Map<String, List<MiraclesOriginalVerse>> {
        val raw = try {
            context.assets.open("verified_quran_verses.json")
                .bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return emptyMap()
        } catch (error: IOException) {
            Timber.e(error, "Verified Quran verses could not be read")
            return emptyMap()
        }

        return try {
            val records = JSONObject(raw).getJSONArray("prayers")
            buildMap {
                for (i in 0 until records.length()) {
                    val record = records.getJSONObject(i)
                    val verses = record.getJSONArray("verses")
                    val items = buildList {
                        for (j in 0 until verses.length()) {
                            val verse = verses.getJSONObject(j)
                            add(
                                MiraclesOriginalVerse(
                                    reference = verse.getString("reference"),
                                    arabic = verse.getString("arabic"),
                                )
                            )
                        }
                    }
                    put(record.getString("title"), items)
                }
            }
        } catch (error: JSONException) {
            Timber.e(error, "Verified Quran verse index is invalid")
            emptyMap()
        }
    }

    // Optional separate sourced hadith extracts; never inherit Quran attribution.
    private fun loadVerifiedHadith(): Map<String, MiraclesOriginalSupplication> {
        val raw = try {
            context.assets.open("verified_hadith_supplications.json")
                .bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return emptyMap()
        } catch (error: IOException) {
            Timber.e(error, "Verified hadith supplications could not be read")
            return emptyMap()
        }

        return try {
            val records = JSONObject(raw).getJSONArray("prayers")
            buildMap {
                for (i in 0 until records.length()) {
                    val entry = records.getJSONObject(i)
                    put(
                        entry.getString("title"),
                        MiraclesOriginalSupplication(
                            reference = entry.getString("reference"),
                            sourceUrl = entry.getString("sourceUrl"),
                            arabic = entry.getString("arabic"),
                        ),
                    )
                }
            }
        } catch (error: JSONException) {
            Timber.e(error, "Verified hadith source index is invalid")
            emptyMap()
        }
    }

    override suspend fun getPrayerByIndex(index: Int): MiraclesPrayer? = withContext(ioDispatcher) {
        getPrayers().getOrNull(index)
    }
}
