package com.parsfilo.contentapp.feature.billing

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

object BillingErrorKeys {
    const val CONNECTING = "BILLING_CONNECTING"
    const val RECONNECTING = "BILLING_RECONNECTING"
    const val UNSUPPORTED = "BILLING_UNSUPPORTED"
}

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val billingPurchaseVerifier: BillingPurchaseVerifier,
    private val billingClientFactory: BillingClientFactory,
) {

    private var scope = createScope()

    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private val _productDetails = MutableStateFlow<List<BillingProduct>>(emptyList())
    val productDetails: StateFlow<List<BillingProduct>> = _productDetails.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        onPurchasesUpdated(billingResult, purchases.orEmpty())
    }

    private var billingClient: BillingClient = createBillingClient()

    private val isConnecting = AtomicBoolean(false)

    private fun createScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun ensureScope() {
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = createScope()
        }
    }

    private fun createBillingClient(): BillingClient {
        return billingClientFactory.create(appContext, purchasesUpdatedListener)
    }

    fun startConnection() {
        ensureScope()

        val client = billingClient
        if (client.isReady) {
            queryProductDetails()
            refreshPurchaseState()
            return
        }
        if (!isConnecting.compareAndSet(false, true)) {
            Timber.d("Billing connection already in progress")
            return
        }
        if (billingClient !== client) {
            isConnecting.set(false)
            Timber.d("Billing client changed before startConnection; retrying with fresh client")
            startConnection()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingClient !== client) {
                    Timber.d("Ignoring billing setup callback from stale client")
                    return
                }
                isConnecting.set(false)
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    refreshPurchaseState()
                } else {
                    handleBillingFailure(
                        operation = "Billing setup failed",
                        billingResult = billingResult,
                        updateUiState = false,
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                if (billingClient !== client) {
                    Timber.d("Ignoring billing disconnect callback from stale client")
                    return
                }
                isConnecting.set(false)
                Timber.w("Billing service disconnected")
                replaceBillingClient(closeCurrent = false, resetScope = false)
            }
        })
    }

    fun endConnection() {
        replaceBillingClient(closeCurrent = true, resetScope = true)
    }

    fun launchBillingFlow(activity: Activity, billingProduct: BillingProduct) {
        if (!billingClient.isReady) {
            startConnection()
            _subscriptionState.value = SubscriptionState.Error(BillingErrorKeys.CONNECTING)
            return
        }

        // ProxyBillingActivity crashes with NPE on some devices when Play Store returns
        // a null PendingIntent for subscriptions. Guard upfront so we surface a clean error.
        val subsSupported = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (subsSupported.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.w("Subscriptions not supported on this device: %s", subsSupported.debugMessage)
            _subscriptionState.value = SubscriptionState.Error(BillingErrorKeys.UNSUPPORTED)
            return
        }

        _subscriptionState.value = SubscriptionState.Loading

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(billingProduct.productDetails)

        if (billingProduct.offerToken.isNotBlank()) {
            productParamsBuilder.setOfferToken(billingProduct.offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            handleBillingFailure(
                operation = "Satın alma başlatılamadı",
                billingResult = result,
                updateUiState = true,
            )
        }
    }

    fun restorePurchases() {
        refreshPurchaseState()
    }

    fun refreshPurchaseState() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        queryPurchases()
    }

    private fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases)
            BillingClient.BillingResponseCode.USER_CANCELED -> queryPurchases()
            else -> handleBillingFailure(
                operation = "Satın alma hatası",
                billingResult = billingResult,
                updateUiState = true,
            )
        }
    }

    internal fun queryProductDetails() {
        val productIds = BillingCatalog.subscriptionProductIds

        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                handleBillingFailure(
                    operation = "Planlar alınamadı",
                    billingResult = billingResult,
                    updateUiState = false,
                )
                return@queryProductDetailsAsync
            }

            val rawProducts = productDetailsResult.productDetailsList
                .flatMap { details -> details.toBillingProducts() }

            logUnfetchedProducts(productDetailsResult)

            // 1) Öncelikli productId'yi seç (aynı anda birden çok katalogu karıştırma)
            val preferredProductId = BillingCatalog.resolvePreferredProductId(rawProducts)
            val scopedProducts = if (preferredProductId != null) {
                rawProducts.filter { it.productId == preferredProductId }
            } else {
                rawProducts
            }

            // 2) Sadece kullanıcıya göstermek istediğimiz temel planları tut.
            val basePlanFiltered = scopedProducts.filter { product ->
                BillingCatalog.isAllowedBasePlan(product.basePlanId)
            }
            val candidateProducts = if (basePlanFiltered.isNotEmpty()) basePlanFiltered else scopedProducts

            // 3) Aynı plan birden fazla offer ile gelirse en uygun fiyatı bırak.
            val deduplicated = candidateProducts
                .groupBy { product -> BillingCatalog.deduplicationKey(product) }
                .map { (_, items) ->
                    items.minByOrNull { it.priceAmountMicros } ?: items.first()
                }

            val mapped = deduplicated.sortedWith(
                compareBy({ BillingCatalog.periodOrder(it.billingPeriod) }, { it.priceAmountMicros })
            )

            _productDetails.value = mapped
            if (mapped.isEmpty()) {
                Timber.w("Billing products are empty")
            }
        }
    }

    private fun logUnfetchedProducts(result: QueryProductDetailsResult) {
        val unfetched = result.unfetchedProductList
        if (unfetched.isEmpty()) return

        unfetched.forEach { unfetchedProduct ->
            Timber.w(
                "Unfetched product: id=%s status=%s",
                unfetchedProduct.productId,
                unfetchedProduct.statusCode
            )
        }
    }

    private fun ProductDetails.toBillingProducts(): List<BillingProduct> {
        return subscriptionOfferDetails.orEmpty().map { offer ->
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull()
            val period = phase?.billingPeriod
            val price = phase?.formattedPrice.orEmpty()
            val basePlanId = offer.basePlanId
            val id = "$productId:$basePlanId"

            BillingProduct(
                id = id,
                productId = productId,
                basePlanId = basePlanId,
                offerToken = offer.offerToken,
                priceAmountMicros = phase?.priceAmountMicros ?: Long.MAX_VALUE,
                priceText = price,
                billingPeriod = period,
                productDetails = this
            )
        }
    }

    private fun queryPurchases() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            } else {
                handleBillingFailure(
                    operation = "Satın alma geçmişi alınamadı",
                    billingResult = billingResult,
                    updateUiState = false,
                )
            }
        }
    }

    internal fun handleBillingFailure(
        operation: String,
        billingResult: BillingResult,
        updateUiState: Boolean
    ) {
        val message = "$operation: ${billingResult.debugMessage}"
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                Timber.i("%s (transient/disconnected)", message)
                replaceBillingClient(closeCurrent = false, resetScope = false)
                if (updateUiState) {
                    _subscriptionState.value = SubscriptionState.Error(BillingErrorKeys.RECONNECTING)
                }
            }

            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.ERROR -> {
                Timber.w("%s (transient)", message)
                if (updateUiState) {
                    _subscriptionState.value = SubscriptionState.Error(message)
                }
            }

            else -> {
                if (updateUiState) {
                    setError(message)
                } else {
                    Timber.w(message)
                }
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        _subscriptionState.value = SubscriptionState.Loading
        ensureScope()
        scope.launch {
            applySubscriptionState(resolveSubscriptionStateForPurchases(purchases))
        }
    }

    @VisibleForTesting
    internal suspend fun resolveSubscriptionStateForPurchases(
        purchases: List<Purchase>
    ): SubscriptionState {
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        if (purchased.isEmpty()) {
            return SubscriptionState.Inactive
        }

        purchased.forEach { purchase ->
            acknowledgeIfNeeded(purchase)
        }

        val verifiedPurchases = purchased.mapNotNull { purchase ->
            val verification = billingPurchaseVerifier.verify(
                packageName = appContext.packageName,
                purchase = purchase,
            )
            if (!verification.verified) {
                Timber.w(
                    "Purchase verification failed for product=%s state=%s ack=%s reason=%s",
                    purchase.products.firstOrNull().orEmpty(),
                    verification.purchaseState,
                    verification.acknowledgementState,
                    verification.error.orEmpty()
                )
                return@mapNotNull null
            }
            verification
        }

        if (verifiedPurchases.isEmpty()) {
            return SubscriptionState.Inactive
        }

        val expiryDate = verifiedPurchases.mapNotNull { it.expiryTimeMillis }.maxOrNull()
        val autoRenewing = verifiedPurchases.any { it.isAutoRenewing }
        return SubscriptionState.Active(
            expiryDate = expiryDate,
            isAutoRenewing = autoRenewing,
        )
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("Acknowledge failed: %s", result.debugMessage)
            }
        }
    }

    private fun applySubscriptionState(state: SubscriptionState) {
        ensureScope()
        scope.launch {
            preferencesDataSource.setPremium(state is SubscriptionState.Active)
        }

        _subscriptionState.value = state
    }

    private fun setError(message: String) {
        _subscriptionState.value = SubscriptionState.Error(message)
        Timber.w(message)
    }

    internal fun replaceBillingClient(
        closeCurrent: Boolean,
        resetScope: Boolean
    ) {
        val currentClient = billingClient

        if (closeCurrent && currentClient.isReady) {
            runCatching {
                currentClient.endConnection()
            }.onFailure { throwable ->
                Timber.w(throwable, "Billing endConnection failed while replacing client")
            }
        }

        billingClient = createBillingClient()
        isConnecting.set(false)

        if (resetScope) {
            scope.coroutineContext[Job]?.cancel()
            scope = createScope()
        }

        Timber.d(
            "Billing client replaced (closeCurrent=%s, resetScope=%s)",
            closeCurrent,
            resetScope
        )
    }
}

fun interface BillingClientFactory {
    fun create(
        context: Context,
        purchasesUpdatedListener: PurchasesUpdatedListener,
    ): BillingClient
}

@Singleton
class DefaultBillingClientFactory @Inject constructor() : BillingClientFactory {
    override fun create(
        context: Context,
        purchasesUpdatedListener: PurchasesUpdatedListener,
    ): BillingClient {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        return BillingClient.newBuilder(context)
            .enablePendingPurchases(pendingPurchasesParams)
            .enableAutoServiceReconnection()
            .setListener(purchasesUpdatedListener)
            .build()
    }
}
