/*
 * AnalysisScreen: İstatistik sekmesinin sayfa düzeni.
 *
 * Kartları alt alta dizer: başlık, dönem seçici, uyku/ruh hali/adım/beslenme/kahve/alkol kartları,
 * harcama kartı ve haftalık akış grafiği. Hesap yapmaz; hazır sonucu AnalysisViewModel'den alır.
 */
package com.example.logio.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.logio.ui.theme.MimoBackground

/**
 * İstatistik sekmesi: yalnızca sayfa düzeni.
 * Hesaplar AnalysisViewModel'de (StatsCalculator.kt), kartlar AnalysisCards.kt, grafikler AnalysisCharts.kt içinde.
 */
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val profile by viewModel.profile.collectAsState()

    MimoBackground {
        Scaffold(containerColor = Color.Transparent, bottomBar = bottomBar) { innerPadding ->
            // İlk hesap arka planda sürerken yalnızca alt menü görünür
            val current = state ?: return@Scaffold
            val stats = current.stats
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                AnalysisHeader(
                    entryCount = stats.entryCount,
                    periodDescription = stringResource(current.period.description),
                    onBack = onBack
                )
                Spacer(modifier = Modifier.height(16.dp))

                PeriodSelector(selected = current.period, onSelect = viewModel::selectPeriod)
                Spacer(modifier = Modifier.height(16.dp))

                // 2x2 metrik kartları
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SleepMetricCard(stats, Modifier.weight(1f).fillMaxHeight())
                    MoodMetricCard(stats, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepsMetricCard(stats, profile.stepGoal, Modifier.weight(1f).fillMaxHeight())
                    NutritionMetricCard(stats, profile.waterGoalMl, Modifier.weight(1f).fillMaxHeight())
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoffeeMetricCard(stats, Modifier.weight(1f).fillMaxHeight())
                    AlcoholMetricCard(stats, current.period, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(12.dp))
                ExpenseCard(stats, current.period)

                Spacer(modifier = Modifier.height(20.dp))
                WeeklyFlowCard(days = current.lastSevenDays, summaries = current.summaries, today = current.today, stepGoal = profile.stepGoal)
            }
        }
    }
}
