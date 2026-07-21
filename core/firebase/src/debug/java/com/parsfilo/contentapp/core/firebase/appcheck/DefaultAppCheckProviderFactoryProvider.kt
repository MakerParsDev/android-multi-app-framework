package com.parsfilo.contentapp.core.firebase.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppCheckProviderFactoryProvider @Inject constructor() : AppCheckProviderFactoryProvider {
    override fun get(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
