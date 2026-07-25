package com.parsfilo.contentapp.core.common.startup

import android.content.Context
import androidx.startup.Initializer
import timber.log.Timber

class AppInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
