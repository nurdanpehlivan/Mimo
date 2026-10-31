/*
 * PremiumManager: "Mimo Premium" satın alımlarını Google Play Faturalandırma ile yönetir.
 *
 * Üç yol var, hepsi aynı Premium'u açar:
 * - Abonelik (SUBSCRIPTION_PRODUCT_ID): Play Console → Monetize → Products → Subscriptions. İçinde iki temel plan
 *   var: yıllık (YEARLY_BASE_PLAN) ve aylık (MONTHLY_BASE_PLAN). Yeni abonelere ücretsiz deneme teklifi varsa
 *   Play onu verir; deneme süresi teklifin kendisinden okunur.
 * - Ömür boyu (LIFETIME_PRODUCT_ID): Play Console → Monetize → Products → One-time products. Tek seferlik ödeme.
 *
 * - Satın alma kullanıcının Google hesabına bağlıdır; uygulama silinip kurulsa da refresh() ile geri gelir.
 * - Sunucu yok: satın almalar telefonda Play'den sorgulanır. Abonelik iptal edilip süresi dolunca ya da
 *   satın alma iade edilince Play listesinden düşer ve Premium kapanır. Son bilinen durum SharedPreferences'ta
 *   tutulur, böylece internet yokken de Premium açık kalır.
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

enum class PremiumPlan { YEARLY, MONTHLY, LIFETIME }

/**
 * Satın alma penceresinde gösterilen bir seçenek. [price]: Play'in kullanıcının para birimiyle biçimlediği fiyat
 * (abonelikte yıllık yenileme fiyatı). [freeTrialDays]: kullanıcı ücretsiz denemeye uygunsa gün sayısı.
 */
