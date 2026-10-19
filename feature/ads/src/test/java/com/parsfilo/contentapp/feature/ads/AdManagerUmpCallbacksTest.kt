package com.parsfilo.contentapp.feature.ads

import android.app.Activity
import android.content.Context
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.RuntimeObservability
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Exercises the production AdManager callbacks, not only the pure consent mapping functions.
 * UMP and Mobile Ads are fully stubbed; no network, production ad units or real consent UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdManagerUmpCallbacksTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val preferences = mockk<PreferencesDataSource>(relaxed = true)
    private val analytics = mockk<AppAnalytics>(relaxed = true)
    private val observability = mockk<RuntimeObservability>(relaxed = true)
    private val syncId = mockk<ConsentSyncIdProvider>(relaxed = true)
    private val policy = mockk<AdsPolicyProvider>(relaxed = true)
    private val consentInfo = mockk<ConsentInformation>(relaxed = true)
    private val success = slot<ConsentInformation.OnConsentInfoUpdateSuccessListener>()
    private val failure = slot<ConsentInformation.OnConsentInfoUpdateFailureListener>()
    private val formDismissed = slot<ConsentForm.OnConsentFormDismissedListener>()
    private lateinit var manager: AdManager

    @Before
    fun setUp() {
        mockkStatic(UserMessagingPlatform::class)
        mockkStatic(MobileAds::class)
        every { UserMessagingPlatform.getConsentInformation(any()) } returns consentInfo
        every {
            consentInfo.requestConsentInfoUpdate(any(), any(), capture(success), capture(failure))
        } just Runs
        every {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(any(), capture(formDismissed))
        } just Runs
        every { consentInfo.canRequestAds() } returns false
        every { MobileAds.getRequestConfiguration() } returns RequestConfiguration.Builder().build()
        every { MobileAds.setRequestConfiguration(any()) } just Runs
        every { MobileAds.initialize(any<Context>(), any()) } answers {
            secondArg<OnInitializationCompleteListener>().onInitializationComplete(mockk(relaxed = true))
        }
        every { preferences.adsLastSuccessfulConsentStatus } returns flowOf("unknown")
        every { preferences.userData } returns flowOf(
            mockk<UserPreferencesData> { every { adsAgeGateStatus } returns "UNKNOWN" },
        )
        coEvery { preferences.setAdsConsentSnapshot(any(), any()) } just Runs
        coEvery { preferences.setAdsLastSuccessfulConsentSnapshot(any(), any()) } just Runs
        coEvery { syncId.getConsentSyncIdOrNull() } returns null
        every { policy.getPolicy().consentRetryBackoffMinutes } returns 5
        AdsConsentRuntimeState.update(false)
        manager = AdManager(
            context,
            preferences,
            analytics,
            observability,
            syncId,
            policy,
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
        unmockkStatic(UserMessagingPlatform::class, MobileAds::class)
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun setExplicitEeaRefusal() {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .putInt("IABTCF_gdprApplies", 1)
            .putString("IABTCF_PurposeConsents", "00000000000")
            .commit()
    }

    @Test
    fun refreshFailureWithoutCachedChoiceRecordsMissingAndKeepsAdsBlocked() {
        var eligible: Boolean? = null
        manager.refreshConsent(activity) { eligible = it }
        idleMain()

        failure.captured.onConsentInfoUpdateFailure(FormError(1, "no network"))
        idleMain()

        assertThat(eligible).isFalse()
        assertThat(manager.consentStatus.value).isEqualTo(ConsentStatus.Missing)
        assertThat(manager.canRequestAds.value).isFalse()
        // A missing snapshot is published as one consent_refreshed event, not duplicated.
        verify(exactly = 1) { analytics.logEvent("consent_refreshed", any()) }
        verify(exactly = 0) { analytics.logEvent("consent_granted", any()) }
    }

    @Test
    fun refreshFailureWithCachedChoiceUsesRetryBackoffAndNotFalseGrant() {
        every { preferences.adsLastSuccessfulConsentStatus } returns flowOf("granted")
        var eligible: Boolean? = null
        manager.refreshConsent(activity) { eligible = it }
        idleMain()

        failure.captured.onConsentInfoUpdateFailure(FormError(2, "timeout"))
        idleMain()

        assertThat(eligible).isFalse()
        val result = manager.consentStatus.value
        assertThat(result).isInstanceOf(ConsentStatus.Error::class.java)
        assertThat((result as ConsentStatus.Error).retryEligibleAtMillis)
            .isGreaterThan(System.currentTimeMillis())
        assertThat(manager.canRequestAds.value).isFalse()
        verify(exactly = 1) { analytics.logEvent("consent_error", any()) }
        verify(exactly = 0) { analytics.logEvent("consent_granted", any()) }
    }

    @Test
    fun refreshFailurePreservesExplicitEeaRefusalDespiteCachedGrant() {
        setExplicitEeaRefusal()
        every { preferences.adsLastSuccessfulConsentStatus } returns flowOf("granted")
        manager.refreshConsent(activity)
        idleMain()

        failure.captured.onConsentInfoUpdateFailure(FormError(2, "timeout"))
        idleMain()

        assertThat(manager.consentStatus.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.canRequestAds.value).isFalse()
        verify(exactly = 0) { analytics.logEvent("consent_granted", any()) }
    }

    @Test
    fun successfulRefreshWithoutAdEligibilityDoesNotResurrectCachedConsent() {
        every { preferences.adsLastSuccessfulConsentStatus } returns flowOf("granted")
        var eligible: Boolean? = null
        manager.refreshConsent(activity) { eligible = it }
        idleMain()

        success.captured.onConsentInfoUpdateSuccess()
        idleMain()

        assertThat(eligible).isFalse()
        assertThat(manager.consentStatus.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.canRequestAds.value).isFalse()
        verify(exactly = 1) { analytics.logEvent("consent_refreshed", any()) }
        verify(exactly = 0) { analytics.logEvent("consent_granted", any()) }
    }

    @Test
    fun successfulRefreshWithLimitedAdEligibilityPreservesEeaRefusalAndFirebaseDenial() {
        setExplicitEeaRefusal()
        every { consentInfo.canRequestAds() } returns true
        var eligible: Boolean? = null
        manager.refreshConsent(activity) { eligible = it }
        idleMain()

        success.captured.onConsentInfoUpdateSuccess()
        idleMain()

        assertThat(eligible).isTrue()
        assertThat(manager.canRequestAds.value).isTrue()
        assertThat(manager.consentStatus.value).isEqualTo(ConsentStatus.Denied)
        verify { analytics.setConsent(false, false, false, false) }
        verify(exactly = 1) { analytics.logEvent("consent_refreshed", any()) }
        verify(exactly = 0) { analytics.logEvent("consent_granted", any()) }
    }

    @Test
    fun newlyGrantedEeaPurposesSupersedeCachedRefusalOnRefresh() {
        setExplicitEeaRefusal()
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit().putString("IABTCF_PurposeConsents", "1001001").commit()
        every { preferences.adsLastSuccessfulConsentStatus } returns flowOf("denied")
        every { consentInfo.canRequestAds() } returns true
        manager.refreshConsent(activity)
        idleMain()

        success.captured.onConsentInfoUpdateSuccess()
        idleMain()

        assertThat(manager.canRequestAds.value).isTrue()
        assertThat(manager.consentStatus.value).isEqualTo(ConsentStatus.Obtained)
        verify { analytics.setConsent(true, true, true, false) }
        verify(exactly = 1) { analytics.logEvent("consent_refreshed", any()) }
    }

    @Test
    fun coldStartFormErrorAfterEeaRefusalNeverReportsConsentGranted() {
        setExplicitEeaRefusal()
        manager.initialize(activity)
        idleMain()
        success.captured.onConsentInfoUpdateSuccess()
        idleMain()

        formDismissed.captured.onConsentFormDismissed(FormError(3, "form failed"))
        idleMain()

        assertThat(manager.consentStatus.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.canRequestAds.value).isFalse()
        verify(exactly = 1) { analytics.logEvent("consent_error", any()) }
        verify(exactly = 0) { analytics.logEvent("consent_granted", any()) }
    }
}
