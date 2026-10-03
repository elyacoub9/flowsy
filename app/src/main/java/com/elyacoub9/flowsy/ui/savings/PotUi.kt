package com.elyacoub9.flowsy.ui.savings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.PotChange
import com.elyacoub9.flowsy.data.PotInput
import com.elyacoub9.flowsy.domain.date.RecordDateRange
import com.elyacoub9.flowsy.domain.date.RecordDateRule
import com.elyacoub9.flowsy.domain.money.PercentFormatter
import com.elyacoub9.flowsy.domain.money.amountInputText
import com.elyacoub9.flowsy.domain.pot.GoalDateRule
import com.elyacoub9.flowsy.domain.pot.GoalProgress
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import com.elyacoub9.flowsy.ui.common.ActionButton
import com.elyacoub9.flowsy.ui.common.AmountCheck
import com.elyacoub9.flowsy.ui.common.AmountField
import com.elyacoub9.flowsy.ui.common.CategoryColors
import com.elyacoub9.flowsy.ui.common.ColorPicker
import com.elyacoub9.flowsy.ui.common.FormDialog
import com.elyacoub9.flowsy.ui.common.IconPicker
import com.elyacoub9.flowsy.ui.common.MinTouchTarget
import com.elyacoub9.flowsy.ui.common.PastDateField
import com.elyacoub9.flowsy.ui.common.RecordDatePickerDialog
import com.elyacoub9.flowsy.ui.common.StatusLabel
import com.elyacoub9.flowsy.ui.common.TextInputField
import com.elyacoub9.flowsy.ui.common.checkAmount
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.formatDate
import com.elyacoub9.flowsy.ui.theme.OverColor
import com.elyacoub9.flowsy.ui.theme.SavingsColor
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** "X of Y, Z %" in the app language. */
@Composable
fun goalText(progress: GoalProgress, money: (Long) -> String): String =
    stringResource(
        R.string.goal_progress,
        money(progress.balance),
        money(progress.goal),
        PercentFormatter.format(progress.percent.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), currentLocale()),
    )

/** "Goal reached" / "Goal date passed" with an icon, never color alone. */
@Composable
fun GoalStatus(progress: GoalProgress) {
    when {
        progress.reached -> StatusLabel(R.drawable.ic_check, stringResource(R.string.goal_reached), SavingsColor)
        progress.datePassed -> StatusLabel(R.drawable.ic_warning, stringResource(R.string.goal_date_passed), OverColor)
    }
}

/** For pot cards: "X of Y, Z %", a bar capped at 100 % and the status. */
@Composable
fun GoalBar(progress: GoalProgress, goalDate: LocalDate?, money: (Long) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(goalText(progress, money), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { progress.barPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = SavingsColor,
        )
        GoalStatus(progress)
        GoalDate(progress, goalDate)
    }
}

/** For the pot detail: a ring capped at 100 % with the percentage inside, then the text and status. */
@Composable
fun GoalRing(progress: GoalProgress, goalDate: LocalDate?, money: (Long) -> String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, useCenter = false, style = stroke)
                drawArc(SavingsColor, -90f, 360f * progress.barPercent / 100f, useCenter = false, style = stroke)
            }
            Text(
                PercentFormatter.format(progress.barPercent, currentLocale()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(goalText(progress, money), style = MaterialTheme.typography.bodyMedium)
            GoalStatus(progress)
            GoalDate(progress, goalDate)
        }
    }
}

