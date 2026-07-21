package com.parsfilo.contentapp.feature.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.android.gms.tasks.Task
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.parsfilo.contentapp.core.common.network.TimberNetworkLoggingInterceptor
import com.parsfilo.contentapp.core.firebase.RuntimeFailure
import com.parsfilo.contentapp.core.firebase.RuntimeObservability
import com.parsfilo.contentapp.core.firebase.RuntimeSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val PURCHASE_VERIFICATION_URL = "purchase_verification_url"

@Singleton
class BillingPurchaseVerifier @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseAppCheck: FirebaseAppCheck,
    private val runtimeObservability: RuntimeObservability,
    @Named(PURCHASE_VERIFICATION_URL) private val verificationUrl: String,
) {
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(TimberNetworkLoggingInterceptor("billing_verify"))
            .build()

    suspend fun verify(
        packageName: String,
        purchase: Purchase,
    ): VerificationResult {
        val result = verifyInternal(packageName, purchase)
        if (!result.verified && result.purchaseState != "AUTH_REQUIRED") {
            runtimeObservability.recordFailure(
                RuntimeFailure(
                    signal = RuntimeSignal.BILLING_PURCHASE_VERIFICATION,
                    code = result.purchaseState,
                    message = result.error ?: "Purchase verification rejected",
                    attributes = mapOf(
                        "package_name" to packageName,
                        "acknowledgement_state" to result.acknowledgementState,
                    ),
                ),
            )
        }
        return result
    }

    private suspend fun verifyInternal(
        packageName: String,
        purchase: Purchase,
    ): VerificationResult {
        if (verificationUrl.isBlank()) {
            return VerificationResult(
                verified = false,
                expiryTimeMillis = null,
                isAutoRenewing = false,
                purchaseState = "MISCONFIGURED",
                acknowledgementState = "UNKNOWN",
                error = "Purchase verification URL is missing",
            )
        }

        val user = firebaseAuth.currentUser ?: return VerificationResult(
            verified = false,
            expiryTimeMillis = null,
            isAutoRenewing = false,
            purchaseState = "AUTH_REQUIRED",
            acknowledgementState = "UNKNOWN",
            error = "Firebase Auth user is required for purchase verification",
        )

        val idToken =
            runCatching { awaitTaskResult(user.getIdToken(false))?.token.orEmpty() }.getOrElse {
                if (it is CancellationException) throw it
                Timber.w(it, "Billing verification failed: unable to read Firebase ID token")
                ""
            }
        if (idToken.isBlank()) {
            return VerificationResult(
                verified = false,
                expiryTimeMillis = null,
                isAutoRenewing = false,
                purchaseState = "AUTH_TOKEN_MISSING",
                acknowledgementState = "UNKNOWN",
                error = "Firebase ID token is missing",
            )
        }

        val appCheckToken = runCatching {
            awaitTaskResult(firebaseAppCheck.getAppCheckToken(false))?.token.orEmpty()
        }.getOrElse {
            if (it is CancellationException) throw it
            Timber.w(it, "Billing verification failed: unable to read App Check token")
            ""
        }
        if (appCheckToken.isBlank()) {
            return VerificationResult(
                verified = false,
                expiryTimeMillis = null,
                isAutoRenewing = false,
                purchaseState = "APP_CHECK_MISSING",
                acknowledgementState = "UNKNOWN",
                error = "App Check token is missing",
            )
        }

        val productId = purchase.products.firstOrNull().orEmpty()
        val purchaseType =
            if (purchase.products.any { BillingCatalog.subscriptionProductIds.contains(it) }) {
                BillingClient.ProductType.SUBS
            } else {
                BillingClient.ProductType.INAPP
            }

        if (productId.isBlank()) {
            return VerificationResult(
                verified = false,
                expiryTimeMillis = null,
                isAutoRenewing = false,
                purchaseState = "INVALID_PURCHASE",
                acknowledgementState = "UNKNOWN",
                error = "Product ID is missing in purchase payload",
            )
        }

        val requestJson = JSONObject().put("packageName", packageName).put("productId", productId)
            .put("purchaseToken", purchase.purchaseToken).put(
                "purchaseType",
                if (purchaseType == BillingClient.ProductType.SUBS) "subs" else "inapp"
            )

        val request =
            Request.Builder().url(verificationUrl).addHeader("Authorization", "Bearer $idToken")
                .addHeader("X-Firebase-AppCheck", appCheckToken)
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)).build()

        val response =
            runCatching { okHttpClient.newCall(request).execute() }.getOrElse { throwable ->
                Timber.w(throwable, "Billing purchase verification request failed")
                return VerificationResult(
                    verified = false,
                    expiryTimeMillis = null,
                    isAutoRenewing = false,
                    purchaseState = "NETWORK_ERROR",
                    acknowledgementState = "UNKNOWN",
                    error = "Network error while verifying purchase",
                )
            }

        response.use { httpResponse ->
            val body = httpResponse.body.string()
            if (!httpResponse.isSuccessful) {
                Timber.w(
                    "Billing purchase verification returned %s (%s)",
                    httpResponse.code,
                    body.take(180)
                )
                return VerificationResult(
                    verified = false,
                    expiryTimeMillis = null,
                    isAutoRenewing = false,
                    purchaseState = "HTTP_${httpResponse.code}",
                    acknowledgementState = "UNKNOWN",
                    error = "Verification endpoint returned HTTP ${httpResponse.code}",
                )
            }

            return parseBillingVerificationResponse(body)
        }
    }

    internal companion object {
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }

        suspend fun <T> awaitTaskResult(task: Task<T>): T =
            suspendCancellableCoroutine { continuation ->
                task.addOnCompleteListener(DIRECT_EXECUTOR) { completedTask ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    val exception = completedTask.exception
                    when {
                        completedTask.isSuccessful -> continuation.resume(completedTask.result)
                        completedTask.isCanceled -> continuation.resumeWithException(
                            CancellationException("Google Task was canceled"),
                        )
                        else -> continuation.resumeWithException(
                            exception ?: IllegalStateException("Google Task failed without an exception"),
                        )
                    }
                }
            }
    }
}

data class VerificationResult(
    val verified: Boolean,
    val expiryTimeMillis: Long?,
    val isAutoRenewing: Boolean,
    val purchaseState: String,
    val acknowledgementState: String,
    val error: String?,
)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
