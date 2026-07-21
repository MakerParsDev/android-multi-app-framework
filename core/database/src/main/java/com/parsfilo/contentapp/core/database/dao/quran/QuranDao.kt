package com.parsfilo.contentapp.core.database.dao.quran

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.parsfilo.contentapp.core.database.model.quran.QuranAudioCacheEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranAyahEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranBookmarkEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranLastReadEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranSuraEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranDao {

    @Query("SELECT * FROM quran_suras ORDER BY number ASC")
    fun observeAllSuras(): Flow<List<QuranSuraEntity>>

    @Query("SELECT * FROM quran_suras WHERE number = :suraNumber")
    suspend fun getSura(suraNumber: Int): QuranSuraEntity?

    @Query("SELECT * FROM quran_suras WHERE number = :suraNumber")
    fun observeSura(suraNumber: Int): Flow<QuranSuraEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuras(suras: List<QuranSuraEntity>)

    @Query("SELECT COUNT(*) FROM quran_suras")
    suspend fun getSuraCount(): Int

    @Query("SELECT * FROM quran_ayahs WHERE suraNumber = :suraNumber ORDER BY ayahNumber ASC")
    fun observeAyahsForSura(suraNumber: Int): Flow<List<QuranAyahEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahs(ayahs: List<QuranAyahEntity>)

    @Query("DELETE FROM quran_ayahs WHERE suraNumber = :suraNumber")
    suspend fun deleteAyahsForSura(suraNumber: Int)

    @Transaction
    suspend fun replaceAyahsForSura(suraNumber: Int, ayahs: List<QuranAyahEntity>) {
        deleteAyahsForSura(suraNumber)
        if (ayahs.isNotEmpty()) {
            insertAyahs(ayahs)
        }
    }

    @Query("SELECT COUNT(*) FROM quran_ayahs WHERE suraNumber = :suraNumber")
    suspend fun getAyahCountForSura(suraNumber: Int): Int

    @Query("SELECT * FROM quran_audio_cache WHERE reciterId = :reciterId AND suraNumber = :sura AND ayahNumber = :ayah")
    suspend fun getCachedAudio(reciterId: String, sura: Int, ayah: Int): QuranAudioCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedAudio(entity: QuranAudioCacheEntity)

    @Query("SELECT * FROM quran_audio_cache WHERE reciterId = :reciterId AND suraNumber = :sura")
    suspend fun getCachedAudiosForSura(reciterId: String, sura: Int): List<QuranAudioCacheEntity>

    @Query("DELETE FROM quran_audio_cache WHERE reciterId = :reciterId AND suraNumber = :sura AND ayahNumber = :ayah")
    suspend fun deleteCachedAudio(reciterId: String, sura: Int, ayah: Int)

    @Query("SELECT * FROM quran_bookmarks ORDER BY savedAt DESC")
    fun observeBookmarks(): Flow<List<QuranBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(entity: QuranBookmarkEntity)

    @Query("DELETE FROM quran_bookmarks WHERE suraNumber = :sura AND ayahNumber = :ayah")
    suspend fun deleteBookmark(sura: Int, ayah: Int)

    @Query("SELECT COUNT(*) FROM quran_bookmarks WHERE suraNumber = :sura AND ayahNumber = :ayah")
    suspend fun isBookmarked(sura: Int, ayah: Int): Int

    @Query("SELECT * FROM quran_last_read WHERE id = 1")
    fun observeLastRead(): Flow<QuranLastReadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLastRead(entity: QuranLastReadEntity)
}
