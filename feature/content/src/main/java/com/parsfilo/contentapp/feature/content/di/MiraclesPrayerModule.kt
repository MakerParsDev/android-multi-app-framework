package com.parsfilo.contentapp.feature.content.di

import com.parsfilo.contentapp.feature.content.data.AssetMiraclesPrayerRepository
import com.parsfilo.contentapp.feature.content.data.MiraclesPrayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MiraclesPrayerModule {
    @Binds
    abstract fun bindMiraclesPrayerRepository(
        repository: AssetMiraclesPrayerRepository
    ): MiraclesPrayerRepository
}
