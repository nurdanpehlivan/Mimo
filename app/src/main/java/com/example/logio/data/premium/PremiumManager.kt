/*
 * PremiumManager: "Mimo Premium" tek seferlik satın alımını Google Play Faturalandırma ile yönetir.
 *
 * - Ürün Play Console → Monetize → Products → In-app products altında PRODUCT_ID adıyla tanımlanır.
 * - Satın alma kullanıcının Google hesabına bağlıdır; uygulama silinip kurulsa da refresh() ile geri gelir.
 * - Sunucu yok: satın alma telefonda Play'den sorgulanır. Son bilinen durum SharedPreferences'ta tutulur,
 *   böylece internet yokken de premium açık kalır.
 * - Satın alma 3 gün içinde onaylanmazsa (acknowledge) Google parayı iade eder; onay burada yapılır.
 */
package com.example.logio.data.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Satın alma sırasında ekrana gösterilecek tek seferlik sonuçlar. */
enum class PremiumEvent { PURCHASED, PENDING, FAILED, UNAVAILABLE, RESTORED, NOTHING_TO_RESTORE }

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("mimo_premium", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** Play'den gelen, kullanıcının para birimiyle biçimlenmiş fiyat (ör. "₺49,99"); gelene kadar null. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _events = Channel<PremiumEvent>(Channel.BUFFERED)
    val events: Flow<PremiumEvent> = _events.receiveAsFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Satın almayı ve fiyatı Play'den yeniden okur. [userAsked] ise sonuç ekranda bildirilir ("Satın alımı geri yükle"). */
    fun refresh(userAsked: Boolean = false) {
        scope.launch {
            if (!connect()) {
                if (userAsked) _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
            loadProductDetails()
            val result = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            )
            // Sorgu başarısızsa (internet yok vb.) son bilinen durum korunur
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                if (userAsked) _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
            val owned = handlePurchases(result.purchasesList)
            // İade edilen satın alma listeden düşer: premium kapanır
            if (!owned) setPremium(false)
            if (userAsked) _events.send(if (owned) PremiumEvent.RESTORED else PremiumEvent.NOTHING_TO_RESTORE)
        }
    }

    /** Google Play'in satın alma penceresini açar. */
    fun launchPurchase(activity: Activity) {
        scope.launch {
            val details = if (connect()) productDetails ?: loadProductDetails() else null
            if (details == null) {
                _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .apply { details.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) } }
                .build()
            val result = billingClient.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
            )
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> Unit // Sonuç onPurchasesUpdated'a gelir
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(userAsked = true)
                else -> {
                    Timber.w("Satın alma penceresi açılamadı: ${result.responseCode} ${result.debugMessage}")
                    _events.send(PremiumEvent.FAILED)
                }
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases.orEmpty().filter { PRODUCT_ID in it.products }
                    when {
                        handlePurchases(list) -> _events.send(PremiumEvent.PURCHASED)
                        list.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> _events.send(PremiumEvent.PENDING)
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> Unit
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(userAsked = true)
                else -> {
                    Timber.w("Satın alma başarısız: ${result.responseCode} ${result.debugMessage}")
                    _events.send(PremiumEvent.FAILED)
                }
            }
        }
    }

    /** Tamamlanmış premium satın alması varsa onaylar ve premium'u açar; varsa true döner. */
    private suspend fun handlePurchases(purchases: List<Purchase>): Boolean {
        val purchase = purchases.firstOrNull {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: return false
        if (!purchase.isAcknowledged) {
            val ack = billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            )
            // Onay olmazsa bir sonraki refresh() yeniden dener; o zamana kadar premium açık kalır
            if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("Satın alma onaylanamadı: ${ack.responseCode}")
            }
        }
        setPremium(true)
        return true
    }

    private suspend fun loadProductDetails(): ProductDetails? {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        )
        val details = result.productDetailsList?.firstOrNull()
        if (details != null) {
            productDetails = details
            _price.value = details.oneTimePurchaseOfferDetails?.formattedPrice
        }
        return details
    }

    private suspend fun connect(): Boolean {
        if (billingClient.isReady) return true
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private fun setPremium(value: Boolean) {
        if (_isPremium.value == value) return
        _isPremium.value = value
        prefs.edit().putBoolean(KEY_PREMIUM, value).apply()
    }

    companion object {
        /** Play Console'daki uygulama içi ürünün kimliği; sonradan değiştirilemez. */
        const val PRODUCT_ID = "mimo_premium"
        private const val KEY_PREMIUM = "premium"
    }
}
