package com.parsfilo.contentapp.core.firebase.appcheck.di

import com.parsfilo.contentapp.core.firebase.appcheck.AppCheckProviderFactoryProvider
import com.parsfilo.contentapp.core.firebase.appcheck.DefaultAppCheckProviderFactoryProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppCheckProviderFactoryModule {
    @Binds
    abstract fun bindsAppCheckProviderFactoryProvider(
        impl: DefaultAppCheckProviderFactoryProvider,
    ): AppCheckProviderFactoryProvider
}
