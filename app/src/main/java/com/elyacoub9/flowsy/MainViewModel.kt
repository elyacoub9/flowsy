package com.elyacoub9.flowsy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.LanguageManager
import com.elyacoub9.flowsy.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

enum class AppState { LOADING, ONBOARDING, READY }

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val languageManager: LanguageManager,
    private val dayTracker: DayTracker,
    /** Bumped after a restore: the screens start over on Month, current month. */
    val restoreCount: StateFlow<Int>,
) : ViewModel() {

    val state: StateFlow<AppState> = settingsRepository.observe()
        .onEach { settings ->
            // AppCompat is the live language; keep the stored copy in sync (e.g. after a
            // change in the system's per-app language screen).
            val live = languageManager.current().tag
            if (settings != null && settings.languageTag != live) {
                settingsRepository.setLanguageTag(live)
            }
        }
        .map { if (it?.onboardingDone == true) AppState.READY else AppState.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.LOADING)

    /** Every resume: pick up date changes. */
    fun onResume() {
        dayTracker.refresh()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                MainViewModel(c.settingsRepository, c.languageManager, c.dayTracker, c.restoreCount)
            }
        }
    }
}
