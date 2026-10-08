package com.example.logio.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.profile.ProfileRepository
import com.example.logio.data.profile.UserProfile
import com.example.logio.data.steps.StepCounterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** İstatistik ekranının çizmek için ihtiyaç duyduğu her şey. */
internal data class AnalysisUiState(
    val period: StatsPeriod,
    val today: LocalDate,
    val stats: PeriodStats,
    val lastSevenDays: List<LocalDate>,
    val summaries: Map<LocalDate, DaySummary>
)

/** Günlük özetler ve hesaplandıkları kayıtlar (dönem değişince yeniden hesaplanmaz). */
private class DayIndex(
    val entries: List<JournalEntryEntity>,
    val zone: ZoneId,
    val today: LocalDate,
    val summaries: Map<LocalDate, DaySummary>
)

/**
 * İstatistikleri ekran dışında, arka planda hesaplar.
 * Kayıtlar Room'dan doğrudan okunur; yeni kayıt eklenince istatistikler kendiliğinden güncellenir.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    journalDao: JournalDao,
    stepCounterManager: StepCounterManager,
    profileRepository: ProfileRepository
) : ViewModel() {

    val profile: StateFlow<UserProfile> = profileRepository.profile

    private val period = MutableStateFlow(StatsPeriod.WEEK)

    // 1. aşama: gün gün özet. Yalnızca kayıtlar ya da bugünkü sensör adımı değişince çalışır
    private val dayIndex = combine(
        journalDao.getAllEntries(),
        stepCounterManager.uiState.map { it.todaySteps }.distinctUntilChanged()
    ) { entries, sensorStepsToday ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        DayIndex(entries, zone, today, summarizeDays(entries, zone, today, sensorStepsToday))
    }.flowOn(Dispatchers.Default)

    // 2. aşama: seçili dönemin özeti. Dönem değişince yalnızca bu kısım yeniden hesaplanır
    internal val uiState: StateFlow<AnalysisUiState?> = combine(dayIndex, period) { index, selected ->
        AnalysisUiState(
            period = selected,
            today = index.today,
            stats = computePeriodStats(index.entries, index.summaries, selected, index.today, index.zone),
            lastSevenDays = (6 downTo 0).map { index.today.minusDays(it.toLong()) },
            summaries = index.summaries
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = null)

    internal fun selectPeriod(selected: StatsPeriod) {
        period.value = selected
    }
}
