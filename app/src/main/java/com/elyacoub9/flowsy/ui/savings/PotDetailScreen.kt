package com.elyacoub9.flowsy.ui.savings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.PotChange
import com.elyacoub9.flowsy.data.PotInput
import com.elyacoub9.flowsy.data.PotWithBalance
import com.elyacoub9.flowsy.domain.pot.GoalProgress
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import com.elyacoub9.flowsy.ui.common.ActionButton
import com.elyacoub9.flowsy.ui.common.CategoryBadge
import com.elyacoub9.flowsy.ui.common.ConfirmDialog
import com.elyacoub9.flowsy.ui.common.MinTouchTarget
import com.elyacoub9.flowsy.ui.common.StatusLabel
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.formatDate
import com.elyacoub9.flowsy.ui.common.rememberMoneyFormat
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.theme.OnSurfaceMuted
import com.elyacoub9.flowsy.ui.theme.SavingsColor
import java.time.LocalDate

private sealed interface PotDialogRequest {
    data class Movement(val type: MovementType, val existing: PotMovement?) : PotDialogRequest
    data object EditPot : PotDialogRequest
    data object ConfirmDelete : PotDialogRequest
}

@Composable
fun PotDetailRoute(
    onBack: () -> Unit,
    viewModel: PotDetailViewModel = viewModel(factory = PotDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val money = rememberMoneyFormat(state?.currencyCode ?: "XXX")
    val resources = LocalResources.current
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is PotEvent.MovementDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        resources.getString(R.string.movement_deleted),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.movement)
                }
                // Refused delete or Undo: never silent, always with the available balance.
                is PotEvent.Refused -> refusedMessage(resources, event.change, money)?.let {
                    snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
                }
                PotEvent.PotDeleted -> onBack()
            }
        }
    }
    PotDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSaveMovement = viewModel::saveMovement,
        onDeleteMovement = viewModel::deleteMovement,
        onUpdatePot = viewModel::updatePot,
        onSetArchived = viewModel::setArchived,
        onDeletePot = viewModel::deletePot,
    )
}

