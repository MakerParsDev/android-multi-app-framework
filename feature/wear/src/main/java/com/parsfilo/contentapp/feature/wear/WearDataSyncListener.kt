package com.parsfilo.contentapp.feature.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class WearDataSyncListener : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        // Wear OS data sync listener callback
    }

    companion object {
        const val ZIKIR_SYNC_PATH = "/zikir_count_sync"
    }
}
