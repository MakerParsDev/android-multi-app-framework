package com.parsfilo.contentapp.core.database.di

import android.content.Context
import androidx.room.Room
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.database.dao.NotificationDao
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.dao.quran.QuranDao
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirSessionDao
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirStreakDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
        )
        .build()

    @Provides
    fun providesNotificationDao(
        database: AppDatabase,
    ): NotificationDao = database.notificationDao()

    @Provides
    fun providesPrayerTimesDao(
        database: AppDatabase,
    ): PrayerTimesDao = database.prayerTimesDao()

    @Provides
    fun providesZikirSessionDao(
        database: AppDatabase,
    ): ZikirSessionDao = database.zikirSessionDao()

    @Provides
    fun providesZikirStreakDao(
        database: AppDatabase,
    ): ZikirStreakDao = database.zikirStreakDao()

    @Provides
    fun providesQuranDao(
        database: AppDatabase,
    ): QuranDao = database.quranDao()
}
