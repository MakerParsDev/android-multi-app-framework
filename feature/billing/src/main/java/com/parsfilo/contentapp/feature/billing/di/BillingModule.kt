package com.parsfilo.contentapp.feature.billing.di

import com.parsfilo.contentapp.feature.billing.BillingClientFactory
import com.parsfilo.contentapp.feature.billing.DefaultBillingClientFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    abstract fun bindBillingClientFactory(
        factory: DefaultBillingClientFactory,
    ): BillingClientFactory
}
