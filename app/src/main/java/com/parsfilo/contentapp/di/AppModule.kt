package com.parsfilo.contentapp.di

import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.firebase.push.PUSH_REGISTRATION_URL
import com.parsfilo.contentapp.feature.billing.PURCHASE_VERIFICATION_URL
import com.parsfilo.contentapp.product.AppProductDefinition
import com.parsfilo.contentapp.product.ContentFamily
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Named("audioFileName")
    fun provideAudioFileName(): String =
        if (AppProductDefinition.current.contentFamily == ContentFamily.QURAN) {
            ""
        } else {
            AppProductDefinition.current.audioFileName.orEmpty()
        }

    @Provides
    @Named("useAssetPackAudio")
    fun provideUseAssetPackAudio(): Boolean = AppProductDefinition.current.useAssetPackAudio

    @Provides
    @Named(PUSH_REGISTRATION_URL)
    fun providePushRegistrationUrl(): String = BuildConfig.PUSH_REGISTRATION_URL

    @Provides
    @Named(PURCHASE_VERIFICATION_URL)
    fun providePurchaseVerificationUrl(): String = BuildConfig.PURCHASE_VERIFICATION_URL
}
