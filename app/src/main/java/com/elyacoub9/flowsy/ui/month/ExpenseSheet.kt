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
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.category.ChipOrder
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.money.amountInputText
import com.elyacoub9.flowsy.ui.common.AmountCheck
import com.elyacoub9.flowsy.ui.common.CategoryBadge
import com.elyacoub9.flowsy.ui.common.MinTouchTarget
import com.elyacoub9.flowsy.ui.common.checkAmount
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.displayName
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import java.util.Currency

/** Add (editing == null) or edit an expense. */
@Composable
fun ExpenseSheet(
    form: RecordFormData,
    editing: Expense?,
    onSave: (ExpenseInput) -> Unit,
    onDelete: (Expense) -> Unit,
    onDismiss: () -> Unit,
) {
    RecordSheet(onDismiss) { ExpenseSheetContent(form, editing, onSave, onDelete, focusAmount = editing == null) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseSheetContent(
    form: RecordFormData,
    editing: Expense?,
    onSave: (ExpenseInput) -> Unit,
    onDelete: (Expense) -> Unit,
    focusAmount: Boolean,
) {
    val locale = currentLocale()
    var amount by remember {
        mutableStateOf(editing?.let { amountInputText(it.amount, Currency.getInstance(form.currencyCode), locale) }.orEmpty())
    }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var date by remember { mutableStateOf(editing?.date ?: form.defaultDate) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var amountError by remember { mutableStateOf<Int?>(null) }
    var categoryError by remember { mutableStateOf(false) }

    // Archived categories are hidden, except the one this expense already has.
    val chips = remember(form.categories, form.categoryUsage, editing?.categoryId) {
        ChipOrder.forExpense(form.categories, form.categoryUsage, editing?.categoryId)
    }

    SheetColumn(if (editing == null) R.string.expense_add_title else R.string.expense_edit_title) {
        SheetAmountField(amount, { amount = it; amountError = null }, form.currencyCode, amountError, focusAmount)

        Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { category ->
                CategoryChip(category, selected = category.id == categoryId) {
                    categoryId = category.id
                    categoryError = false
                }
            }
        }
        if (categoryError) {
            Text(
                stringResource(R.string.error_category_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SheetDateRow(date, form.today) { date = it }
        SheetNoteField(note) { note = it }
        SheetActions(
            onDelete = editing?.let { { onDelete(it) } },
            onSave = {
                val check = checkAmount(amount, form.currencyCode, locale, allowZero = false)
                amountError = (check as? AmountCheck.Invalid)?.messageRes
                categoryError = categoryId == null
                val id = categoryId
                if (check is AmountCheck.Valid && id != null) {
                    onSave(ExpenseInput(check.minorUnits, id, date, note.trim().ifEmpty { null }))
                }
            },
        )
    }
}

/** Selected chip shows a check mark, not only a color change. */
@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = MinTouchTarget),
        leadingIcon = {
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            } else {
                CategoryBadge(category, size = 22.dp)
            }
        },
        label = { Text(category.displayName()) },
    )
}

@Preview
@Composable
private fun ExpenseSheetPreview() {
    FlowsyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseSheetContent(PreviewData.form, editing = null, onSave = {}, onDelete = {}, focusAmount = false)
        }
    }
}
