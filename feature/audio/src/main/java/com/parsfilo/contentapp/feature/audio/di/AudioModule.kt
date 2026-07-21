package com.parsfilo.contentapp.feature.audio.di

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.parsfilo.contentapp.core.common.network.TimberNetworkLoggingInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideAudioOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(TimberNetworkLoggingInterceptor("audio_remote"))
        .build()

    @Provides
    @Singleton
    fun provideAssetPackManager(
        @ApplicationContext context: Context
    ): AssetPackManager = AssetPackManagerFactory.getInstance(context)
}
