package com.elyacoub9.flowsy.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.money.CurrencyDefaults
import java.util.Currency

/** Searchable currency list, shared by onboarding and Settings. */
@Composable
fun CurrencyList(
    selectedCode: String,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val all = remember { CurrencyDefaults.available() }
    // The currency selected when the step opens is pinned first; later taps don't reorder the list.
    val pinnedCode = remember { selectedCode }
    val filtered = remember(query, locale) {
        val q = query.trim()
        val matches = if (q.isEmpty()) all
        else all.filter {
            it.currencyCode.contains(q, ignoreCase = true) ||
                it.getDisplayName(locale).contains(q, ignoreCase = true)
        }
        val (pinned, rest) = matches.partition { it.currencyCode == pinnedCode }
        pinned + rest
    }
    val selected = remember(selectedCode) { Currency.getInstance(selectedCode) }

    Column(modifier) {
        Text(
            stringResource(R.string.currency_selected, "${selected.currencyCode} — ${selected.getDisplayName(locale)}"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.currency_search)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.size(8.dp))
        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.currency_no_results),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.selectableGroup()) {
            items(filtered, key = { it.currencyCode }) { currency ->
                val isSelected = currency.currencyCode == selectedCode
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(currency.currencyCode) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        currency.currencyCode,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(currency.getDisplayName(locale), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
