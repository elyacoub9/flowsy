package com.elyacoub9.flowsy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.AppLanguage
import com.elyacoub9.flowsy.data.LanguageManager
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.domain.money.CurrencyDefaults
import com.elyacoub9.flowsy.ui.backup.RestoreController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class OnboardingStep { LANGUAGE, CURRENCY }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LANGUAGE,
    val language: AppLanguage,
    val currencyCode: String,
    val currencyQuery: String = "",
    val saving: Boolean = false,
)

/**
 * Activity-scoped, so the state survives the recreate triggered by a language change.
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val languageManager: LanguageManager,
    systemLocale: Locale,
    restoreFactory: (CoroutineScope) -> RestoreController,
) : ViewModel() {

    /** "Restore from backup" for a new phone: the restored settings finish onboarding. */
    val restore = restoreFactory(viewModelScope)

    private val _state = MutableStateFlow(
        OnboardingUiState(
            language = languageManager.current(),
            currencyCode = CurrencyDefaults.forRegion(systemLocale.country).currencyCode,
        )
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun selectLanguage(language: AppLanguage) {
        _state.update { it.copy(language = language) }
        languageManager.apply(language)
    }

    fun selectCurrency(code: String) = _state.update { it.copy(currencyCode = code) }

    fun updateCurrencyQuery(query: String) = _state.update { it.copy(currencyQuery = query) }

    fun next() = _state.update { state ->
        val steps = OnboardingStep.entries
        state.copy(step = steps.getOrElse(state.step.ordinal + 1) { state.step })
    }

    /** Returns false when already on the first step. */
    fun back(): Boolean {
        val current = _state.value.step
        if (current.ordinal == 0) return false
        _state.update { it.copy(step = OnboardingStep.entries[current.ordinal - 1]) }
        return true
    }

    fun finish() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            settingsRepository.completeOnboarding(currencyCode = s.currencyCode, languageTag = s.language.tag)
            languageManager.apply(s.language)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                OnboardingViewModel(
                    c.settingsRepository, c.languageManager, c.systemLocale(),
                    restoreFactory = { RestoreController(c.backupRepository, c.documentStore, c.languageManager, c.restoreCount, it) },
                )
            }
        }
    }
}
