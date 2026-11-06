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
    @Named("audioStartupEnabled")
    fun provideAudioStartupEnabled(): Boolean =
        shouldStartAudio(
            ciSmoke = BuildConfig.CI_SMOKE,
            buildType = BuildConfig.BUILD_TYPE,
            family = AppProductDefinition.current.contentFamily,
        )

    @Provides
    @Named(PUSH_REGISTRATION_URL)
    fun providePushRegistrationUrl(): String = BuildConfig.PUSH_REGISTRATION_URL

    @Provides
    @Named(PURCHASE_VERIFICATION_URL)
    fun providePurchaseVerificationUrl(): String = BuildConfig.PURCHASE_VERIFICATION_URL
}

// These non-audio families remove AudioService from their merged manifest.
// Do not create a MediaSession controller for a service absent from the app.
// Audio families, including QURAN's streamed ayah playback, retain the service.
internal fun shouldStartAudio(
    ciSmoke: Boolean,
    buildType: String,
    family: ContentFamily,
): Boolean =
    family !in
        setOf(
            ContentFamily.MIRACLES,
            ContentFamily.QIBLA,
            ContentFamily.PRAYER_TIMES,
            ContentFamily.ZIKIR_COUNTER,
        ) &&
        (
            !ciSmoke ||
                (
                    buildType == "nonMinifiedRelease" &&
                        family in setOf(ContentFamily.CONTENT, ContentFamily.PRAYER_LIBRARY, ContentFamily.ESMA)
                )
        )
