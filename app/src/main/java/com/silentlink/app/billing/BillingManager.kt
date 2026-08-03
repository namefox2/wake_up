package com.silentlink.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 개발자 응원 상품 (소모성)
data class SupportTier(val productId: String, val displayName: String, val price: String)
val SUPPORT_TIERS = listOf(
    SupportTier("support_400", "작은 응원", "400원"),
    SupportTier("support_900", "감사해요", "900원"),
    SupportTier("support_1500", "많이 응원해요", "1,500원"),
    SupportTier("support_2000", "최고예요!", "2,000원")
)

// 디바이스 슬롯 상품 (비소모성 - 최대 4개 추가 = 총 5대)
// extra_slot_1 = 2번째 기기, extra_slot_2 = 3번째 기기 ...
val DEVICE_SLOT_PRODUCT_IDS = listOf("extra_slot_1", "extra_slot_2", "extra_slot_3", "extra_slot_4")

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState

    private var onSlotPurchased: ((Int) -> Unit)? = null  // 구매된 총 슬롯 수 콜백

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    @Volatile private var productDetailsList: List<ProductDetails> = emptyList()

    sealed class BillingState {
        object Idle : BillingState()
        object Loading : BillingState()
        data class SlotPurchased(val newTotalSlots: Int) : BillingState()
        data class SupportPurchased(val productId: String) : BillingState()
        data class Error(val message: String) : BillingState()
    }

    fun connect(onSlotPurchased: (Int) -> Unit = {}, onBillingReady: (ownedSlots: Int) -> Unit = {}) {
        this.onSlotPurchased = onSlotPurchased
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAllProducts()
                    queryOwnedSlots { onBillingReady(it) }
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryAllProducts() {
        val allIds = SUPPORT_TIERS.map { it.productId } + DEVICE_SLOT_PRODUCT_IDS
        val productList = allIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        ) { _, details ->
            productDetailsList = details.productDetailsList
        }
    }

    // 현재 소유한 슬롯 수 조회 (앱 시작 시 복원용)
    fun queryOwnedSlots(onResult: (Int) -> Unit) {
        if (!billingClient.isReady) { onResult(0); return }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val slots = purchases.count { p ->
                    p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    p.products.any { it.startsWith("extra_slot_") }
                }
                onResult(slots)
            } else {
                onResult(0)
            }
        }
    }

    fun launchSlotPurchase(activity: Activity, currentSlots: Int) {
        if (currentSlots >= DEVICE_SLOT_PRODUCT_IDS.size) {
            _billingState.value = BillingState.Error("최대 슬롯(5대)에 도달했습니다")
            return
        }
        val productId = DEVICE_SLOT_PRODUCT_IDS[currentSlots]
        launchBillingFlow(activity, productId)
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            _billingState.value = BillingState.Error("결제 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            return
        }
        val details = productDetailsList.find { it.productId == productId } ?: run {
            _billingState.value = BillingState.Error("상품 정보를 불러올 수 없습니다")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            ).build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val productId = purchase.products.firstOrNull() ?: continue
            if (productId.startsWith("extra_slot_")) {
                handleSlotPurchase(purchase)
            } else {
                handleSupportPurchase(purchase)
            }
        }
    }

    private fun handleSlotPurchase(purchase: Purchase) {
        val slotIndex = DEVICE_SLOT_PRODUCT_IDS.indexOf(purchase.products.firstOrNull())
        if (slotIndex < 0) return
        val newTotalSlots = slotIndex + 1

        if (purchase.isAcknowledged) {
            // 이미 승인된 구매 — 바로 슬롯 반영
            _billingState.value = BillingState.SlotPurchased(newTotalSlots)
            onSlotPurchased?.invoke(newTotalSlots)
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _billingState.value = BillingState.SlotPurchased(newTotalSlots)
                onSlotPurchased?.invoke(newTotalSlots)
            } else {
                _billingState.value = BillingState.Error("슬롯 승인 실패 (${result.responseCode}), 잠시 후 다시 시도해주세요")
            }
        }
    }

    private fun handleSupportPurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _billingState.value = BillingState.SupportPurchased(purchase.products.firstOrNull() ?: "")
            }
        }
    }

    fun resetState() { _billingState.value = BillingState.Idle }

    fun disconnect() { billingClient.endConnection() }
}
