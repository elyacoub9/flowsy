package com.elyacoub9.flowsy.ui.month

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.date.RecordDateRule
import com.elyacoub9.flowsy.ui.common.ActionButton
import com.elyacoub9.flowsy.ui.common.MinTouchTarget
import com.elyacoub9.flowsy.ui.common.RecordDatePickerDialog
import com.elyacoub9.flowsy.ui.common.formatDate
import java.time.LocalDate

// Building blocks shared by the expense and income sheets.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) { content() }
}

@Composable
fun SheetColumn(@StringRes titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        content()
        Spacer(Modifier.height(8.dp))
    }
}

/** Large amount field with the number keyboard; focused when [focus] is true (adding). */
@Composable
fun SheetAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currencyCode: String,
    @StringRes errorRes: Int?,
    focus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        label = { Text(stringResource(R.string.field_amount)) },
        suffix = { Text(currencyCode) },
        isError = errorRes != null,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
    )
    LaunchedEffect(focus) {
        if (focus) focusRequester.requestFocus()
    }
}

/** "Date" with a chip showing Today / Yesterday / the date; opens a picker (no future dates). */
@Composable
fun SheetDateRow(date: LocalDate, today: LocalDate, onDateChange: (LocalDate) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.field_date),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = { pick = true },
            modifier = Modifier.heightIn(min = MinTouchTarget),
            leadingIcon = { Icon(painterResource(R.drawable.ic_calendar), contentDescription = null) },
            label = {
                Text(
                    when (date) {
                        today -> stringResource(R.string.date_today)
                        today.minusDays(1) -> stringResource(R.string.date_yesterday)
                        else -> formatDate(date)
                    }
                )
            },
        )
    }
    if (pick) {
        RecordDatePickerDialog(
            date = date,
            range = RecordDateRule.range(today),
            onConfirm = { onDateChange(it); pick = false },
            onDismiss = { pick = false },
        )
    }
}

@Composable
fun SheetNoteField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_note_optional)) },
    )
}

/** Delete (when editing) and Save. */
@Composable
fun SheetActions(onDelete: (() -> Unit)?, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) ActionButton(R.drawable.ic_delete, R.string.action_delete, onDelete)
        Spacer(Modifier.weight(1f))
        Button(onClick = onSave, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_save)) }
    }
}
