package com.parsfilo.contentapp.core.auth

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test

class AuthManagerTest {
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `current user missing reports signed out`() {
        val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        every { firebaseAuth.currentUser } returns null
        every { firebaseAuth.addAuthStateListener(capture(listenerSlot)) } answers {}

        val manager =
            AuthManager(
                context = context,
                firebaseAuth = firebaseAuth,
            )

        assertThat(manager.isUserSignedIn()).isFalse()
        assertThat(manager.authState.value).isFalse()
    }

    @Test
    fun `auth state listener updates flow when user signs in`() {
        val user = mockk<FirebaseUser>()
        val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        every { firebaseAuth.currentUser } returns null andThen user
        every { firebaseAuth.addAuthStateListener(capture(listenerSlot)) } answers {}

        val manager =
            AuthManager(
                context = context,
                firebaseAuth = firebaseAuth,
            )

        listenerSlot.captured.onAuthStateChanged(firebaseAuth)

        assertThat(manager.authState.value).isTrue()
        assertThat(manager.isUserSignedIn()).isTrue()
    }
}
