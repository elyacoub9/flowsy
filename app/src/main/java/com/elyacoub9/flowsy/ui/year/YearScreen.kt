package com.elyacoub9.flowsy.ui.year

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.month.MonthTotals
import com.elyacoub9.flowsy.domain.month.SpentRatio
import com.elyacoub9.flowsy.domain.year.MonthStatus
import com.elyacoub9.flowsy.domain.year.MonthSummary
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.formatMonth
import com.elyacoub9.flowsy.ui.common.rememberMoneyFormat
import com.elyacoub9.flowsy.ui.month.RatioBar
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.theme.IncomeColor
import com.elyacoub9.flowsy.ui.theme.LeftColor
import com.elyacoub9.flowsy.ui.theme.OverColor
import com.elyacoub9.flowsy.ui.theme.SpentColor
import java.time.YearMonth

@Composable
fun YearRoute(
    onOpenSettings: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    viewModel: YearViewModel = viewModel(factory = YearViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    YearScreen(state, onOpenSettings, viewModel::previousYear, viewModel::nextYear, onOpenMonth)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(
    state: YearUiState?,
    onOpenSettings: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state == null) Text(stringResource(R.string.nav_year)) else YearSwitcher(state, onPreviousYear, onNextYear)
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.action_open_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (state == null) return@Scaffold
        val money = rememberMoneyFormat(state.currencyCode)
        // The tapped chart month, per year; until then the default (current or last month with records).
        var chartMonth by rememberSaveable(state.year) { mutableStateOf<String?>(null) }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TotalsCard(state.summary.totals, money) }
            item {
                YearCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.year_chart_title), style = MaterialTheme.typography.titleMedium)
                        if (state.bars == null) {
                            Text(
                                stringResource(R.string.year_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            YearBarChart(
                                summary = state.summary,
                                bars = state.bars,
                                selected = chartMonth?.let(YearMonth::parse) ?: state.defaultChartMonth,
                                onSelect = { chartMonth = it.toString() },
                                currencyCode = state.currencyCode,
                                money = money,
                            )
                        }
                    }
                }
            }
            items(state.summary.months, key = { it.month.toString() }) { month ->
                MonthCard(month, money, onClick = { onOpenMonth(month.month) })
            }
        }
    }
}

@Composable
private fun YearSwitcher(state: YearUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = state.canGoBack) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.action_previous_year))
        }
        Text(state.year.toString())
        IconButton(onClick = onNext, enabled = state.canGoForward) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.action_next_year))
        }
    }
}

@Composable
private fun YearCard(modifier: Modifier = Modifier, border: BorderStroke? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = border,
    ) { content() }
}

@Composable
private fun TotalsCard(totals: MonthTotals, money: (Long) -> String) {
    YearCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.year_totals_title), style = MaterialTheme.typography.titleMedium)
            AmountRow(stringResource(R.string.month_income), money(totals.income), IncomeColor)
            AmountRow(stringResource(R.string.month_spent), money(totals.spent), SpentColor)
            LeftRow(totals, money, emphasized = true)
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

/** Left in yellow, or "Over by X" with a warning icon (never color alone). */
@Composable
private fun LeftRow(totals: MonthTotals, money: (Long) -> String, emphasized: Boolean = false) {
    if (totals.isOver) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = OverColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.month_over_by, money(totals.overBy)),
                style = MaterialTheme.typography.bodyLarge,
                color = OverColor,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        AmountRow(stringResource(R.string.month_left), money(totals.left), LeftColor, bold = emphasized)
    }
}

/**
 * Future months: dimmed, no amounts, not tappable. Past months without records: dimmed, "No records".
 * The current month gets a "This month" label and an outline.
 */
@Composable
private fun MonthCard(month: MonthSummary, money: (Long) -> String, onClick: () -> Unit) {
    val dimmed = month.status != MonthStatus.RECORDS
    val border = if (month.isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    YearCard(
        modifier = Modifier
            .then(if (month.status != MonthStatus.FUTURE) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp),
        border = border,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .alpha(if (dimmed) 0.55f else 1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMonth(month.month, currentLocale(), withYear = false),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (month.isCurrent) {
                    Text(
                        stringResource(R.string.month_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            when (month.status) {
                MonthStatus.FUTURE -> Unit
                MonthStatus.NO_RECORDS -> Text(
                    stringResource(R.string.month_no_records),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MonthStatus.RECORDS -> {
                    AmountRow(stringResource(R.string.month_income), money(month.totals.income), IncomeColor)
                    AmountRow(stringResource(R.string.month_spent), money(month.totals.spent), SpentColor)
                    LeftRow(month.totals, money)
                    (month.ratio as? SpentRatio.Ratio)?.let { RatioBar(it, height = 4.dp) }
                }
            }
        }
    }
}

@Composable
private fun YearPreview(state: YearUiState) {
    FlowsyTheme { Surface { YearScreen(state, {}, {}, {}, {}) } }
}

@Preview(heightDp = 2200)
@Composable
private fun YearCurrentPreview() = YearPreview(PreviewData.current)

@Preview(heightDp = 2200)
@Composable
private fun YearPastFullPreview() = YearPreview(PreviewData.pastFull)

@Preview
@Composable
private fun YearEmptyPreview() = YearPreview(PreviewData.empty)

@Preview(heightDp = 2200)
@Composable
private fun YearOverBudgetPreview() = YearPreview(PreviewData.overBudget)
