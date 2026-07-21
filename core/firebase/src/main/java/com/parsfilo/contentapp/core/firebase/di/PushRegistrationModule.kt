package com.parsfilo.contentapp.core.firebase.di

import com.parsfilo.contentapp.core.firebase.push.HttpPushRegistrationSender
import com.parsfilo.contentapp.core.firebase.push.PushRegistrationSender
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PushRegistrationModule {
    @Binds
    abstract fun bindsPushRegistrationSender(
        impl: HttpPushRegistrationSender,
    ): PushRegistrationSender
}
