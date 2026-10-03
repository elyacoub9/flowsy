package com.elyacoub9.flowsy.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.month.MonthTotals
import com.elyacoub9.flowsy.domain.month.SpentRatio
import com.elyacoub9.flowsy.ui.common.CategoryBadge
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.formatMonth
import com.elyacoub9.flowsy.ui.common.displayName
import com.elyacoub9.flowsy.ui.common.rememberMoneyFormat
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.theme.IncomeColor
import com.elyacoub9.flowsy.ui.theme.LeftColor
import com.elyacoub9.flowsy.ui.theme.OverColor
import com.elyacoub9.flowsy.ui.theme.SpentColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Room under the list for the + button: its size, its margin and some breathing space. */
private val FabClearance = 56.dp + 16.dp + 24.dp

/** Which sheet is open: a new or existing expense or income, or the Expense / Income choice. */
private sealed interface SheetRequest {
    data object Choose : SheetRequest
    data class ExpenseSheet(val editing: Expense?) : SheetRequest
    data class IncomeSheet(val editing: Income?) : SheetRequest
}

@Composable
fun MonthRoute(
    onOpenSettings: () -> Unit,
    /** Set when the month was opened from Year: shows a back arrow. */
    onBack: (() -> Unit)? = null,
    viewModel: MonthViewModel = viewModel(factory = MonthViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RecordEventsEffect(viewModel.editor.events, snackbarHostState, viewModel.editor::undo)
    MonthScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSaveExpense = viewModel.editor::saveExpense,
        onDeleteExpense = viewModel.editor::deleteExpense,
        onSaveIncome = viewModel.editor::saveIncome,
        onDeleteIncome = viewModel.editor::deleteIncome,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    state: MonthUiState?,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSaveExpense: (Expense?, ExpenseInput) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onSaveIncome: (Income?, IncomeInput) -> Unit,
    onDeleteIncome: (Income) -> Unit,
) {
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                title = {
                    if (state == null) Text(stringResource(R.string.nav_month))
                    else MonthSwitcher(state, locale, onPreviousMonth, onNextMonth)
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.action_open_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state != null) {
                FloatingActionButton(onClick = { sheet = SheetRequest.Choose }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_record))
                }
            }
        },
    ) { padding ->
        if (state == null) return@Scaffold
        val money = rememberMoneyFormat(state.currencyCode)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            // The last item can always scroll fully above the + button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { LeftCard(state.totals, money) }
            item {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(stringResource(R.string.month_income), money(state.totals.income), IncomeColor, Modifier.weight(1f))
                    Tile(stringResource(R.string.month_spent), money(state.totals.spent), SpentColor, Modifier.weight(1f))
                }
            }
            if (state.ratio == SpentRatio.Empty) {
                item {
                    Text(
                        stringResource(R.string.month_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item {
                    MonthCard(Modifier.padding(top = 8.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            SpentBar(state.ratio)
                            SpendingBreakdown(state.slices, state.totals.spent, money, resetKey = state.month)
                        }
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day, state.today, money) }
                items(day.records, key = { it.key }) { record ->
                    when (record) {
                        is MonthRecord.IncomeRecord ->
                            IncomeRow(record.income, money) { sheet = SheetRequest.IncomeSheet(record.income) }
                        is MonthRecord.ExpenseRecord ->
                            ExpenseRow(record, money) { sheet = SheetRequest.ExpenseSheet(record.expense) }
                    }
                }
            }
        }

        when (val request = sheet) {
            null -> Unit
            SheetRequest.Choose -> AddChoiceDialog(
                onExpense = { sheet = SheetRequest.ExpenseSheet(null) },
                onIncome = { sheet = SheetRequest.IncomeSheet(null) },
                onDismiss = { sheet = null },
            )
            is SheetRequest.ExpenseSheet -> ExpenseSheet(
                form = state.form,
                editing = request.editing,
                onSave = { onSaveExpense(request.editing, it); sheet = null },
                onDelete = { onDeleteExpense(it); sheet = null },
                onDismiss = { sheet = null },
            )
            is SheetRequest.IncomeSheet -> IncomeSheet(
                form = state.form,
                editing = request.editing,
                onSave = { onSaveIncome(request.editing, it); sheet = null },
                onDelete = { onDeleteIncome(it); sheet = null },
                onDismiss = { sheet = null },
            )
        }
    }
}

private val MonthRecord.key: String
    get() = when (this) {
        is MonthRecord.IncomeRecord -> "income-${income.id}"
        is MonthRecord.ExpenseRecord -> "expense-${expense.id}"
    }

@Composable
private fun MonthCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

