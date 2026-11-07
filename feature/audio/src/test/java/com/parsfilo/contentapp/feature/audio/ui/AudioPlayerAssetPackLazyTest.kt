package com.parsfilo.contentapp.feature.audio.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.android.play.core.assetpacks.AssetPackManager
import com.parsfilo.contentapp.core.firebase.config.EndpointsProvider
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPlayerAssetPackLazyTest {
    @Test
    fun disabledAssetPackDoesNotInitializeManagerUntilLegacyMethodIsUsed() {
        val manager = mockk<AssetPackManager>()
        every { manager.getPackLocation("audioassets") } returns null
        var resolutions = 0
        val deferred = Lazy {
            resolutions++
            manager
        }

        val viewModel = AudioPlayerViewModel(
            context = mockk<Context>(),
            assetPackManagerProvider = deferred,
            endpointsProvider = mockk<EndpointsProvider>(),
            okHttpClient = OkHttpClient(),
            audioFileName = "",
            useAssetPackAudio = false,
            audioStartupEnabled = false,
            savedStateHandle = SavedStateHandle(),
        )

        // All 17 shipping flavors disable Play Asset Delivery.
        // Instantiating an inactive manager here would crash on API 28,
        // because the obsolete ExtractionForegroundService was removed.
        assertEquals(0, resolutions)

        // Preserve the legacy enabled behavior: resolve once when requested.
        assertNull(viewModel.getAssetPackFilePath("audio.mp3"))
        assertNull(viewModel.getAssetPackFilePath("audio.mp3"))
        assertEquals(1, resolutions)
        verify(exactly = 2) { manager.getPackLocation("audioassets") }
    }
}
