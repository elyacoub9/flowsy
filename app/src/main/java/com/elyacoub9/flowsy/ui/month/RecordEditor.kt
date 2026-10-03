package com.elyacoub9.flowsy.ui.month

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.ExpenseRepository
import com.elyacoub9.flowsy.data.IncomeRepository
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the expense and income sheets need besides the record itself. */
data class RecordFormData(
    val currencyCode: String,
    val today: LocalDate,
    /** Proposed date for a new record: today in the current month, else the 1st of the viewed month. */
    val defaultDate: LocalDate,
    val categories: List<Category>,
    /** Expense count per category, last 90 days. */
    val categoryUsage: Map<Long, Int>,
    /** Income count per label, last 90 days. */
    val labelUsage: Map<String, Int>,
)

/** Typed values from the expense sheet, already validated. */
data class ExpenseInput(
    val amount: Long,
    val categoryId: Long,
    val date: LocalDate,
    val note: String?,
)

/** Typed values from the income sheet, already validated. */
data class IncomeInput(
    val label: String,
    val amount: Long,
    val date: LocalDate,
    val note: String?,
)

sealed interface RecordEvent {
    data class Saved(@param:StringRes val messageRes: Int) : RecordEvent
    data class ExpenseDeleted(val expense: Expense) : RecordEvent
    data class IncomeDeleted(val income: Income) : RecordEvent
}

/** Add / edit / delete / undo of expenses and income. */
class RecordEditor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val scope: CoroutineScope,
) {
    private val _events = Channel<RecordEvent>(Channel.BUFFERED)
    val events: Flow<RecordEvent> = _events.receiveAsFlow()

    fun saveExpense(editing: Expense?, input: ExpenseInput) {
        scope.launch {
            if (editing == null) expenseRepository.add(input.amount, input.categoryId, input.date, input.note)
            else expenseRepository.update(editing.id, input.amount, input.categoryId, input.date, input.note)
            _events.send(RecordEvent.Saved(R.string.expense_saved))
        }
    }

    fun deleteExpense(expense: Expense) {
        scope.launch { expenseRepository.delete(expense.id)?.let { _events.send(RecordEvent.ExpenseDeleted(it)) } }
    }

    fun saveIncome(editing: Income?, input: IncomeInput) {
        scope.launch {
            if (editing == null) incomeRepository.add(input.label, input.amount, input.date, input.note)
            else incomeRepository.update(editing.id, input.label, input.amount, input.date, input.note)
            _events.send(RecordEvent.Saved(R.string.income_saved))
        }
    }

    fun deleteIncome(income: Income) {
        scope.launch { incomeRepository.delete(income.id)?.let { _events.send(RecordEvent.IncomeDeleted(it)) } }
    }

    fun undo(event: RecordEvent) {
        scope.launch {
            when (event) {
                is RecordEvent.ExpenseDeleted -> expenseRepository.restore(event.expense)
                is RecordEvent.IncomeDeleted -> incomeRepository.restore(event.income)
                is RecordEvent.Saved -> Unit
            }
        }
    }
}

/** Snackbar after save, and after delete with Undo. */
@Composable
fun RecordEventsEffect(
    events: Flow<RecordEvent>,
    snackbarHostState: SnackbarHostState,
    onUndo: (RecordEvent) -> Unit,
) {
    val resources = LocalResources.current
    LaunchedEffect(events, resources) {
        events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val deletedRes = when (event) {
                is RecordEvent.Saved -> {
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes), duration = SnackbarDuration.Short)
                    return@collect
                }
                is RecordEvent.ExpenseDeleted -> R.string.expense_deleted
                is RecordEvent.IncomeDeleted -> R.string.income_deleted
            }
            val result = snackbarHostState.showSnackbar(
                resources.getString(deletedRes),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo(event)
        }
    }
}
