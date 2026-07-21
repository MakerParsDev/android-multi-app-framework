package com.parsfilo.contentapp.core.firebase.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAppCheckInstaller @Inject constructor(
    private val providerFactoryProvider: AppCheckProviderFactoryProvider,
) {
    fun install() {
        val factory = providerFactoryProvider.get()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
        Timber.i("Firebase App Check installed (%s)", factory::class.java.simpleName)
    }
}
