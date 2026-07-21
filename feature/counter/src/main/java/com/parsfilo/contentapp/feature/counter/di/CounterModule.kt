package com.parsfilo.contentapp.feature.counter.di

import android.content.Context
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.counter.alarm.ZikirReminderScheduler
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import com.parsfilo.contentapp.feature.counter.data.ZikirRepositoryImpl
import com.parsfilo.contentapp.feature.counter.sharing.ZikirShareHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CounterBindingsModule {
    @Binds
    @Singleton
    abstract fun bindZikirRepository(repository: ZikirRepositoryImpl): ZikirRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CounterModule {
    @Provides
    @Singleton
    fun provideZikirShareHelper(
        @ApplicationContext context: Context,
    ): ZikirShareHelper = ZikirShareHelper(context)

    @Provides
    @Singleton
    fun provideZikirReminderScheduler(
        @ApplicationContext context: Context,
        preferencesDataSource: PreferencesDataSource,
    ): ZikirReminderScheduler = ZikirReminderScheduler(context, preferencesDataSource)
}
