/*
 * PremiumDialog: "Mimo Premium" tanıtım ve satın alma penceresi.
 *
 * Profil → Ayarlar'dan, Journey videosunda kilitli bir seçeneğe dokununca ve günlük ücretsiz
 * öğün analizi hakkı bitince açılır. Satın alma PremiumManager üzerinden Google Play ile yapılır.
 */
package com.example.logio.ui.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.Image
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
import com.example.logio.data.premium.PremiumEvent
import com.example.logio.data.premium.PremiumManager
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
    val price: StateFlow<String?> = premiumManager.price
    val events: Flow<PremiumEvent> = premiumManager.events

    init {
        // Fiyat pencere açılınca Play'den yeniden okunur
        premiumManager.refresh()
    }

    fun buy(activity: Activity) = premiumManager.launchPurchase(activity)
    fun restore() = premiumManager.refresh(userAsked = true)
}

@Composable
fun PremiumDialog(
    reason: PremiumReason,
    onDismiss: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val price by viewModel.price.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

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
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { context.findActivity()?.let { viewModel.buy(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = MimoPurple),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = price?.let { stringResource(R.string.premium_buy_price, it) } ?: stringResource(R.string.premium_buy),
                        fontWeight = FontWeight.SemiBold
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
