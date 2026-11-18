package com.parsfilo.contentapp.debug

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.feature.ads.AdManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AdInspectorDebugActivity : ComponentActivity() {
    @Inject
    lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AdInspectorDebugDevicePolicy.isLaunchAction(intent.action)) {
            Log.w(TAG, "AD_INSPECTOR_BLOCKED_INVALID_REQUEST")
            finish()
            return
        }

        if (!AdInspectorDebugDevicePolicy.isEnabled(BuildConfig.ENABLE_AD_INSPECTOR_DEBUG, Build.MODEL)) {
            Log.w(
                TAG,
                "AD_INSPECTOR_BLOCKED_BY_BUILD_FLAG_OR_DEVICE",
            )
            finish()
            return
        }

        lifecycleScope.launch {
            val sdkReady =
                withTimeoutOrNull(SDK_READY_TIMEOUT_MS) {
                    adManager.isSdkReady.first { it }
                } == true

            if (!sdkReady) {
                Log.e(
                    TAG,
                    "AD_INSPECTOR_BLOCKED_SDK_NOT_READY; normal UMP flow was not bypassed",
                )
                finish()
                return@launch
            }

            Log.i(TAG, "AD_INSPECTOR_OPEN_REQUESTED")
            adManager.openAdInspector(this@AdInspectorDebugActivity) { error ->
                if (error == null) {
                    Log.i(TAG, "AD_INSPECTOR_CLOSED")
                } else {
                    Log.e(TAG, "AD_INSPECTOR_ERROR")
                }
                finish()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_AD_INSPECTOR = "com.parsfilo.contentapp.action.OPEN_AD_INSPECTOR"

        private const val TAG = "AdInspectorDebug"
        private const val SDK_READY_TIMEOUT_MS = 60_000L
    }
}

internal object AdInspectorDebugDevicePolicy {
    private const val ALLOWED_MODEL = "SM-G611F"

    fun isLaunchAction(action: String?): Boolean =
        action == AdInspectorDebugActivity.ACTION_OPEN_AD_INSPECTOR

    fun isEnabled(buildFlag: Boolean, model: String): Boolean = buildFlag && isAllowed(model)

    fun isAllowed(model: String): Boolean =
        model.trim().uppercase(Locale.ROOT) == ALLOWED_MODEL
}
