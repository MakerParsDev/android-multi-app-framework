package com.parsfilo.contentapp.core.firebase.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.parsfilo.contentapp.core.firebase.FirebaseRuntimeObservability
import com.parsfilo.contentapp.core.firebase.RuntimeObservability
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RuntimeObservabilityModule {
    @Provides
    @Singleton
    fun provideRuntimeObservability(
        crashlytics: FirebaseCrashlytics,
    ): RuntimeObservability = FirebaseRuntimeObservability(crashlytics)
}
