package com.elyacoub9.flowsy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.AppLanguage
import com.elyacoub9.flowsy.data.LanguageManager
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.ui.backup.BackupController
import com.elyacoub9.flowsy.ui.backup.RestoreController
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: AppLanguage,
    val currencyCode: String,
    /** True once any income, expense or pot exists. */
    val currencyLocked: Boolean,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val languageManager: LanguageManager,
    val today: () -> LocalDate,
    backupFactory: (CoroutineScope) -> BackupController,
    restoreFactory: (CoroutineScope) -> RestoreController,
) : ViewModel() {

    val backup = backupFactory(viewModelScope)
    val restore = restoreFactory(viewModelScope)

    val state: StateFlow<SettingsUiState?> = combine(
        settingsRepository.observe().filterNotNull(),
        settingsRepository.observeCurrencyLocked(),
    ) { settings, locked ->
        SettingsUiState(language = languageManager.current(), currencyCode = settings.currencyCode, currencyLocked = locked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setLanguageTag(language.tag)
            languageManager.apply(language)
        }
    }

    /** Ignored by the repository if a record was added meanwhile (checked in its transaction). */
    fun selectCurrency(code: String) = viewModelScope.launch { settingsRepository.setCurrency(code) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                SettingsViewModel(
                    c.settingsRepository, c.languageManager, c.today,
                    backupFactory = { BackupController(c.backupRepository, c.documentStore, it) },
                    restoreFactory = { RestoreController(c.backupRepository, c.documentStore, c.languageManager, c.restoreCount, it) },
                )
            }
        }
    }
}
