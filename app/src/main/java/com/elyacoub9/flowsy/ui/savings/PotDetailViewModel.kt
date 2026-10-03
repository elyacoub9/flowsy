package com.elyacoub9.flowsy.ui.savings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.DayTracker
import com.elyacoub9.flowsy.data.DeleteResult
import com.elyacoub9.flowsy.data.PotChange
import com.elyacoub9.flowsy.data.PotInput
import com.elyacoub9.flowsy.data.PotRepository
import com.elyacoub9.flowsy.data.PotWithBalance
import com.elyacoub9.flowsy.data.SettingsRepository
import com.elyacoub9.flowsy.domain.pot.GoalProgress
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PotDetailUiState(
    val currencyCode: String,
    val today: LocalDate,
    val pot: PotWithBalance,
    val goal: GoalProgress?,
    /** Delete allowed only for an empty pot without movements; otherwise archive. */
    val canDelete: Boolean,
)

sealed interface PotEvent {
    data class MovementDeleted(val movement: PotMovement) : PotEvent
    /** A delete or an Undo refused by the pot rules (shown with the available balance). */
    data class Refused(val change: PotChange) : PotEvent
    data object PotDeleted : PotEvent
}

class PotDetailViewModel(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    dayTracker: DayTracker,
    private val repository: PotRepository,
) : ViewModel() {

    private val potId: Long = checkNotNull(savedStateHandle.get<Long>(POT_ID))

    private val _events = Channel<PotEvent>(Channel.BUFFERED)
    val events: Flow<PotEvent> = _events.receiveAsFlow()

    val state: StateFlow<PotDetailUiState?> = combine(
        settingsRepository.observe().filterNotNull(),
        dayTracker.day,
        repository.observePot(potId),
    ) { settings, today, pot ->
        pot ?: return@combine null
        PotDetailUiState(
            currencyCode = settings.currencyCode,
            today = today,
            pot = pot,
            goal = GoalProgress.of(pot.balance, pot.pot.goalAmount, pot.pot.goalDate, today),
            canDelete = PotRules.canDeletePot(pot.pot, pot.movements),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveMovement(
        type: MovementType,
        existing: PotMovement?,
        amount: Long,
        date: LocalDate,
        note: String?,
        onResult: (PotChange) -> Unit,
    ) {
        viewModelScope.launch {
            val result = if (existing == null) repository.addMovement(potId, type, amount, date, note)
            else repository.updateMovement(existing.id, amount, date, note)
            onResult(result)
        }
    }

    fun deleteMovement(movement: PotMovement) = viewModelScope.launch {
        when (val result = repository.deleteMovement(movement.id)) {
            is DeleteResult.Deleted -> _events.send(PotEvent.MovementDeleted(result.movement))
            is DeleteResult.Refused -> if (result.change != PotChange.Done) _events.send(PotEvent.Refused(result.change))
        }
    }

    fun undoDelete(movement: PotMovement) = viewModelScope.launch {
        val result = repository.restoreMovement(movement)
        if (result != PotChange.Done) _events.send(PotEvent.Refused(result))
    }

    fun updatePot(input: PotInput, onResult: (PotChange) -> Unit) {
        viewModelScope.launch { onResult(repository.update(potId, input)) }
    }

    fun setArchived(archived: Boolean) = viewModelScope.launch { repository.setArchived(potId, archived) }

    fun deletePot() = viewModelScope.launch {
        if (repository.delete(potId)) _events.send(PotEvent.PotDeleted)
    }

    companion object {
        const val POT_ID = "potId"

        val Factory = viewModelFactory {
            initializer {
                val c = container()
                PotDetailViewModel(createSavedStateHandle(), c.settingsRepository, c.dayTracker, c.potRepository)
            }
        }
    }
}
