package com.elyacoub9.flowsy.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R

/** A full-width row that opens another screen. */
@Composable
fun NavRow(@StringRes textRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
}

/** Status shown with an icon and a word, never by color alone. */
@Composable
fun StatusLabel(@DrawableRes iconRes: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun ActionButton(@DrawableRes iconRes: Int, @StringRes textRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = MinTouchTarget)) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(textRes))
    }
}
