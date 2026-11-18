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

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (intent.action != ACTION_OPEN_AD_INSPECTOR || !isValidSessionId(sessionId)) {
            Log.w(TAG, "AD_INSPECTOR_BLOCKED_INVALID_REQUEST")
            finish()
            return
        }

        if (!AdInspectorDebugDevicePolicy.isEnabled(BuildConfig.ENABLE_AD_INSPECTOR_DEBUG, Build.MODEL)) {
            Log.w(
                TAG,
                "AD_INSPECTOR_BLOCKED_BY_BUILD_FLAG_OR_DEVICE session=" + sessionId,
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
                    "AD_INSPECTOR_BLOCKED_SDK_NOT_READY session=" + sessionId +
                        "; normal UMP flow was not bypassed",
                )
                finish()
                return@launch
            }

            Log.i(TAG, "AD_INSPECTOR_OPEN_REQUESTED session=" + sessionId)
            adManager.openAdInspector(this@AdInspectorDebugActivity) { error ->
                if (error == null) {
                    Log.i(TAG, "AD_INSPECTOR_CLOSED session=" + sessionId)
                } else {
                    Log.e(TAG, "AD_INSPECTOR_ERROR session=" + sessionId + " message=" + error)
                }
                finish()
            }
        }
    }

    private fun isValidSessionId(value: String?): Boolean =
        value?.matches(SESSION_ID_PATTERN) == true

    companion object {
        const val ACTION_OPEN_AD_INSPECTOR = "com.parsfilo.contentapp.action.OPEN_AD_INSPECTOR"
        const val EXTRA_SESSION_ID = "com.parsfilo.contentapp.extra.AD_INSPECTOR_SESSION_ID"

        private const val TAG = "AdInspectorDebug"
        private const val SDK_READY_TIMEOUT_MS = 60_000L
        private val SESSION_ID_PATTERN = Regex("[a-f0-9]{32}")
    }
}

internal object AdInspectorDebugDevicePolicy {
    private const val ALLOWED_MODEL = "SM-G611F"

    fun isEnabled(buildFlag: Boolean, model: String): Boolean = buildFlag && isAllowed(model)

    fun isAllowed(model: String): Boolean =
        model.trim().uppercase(Locale.ROOT) == ALLOWED_MODEL
}
