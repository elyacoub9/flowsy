package com.elyacoub9.flowsy.ui.year

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.money.CompactAmountFormatter
import com.elyacoub9.flowsy.domain.money.fractionDigits
import com.elyacoub9.flowsy.domain.year.MonthBars
import com.elyacoub9.flowsy.domain.year.MonthStatus
import com.elyacoub9.flowsy.domain.year.YearSummary
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.formatMonth
import com.elyacoub9.flowsy.ui.common.formatMonthInitial
import com.elyacoub9.flowsy.ui.common.rememberMoneyFormat
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.theme.IncomeColor
import com.elyacoub9.flowsy.ui.theme.SpentColor
import java.time.YearMonth
import java.util.Currency

/**
 * Income and spent bars side by side for each month. On a 360 dp phone there is no room for 24
 * labels, so only the selected month (tap anywhere in its column) gets value labels; the line under
 * the chart repeats them in full. The current month has a triangle marker and a bold initial.
 */
@Composable
fun YearBarChart(
    summary: YearSummary,
    bars: List<MonthBars>,
    selected: YearMonth?,
    onSelect: (YearMonth) -> Unit,
    currencyCode: String,
    money: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val digits = Currency.getInstance(currencyCode).fractionDigits()
    val compact = { minor: Long -> CompactAmountFormatter.format(minor, digits, locale) }
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val initials = summary.months.map { formatMonthInitial(it.month, locale) }

    val described = summary.months.filter { it.status != MonthStatus.FUTURE }.map {
        stringResource(R.string.chart_month, formatMonth(it.month, locale, withYear = false), money(it.totals.income), money(it.totals.spent))
    }
    val description = stringResource(R.string.chart_description, described.joinToString("; "))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clearAndSetSemantics { contentDescription = description }
                .pointerInput(summary.year) {
                    // Tap area = the full height of a month's column.
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width / 12f)).toInt().coerceIn(0, 11)
                        onSelect(summary.months[index].month)
                    }
                },
        ) {
            val column = size.width / 12f
            val labelArea = 34.dp.toPx()
            val axisArea = 30.dp.toPx()
            val chartTop = labelArea
            val chartBottom = size.height - axisArea
            val chartHeight = chartBottom - chartTop
            val barWidth = column * 0.3f
            val gap = column * 0.06f
            val labelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

            drawLine(track, Offset(0f, chartBottom), Offset(size.width, chartBottom), strokeWidth = 1.dp.toPx())

            summary.months.forEachIndexed { i, month ->
                val left = column * i
                val center = left + column / 2
                val isSelected = month.month == selected
                if (isSelected) {
                    drawRoundRect(track, Offset(left + 1.dp.toPx(), chartTop), Size(column - 2.dp.toPx(), chartHeight), CornerRadius(6.dp.toPx()))
                }
                val incomeHeight = chartHeight * bars[i].income
                val spentHeight = chartHeight * bars[i].spent
                drawRect(IncomeColor, Offset(center - gap / 2 - barWidth, chartBottom - incomeHeight), Size(barWidth, incomeHeight))
                drawRect(SpentColor, Offset(center + gap / 2, chartBottom - spentHeight), Size(barWidth, spentHeight))

                // Month initial; the current month is bold with a triangle above it.
                val initial = measurer.measure(
                    initials[i],
                    TextStyle(fontSize = 12.sp, fontWeight = if (month.isCurrent) FontWeight.Bold else FontWeight.Normal),
                )
                val initialTop = chartBottom + 12.dp.toPx()
                drawText(
                    initial,
                    color = if (month.status == MonthStatus.FUTURE) muted.copy(alpha = 0.5f) else if (month.isCurrent) onSurface else muted,
                    topLeft = Offset(center - initial.size.width / 2f, initialTop),
                )
                if (month.isCurrent) {
                    val t = 4.dp.toPx()
                    val top = chartBottom + 3.dp.toPx()
                    drawPath(
                        Path().apply { moveTo(center, top); lineTo(center - t, top + t * 1.5f); lineTo(center + t, top + t * 1.5f); close() },
                        onSurface,
                    )
                }

                // Value labels for the selected month only, kept inside the chart's width.
                if (isSelected && month.status == MonthStatus.RECORDS) {
                    listOf(compact(month.totals.income) to IncomeColor, compact(month.totals.spent) to SpentColor)
                        .forEachIndexed { line, (text, color) ->
                            val layout = measurer.measure(text, labelStyle)
                            val x = (center - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                            drawText(layout, color = color, topLeft = Offset(x, line * (labelArea / 2)))
                        }
                }
            }
        }

        Legend()

        val month = summary.months.firstOrNull { it.month == selected }
        Text(
            when {
                month == null || month.status == MonthStatus.FUTURE -> stringResource(R.string.chart_hint)
                month.status == MonthStatus.NO_RECORDS ->
                    stringResource(R.string.chart_month_no_records, formatMonth(month.month, locale, withYear = false))
                else -> stringResource(
                    R.string.chart_month, formatMonth(month.month, locale, withYear = false),
                    money(month.totals.income), money(month.totals.spent),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(IncomeColor, stringResource(R.string.month_income))
        LegendItem(SpentColor, stringResource(R.string.month_spent))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview
@Composable
private fun YearBarChartPreview() {
    FlowsyTheme {
        Surface {
            val state = PreviewData.current
            YearBarChart(
                state.summary, state.bars!!, state.defaultChartMonth, {}, "MAD",
                rememberMoneyFormat("MAD"), Modifier.padding(16.dp),
            )
        }
    }
}
