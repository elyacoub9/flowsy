package com.elyacoub9.flowsy.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.income.IncomeLabels
import com.elyacoub9.flowsy.domain.income.IncomeRules
import com.elyacoub9.flowsy.domain.money.amountInputText
import com.elyacoub9.flowsy.ui.common.AmountCheck
import com.elyacoub9.flowsy.ui.common.MinTouchTarget
import com.elyacoub9.flowsy.ui.common.TextInputField
import com.elyacoub9.flowsy.ui.common.checkAmount
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import java.util.Currency

/** Add (editing == null) or edit an income. */
@Composable
fun IncomeSheet(
    form: RecordFormData,
    editing: Income?,
    onSave: (IncomeInput) -> Unit,
    onDelete: (Income) -> Unit,
    onDismiss: () -> Unit,
) {
    RecordSheet(onDismiss) { IncomeSheetContent(form, editing, onSave, onDelete, focusAmount = editing == null) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomeSheetContent(
    form: RecordFormData,
    editing: Income?,
    onSave: (IncomeInput) -> Unit,
    onDelete: (Income) -> Unit,
    focusAmount: Boolean,
) {
    val locale = currentLocale()
    var amount by remember {
        mutableStateOf(editing?.let { amountInputText(it.amount, Currency.getInstance(form.currencyCode), locale) }.orEmpty())
    }
    var label by remember { mutableStateOf(editing?.label.orEmpty()) }
    var date by remember { mutableStateOf(editing?.date ?: form.defaultDate) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var amountError by remember { mutableStateOf<Int?>(null) }
    var labelError by remember { mutableStateOf(false) }

    val defaults = listOf(
        stringResource(R.string.income_label_salary),
        stringResource(R.string.income_label_freelance),
        stringResource(R.string.income_label_bonus),
        stringResource(R.string.income_label_other),
    )
    val chips = remember(defaults, form.labelUsage) { IncomeLabels.chips(defaults, form.labelUsage) }

    SheetColumn(if (editing == null) R.string.income_add_title else R.string.income_edit_title) {
        SheetAmountField(amount, { amount = it; amountError = null }, form.currencyCode, amountError, focusAmount)

        Text(stringResource(R.string.field_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                val selected = chip.equals(label.trim(), ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { label = chip; labelError = false },
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    label = { Text(chip) },
                )
            }
        }
        TextInputField(
            value = label,
            onValueChange = { label = it; labelError = false },
            labelRes = R.string.field_label,
            errorRes = if (labelError) R.string.error_label_required else null,
        )

        SheetDateRow(date, form.today) { date = it }
        SheetNoteField(note) { note = it }
        SheetActions(
            onDelete = editing?.let { { onDelete(it) } },
            onSave = {
                val check = checkAmount(amount, form.currencyCode, locale, allowZero = false)
                amountError = (check as? AmountCheck.Invalid)?.messageRes
                labelError = !IncomeRules.isValidLabel(label)
                if (check is AmountCheck.Valid && !labelError) {
                    onSave(IncomeInput(label.trim(), check.minorUnits, date, note.trim().ifEmpty { null }))
                }
            },
        )
    }
}

@Preview
@Composable
private fun IncomeSheetPreview() {
    FlowsyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            IncomeSheetContent(PreviewData.form, editing = null, onSave = {}, onDelete = {}, focusAmount = false)
        }
    }
}
