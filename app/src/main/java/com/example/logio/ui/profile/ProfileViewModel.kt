/*
 * ProfileViewModel: Profil sekmesinin ViewModel'i.
 *
 * Kullanıcı profilini (isim, su ve adım hedefi) ProfileRepository'den, kayıtları JournalRepository'den alır.
 * Profil özetini (seri, rozetler, en çok kullanılan türler) arka planda hesaplar (ProfileStats.kt).
 * "Geçmişten bir anı" kartındaki kayıt için silme ve yapılacak işaretleme de buradan yapılır.
 */
package com.example.logio.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.journal.JournalRepository
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.profile.ProfileRepository
import com.example.logio.data.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val profile: StateFlow<UserProfile> = profileRepository.profile

    val entries: StateFlow<List<JournalEntryEntity>> = journalRepository.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Özet yalnızca kayıtlar ya da su hedefi değişince yeniden hesaplanır (isim değişince değil)
    internal val summary: StateFlow<ProfileSummary?> = combine(
        journalRepository.entries,
        profileRepository.profile.map { it.waterGoalMl }.distinctUntilChanged()
    ) { entries, waterGoalMl ->
        val zone = ZoneId.systemDefault()
        summarizeProfile(entries, waterGoalMl, zone, LocalDate.now(zone))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = null)

    fun updateProfile(profile: UserProfile) = profileRepository.update(profile)

    fun deleteEntry(entry: JournalEntryEntity) {
        viewModelScope.launch { journalRepository.delete(entry) }
    }

    fun toggleTodoItem(entry: JournalEntryEntity, index: Int) {
        viewModelScope.launch { journalRepository.toggleTodoItem(entry, index) }
    }
}