/** An archived pot is read-only: no deposit, withdrawal, edit or movement changes until restored. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PotDetailScreen(
    state: PotDetailUiState?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSaveMovement: (MovementType, PotMovement?, Long, LocalDate, String?, (PotChange) -> Unit) -> Unit,
    onDeleteMovement: (PotMovement) -> Unit,
    onUpdatePot: (PotInput, (PotChange) -> Unit) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDeletePot: () -> Unit,
) {
    var dialog by remember { mutableStateOf<PotDialogRequest?>(null) }
    var potError by remember { mutableStateOf<PotChange?>(null) }
    val readOnly = state?.pot?.pot?.let(PotRules::isReadOnly) ?: true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.pot?.pot?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state != null && !readOnly) {
                        IconButton(onClick = { potError = null; dialog = PotDialogRequest.EditPot }) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit_pot))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state == null) return@Scaffold
        val money = rememberMoneyFormat(state.currencyCode)
        val pot = state.pot.pot
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(pot.iconKey, pot.colorKey)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.pot_balance), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (pot.archived) StatusLabel(R.drawable.ic_archive, stringResource(R.string.status_archived), OnSurfaceMuted)
                        }
                        Text(money(state.pot.balance), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = SavingsColor)
                        if (pot.openingBalance > 0) {
                            Text(
                                stringResource(R.string.pot_opening_info, money(pot.openingBalance)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.goal?.let { GoalRing(it, pot.goalDate, money) }
                        if (pot.archived) {
                            Text(
                                stringResource(R.string.savings_archived_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (!readOnly) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { dialog = PotDialogRequest.Movement(MovementType.DEPOSIT, null) },
                            modifier = Modifier.heightIn(min = MinTouchTarget),
                        ) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_deposit))
                        }
                        OutlinedButton(
                            onClick = { dialog = PotDialogRequest.Movement(MovementType.WITHDRAWAL, null) },
                            enabled = state.pot.balance > 0,
                            modifier = Modifier.heightIn(min = MinTouchTarget),
                        ) {
                            Icon(painterResource(R.drawable.ic_remove), contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_withdraw))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.pot_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (state.pot.movements.isEmpty()) {
                    Text(stringResource(R.string.pot_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.pot.movements, key = { it.id }) { movement ->
                MovementRow(
                    movement, money,
                    onClick = if (readOnly) null else { { dialog = PotDialogRequest.Movement(movement.type, movement) } },
                )
            }
            item {
                // Delete only for an empty pot without movements; otherwise only Archive is offered.
                Row(Modifier.padding(top = 16.dp)) {
                    if (pot.archived) {
                        ActionButton(R.drawable.ic_unarchive, R.string.action_restore) { onSetArchived(false) }
                    } else {
                        ActionButton(R.drawable.ic_archive, R.string.action_archive) { onSetArchived(true) }
                    }
                    if (state.canDelete) {
                        ActionButton(R.drawable.ic_delete, R.string.action_delete) { dialog = PotDialogRequest.ConfirmDelete }
                    }
                }
            }
        }

        when (val d = dialog) {
            is PotDialogRequest.Movement -> MovementDialog(
                type = d.type,
                existing = d.existing,
                balance = state.pot.balance,
                currencyCode = state.currencyCode,
                today = state.today,
                money = money,
                onSave = { amount, date, note, onResult -> onSaveMovement(d.type, d.existing, amount, date, note, onResult) },
                onDelete = { onDeleteMovement(it); dialog = null },
                onDismiss = { dialog = null },
            )
            PotDialogRequest.EditPot -> PotDialog(
                initial = PotFormInput.from(pot, state.currencyCode, currentLocale()),
                isNew = false,
                currencyCode = state.currencyCode,
                today = state.today,
                onSave = { input -> onUpdatePot(input) { result -> if (result == PotChange.Done) dialog = null else potError = result } },
                onDismiss = { dialog = null },
                externalError = potError?.let { refusedMessage(LocalResources.current, it, money) },
            )
            PotDialogRequest.ConfirmDelete -> ConfirmDialog(
                title = stringResource(R.string.dialog_delete_title, pot.name),
                message = stringResource(R.string.dialog_delete_pot_message),
                confirmRes = R.string.action_delete,
                onConfirm = { dialog = null; onDeletePot() },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

/** Newest first (the list comes ordered by date, then creation time). Not tappable when read-only. */
@Composable
private fun MovementRow(movement: PotMovement, money: (Long) -> String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (movement.type == MovementType.DEPOSIT) R.string.movement_deposit else R.string.movement_withdrawal),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                listOfNotNull(formatDate(movement.date), movement.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(signedAmount(movement, money), style = MaterialTheme.typography.titleMedium)
    }
}

private val previewToday = LocalDate.of(2026, 10, 12)

private fun previewState(archived: Boolean): PotDetailUiState {
    val pot = Pot(1, "Emergency", "green", "heart", 3_000_000, LocalDate.of(2027, 6, 1), 1_000_000, archived, 0)
    val movements = listOf(
        PotMovement(2, 1, MovementType.WITHDRAWAL, 30_000, previewToday, "Car repair", 2),
        PotMovement(1, 1, MovementType.DEPOSIT, 100_000, LocalDate.of(2026, 10, 1), null, 1),
    )
    val balance = PotRules.balance(pot.openingBalance, movements)
    return PotDetailUiState(
        currencyCode = "MAD",
        today = previewToday,
        pot = PotWithBalance(pot, movements, balance),
        goal = GoalProgress.of(balance, pot.goalAmount, pot.goalDate, previewToday),
        canDelete = false,
    )
}

@Composable
private fun PotDetailPreview(state: PotDetailUiState) {
    FlowsyTheme {
        PotDetailScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onSaveMovement = { _, _, _, _, _, _ -> }, onDeleteMovement = {},
            onUpdatePot = { _, _ -> }, onSetArchived = {}, onDeletePot = {},
        )
    }
}

@Preview
@Composable
private fun PotDetailActivePreview() = PotDetailPreview(previewState(archived = false))

@Preview
@Composable
private fun PotDetailArchivedPreview() = PotDetailPreview(previewState(archived = true))