/** Left, or "Over by X" in red with a warning icon (never color alone). */
@Composable
private fun LeftCard(totals: MonthTotals, money: (Long) -> String) {
    MonthCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (totals.isOver) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = OverColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.month_over_label), style = MaterialTheme.typography.labelLarge, color = OverColor)
                }
                BigAmount(stringResource(R.string.month_over_by, money(totals.overBy)), OverColor)
            } else {
                Text(
                    stringResource(R.string.month_left),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BigAmount(money(totals.left), LeftColor)
            }
        }
    }
}

/** One line; the text shrinks instead of wrapping a large amount. */
@Composable
private fun BigAmount(text: String, color: Color) {
    val style = MaterialTheme.typography.displaySmall
    BasicText(
        text,
        style = style.copy(fontWeight = FontWeight.Bold, color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = style.fontSize),
    )
}

@Composable
private fun Tile(label: String, value: String, valueColor: Color, modifier: Modifier) {
    MonthCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val style = MaterialTheme.typography.titleLarge
            BasicText(
                value,
                style = style.copy(fontWeight = FontWeight.Bold, color = valueColor),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize),
            )
        }
    }
}

/** Date with the day's income (+) and spent (−) totals; zero totals are left out. */
@Composable
private fun DayHeader(day: RecordDay, today: LocalDate, money: (Long) -> String) {
    val label = when (day.date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale()).format(day.date)
    }
    val totalsDescription = stringResource(R.string.day_totals_description, money(day.total.income), money(day.total.spent))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Row(
            Modifier.clearAndSetSemantics { contentDescription = totalsDescription },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (day.total.income > 0) {
                Text("+ ${money(day.total.income)}", style = MaterialTheme.typography.labelLarge, color = IncomeColor)
            }
            if (day.total.spent > 0) {
                Text(
                    "− ${money(day.total.spent)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ◀ Month ▶ in the top bar. ▶ is disabled on the current month, ◀ on the first month. */
@Composable
private fun MonthSwitcher(state: MonthUiState, locale: Locale, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = state.canGoBack) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.action_previous_month))
        }
        Text(
            formatMonth(state.month, locale),
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = onNext, enabled = state.canGoForward) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.action_next_month))
        }
    }
}

@Composable
private fun RecordRow(
    badge: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    amount: String,
    amountColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor)
    }
}

/** Income: "+" sign and an income icon, not only the green color. */
@Composable
private fun IncomeRow(income: Income, money: (Long) -> String, onClick: () -> Unit) {
    RecordRow(
        badge = {
            Box(
                Modifier
                    .size(36.dp)
                    .background(IncomeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_income),
                    contentDescription = stringResource(R.string.record_income),
                    tint = Color(0xFF15161C),
                    modifier = Modifier.size(21.dp),
                )
            }
        },
        title = income.label,
        subtitle = income.note,
        amount = "+ ${money(income.amount)}",
        amountColor = IncomeColor,
        onClick = onClick,
    )
}

@Composable
private fun ExpenseRow(record: MonthRecord.ExpenseRecord, money: (Long) -> String, onClick: () -> Unit) {
    val subtitle = listOfNotNull(
        record.expense.note,
        if (record.category.archived) stringResource(R.string.category_archived_tag) else null,
    ).joinToString(" · ")
    RecordRow(
        badge = { CategoryBadge(record.category) },
        title = record.category.displayName(),
        subtitle = subtitle,
        amount = "− ${money(record.expense.amount)}",
        amountColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
    )
}

@Composable
private fun AddChoiceDialog(onExpense: () -> Unit, onIncome: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_choice_title)) },
        text = {
            Column {
                ChoiceRow(R.drawable.ic_expense, stringResource(R.string.record_expense), onExpense)
                ChoiceRow(R.drawable.ic_income, stringResource(R.string.record_income), onIncome)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MonthPreview(state: MonthUiState) {
    FlowsyTheme {
        MonthScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onOpenSettings = {}, onBack = null, onPreviousMonth = {}, onNextMonth = {},
            onSaveExpense = { _, _ -> }, onDeleteExpense = {}, onSaveIncome = { _, _ -> }, onDeleteIncome = {},
        )
    }
}

@Preview(heightDp = 1400)
@Composable
private fun MonthNormalPreview() = MonthPreview(PreviewData.normal)

@Preview(heightDp = 1400)
@Composable
private fun MonthOverBudgetPreview() = MonthPreview(PreviewData.over)

@Preview(heightDp = 1400)
@Composable
private fun MonthNoIncomePreview() = MonthPreview(PreviewData.noIncome)

@Preview
@Composable
private fun MonthNoExpensesPreview() = MonthPreview(PreviewData.noExpenses)

@Preview
@Composable
private fun MonthEmptyPreview() = MonthPreview(PreviewData.empty)
