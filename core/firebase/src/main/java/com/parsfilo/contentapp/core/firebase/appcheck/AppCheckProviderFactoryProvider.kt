package com.parsfilo.contentapp.core.firebase.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory

interface AppCheckProviderFactoryProvider {
    fun get(): AppCheckProviderFactory
}
