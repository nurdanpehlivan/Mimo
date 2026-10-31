/*
 * PremiumDialog: "Mimo Premium" tanıtım ve satın alma penceresi.
 *
 * Profil → Ayarlar'dan, Journey videosunda kilitli bir seçeneğe dokununca ve günlük ücretsiz
 * öğün analizi hakkı bitince açılır. Üç seçenek var: yıllık abonelik (uygunsa ücretsiz denemeyle), aylık abonelik
 * ve ömür boyu.
 * Satın alma PremiumManager üzerinden Google Play ile yapılır. Abonelikte fiyat, deneme süresi ve otomatik
 * yenileme butonun altında açıkça yazılır (Google Play abonelik politikası bunu ister).
 */
package com.example.logio.ui.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.logio.R
import com.example.logio.data.premium.MealAnalysisQuota
import com.example.logio.data.premium.PlanOffer
import com.example.logio.data.premium.PremiumEvent
import com.example.logio.data.premium.PremiumManager
import com.example.logio.data.premium.PremiumPlan
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Pencerenin neden açıldığı; üstteki açıklama buna göre değişir. */
enum class PremiumReason { GENERAL, JOURNEY, MEAL_LIMIT }

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val premiumManager: PremiumManager
) : ViewModel() {
    val isPremium: StateFlow<Boolean> = premiumManager.isPremium
    val offers: StateFlow<Map<PremiumPlan, PlanOffer>> = premiumManager.offers
    val events: Flow<PremiumEvent> = premiumManager.events

    init {
        // Fiyatlar pencere açılınca Play'den yeniden okunur
        premiumManager.refresh()
    }

    fun buy(activity: Activity, plan: PremiumPlan) = premiumManager.launchPurchase(activity, plan)
    fun restore() = premiumManager.refresh(userAsked = true)
}

