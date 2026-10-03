package com.elyacoub9.flowsy.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.AppLanguage
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale of the current app language, for formatting numbers and dates. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

val MinTouchTarget = 48.dp

@get:StringRes
val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.FRENCH -> R.string.language_french
    }

/** Radio list with one row per language; the whole row is the touch target. */
@Composable
fun LanguagePicker(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup()) {
        AppLanguage.entries.forEach { language ->
            RadioRow(
                text = stringResource(language.labelRes),
                selected = language == selected,
                onClick = { onSelect(language) },
            )
        }
    }
}

@Composable
fun RadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "October 2026" / "Octobre 2026", or just "October" / "Octobre", in the app language. */
fun formatMonth(month: YearMonth, locale: Locale, withYear: Boolean = true): String =
    DateTimeFormatter.ofPattern(if (withYear) "LLLL yyyy" else "LLLL", locale).format(month)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** One-letter month for chart axes: "O" for October, "O" for octobre. */
fun formatMonthInitial(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLLL", locale).format(month).uppercase(locale)
