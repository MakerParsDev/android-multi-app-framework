package com.parsfilo.contentapp.feature.content.di

import com.parsfilo.contentapp.feature.content.data.AssetContentRepository
import com.parsfilo.contentapp.feature.content.data.ContentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ContentModule {
    @Binds
    abstract fun bindContentRepository(
        repository: AssetContentRepository
    ): ContentRepository
}