@Composable
fun PremiumDialog(
    reason: PremiumReason,
    onDismiss: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val offers by viewModel.offers.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    // Varsayılan seçim yıllık; Play'de yıllık yoksa (henüz tanımlanmadıysa) ömür boyu
    var selected by remember { mutableStateOf(PremiumPlan.YEARLY) }
    LaunchedEffect(offers) {
        if (selected !in offers && offers.isNotEmpty()) selected = offers.keys.first()
    }
    val yearly = offers[PremiumPlan.YEARLY]
    val monthly = offers[PremiumPlan.MONTHLY]
    val lifetime = offers[PremiumPlan.LIFETIME]

    val messages = mapOf(
        PremiumEvent.PURCHASED to stringResource(R.string.premium_msg_purchased),
        PremiumEvent.RESTORED to stringResource(R.string.premium_msg_restored),
        PremiumEvent.PENDING to stringResource(R.string.premium_msg_pending),
        PremiumEvent.FAILED to stringResource(R.string.premium_msg_failed),
        PremiumEvent.UNAVAILABLE to stringResource(R.string.premium_msg_unavailable),
        PremiumEvent.NOTHING_TO_RESTORE to stringResource(R.string.premium_msg_nothing_to_restore)
    )
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            messages[event]?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    // Satın alma tamamlanınca (ya da geri yüklenince) pencere kendiliğinden kapanır
    LaunchedEffect(isPremium) {
        if (isPremium) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.ic_mascot_premium),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.premium_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MimoNavy
                )
                Text(
                    text = when (reason) {
                        PremiumReason.MEAL_LIMIT -> stringResource(R.string.premium_reason_meal, MealAnalysisQuota.FREE_DAILY_LIMIT)
                        PremiumReason.JOURNEY -> stringResource(R.string.premium_reason_journey)
                        PremiumReason.GENERAL -> stringResource(R.string.premium_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MimoNavyMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                BenefitRow("🎬", stringResource(R.string.premium_benefit_journey), stringResource(R.string.premium_benefit_journey_desc))
                Spacer(modifier = Modifier.height(8.dp))
                BenefitRow("🍽️", stringResource(R.string.premium_benefit_meal), stringResource(R.string.premium_benefit_meal_desc, MealAnalysisQuota.FREE_DAILY_LIMIT))
                Spacer(modifier = Modifier.height(8.dp))
                BenefitRow("💜", stringResource(R.string.premium_benefit_once), stringResource(R.string.premium_benefit_once_desc))
                Spacer(modifier = Modifier.height(16.dp))
                if (yearly != null) {
                    PlanCard(
                        title = stringResource(R.string.premium_plan_yearly),
                        price = stringResource(R.string.premium_plan_yearly_price, yearly.price),
                        badge = yearly.freeTrialDays?.let { stringResource(R.string.premium_trial_badge, it) },
                        selected = selected == PremiumPlan.YEARLY,
                        onClick = { selected = PremiumPlan.YEARLY }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (monthly != null) {
                    PlanCard(
                        title = stringResource(R.string.premium_plan_monthly),
                        price = stringResource(R.string.premium_plan_monthly_price, monthly.price),
                        badge = monthly.freeTrialDays?.let { stringResource(R.string.premium_trial_badge, it) },
                        selected = selected == PremiumPlan.MONTHLY,
                        onClick = { selected = PremiumPlan.MONTHLY }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (lifetime != null) {
                    PlanCard(
                        title = stringResource(R.string.premium_plan_lifetime),
                        price = stringResource(R.string.premium_plan_lifetime_price, lifetime.price),
                        badge = null,
                        selected = selected == PremiumPlan.LIFETIME,
                        onClick = { selected = PremiumPlan.LIFETIME }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                val chosen = offers[selected]
                Button(
                    onClick = { context.findActivity()?.let { viewModel.buy(it, selected) } },
                    colors = ButtonDefaults.buttonColors(containerColor = MimoPurple),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = when {
                            chosen == null -> stringResource(R.string.premium_buy)
                            chosen.plan != PremiumPlan.LIFETIME && chosen.freeTrialDays != null -> stringResource(R.string.premium_start_trial)
                            chosen.plan != PremiumPlan.LIFETIME -> stringResource(R.string.premium_subscribe)
                            else -> stringResource(R.string.premium_buy_price, chosen.price)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // Abonelik koşulları: ne zaman, ne kadar ücret alınacağı ve nasıl iptal edileceği
                if (chosen != null && chosen.plan != PremiumPlan.LIFETIME) {
                    val monthlyPlan = chosen.plan == PremiumPlan.MONTHLY
                    Text(
                        text = when {
                            chosen.freeTrialDays != null && monthlyPlan -> stringResource(R.string.premium_terms_trial_monthly, chosen.freeTrialDays, chosen.price)
                            chosen.freeTrialDays != null -> stringResource(R.string.premium_terms_trial, chosen.freeTrialDays, chosen.price)
                            monthlyPlan -> stringResource(R.string.premium_terms_monthly, chosen.price)
                            else -> stringResource(R.string.premium_terms_yearly, chosen.price)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MimoNavyMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                TextButton(onClick = { viewModel.restore() }) {
                    Text(stringResource(R.string.premium_restore), color = MimoPurple)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.premium_later), color = Color(0xFF64748B)) }
        }
    )
}

/** Bir satın alma seçeneği: seçiliyse mor çerçeveli. [badge] örn. "7 gün ücretsiz". */
@Composable
private fun PlanCard(title: String, price: String, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MimoCloud else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MimoPurple else Color(0xFFE5E0F2)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp, top = 6.dp, bottom = 6.dp)) {
            RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MimoPurple))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MimoNavy)
                Text(price, style = MaterialTheme.typography.bodySmall, color = MimoNavyMuted)
            }
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(MimoPurple, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(emoji: String, title: String, description: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MimoCloud, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MimoNavy)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MimoNavyMuted)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