data class PlanOffer(val plan: PremiumPlan, val price: String, val freeTrialDays: Int? = null)

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("mimo_premium", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** Premium abonelikten mi geliyor (Profil'de "Aboneliği yönet" bağlantısı için). */
    private val _isSubscribed = MutableStateFlow(prefs.getBoolean(KEY_SUBSCRIBED, false))
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    /** Play'den okunan seçenekler; Play'e ulaşılana ya da ürün Play Console'da tanımlanana kadar boş. */
    private val _offers = MutableStateFlow<Map<PremiumPlan, PlanOffer>>(emptyMap())
    val offers: StateFlow<Map<PremiumPlan, PlanOffer>> = _offers.asStateFlow()

    private val _events = Channel<PremiumEvent>(Channel.BUFFERED)
    val events: Flow<PremiumEvent> = _events.receiveAsFlow()

    private var subscriptionDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Satın almaları ve fiyatları Play'den yeniden okur. [userAsked] ise sonuç ekranda bildirilir ("Satın alımı geri yükle"). */
    fun refresh(userAsked: Boolean = false) {
        scope.launch {
            if (!connect()) {
                if (userAsked) _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
            loadProductDetails()
            val inApp = queryPurchases(BillingClient.ProductType.INAPP)
            val subs = queryPurchases(BillingClient.ProductType.SUBS)
            // Sorgulardan biri başarısızsa (internet yok vb.) son bilinen durum korunur
            if (inApp == null || subs == null) {
                if (userAsked) _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
            val owned = handlePurchases(inApp + subs)
            // Süresi dolan abonelik ya da iade edilen satın alma listeden düşer: Premium kapanır
            if (!owned) setPremium(premium = false, subscribed = false)
            if (userAsked) _events.send(if (owned) PremiumEvent.RESTORED else PremiumEvent.NOTHING_TO_RESTORE)
        }
    }

    /** Seçilen plan için Google Play'in satın alma penceresini açar. */
    fun launchPurchase(activity: Activity, plan: PremiumPlan) {
        scope.launch {
            if (!connect()) {
                _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
            if (subscriptionDetails == null || lifetimeDetails == null) loadProductDetails()
            val productParams = when (plan) {
                PremiumPlan.YEARLY, PremiumPlan.MONTHLY -> subscriptionDetails?.let { details ->
                    // Abonelikte teklif belirteci zorunlu: deneme teklifi (uygunsa) ya da temel plan
                    val offer = bestSubscriptionOffer(details, basePlanOf(plan)) ?: return@let null
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                }
                PremiumPlan.LIFETIME -> lifetimeDetails?.let { details ->
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .apply { details.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) } }
                        .build()
                }
            }
            if (productParams == null) {
                _events.send(PremiumEvent.UNAVAILABLE)
                return@launch
            }
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
                    val list = purchases.orEmpty().filter { it.isPremiumProduct() }
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

    /** Tamamlanmış Premium satın almalarını onaylar ve Premium'u açar; en az biri varsa true döner. */
    private suspend fun handlePurchases(purchases: List<Purchase>): Boolean {
        val completed = purchases.filter { it.isPremiumProduct() && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (completed.isEmpty()) return false
        completed.filterNot { it.isAcknowledged }.forEach { purchase ->
            val ack = billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            )
            // Onay olmazsa bir sonraki refresh() yeniden dener; o zamana kadar Premium açık kalır
            if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("Satın alma onaylanamadı: ${ack.responseCode}")
            }
        }
        // Ömür boyu da varsa "Aboneliği yönet" gösterilmez: kullanıcı aboneliği iptal etse de Premium sürer
        val hasLifetime = completed.any { LIFETIME_PRODUCT_ID in it.products }
        setPremium(premium = true, subscribed = !hasLifetime && completed.any { SUBSCRIPTION_PRODUCT_ID in it.products })
        return true
    }

    private fun Purchase.isPremiumProduct() = LIFETIME_PRODUCT_ID in products || SUBSCRIPTION_PRODUCT_ID in products

    /** null: sorgu başarısız (son bilinen durum korunmalı). */
    private suspend fun queryPurchases(type: String): List<Purchase>? {
        val result = billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) result.purchasesList else null
    }

    private suspend fun loadProductDetails() {
        subscriptionDetails = queryDetails(SUBSCRIPTION_PRODUCT_ID, BillingClient.ProductType.SUBS) ?: subscriptionDetails
        lifetimeDetails = queryDetails(LIFETIME_PRODUCT_ID, BillingClient.ProductType.INAPP) ?: lifetimeDetails

        val offers = buildMap {
            subscriptionDetails?.let { details ->
                for (plan in listOf(PremiumPlan.YEARLY, PremiumPlan.MONTHLY)) {
                    val offer = bestSubscriptionOffer(details, basePlanOf(plan)) ?: continue
                    val phases = offer.pricingPhases.pricingPhaseList
                    // Yenileme fiyatı son aşamadır (deneme varsa ilk aşama ücretsizdir)
                    val renewal = phases.lastOrNull() ?: continue
                    val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
                    put(plan, PlanOffer(plan, renewal.formattedPrice, trial?.let { isoPeriodToDays(it.billingPeriod) }))
                }
            }
            lifetimeDetails?.oneTimePurchaseOfferDetails?.let {
                put(PremiumPlan.LIFETIME, PlanOffer(PremiumPlan.LIFETIME, it.formattedPrice))
            }
        }
        if (offers.isNotEmpty()) _offers.value = offers
    }

    private suspend fun queryDetails(productId: String, type: String): ProductDetails? {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(type).build()
        val result = billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
        return result.productDetailsList?.firstOrNull()
    }

    /**
     * Bir temel planın (yıllık/aylık) en iyi teklifi. Play yalnızca kullanıcının uygun olduğu teklifleri döndürür:
     * ücretsiz deneme teklifi varsa o seçilir (daha önce deneme kullanan biri için gelmez), yoksa temel planın kendisi.
     */
    private fun bestSubscriptionOffer(details: ProductDetails, basePlanId: String): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == basePlanId }
        return offers.firstOrNull { offer -> offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.firstOrNull()
    }

    private fun basePlanOf(plan: PremiumPlan) = if (plan == PremiumPlan.MONTHLY) MONTHLY_BASE_PLAN else YEARLY_BASE_PLAN

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

    private fun setPremium(premium: Boolean, subscribed: Boolean) {
        _isPremium.value = premium
        _isSubscribed.value = subscribed
        prefs.edit().putBoolean(KEY_PREMIUM, premium).putBoolean(KEY_SUBSCRIBED, subscribed).apply()
    }

    companion object {
        /** Play Console'daki ürün ve temel plan kimlikleri; sonradan değiştirilemez.
         *  Abonelik ürününün adı "yearly" ile bitse de içinde hem yıllık hem aylık temel plan var. */
        const val LIFETIME_PRODUCT_ID = "mimo_premium"
        const val SUBSCRIPTION_PRODUCT_ID = "mimo_premium_yearly"
        const val YEARLY_BASE_PLAN = "yearly"
        const val MONTHLY_BASE_PLAN = "monthly"
        private const val KEY_PREMIUM = "premium"
        private const val KEY_SUBSCRIBED = "subscribed"

        /** Play'in abonelik sayfası (iptal / ödeme yöntemi); kullanıcı buradan aboneliğini yönetir. */
        fun manageSubscriptionUrl(packageName: String) =
            "https://play.google.com/store/account/subscriptions?sku=$SUBSCRIPTION_PRODUCT_ID&package=$packageName"

        /** ISO 8601 süre ("P7D", "P1W", "P1M") → gün; ay 30, yıl 365 gün sayılır. Anlaşılamazsa null. */
        internal fun isoPeriodToDays(period: String): Int? {
            val match = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""").matchEntire(period) ?: return null
            val (y, m, w, d) = match.destructured
            val days = (y.toIntOrNull() ?: 0) * 365 + (m.toIntOrNull() ?: 0) * 30 + (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
            return days.takeIf { it > 0 }
        }
    }
}
