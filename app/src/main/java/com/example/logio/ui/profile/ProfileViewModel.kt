/*
 * ProfileViewModel: Profil sekmesinin ViewModel'i.
 *
 * Kullanıcı profilini (isim, su ve adım hedefi) ProfileRepository'den, kayıtları JournalRepository'den alır.
 * Profil özetini (seri, rozetler, en çok kullanılan türler) arka planda hesaplar (ProfileStats.kt).
 * "Geçmişten bir anı" kartındaki kayıt için silme ve yapılacak işaretleme de buradan yapılır.
 * Ayarlardaki "Yedeği dışa aktar / Yedekten geri yükle" işlemleri BackupManager üzerinden yürütülür.
 * Premium durumu PremiumManager'dan gelir (satın alma penceresi PremiumDialog'dadır).
 */
package com.example.logio.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.backup.BackupManager
import com.example.logio.data.backup.NotABackupException
import com.example.logio.data.backup.WrongPasswordException
import com.example.logio.data.journal.JournalRepository
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.profile.ProfileRepository
import com.example.logio.data.profile.UserProfile
import com.example.logio.data.premium.PremiumManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import timber.log.Timber
import javax.inject.Inject

/** Yedekleme işleminin durumu; ekran buna göre bekleme penceresi ya da sonuç mesajı gösterir. */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data class Exported(val entryCount: Int) : BackupStatus
    data class Imported(val added: Int, val skipped: Int) : BackupStatus
    data object WrongPassword : BackupStatus
    data object Failed : BackupStatus
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val profileRepository: ProfileRepository,
    private val backupManager: BackupManager,
    premiumManager: PremiumManager
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = premiumManager.isPremium

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    /** Günlüğü [target] dosyasına şifreli yedekler. Şifre işlem bitince bellekten silinir. */
    fun exportBackup(target: Uri, password: CharArray) = runBackup(password) {
        BackupStatus.Exported(backupManager.export(target, password))
    }

    /** [source] yedeğini geri yükler. Şifre işlem bitince bellekten silinir. */
    fun importBackup(source: Uri, password: CharArray) = runBackup(password) {
        backupManager.import(source, password).let { BackupStatus.Imported(it.added, it.skipped) }
    }

    fun clearBackupStatus() {
        _backupStatus.value = BackupStatus.Idle
    }

    private fun runBackup(password: CharArray, work: suspend () -> BackupStatus) {
        if (_backupStatus.value == BackupStatus.Working) return
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            _backupStatus.value = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongPasswordException) {
                BackupStatus.WrongPassword
            } catch (e: NotABackupException) {
                BackupStatus.WrongPassword
            } catch (e: Exception) {
                Timber.e(e, "Yedekleme işlemi başarısız")
                BackupStatus.Failed
            } finally {
                password.fill('\u0000')
            }
        }
    }

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
