package com.casco.kettle.shell.support

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.casco.kettle.domain.TIP_SKU_LIST
import com.casco.kettle.domain.TipProduct
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A purchase as the store holds it: unconsumed, so either still pending or waiting to be settled. */
data class StorePurchase(
    val skus: List<String>,
    /** Not paid yet: never consumed, since consuming a pending purchase would close it unpaid. */
    val pending: Boolean,
    val token: String,
)

/** How a purchase flow ended, as the store reports it. */
sealed interface PurchaseUpdate {
    data class Bought(
        val purchases: List<StorePurchase>,
    ) : PurchaseUpdate

    /** Backing out of the store's sheet: the common path, and no failure worth a message. */
    data object Cancelled : PurchaseUpdate

    /** The store still holds one of this tier unconsumed, so it refuses another until it's settled. */
    data object AlreadyOwned : PurchaseUpdate

    data object Failed : PurchaseUpdate
}

/**
 * What Support asks of the store, as a boundary its test fakes: the role `safe-iap.ts` played for RN,
 * except nothing here can be missing from the binary, so every failure is a runtime one and ends in a
 * value rather than a throw.
 */
interface TipBilling {
    /** Connects, and says whether the store is there. [onUpdate] hears every purchase flow's end. */
    suspend fun connect(onUpdate: (PurchaseUpdate) -> Unit): Boolean

    /** The tips the store sells, or null when it couldn't say. */
    suspend fun products(): List<TipProduct>?

    /** Every purchase the store holds unconsumed, or null when it couldn't say. */
    suspend fun purchases(): List<StorePurchase>?

    /** Consuming also acknowledges, and is what lets the same tier be bought again. */
    suspend fun consume(purchase: StorePurchase): Boolean

    /** Opens the store's sheet for [sku]. How it ends arrives at `connect`'s `onUpdate`, never here. */
    fun launch(sku: String)

    fun close()
}

/**
 * [TipBilling] on the Play Billing Library (#189 holds the facts). [activity] hosts the store's sheet.
 *
 * Neither account nor profile ID is ever set on a purchase, and no token leaves the device: either
 * would change the Data Safety answer, which is "nothing collected".
 */
class PlayTipBilling(
    private val activity: Activity,
) : TipBilling {
    private var onUpdate: (PurchaseUpdate) -> Unit = {}

    /** The last query's, since a launch needs the store's own details object. */
    private val details = mutableMapOf<String, ProductDetails>()

    private val client =
        BillingClient
            .newBuilder(activity)
            .setListener { result, purchases -> onUpdate(updateOf(result, purchases)) }
            // Required for one-time products: slow payment methods (boleto, some cards) land pending.
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            // The library reconnects by itself after Play drops the service, rather than every call
            // after a drop failing with SERVICE_DISCONNECTED.
            .enableAutoServiceReconnection()
            .build()

    override suspend fun connect(onUpdate: (PurchaseUpdate) -> Unit): Boolean {
        this.onUpdate = onUpdate
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    // Before setup finished, it never connected. After, the library reconnects itself.
                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }
    }

    override suspend fun products(): List<TipProduct>? {
        val query =
            TIP_SKU_LIST.map {
                QueryProductDetailsParams.Product
                    .newBuilder()
                    .setProductId(it)
                    .setProductType(ProductType.INAPP)
                    .build()
            }
        val params = QueryProductDetailsParams.newBuilder().setProductList(query).build()
        val fetched: List<ProductDetails> =
            suspendCancellableCoroutine<List<ProductDetails>?> { continuation ->
                client.queryProductDetailsAsync(params) { result, products ->
                    continuation.resume(
                        products.productDetailsList.takeIf {
                            result.responseCode ==
                                BillingResponseCode.OK
                        },
                    )
                }
            } ?: return null
        details.clear()
        return fetched.mapNotNull { product ->
            val price = product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice ?: return@mapNotNull null
            details[product.productId] = product
            TipProduct(product.productId, price)
        }
    }

    override suspend fun purchases(): List<StorePurchase>? =
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) {
                result,
                purchases,
                ->
                continuation.resume(
                    purchases.map(::storePurchase).takeIf {
                        result.responseCode ==
                            BillingResponseCode.OK
                    },
                )
            }
        }

    override suspend fun consume(purchase: StorePurchase): Boolean =
        suspendCancellableCoroutine { continuation ->
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.token).build()) { result, _ ->
                continuation.resume(result.responseCode == BillingResponseCode.OK)
            }
        }

    override fun launch(sku: String) {
        val product = details[sku]
        if (product == null) {
            onUpdate(PurchaseUpdate.Failed)
            return
        }
        val params =
            BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(product)
                .apply {
                    product.oneTimePurchaseOfferDetailsList
                        ?.firstOrNull()
                        ?.offerToken
                        ?.let(::setOfferToken)
                }.build()
        val result =
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build(),
            )
        // A flow that started reports its end to the listener. One that didn't start reports here only.
        if (result.responseCode != BillingResponseCode.OK) onUpdate(updateOf(result, null))
    }

    override fun close() = client.endConnection()

    private fun updateOf(
        result: BillingResult,
        purchases: List<Purchase>?,
    ): PurchaseUpdate =
        when (result.responseCode) {
            BillingResponseCode.OK -> PurchaseUpdate.Bought(purchases.orEmpty().map(::storePurchase))
            BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
            BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.AlreadyOwned
            else -> PurchaseUpdate.Failed
        }

    private fun storePurchase(purchase: Purchase) =
        StorePurchase(
            skus = purchase.products,
            pending = purchase.purchaseState != Purchase.PurchaseState.PURCHASED,
            token = purchase.purchaseToken,
        )
}
