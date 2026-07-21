package com.parsfilo.contentapp.core.firebase.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppCheckProviderFactoryProvider @Inject constructor() : AppCheckProviderFactoryProvider {
    override fun get(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
