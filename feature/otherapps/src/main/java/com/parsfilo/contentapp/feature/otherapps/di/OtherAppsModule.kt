package com.parsfilo.contentapp.feature.otherapps.di

import com.parsfilo.contentapp.feature.otherapps.data.NetworkCachedOtherAppsRepository
import com.parsfilo.contentapp.feature.otherapps.data.OtherAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OtherAppsModule {
    @Binds
    abstract fun bindOtherAppsRepository(
        impl: NetworkCachedOtherAppsRepository,
    ): OtherAppsRepository
}
