package com.elyacoub9.flowsy.ui.savings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.PotInput
import com.elyacoub9.flowsy.data.PotWithBalance
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import com.elyacoub9.flowsy.ui.common.CategoryBadge
import com.elyacoub9.flowsy.ui.common.StatusLabel
import com.elyacoub9.flowsy.ui.common.rememberMoneyFormat
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.theme.OnSurfaceMuted
import com.elyacoub9.flowsy.ui.theme.SavingsColor
import java.time.LocalDate

@Composable
fun SavingsRoute(
    onOpenPot: (Long) -> Unit,
    viewModel: SavingsViewModel = viewModel(factory = SavingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavingsScreen(state, onOpenPot, viewModel::add, viewModel::move)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    state: SavingsUiState?,
    onOpenPot: (Long) -> Unit,
    onAdd: (PotInput) -> Unit,
    onMove: (id: Long, up: Boolean) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_savings)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_pot)) },
            )
        },
    ) { padding ->
        if (state == null) return@Scaffold
        val money = rememberMoneyFormat(state.currencyCode)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.active.isEmpty() && state.archived.isEmpty()) {
                item {
                    Text(stringResource(R.string.savings_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item { TotalCard(money(state.total)) }
            }
            itemsIndexed(state.active, key = { _, item -> item.pot.pot.id }) { index, item ->
                PotCard(
                    item, money,
                    onClick = { onOpenPot(item.pot.pot.id) },
                    move = MoveButtons(
                        canMoveUp = index > 0,
                        canMoveDown = index < state.active.lastIndex,
                        onMove = { up -> onMove(item.pot.pot.id, up) },
                    ),
                )
            }
            if (state.archived.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                        Text(stringResource(R.string.categories_archived_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.savings_archived_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.archived, key = { it.pot.pot.id }) { item ->
                    PotCard(item, money, onClick = { onOpenPot(item.pot.pot.id) }, move = null)
                }
            }
        }
        if (adding) {
            PotDialog(
                initial = PotFormInput(),
                isNew = true,
                currencyCode = state.currencyCode,
                today = state.today,
                onSave = { onAdd(it); adding = false },
                onDismiss = { adding = false },
            )
        }
    }
}

@Composable
private fun TotalCard(total: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.savings_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val style = MaterialTheme.typography.displaySmall
            BasicText(
                total,
                style = style.copy(fontWeight = FontWeight.Bold, color = SavingsColor),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = style.fontSize),
            )
        }
    }
}

private class MoveButtons(val canMoveUp: Boolean, val canMoveDown: Boolean, val onMove: (up: Boolean) -> Unit)

@Composable
private fun PotCard(item: PotItem, money: (Long) -> String, onClick: () -> Unit, move: MoveButtons?) {
    val pot = item.pot.pot
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 16.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(pot.iconKey, pot.colorKey)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pot.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        money(item.pot.balance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SavingsColor,
                    )
                    if (PotRules.isReadOnly(pot)) StatusLabel(R.drawable.ic_archive, stringResource(R.string.status_archived), OnSurfaceMuted)
                }
                if (move != null) {
                    IconButton(onClick = { move.onMove(true) }, enabled = move.canMoveUp) {
                        Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.action_move_up_named, pot.name))
                    }
                    IconButton(onClick = { move.onMove(false) }, enabled = move.canMoveDown) {
                        Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.action_move_down_named, pot.name))
                    }
                }
            }
            item.goal?.let { Column(Modifier.padding(end = 12.dp)) { GoalBar(it, pot.goalDate, money) } }
        }
    }
}

private val previewToday = LocalDate.of(2026, 10, 12)

private fun previewPot(pot: Pot, vararg deposits: Long): PotWithBalance {
    val movements = deposits.mapIndexed { i, amount ->
        PotMovement(pot.id * 10 + i, pot.id, MovementType.DEPOSIT, amount, previewToday, null, i.toLong())
    }
    return PotWithBalance(pot, movements, PotRules.balance(pot.openingBalance, movements))
}

/** Spec case: Emergency 11,000.00; Travel 200.00 (goal date passed); Laptop goal reached; one archived pot. */
private val previewPots = listOf(
    previewPot(Pot(1, "Emergency", "green", "heart", 3_000_000, LocalDate.of(2027, 6, 1), 1_000_000, false, 0), 100_000),
    previewPot(Pot(2, "Travel", "blue", "plane", 500_000, LocalDate.of(2026, 9, 1), 0, false, 1), 20_000),
    previewPot(Pot(3, "Laptop", "violet", "phone", 1_200_000, null, 0, false, 2), 1_250_000),
    previewPot(Pot(4, "Old car", "red", "car", null, null, 50_000, true, 3)),
)

@Preview(heightDp = 1300)
@Composable
private fun SavingsWithGoalsPreview() {
    FlowsyTheme { SavingsScreen(SavingsUiState.build("MAD", previewToday, previewPots), {}, {}, { _, _ -> }) }
}

@Preview
@Composable
private fun SavingsEmptyPreview() {
    FlowsyTheme { SavingsScreen(SavingsUiState.build("MAD", previewToday, emptyList()), {}, {}, { _, _ -> }) }
}
