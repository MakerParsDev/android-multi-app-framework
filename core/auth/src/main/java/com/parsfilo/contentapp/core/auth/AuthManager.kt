package com.parsfilo.contentapp.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager
    @Inject constructor(
        @ApplicationContext private val context: Context,
        private val firebaseAuth: FirebaseAuth,
    ) {
        companion object {
            private const val REAUTH_REQUIRED_CODE = 16
            private const val MASK_SHORT_THRESHOLD = 8
            private const val MASK_SHORT_SUFFIX = 2
            private const val MASK_VISIBLE_PREFIX = 4
            private const val MASK_VISIBLE_SUFFIX = 4
            private const val GOOGLE_CLIENT_ID_DOMAIN = ".apps.googleusercontent.com"
        }

        sealed interface SignInResult {
            data object Success : SignInResult

            data object NoCredential : SignInResult

            data object ReauthRequired : SignInResult

            data object Cancelled : SignInResult

            data object Failure : SignInResult
        }

        private val _authState = MutableStateFlow(firebaseAuth.currentUser != null)
        val authState: StateFlow<Boolean> = _authState.asStateFlow()

        private val signInInProgress = AtomicBoolean(false)
        private val reauthStateCleared = AtomicBoolean(false)

        private val attemptCounter = AtomicLong(0L)

        init {
            firebaseAuth.addAuthStateListener { auth ->
                _authState.value = auth.currentUser != null
            }
        }

        suspend fun signIn(activityContext: Activity): SignInResult {
            val attemptId = attemptCounter.incrementAndGet()

            if (!signInInProgress.compareAndSet(false, true)) {
                Timber.w("[Auth][$attemptId] Duplicate sign-in request blocked: already in progress")
                return SignInResult.Cancelled
            }

            try {
                if (!isActivityResumed(activityContext)) {
                    Timber.w("[Auth][$attemptId] Sign-in skipped: activity is not RESUMED")
                    return SignInResult.Cancelled
                }

                val credentialManager = CredentialManager.create(activityContext)

                Timber.i(
                    "[Auth][$attemptId] Sign-in started. userSignedIn=%s activity=%s",
                    firebaseAuth.currentUser != null,
                    activityContext::class.java.simpleName,
                )

                val allAccountsResult = trySignIn(
                    attemptId = attemptId,
                    activityContext = activityContext,
                    credentialManager = credentialManager,
                    filterByAuthorizedAccounts = false,
                )

                return when (allAccountsResult) {
                    SignInResult.Success -> {
                        reauthStateCleared.set(false)
                        Timber.i("[Auth][$attemptId] Sign-in SUCCESS (all accounts)")
                        SignInResult.Success
                    }

                    SignInResult.ReauthRequired -> {
                        Timber.w("[Auth][$attemptId] Reauth required (all accounts). Clearing credential state…")
                        clearCredentialStateOnce(attemptId, credentialManager)
                        SignInResult.ReauthRequired
                    }

                    SignInResult.NoCredential -> {
                        Timber.i("[Auth][$attemptId] NoCredential (all accounts). Trying explicit Google button flow…")
                        val explicitResult = trySignInWithGoogleButton(
                            attemptId = attemptId,
                            activityContext = activityContext,
                            credentialManager = credentialManager,
                        )

                        when (explicitResult) {
                            SignInResult.Success -> {
                                reauthStateCleared.set(false)
                                Timber.i("[Auth][$attemptId] Sign-in SUCCESS (button flow)")
                                SignInResult.Success
                            }

                            SignInResult.ReauthRequired -> {
                                Timber.w("[Auth][$attemptId] Reauth required (button flow). Clearing credential state…")
                                clearCredentialStateOnce(attemptId, credentialManager)
                                SignInResult.ReauthRequired
                            }

                            SignInResult.NoCredential -> {
                                Timber.w("[Auth][$attemptId] No Google credentials available on this device")
                                SignInResult.NoCredential
                            }

                            else -> {
                                Timber.i(
                                    "[Auth][$attemptId] Button flow finished with %s",
                                    explicitResult.logLabel(),
                                )
                                explicitResult
                            }
                        }
                    }

                    else -> {
                        Timber.i(
                            "[Auth][$attemptId] Sign-in finished with %s",
                            allAccountsResult.logLabel(),
                        )
                        allAccountsResult
                    }
                }
            } finally {
                signInInProgress.set(false)
                Timber.i("[Auth][$attemptId] Sign-in finished. inProgress reset.")
            }
        }

        private suspend fun trySignIn(
            attemptId: Long,
            activityContext: Activity,
            credentialManager: CredentialManager,
            filterByAuthorizedAccounts: Boolean,
        ): SignInResult {
            val serverClientId = resolveServerClientId()
            val maskedClientId = maskClientId(serverClientId)

            if (serverClientId.isBlank()) {
                Timber.e("[Auth][$attemptId] MISCONFIG: missing serverClientId (web_client_id)")
                return SignInResult.Failure
            }

            Timber.i(
                "[Auth][$attemptId] trySignIn(filterByAuthorizedAccounts=%s) serverClientId=%s",
                filterByAuthorizedAccounts,
                maskedClientId,
            )

            return try {
                val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(filterByAuthorizedAccounts).setAutoSelectEnabled(false).setServerClientId(serverClientId).build()

                val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

                Timber.i("[Auth][$attemptId] Calling CredentialManager.getCredential()…")

                val result = credentialManager.getCredential(activityContext, request)

                Timber.i(
                    "[Auth][$attemptId] getCredential() returned. credentialClass=%s",
                    result.credential::class.java.name,
                )

                when (val credential = result.credential) {
                    is CustomCredential -> {
                        Timber.d(
                            "[Auth][$attemptId] CustomCredential received. type=%s",
                            credential.type,
                        )

                        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            Timber.e(
                                "[Auth][$attemptId] Unexpected CustomCredential.type=%s",
                                credential.type,
                            )
                            return SignInResult.Failure
                        }

                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Timber.d("[Auth][$attemptId] GoogleIdTokenCredential parsed")

                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                        Timber.i("[Auth][$attemptId] Signing in to Firebase with Google credential…")
                        firebaseAuth.signInWithCredential(firebaseCredential).await()
                        Timber.i("[Auth][$attemptId] Firebase signInWithCredential SUCCESS")
                        SignInResult.Success
                    }

                    else -> {
                        Timber.e(
                            "[Auth][$attemptId] Unexpected credential type: %s",
                            result.credential::class.java.name,
                        )
                        SignInResult.Failure
                    }
                }
            } catch (_: NoCredentialException) {
                Timber.i("[Auth][$attemptId] NoCredentialException")
                SignInResult.NoCredential
            } catch (e: GetCredentialCancellationException) {
                val code = parseBracketCode(e.message)
                val isReauth = isAccountReauthFailure(e)

                if (isReauth) {
                    Timber.w(
                        "[Auth][$attemptId] REAUTH required. code=%s msg=%s",
                        code ?: "n/a",
                        e.message,
                    )
                    SignInResult.ReauthRequired
                } else {
                    Timber.i(
                        "[Auth][$attemptId] CANCELLED by user/system. code=%s msg=%s",
                        code ?: "n/a",
                        e.message,
                    )
                    SignInResult.Cancelled
                }
            } catch (e: GetCredentialException) {
                val code = parseBracketCode(e.message)
                Timber.e(
                    e,
                    "[Auth][$attemptId] GetCredentialException. code=%s msg=%s",
                    code ?: "n/a",
                    e.message,
                )
                SignInResult.Failure
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "[Auth][$attemptId] Invalid sign-in credential payload. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: IllegalStateException) {
                Timber.e(e, "[Auth][$attemptId] Illegal auth state during sign-in. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: FirebaseException) {
                Timber.e(e, "[Auth][$attemptId] Firebase sign-in failed. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: SecurityException) {
                Timber.e(e, "[Auth][$attemptId] Security exception during sign-in. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: CancellationException) {
                Timber.i(e, "[Auth][$attemptId] Sign-in coroutine cancelled")
                SignInResult.Cancelled
            } catch (e: java.util.concurrent.ExecutionException) {
                Timber.e(e, "[Auth][$attemptId] Credential execution failed. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Timber.e(e, "[Auth][$attemptId] Sign-in interrupted")
                SignInResult.Failure
            } catch (e: UnsupportedOperationException) {
                Timber.e(e, "[Auth][$attemptId] Unexpected exception during sign-in. msg=%s", e.message)
                SignInResult.Failure
            }
        }

        private suspend fun trySignInWithGoogleButton(
            attemptId: Long,
            activityContext: Activity,
            credentialManager: CredentialManager,
        ): SignInResult {
            val serverClientId = resolveServerClientId()
            val maskedClientId = maskClientId(serverClientId)

            if (serverClientId.isBlank()) {
                Timber.e("[Auth][$attemptId] MISCONFIG: missing serverClientId (web_client_id) for button flow")
                return SignInResult.Failure
            }

            Timber.i("[Auth][$attemptId] trySignInWithGoogleButton() serverClientId=%s", maskedClientId)

            return try {
                val option = GetSignInWithGoogleOption.Builder(serverClientId).build()

                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

                Timber.i("[Auth][$attemptId] Calling CredentialManager.getCredential() (button flow)…")
                val result = credentialManager.getCredential(activityContext, request)

                Timber.i(
                    "[Auth][$attemptId] Button flow returned. credentialClass=%s",
                    result.credential::class.java.name,
                )

                when (val credential = result.credential) {
                    is CustomCredential -> {
                        Timber.i(
                            "[Auth][$attemptId] Button flow CustomCredential.type=%s",
                            credential.type,
                        )

                        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            Timber.e(
                                "[Auth][$attemptId] Button flow unexpected CustomCredential.type=%s",
                                credential.type,
                            )
                            return SignInResult.Failure
                        }

                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Timber.d("[Auth][$attemptId] Button flow token parsed")

                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                        Timber.i("[Auth][$attemptId] Button flow: signing in to Firebase…")
                        firebaseAuth.signInWithCredential(firebaseCredential).await()
                        Timber.i("[Auth][$attemptId] Button flow: Firebase sign-in SUCCESS")
                        SignInResult.Success
                    }

                    else -> {
                        Timber.e(
                            "[Auth][$attemptId] Button flow unexpected credential type: %s",
                            result.credential::class.java.name,
                        )
                        SignInResult.Failure
                    }
                }
            } catch (_: NoCredentialException) {
                Timber.i("[Auth][$attemptId] Button flow: NoCredentialException")
                SignInResult.NoCredential
            } catch (e: GetCredentialCancellationException) {
                val code = parseBracketCode(e.message)
                val isReauth = isAccountReauthFailure(e)
                if (isReauth) {
                    Timber.w(
                        "[Auth][$attemptId] Button flow REAUTH required. code=%s msg=%s",
                        code ?: "n/a",
                        e.message,
                    )
                    SignInResult.ReauthRequired
                } else {
                    Timber.i(
                        "[Auth][$attemptId] Button flow CANCELLED. code=%s msg=%s",
                        code ?: "n/a",
                        e.message,
                    )
                    SignInResult.Cancelled
                }
            } catch (e: GetCredentialException) {
                val code = parseBracketCode(e.message)
                Timber.e(
                    e,
                    "[Auth][$attemptId] Button flow GetCredentialException. code=%s msg=%s",
                    code ?: "n/a",
                    e.message,
                )
                SignInResult.Failure
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "[Auth][$attemptId] Button flow invalid credential payload. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: IllegalStateException) {
                Timber.e(e, "[Auth][$attemptId] Button flow illegal state. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: FirebaseException) {
                Timber.e(e, "[Auth][$attemptId] Button flow Firebase sign-in failed. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: SecurityException) {
                Timber.e(e, "[Auth][$attemptId] Button flow security exception. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: CancellationException) {
                Timber.i(e, "[Auth][$attemptId] Button flow coroutine cancelled")
                SignInResult.Cancelled
            } catch (e: java.util.concurrent.ExecutionException) {
                Timber.e(e, "[Auth][$attemptId] Button flow credential execution failed. msg=%s", e.message)
                SignInResult.Failure
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Timber.e(e, "[Auth][$attemptId] Button flow interrupted")
                SignInResult.Failure
            } catch (e: UnsupportedOperationException) {
                Timber.e(e, "[Auth][$attemptId] Button flow unexpected exception. msg=%s", e.message)
                SignInResult.Failure
            }
        }

        private fun resolveServerClientId(): String {
            val fromLocalProperties = runCatching {
                context.getString(R.string.web_client_id).trim()
            }.getOrDefault("")
            if (fromLocalProperties.isNotBlank()) return fromLocalProperties

            // Fallback to google-services generated resource when WEB_CLIENT_ID is missing.
            val fromGoogleServices = optionalStringResource("default_web_client_id")
            if (fromGoogleServices.isNotBlank()) return fromGoogleServices

            return ""
        }

        private fun optionalStringResource(name: String): String {
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId == 0) return ""
            return runCatching { context.getString(resId).trim() }.getOrDefault("")
        }

        private suspend fun clearCredentialStateOnce(
            attemptId: Long,
            credentialManager: CredentialManager,
        ) {
            if (!reauthStateCleared.compareAndSet(false, true)) {
                Timber.i("[Auth][$attemptId] Credential state already cleared after previous reauth failure")
                return
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                Timber.i("[Auth][$attemptId] Credential state CLEARED after reauth failure")
            } catch (e: ClearCredentialException) {
                // Sen istedin: warning değil ERROR
                Timber.e(e, "[Auth][$attemptId] Failed to clear credential state")
            } catch (e: IllegalStateException) {
                Timber.e(e, "[Auth][$attemptId] Failed to clear credential state")
            } catch (e: SecurityException) {
                // Sen istedin: warning değil ERROR
                Timber.e(e, "[Auth][$attemptId] Failed to clear credential state")
            }
        }

        private fun isActivityResumed(activity: Activity): Boolean {
            val lifecycleOwner = activity as? LifecycleOwner ?: run {
                Timber.w("[Auth] Activity is not a LifecycleOwner: %s", activity::class.java.name)
                return false
            }
            return lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }

        private fun isAccountReauthFailure(e: GetCredentialCancellationException): Boolean {
            // Daha sağlam: [16] gibi kodu yakala
            val code = parseBracketCode(e.message)
            if (code == REAUTH_REQUIRED_CODE) return true

            // Fallback: mesaj kontrolü
            val msg = e.message?.lowercase().orEmpty()
            return msg.contains("reauth") || msg.contains("account reauth failed")
        }

        private fun parseBracketCode(message: String?): Int? {
            // örnek message: "[16] Account reauth failed." veya "[28444] Developer console is not set up correctly."
            if (message.isNullOrBlank()) return null
            val start = message.indexOf('[')
            val end = message.indexOf(']')
            if (start == -1 || end == -1 || end <= start + 1) return null
            return message.substring(start + 1, end).toIntOrNull()
        }

        private fun maskClientId(clientId: String): String {
            if (clientId.isBlank()) return "(blank)"
            val at = clientId.indexOf(GOOGLE_CLIENT_ID_DOMAIN)
            val core = if (at > 0) clientId.substring(0, at) else clientId
            val maskedCore = "${core.take(MASK_VISIBLE_PREFIX)}****${core.takeLast(MASK_VISIBLE_SUFFIX)}"
            return when {
                core.length <= MASK_SHORT_THRESHOLD -> "****${core.takeLast(MASK_SHORT_SUFFIX)}"
                else -> "$maskedCore$GOOGLE_CLIENT_ID_DOMAIN"
            }
        }

        fun signOut() {
            firebaseAuth.signOut()
        }

        fun isUserSignedIn(): Boolean = firebaseAuth.currentUser != null

        private fun SignInResult.logLabel(): String = when (this) {
            SignInResult.Success -> "success"
            SignInResult.NoCredential -> "no_credential"
            SignInResult.ReauthRequired -> "reauth_required"
            SignInResult.Cancelled -> "cancelled"
            SignInResult.Failure -> "failure"
        }
    }
