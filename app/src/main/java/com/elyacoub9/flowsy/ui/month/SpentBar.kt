package com.elyacoub9.flowsy.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.money.PercentFormatter
import com.elyacoub9.flowsy.domain.month.SpentRatio
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.theme.OverColor
import com.elyacoub9.flowsy.ui.theme.SpentColor

/**
 * "54 % of income spent" with a bar capped at 100 %. Over: Over color, a warning icon and the real
 * percentage. No income: text only. Nothing for an empty month.
 */
@Composable
fun SpentBar(ratio: SpentRatio, modifier: Modifier = Modifier) {
    when (ratio) {
        SpentRatio.Empty -> Unit
        SpentRatio.NoIncome -> WarningText(stringResource(R.string.month_no_income), modifier)
        is SpentRatio.Ratio -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val text = stringResource(R.string.month_spent_ratio, PercentFormatter.format(ratio.percent, currentLocale()))
            if (ratio.over) {
                WarningText(text)
            } else {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RatioBar(ratio, height = 10.dp)
        }
    }
}

/** Spent / income bar, capped at 100 %; Over color when spent > income. */
@Composable
fun RatioBar(ratio: SpentRatio.Ratio, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.barPercent / 100f)
                .background(if (ratio.over) OverColor else SpentColor),
        )
    }
}

@Composable
private fun WarningText(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = OverColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OverColor)
    }
}

@Preview
@Composable
private fun SpentBarPreview() {
    FlowsyTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SpentBar(SpentRatio.Ratio(percent = 54, barPercent = 54, over = false))
                SpentBar(SpentRatio.Ratio(percent = 112, barPercent = 100, over = true))
                SpentBar(SpentRatio.NoIncome)
            }
        }
    }
}
