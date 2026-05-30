package com.silentlink.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SupportTier(
    val productId: String,
    val displayName: String,
    val price: String
)

val SUPPORT_TIERS = listOf(
    SupportTier("support_400", "작은 응원", "400원"),
    SupportTier("support_900", "감사해요", "900원"),
    SupportTier("support_1500", "많이 응원해요", "1,500원"),
    SupportTier("support_2000", "최고예요!", "2,000원")
)

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val productDetailsList = mutableListOf<ProductDetails>()

    sealed class BillingState {
        object Idle : BillingState()
        object Loading : BillingState()
        data class Success(val productId: String) : BillingState()
        data class Error(val message: String) : BillingState()
    }

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProducts() {
        val productList = SUPPORT_TIERS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { _, details ->
            productDetailsList.clear()
            productDetailsList.addAll(details)
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        val productDetails = productDetailsList.find { it.productId == productId } ?: run {
            _billingState.value = BillingState.Error("상품 정보를 불러올 수 없습니다")
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 소모성 상품이므로 consume 처리
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.consumeAsync(consumeParams) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingState.value = BillingState.Success(
                        purchase.products.firstOrNull() ?: ""
                    )
                }
            }
        }
    }

    fun disconnect() {
        billingClient.endConnection()
    }
}
