package com.parsfilo.contentapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.parsfilo.contentapp.core.database.dao.NotificationDao
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.dao.quran.QuranDao
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirSessionDao
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirStreakDao
import com.parsfilo.contentapp.core.database.model.NotificationEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCityEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCountryEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerDistrictEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerSyncStateEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranAudioCacheEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranAyahEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranBookmarkEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranLastReadEntity
import com.parsfilo.contentapp.core.database.model.quran.QuranSuraEntity
import com.parsfilo.contentapp.core.database.model.zikir.ZikirSessionEntity
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity

@Database(
    entities = [
        NotificationEntity::class,
        PrayerCountryEntity::class,
        PrayerCityEntity::class,
        PrayerDistrictEntity::class,
        PrayerTimeEntity::class,
        PrayerSyncStateEntity::class,
        ZikirSessionEntity::class,
        ZikirStreakEntity::class,
        QuranSuraEntity::class,
        QuranAyahEntity::class,
        QuranAudioCacheEntity::class,
        QuranBookmarkEntity::class,
        QuranLastReadEntity::class,
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    abstract fun prayerTimesDao(): PrayerTimesDao

    abstract fun zikirSessionDao(): ZikirSessionDao

    abstract fun zikirStreakDao(): ZikirStreakDao

    abstract fun quranDao(): QuranDao

    companion object {
        const val DATABASE_NAME = "app-database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_country` (" +
                        "`country_id` INTEGER NOT NULL, " +
                        "`name_tr` TEXT NOT NULL, " +
                        "`name_en` TEXT NOT NULL, " +
                        "`fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`country_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_city` (" +
                        "`city_id` INTEGER NOT NULL, " +
                        "`country_id` INTEGER NOT NULL, " +
                        "`name_tr` TEXT NOT NULL, " +
                        "`name_en` TEXT NOT NULL, " +
                        "`fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`city_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_prayer_city_country_id` " +
                        "ON `prayer_city` (`country_id`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_district` (" +
                        "`district_id` INTEGER NOT NULL, " +
                        "`city_id` INTEGER NOT NULL, " +
                        "`name_tr` TEXT NOT NULL, " +
                        "`name_en` TEXT NOT NULL, " +
                        "`fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`district_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_prayer_district_city_id` " +
                        "ON `prayer_district` (`city_id`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_times` (" +
                        "`district_id` INTEGER NOT NULL, " +
                        "`local_date` TEXT NOT NULL, " +
                        "`imsak` TEXT NOT NULL, " +
                        "`gunes` TEXT NOT NULL, " +
                        "`ogle` TEXT NOT NULL, " +
                        "`ikindi` TEXT NOT NULL, " +
                        "`aksam` TEXT NOT NULL, " +
                        "`yatsi` TEXT NOT NULL, " +
                        "`fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`district_id`, `local_date`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_sync_state` (" +
                        "`district_id` INTEGER NOT NULL, " +
                        "`last_sync_at` INTEGER NOT NULL, " +
                        "`coverage_start` TEXT NOT NULL, " +
                        "`coverage_end` TEXT NOT NULL, " +
                        "`last_access_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`district_id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zikir_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`zikirKey` TEXT NOT NULL, " +
                        "`arabicText` TEXT NOT NULL, " +
                        "`latinText` TEXT NOT NULL, " +
                        "`targetCount` INTEGER NOT NULL, " +
                        "`completedCount` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`isComplete` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_zikir_sessions_completedAt` " +
                        "ON `zikir_sessions` (`completedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_zikir_sessions_zikirKey_completedAt` " +
                        "ON `zikir_sessions` (`zikirKey`, `completedAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zikir_streak` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`currentStreak` INTEGER NOT NULL, " +
                        "`longestStreak` INTEGER NOT NULL, " +
                        "`lastActivityDate` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_suras` (" +
                        "`number` INTEGER NOT NULL, " +
                        "`nameArabic` TEXT NOT NULL, " +
                        "`nameLatin` TEXT NOT NULL, " +
                        "`nameTurkish` TEXT NOT NULL, " +
                        "`nameEnglish` TEXT NOT NULL, " +
                        "`revelationType` TEXT NOT NULL, " +
                        "`ayahCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`number`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_suras_nameLatin` ON `quran_suras` (`nameLatin`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_suras_nameTurkish` ON `quran_suras` (`nameTurkish`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_ayahs` (" +
                        "`suraNumber` INTEGER NOT NULL, " +
                        "`ayahNumber` INTEGER NOT NULL, " +
                        "`arabic` TEXT NOT NULL, " +
                        "`latin` TEXT NOT NULL, " +
                        "`turkish` TEXT NOT NULL, " +
                        "`english` TEXT NOT NULL, " +
                        "`german` TEXT NOT NULL, " +
                        "PRIMARY KEY(`suraNumber`, `ayahNumber`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_ayahs_suraNumber` ON `quran_ayahs` (`suraNumber`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_audio_cache` (" +
                        "`reciterId` TEXT NOT NULL, " +
                        "`suraNumber` INTEGER NOT NULL, " +
                        "`ayahNumber` INTEGER NOT NULL, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`downloadedAt` INTEGER NOT NULL, " +
                        "`fileSize` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`reciterId`, `suraNumber`, `ayahNumber`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_audio_cache_reciterId_suraNumber` ON `quran_audio_cache` (`reciterId`, `suraNumber`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_bookmarks` (" +
                        "`suraNumber` INTEGER NOT NULL, " +
                        "`ayahNumber` INTEGER NOT NULL, " +
                        "`savedAt` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "PRIMARY KEY(`suraNumber`, `ayahNumber`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_last_read` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`suraNumber` INTEGER NOT NULL, " +
                        "`ayahNumber` INTEGER NOT NULL, " +
                        "`savedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Repair legacy v4 installs that may contain incompatible bookmark schema/index.
                val hasOldBookmarksTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='quran_bookmarks'"
                ).use { cursor -> cursor.moveToFirst() }

                db.execSQL("DROP INDEX IF EXISTS `index_quran_bookmarks_savedAt`")
                db.execSQL("DROP TABLE IF EXISTS `quran_bookmarks_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_bookmarks_new` (" +
                        "`suraNumber` INTEGER NOT NULL, " +
                        "`ayahNumber` INTEGER NOT NULL, " +
                        "`savedAt` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "PRIMARY KEY(`suraNumber`, `ayahNumber`))"
                )

                if (hasOldBookmarksTable) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO `quran_bookmarks_new` (`suraNumber`, `ayahNumber`, `savedAt`, `note`) " +
                            "SELECT `suraNumber`, `ayahNumber`, " +
                            "COALESCE(`savedAt`, CAST(strftime('%s','now') AS INTEGER) * 1000), " +
                            "COALESCE(`note`, '') " +
                            "FROM `quran_bookmarks`"
                    )
                    db.execSQL("DROP TABLE `quran_bookmarks`")
                }

                db.execSQL("ALTER TABLE `quran_bookmarks_new` RENAME TO `quran_bookmarks`")
            }
        }
    }
}
