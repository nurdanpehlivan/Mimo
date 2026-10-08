package com.example.logio

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.logio.ui.analysis.AnalysisScreen
import com.example.logio.ui.calendar.CalendarScreen
import com.example.logio.ui.navigation.BottomTab
import com.example.logio.ui.navigation.FloatingBottomBar
import com.example.logio.ui.profile.ProfileScreen
import com.example.logio.ui.theme.LogioTheme
import com.example.logio.ui.timeline.TimelineScreen
import com.example.logio.ui.timeline.TimelineViewModel
import com.example.logio.util.AppLocale
import com.example.logio.ui.lock.LockScreen
import com.example.logio.util.AppLock
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // Uygulama kilidi açıksa günlük, doğrulama yapılana kadar gösterilmez
    private var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked = savedInstanceState?.getBoolean(KEY_LOCKED) ?: AppLock.isEnabled(this)

        // App Check Debug Provider'ı başlatıyoruz
        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        enableEdgeToEdge()
        setContent {
            LogioTheme {
                if (locked) LockScreen(onUnlock = ::unlock) else LogioRoot()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Kısa süreli çıkışlarda (foto seçici, kamera, doğrulama ekranı) tekrar kilitlenmez
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (backgroundedAt != 0L && away > AppLock.RELOCK_AFTER_MS && AppLock.isEnabled(this)) locked = true
        backgroundedAt = 0L
        // Kilit açıksa son uygulamalar ekranında günlüğün önizlemesi görünmesin
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!AppLock.isEnabled(this))
    }

    private fun unlock() {
        // Telefonun ekran kilidi sonradan kaldırıldıysa doğrulama yapılamaz; kullanıcı dışarıda kalmasın
        if (!AppLock.isAvailable(this)) {
            AppLock.setEnabled(this, false)
            locked = false
            return
        }
        AppLock.authenticate(
            activity = this,
            title = getString(R.string.lock_prompt_title),
            subtitle = getString(R.string.lock_prompt_subtitle),
            onSuccess = { locked = false },
            onError = { message -> message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() } }
        )
    }

    private companion object {
        const val KEY_LOCKED = "app_locked"
    }
}

/** Alt bardaki sekmeler arasında geçiş; İstatistik dışındaki ekranlar aynı TimelineViewModel'i paylaşır. */
@Composable
private fun LogioRoot(viewModel: TimelineViewModel = hiltViewModel()) {
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.JOURNAL) }

    // Diğer sekmelerde geri tuşu önce Günlük'e döner
    BackHandler(enabled = selectedTab != BottomTab.JOURNAL) { selectedTab = BottomTab.JOURNAL }

    val bottomBar: @Composable () -> Unit = {
        FloatingBottomBar(selectedTab = selectedTab, onTabClick = { selectedTab = it })
    }

    when (selectedTab) {
        BottomTab.JOURNAL -> TimelineScreen(viewModel = viewModel, onNavigate = { selectedTab = it })
        BottomTab.CALENDAR -> CalendarScreen(viewModel = viewModel, bottomBar = bottomBar)
        BottomTab.STATS -> AnalysisScreen(bottomBar = bottomBar)
        BottomTab.PROFILE -> ProfileScreen(viewModel = viewModel, bottomBar = bottomBar)
    }
}
