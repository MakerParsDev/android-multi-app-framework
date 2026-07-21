package com.parsfilo.contentapp.feature.content.di

import com.parsfilo.contentapp.feature.content.data.AssetPrayerRepository
import com.parsfilo.contentapp.feature.content.data.PrayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PrayerModule {
    @Binds
    abstract fun bindPrayerRepository(
        repository: AssetPrayerRepository
    ): PrayerRepository
}
