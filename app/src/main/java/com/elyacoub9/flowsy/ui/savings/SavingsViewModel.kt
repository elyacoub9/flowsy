package com.elyacoub9.flowsy.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.PotInput
import com.elyacoub9.flowsy.data.PotRepository
import com.elyacoub9.flowsy.data.PotWithBalance
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.domain.pot.GoalProgress
import com.elyacoub9.flowsy.domain.pot.SavingsCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PotItem(
    val pot: PotWithBalance,
    val goal: GoalProgress?,
)

data class SavingsUiState(
    val currencyCode: String,
    val today: LocalDate,
    /** Sum of the active pots' balances; archived pots are not counted. */
    val total: Long,
    val active: List<PotItem>,
    /** Archived pots stay listed with their balance, read-only. */
    val archived: List<PotItem>,
) {
    companion object {
        fun build(currencyCode: String, today: LocalDate, pots: List<PotWithBalance>): SavingsUiState {
            // Goal states use today from DayTracker (the clock, re-read on every resume).
            val items = pots.map { PotItem(it, GoalProgress.of(it.balance, it.pot.goalAmount, it.pot.goalDate, today)) }
            return SavingsUiState(
                currencyCode = currencyCode,
                today = today,
                total = SavingsCalculator.total(pots.map { it.pot to it.balance }),
                active = items.filterNot { it.pot.pot.archived },
                archived = items.filter { it.pot.pot.archived },
            )
        }
    }
}

class SavingsViewModel(
    settingsRepository: SettingsRepository,
    private val potRepository: PotRepository,
    dayTracker: DayTracker,
) : ViewModel() {

    val state: StateFlow<SavingsUiState?> = combine(
        settingsRepository.observe().filterNotNull(),
        potRepository.observeAll(),
        dayTracker.day,
    ) { settings, pots, day -> SavingsUiState.build(settings.currencyCode, day, pots) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(input: PotInput) = viewModelScope.launch { potRepository.add(input) }

    fun move(id: Long, up: Boolean) = viewModelScope.launch { potRepository.move(id, up) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                SavingsViewModel(c.settingsRepository, c.potRepository, c.dayTracker)
            }
        }
    }
}
