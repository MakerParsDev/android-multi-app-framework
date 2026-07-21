package com.parsfilo.contentapp.feature.prayertimes.di

import com.parsfilo.contentapp.feature.prayertimes.data.DefaultPrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrayerTimesModule {
    @Provides
    @Singleton
    fun providePrayerTimesRepository(impl: DefaultPrayerTimesRepository): PrayerTimesRepository =
        impl
}
