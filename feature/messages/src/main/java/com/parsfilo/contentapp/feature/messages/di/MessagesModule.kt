package com.parsfilo.contentapp.feature.messages.di

import com.parsfilo.contentapp.feature.messages.data.FirestoreMessageRepository
import com.parsfilo.contentapp.feature.messages.data.MessageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagesModule {
    @Binds
    abstract fun bindMessageRepository(
        repository: FirestoreMessageRepository
    ): MessageRepository
}
