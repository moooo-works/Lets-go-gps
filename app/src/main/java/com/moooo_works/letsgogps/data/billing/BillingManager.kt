package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.*
import com.moooo_works.letsgogps.data.repository.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "MockGPS/Billing"
        const val SUBSCRIPTION_ID = "mockgps_pro_monthly"
        // 開發用：設為 true 強制解鎖所有 Pro 功能，上架前務必改回 false
        const val DEV_FORCE_PRO = false

        private val KEY_PRO_CACHE = booleanPreferencesKey("pro_state_cache")
        private const val MAX_RETRY = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryCount = 0

    private val _isProActive = MutableStateFlow(DEV_FORCE_PRO)
    val isProActive: StateFlow<Boolean> = _isProActive.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        // 從 DataStore 讀取上次已知的 Pro 狀態，在 Billing 連線前給初始值
        scope.launch {
            val cached = context.dataStore.data.first()[KEY_PRO_CACHE] ?: false
            if (!DEV_FORCE_PRO) _isProActive.value = cached
        }
        connect()
        registerForegroundObserver()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    retryCount = 0
                    queryActiveSubscriptions()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected (retry $retryCount/$MAX_RETRY)")
                if (retryCount < MAX_RETRY) {
                    val delay = RETRY_BASE_DELAY_MS * (1L shl retryCount) // 2s, 4s, 8s
                    retryCount++
                    Handler(Looper.getMainLooper()).postDelayed({ connect() }, delay)
                } else {
                    Log.w(TAG, "Billing max retries reached, giving up")
                }
            }
        })
    }

    private fun registerForegroundObserver() {
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // App 回到前景時重新驗證訂閱狀態
                    if (billingClient.isReady) {
                        queryActiveSubscriptions()
                    } else {
                        retryCount = 0
                        connect()
                    }
                }
            })
        }
    }

    fun queryActiveSubscriptions() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = DEV_FORCE_PRO || purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _isProActive.value = active
                Log.d(TAG, "Pro active: $active (${purchases.size} purchases)")
                // 持久化最新狀態，供下次冷啟動前使用
                scope.launch {
                    context.dataStore.edit { it[KEY_PRO_CACHE] = active }
                }
                purchases.filter { !it.isAcknowledged }.forEach { acknowledgePurchase(it) }
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        if (!billingClient.isReady) {
            Log.w(TAG, "BillingClient not ready, retrying connection")
            Toast.makeText(context, "[Dev] Billing 尚未連線，請稍後再試", Toast.LENGTH_SHORT).show()
            retryCount = 0
            connect()
            return
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isEmpty()) {
                Log.w(TAG, "Product details not found: ${result.debugMessage}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "[Dev] 商品尚未在 Play Console 建立 (${result.responseCode})", Toast.LENGTH_LONG).show()
                }
                return@queryProductDetailsAsync
            }
            val productDetails = productDetailsList.first()
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()?.offerToken ?: return@queryProductDetailsAsync

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        _isProActive.value = true
                        scope.launch {
                            context.dataStore.edit { it[KEY_PRO_CACHE] = true }
                        }
                        acknowledgePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Log.d(TAG, "User cancelled")
            else -> Log.w(TAG, "Purchase error: ${result.debugMessage}")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Log.d(TAG, "Acknowledge result: ${result.responseCode}")
        }
    }
}