@Composable
private fun GoalDate(progress: GoalProgress, goalDate: LocalDate?) {
    if (goalDate != null && !progress.reached) {
        Text(
            stringResource(R.string.goal_by_date, formatDate(goalDate)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "+ 500.00" / "− 300.00": signs and words, not color alone. */
@Composable
fun signedAmount(movement: PotMovement, money: (Long) -> String): String = when (movement.type) {
    MovementType.DEPOSIT -> "+ ${money(movement.amount)}"
    MovementType.WITHDRAWAL -> "− ${money(movement.amount)}"
}

/** Message for a refused change, with the available balance; null when it went through. */
fun refusedMessage(resources: Resources, change: PotChange, money: (Long) -> String): String? = when (change) {
    PotChange.Done -> null
    is PotChange.NotEnough -> resources.getString(R.string.pot_refused, money(change.balance))
    is PotChange.OpeningTooLow -> resources.getString(R.string.pot_opening_min, money(change.minimum))
    PotChange.ReadOnly -> resources.getString(R.string.pot_read_only)
}

// Pot form

@Immutable
data class PotFormInput(
    val name: String = "",
    val colorKey: String = CategoryColors.options[3].key,
    val iconKey: String = "other",
    val goal: String = "",
    val goalDate: LocalDate? = null,
    val opening: String = "",
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val goalError: Int? = null,
    @param:StringRes val openingError: Int? = null,
) {
    companion object {
        fun from(pot: Pot, currencyCode: String, locale: Locale): PotFormInput {
            val currency = Currency.getInstance(currencyCode)
            fun text(amount: Long?) = amount?.let { amountInputText(it, currency, locale) }.orEmpty()
            return PotFormInput(
                name = pot.name,
                colorKey = pot.colorKey,
                iconKey = pot.iconKey,
                goal = text(pot.goalAmount),
                goalDate = pot.goalDate,
                opening = if (pot.openingBalance == 0L) "" else text(pot.openingBalance),
            )
        }
    }
}

/** Blank optional fields mean "none" (opening balance: 0). Opening ≥ 0; goal > 0 when set. */
fun PotFormInput.validate(currencyCode: String, locale: Locale): Pair<PotInput?, PotFormInput> {
    fun optional(text: String, allowZero: Boolean): Pair<Long?, Int?> =
        if (text.isBlank()) null to null
        else when (val c = checkAmount(text, currencyCode, locale, allowZero)) {
            is AmountCheck.Valid -> c.minorUnits to null
            is AmountCheck.Invalid -> null to c.messageRes
        }
    val nameError = if (name.isBlank()) R.string.error_name_required else null
    val (goalAmount, goalError) = optional(goal, allowZero = false)
    val (openingAmount, openingError) = optional(opening, allowZero = true)
    val checked = copy(nameError = nameError, goalError = goalError, openingError = openingError)
    if (listOf(nameError, goalError, openingError).any { it != null }) return null to checked
    return PotInput(
        name = name.trim(),
        colorKey = colorKey,
        iconKey = iconKey,
        goalAmount = goalAmount,
        goalDate = goalDate.takeIf { goalAmount != null },
        openingBalance = openingAmount ?: 0,
    ) to checked
}

@Composable
fun PotDialog(
    initial: PotFormInput,
    isNew: Boolean,
    currencyCode: String,
    today: LocalDate,
    onSave: (PotInput) -> Unit,
    onDismiss: () -> Unit,
    externalError: String? = null,
) {
    val locale = currentLocale()
    var input by remember { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (isNew) R.string.dialog_pot_add_title else R.string.dialog_pot_edit_title),
        onConfirm = {
            val (valid, checked) = input.validate(currencyCode, locale)
            input = checked
            valid?.let(onSave)
        },
        onDismiss = onDismiss,
    ) {
        TextInputField(input.name, { input = input.copy(name = it, nameError = null) }, R.string.field_pot_name, errorRes = input.nameError)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
        IconPicker(input.iconKey, { input = input.copy(iconKey = it) })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelLarge)
        ColorPicker(input.colorKey, { input = input.copy(colorKey = it) })
        Spacer(Modifier.height(8.dp))
        AmountField(
            input.opening, { input = input.copy(opening = it, openingError = null) }, currencyCode,
            input.openingError, labelRes = R.string.field_opening_balance,
        )
        externalError?.let { StatusLabel(R.drawable.ic_warning, it, OverColor) }
        Spacer(Modifier.height(8.dp))
        AmountField(input.goal, { input = input.copy(goal = it, goalError = null) }, currencyCode, input.goalError, labelRes = R.string.field_goal_optional)
        if (input.goal.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget)) {
                    Icon(painterResource(R.drawable.ic_calendar), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(input.goalDate?.let { stringResource(R.string.goal_by_date, formatDate(it)) } ?: stringResource(R.string.field_goal_date_optional))
                }
                if (input.goalDate != null) {
                    TextButton(onClick = { input = input.copy(goalDate = null) }, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
        }
    }
    if (pickDate) {
        // A goal date is a target, so future dates are allowed: GoalDateRule, not RecordDateRule.
        RecordDatePickerDialog(
            date = input.goalDate ?: today.plusMonths(6),
            range = RecordDateRange(GoalDateRule.EARLIEST, GoalDateRule.latest(today)),
            onConfirm = { input = input.copy(goalDate = it); pickDate = false },
            onDismiss = { pickDate = false },
        )
    }
}

// Movements

/**
 * Add (existing == null) or edit a deposit / withdrawal. A withdrawal shows the available balance up
 * front. Stays open with the refused message when the balance rule says no.
 */
@Composable
fun MovementDialog(
    type: MovementType,
    existing: PotMovement?,
    balance: Long,
    currencyCode: String,
    today: LocalDate,
    money: (Long) -> String,
    onSave: (amount: Long, date: LocalDate, note: String?, onResult: (PotChange) -> Unit) -> Unit,
    onDelete: (PotMovement) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    var amount by remember {
        mutableStateOf(existing?.let { amountInputText(it.amount, Currency.getInstance(currencyCode), locale) }.orEmpty())
    }
    var date by remember { mutableStateOf(existing?.date ?: today) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var amountError by remember { mutableStateOf<Int?>(null) }
    var refused by remember { mutableStateOf<PotChange?>(null) }
    val title = when {
        existing != null && type == MovementType.DEPOSIT -> R.string.dialog_edit_deposit_title
        existing != null -> R.string.dialog_edit_withdrawal_title
        type == MovementType.DEPOSIT -> R.string.action_deposit
        else -> R.string.action_withdraw
    }
    FormDialog(
        title = stringResource(title),
        onConfirm = {
            when (val check = checkAmount(amount, currencyCode, locale, allowZero = false)) {
                is AmountCheck.Invalid -> amountError = check.messageRes
                is AmountCheck.Valid -> onSave(check.minorUnits, date, note.trim().ifEmpty { null }) { result ->
                    if (result == PotChange.Done) onDismiss() else refused = result
                }
            }
        },
        onDismiss = onDismiss,
    ) {
        if (type == MovementType.WITHDRAWAL) {
            Text(
                stringResource(R.string.pot_available, money(PotRules.availableForWithdrawal(balance, existing))),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
        AmountField(amount, { amount = it; amountError = null; refused = null }, currencyCode, amountError)
        refused?.let { change -> refusedMessage(LocalResources.current, change, money)?.let { StatusLabel(R.drawable.ic_warning, it, OverColor) } }
        Spacer(Modifier.height(8.dp))
        PastDateField(date, RecordDateRule.range(today), { date = it; refused = null }, R.string.field_date)
        Spacer(Modifier.height(8.dp))
        TextInputField(note, { note = it }, R.string.field_note_optional, singleLine = false)
        if (existing != null) {
            Spacer(Modifier.height(8.dp))
            ActionButton(R.drawable.ic_delete, R.string.action_delete) { onDelete(existing) }
        }
    }
}
