package com.parsfilo.contentapp.feature.quran.di

import com.parsfilo.contentapp.feature.quran.config.QuranApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QuranModule {

    @Provides
    @Named("defaultReciterId")
    fun provideDefaultReciterId(): String = QuranApiConfig.Reciters.DEFAULT.id

    @Provides
    @Named("defaultReciterFolder")
    fun provideDefaultReciterFolder(): String = QuranApiConfig.Reciters.DEFAULT.folderName

    @Provides
    @Singleton
    fun provideReciterList(): List<QuranApiConfig.Reciters.ReciterInfo> = QuranApiConfig.Reciters.ALL
}
