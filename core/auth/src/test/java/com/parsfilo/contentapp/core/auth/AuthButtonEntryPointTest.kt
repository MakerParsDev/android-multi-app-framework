package com.parsfilo.contentapp.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthButtonEntryPointTest {
    @Test
    fun explicitGoogleButtonStartsExplicitCredentialFlowForAccountsNeedingReauth() = runTest {
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getString(R.string.web_client_id) } returns "test.apps.googleusercontent.com"
        val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        every { firebaseAuth.currentUser } returns null
        val activity = mockk<Activity>(
            relaxed = true,
            moreInterfaces = arrayOf(LifecycleOwner::class),
        )
        val lifecycle = mockk<Lifecycle>()
        every { (activity as LifecycleOwner).lifecycle } returns lifecycle
        every { lifecycle.currentState } returns Lifecycle.State.RESUMED

        val credentialManager = mockk<CredentialManager>(relaxed = true)
        val request = slot<GetCredentialRequest>()
        coEvery { credentialManager.getCredential(activity, capture(request)) } throws
            GetCredentialCancellationException("[16] Account reauth failed.")

        mockkObject(CredentialManager.Companion)
        mockkConstructor(GetGoogleIdOption.Builder::class, GetSignInWithGoogleOption.Builder::class)
        try {
            every { anyConstructed<GetGoogleIdOption.Builder>().build() } returns mockk()
            every { anyConstructed<GetSignInWithGoogleOption.Builder>().build() } returns mockk()
            every { CredentialManager.create(activity) } returns credentialManager
            val result = AuthManager(appContext, firebaseAuth).signIn(activity)

            assertThat(result).isEqualTo(AuthManager.SignInResult.ReauthRequired)
            assertThat(request.captured.credentialOptions).hasSize(1)
            assertThat(request.captured.credentialOptions.single())
                .isInstanceOf(GetSignInWithGoogleOption::class.java)
        } finally {
            unmockkConstructor(GetGoogleIdOption.Builder::class, GetSignInWithGoogleOption.Builder::class)
            unmockkObject(CredentialManager.Companion)
        }
    }

    @Test
    fun userCancelsGoogleChooserWithoutBeingToldToReauthenticate() = runTest {
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getString(R.string.web_client_id) } returns "test.apps.googleusercontent.com"
        val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        every { firebaseAuth.currentUser } returns null
        val activity = mockk<Activity>(
            relaxed = true,
            moreInterfaces = arrayOf(LifecycleOwner::class),
        )
        val lifecycle = mockk<Lifecycle>()
        every { (activity as LifecycleOwner).lifecycle } returns lifecycle
        every { lifecycle.currentState } returns Lifecycle.State.RESUMED

        val credentialManager = mockk<CredentialManager>(relaxed = true)
        coEvery { credentialManager.getCredential(activity, any<GetCredentialRequest>()) } throws
            GetCredentialCancellationException("[16] Cancelled by user.")

        mockkObject(CredentialManager.Companion)
        mockkConstructor(GetSignInWithGoogleOption.Builder::class)
        try {
            every { anyConstructed<GetSignInWithGoogleOption.Builder>().build() } returns mockk()
            every { CredentialManager.create(activity) } returns credentialManager
            val result = AuthManager(appContext, firebaseAuth).signIn(activity)

            assertThat(result).isEqualTo(AuthManager.SignInResult.Cancelled)
            coVerify(exactly = 1) { credentialManager.getCredential(activity, any<GetCredentialRequest>()) }
            coVerify(exactly = 0) { credentialManager.clearCredentialState(any()) }
        } finally {
            unmockkConstructor(GetSignInWithGoogleOption.Builder::class)
            unmockkObject(CredentialManager.Companion)
        }
    }
}
