package com.elyacoub9.flowsy.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.date.RecordDateRange
import com.elyacoub9.flowsy.domain.money.MoneyFormatter
import com.elyacoub9.flowsy.domain.money.MoneyParser
import com.elyacoub9.flowsy.domain.money.ParseError
import com.elyacoub9.flowsy.domain.money.ParseResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

/** Formats minor units in [currencyCode] with the current app locale. */
@Composable
fun rememberMoneyFormat(currencyCode: String): (Long) -> String {
    val locale = currentLocale()
    val currency = remember(currencyCode) { Currency.getInstance(currencyCode) }
    return remember(currency, locale) { { minor: Long -> MoneyFormatter.format(minor, currency, locale) } }
}

@Composable
fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

sealed interface AmountCheck {
    data class Valid(val minorUnits: Long) : AmountCheck
    data class Invalid(@param:StringRes val messageRes: Int) : AmountCheck
}

/** Parses a typed amount; [allowZero] = false also refuses 0. */
fun checkAmount(text: String, currencyCode: String, locale: Locale, allowZero: Boolean): AmountCheck =
    when (val result = MoneyParser.parse(text, Currency.getInstance(currencyCode), locale)) {
        is ParseResult.Success ->
            if (!allowZero && result.minorUnits == 0L) AmountCheck.Invalid(R.string.amount_error_zero)
            else AmountCheck.Valid(result.minorUnits)
        is ParseResult.Error -> AmountCheck.Invalid(
            when (result.reason) {
                ParseError.EMPTY -> R.string.amount_error_empty
                ParseError.INVALID -> R.string.amount_error_invalid
                ParseError.TOO_MANY_DECIMALS -> R.string.amount_error_decimals
                ParseError.TOO_LARGE -> R.string.amount_error_too_large
            }
        )
    }

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currencyCode: String,
    @StringRes errorRes: Int?,
    modifier: Modifier = Modifier,
    @StringRes labelRes: Int = R.string.field_amount,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(labelRes)) },
        suffix = { Text(currencyCode) },
        isError = errorRes != null,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
    )
}

@Composable
fun TextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        label = { Text(stringResource(labelRes)) },
        isError = errorRes != null,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
    )
}

/** A date button that opens a picker limited to [range]. */
@Composable
fun PastDateField(
    date: LocalDate,
    range: RecordDateRange,
    onDateChange: (LocalDate) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) {
            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(formatDate(date))
        }
    }
    if (open) {
        RecordDatePickerDialog(
            date = date,
            range = range,
            onConfirm = { onDateChange(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/** Date picker that only allows days inside [range]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDatePickerDialog(
    date: LocalDate,
    range: RecordDateRange,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val minMillis = range.min.toEpochDay() * MILLIS_PER_DAY
    val maxMillis = range.max.toEpochDay() * MILLIS_PER_DAY
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.coerceIn(range.min, range.max).toEpochDay() * MILLIS_PER_DAY,
        yearRange = range.min.year..range.max.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in minMillis..maxMillis
            override fun isSelectableYear(year: Int) = year in range.min.year..range.max.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) }
                        ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) { DatePicker(state = state) }
}

/** Standard form dialog: title, scrollable content, Save / Cancel. */
@Composable
fun FormDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    @StringRes confirmRes: Int = R.string.action_save,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    @StringRes confirmRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
