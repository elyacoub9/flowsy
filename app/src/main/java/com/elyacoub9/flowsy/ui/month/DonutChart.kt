package com.elyacoub9.flowsy.ui.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.money.PercentFormatter
import com.elyacoub9.flowsy.domain.month.CategorySlice
import com.elyacoub9.flowsy.domain.month.DonutGeometry
import com.elyacoub9.flowsy.ui.common.CategoryBadge
import com.elyacoub9.flowsy.ui.common.CategoryColors
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.displayName
import com.elyacoub9.flowsy.ui.common.rememberMoneyFormat
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme

private val DonutSize = 220.dp
private val RingWidth = 28.dp

/** Extra width of the selected slice, pushed outwards (the ring leaves room for it). */
private val SelectedExtra = 8.dp

/** Diameter of the hole: the center text must fit inside it. */
private val HoleSize = DonutSize - SelectedExtra * 2 - RingWidth * 2

/**
 * Interactive spending donut: total spent in the center; tapping a slice selects it and shows its icon,
 * name, amount and % in the center; tapping it again or the center goes back to the total. The
 * selection resets when [resetKey] (the month) or the slices change.
 */
@Composable
fun SpendingBreakdown(
    slices: List<CategorySlice>,
    totalSpent: Long,
    money: (Long) -> String,
    resetKey: Any,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.month_spending_title), style = MaterialTheme.typography.titleMedium)
        if (slices.isEmpty()) {
            Text(
                stringResource(R.string.month_no_expenses),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        var selectedId by rememberSaveable(resetKey, slices) { mutableStateOf<Long?>(null) }
        Donut(
            slices = slices,
            totalSpent = totalSpent,
            money = money,
            selected = slices.firstOrNull { it.category.id == selectedId },
            onSelect = { id -> selectedId = id },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            stringResource(R.string.donut_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
internal fun Donut(
    slices: List<CategorySlice>,
    totalSpent: Long,
    money: (Long) -> String,
    selected: CategorySlice?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val sweeps = remember(slices) { DonutGeometry.sweeps(slices.map { it.amount }) }
    val colors = slices.map { CategoryColors.color(it.category.colorKey) }
    fun percent(slice: CategorySlice) = PercentFormatter.format(slice.percent, locale)

    // Accessibility: the whole chart is read as one description; each slice is an action.
    val parts = slices.map { stringResource(R.string.donut_slice, it.category.displayName(), money(it.amount), percent(it)) }
    val description = stringResource(R.string.donut_description, money(totalSpent), parts.joinToString("; "))
    val selectedText = selected?.let { parts[slices.indexOf(it)] } ?: stringResource(R.string.month_spent) + " " + money(totalSpent)
    val names = slices.map { it.category.displayName() }
    val actions = slices.mapIndexed { i, slice ->
        CustomAccessibilityAction(stringResource(R.string.donut_action_show, names[i])) { onSelect(slice.category.id); true }
    } + CustomAccessibilityAction(stringResource(R.string.donut_action_total)) { onSelect(null); true }

    // The tap handler lives as long as the slices; it must read the current selection, not the first one.
    val currentSelected by rememberUpdatedState(selected)
    val density = LocalDensity.current
    val ring = with(density) { RingWidth.toPx() }
    val extra = with(density) { SelectedExtra.toPx() }

    Box(
        modifier
            .size(DonutSize)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = selectedText
                liveRegion = LiveRegionMode.Polite
                customActions = actions
            }
            .pointerInput(slices) {
                detectTapGestures { offset ->
                    val outer = size.width / 2f - extra
                    when (val hit = DonutGeometry.hitTest(
                        offset.x, offset.y, size.width / 2f, size.height / 2f,
                        innerRadius = outer - ring, outerRadius = outer, sweeps = sweeps, outerMargin = extra,
                    )) {
                        is DonutGeometry.Hit.Slice -> {
                            val id = slices[hit.index].category.id
                            onSelect(if (id == currentSelected?.category?.id) null else id)
                        }
                        DonutGeometry.Hit.Center -> onSelect(null)
                        DonutGeometry.Hit.Outside -> Unit
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f - extra
            val center = Offset(size.width / 2f, size.height / 2f)
            // A thin gap between slices keeps neighbours apart without relying on color.
            val gap = if (sweeps.size > 1) 1.5f else 0f
            var start = -90f
            sweeps.forEachIndexed { i, sweep ->
                val isSelected = slices[i] == selected
                // Selected: thicker, pushed outwards (same inner edge); the others are dimmed.
                val width = if (isSelected) ring + extra else ring
                val radius = outer - ring + width / 2f
                drawArc(
                    color = colors[i].copy(alpha = if (selected == null || isSelected) 1f else 0.35f),
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width),
                )
                start += sweep
            }
        }
        CenterContent(selected, totalSpent, money, ::percent)
    }
}

/**
 * Fits inside the hole whatever the text size: the name takes at most 2 lines (ellipsis), the amount
 * shrinks (down to 9 sp) instead of wrapping, and at large font scales the decorative icon is dropped.
 */
@Composable
private fun CenterContent(selected: CategorySlice?, totalSpent: Long, money: (Long) -> String, percent: (CategorySlice) -> String) {
    val fontScale = LocalDensity.current.fontScale
    Column(
        Modifier
            .size(HoleSize * 0.8f, HoleSize * 0.86f)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val onSurface = MaterialTheme.colorScheme.onSurface
        if (selected == null) {
            Text(stringResource(R.string.month_spent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Amount(money(totalSpent), MaterialTheme.typography.titleLarge.fontSize.value, onSurface, Modifier.weight(1f, fill = false))
        } else {
            if (fontScale < 1.5f) CategoryBadge(selected.category, size = 24.dp)
            Text(
                selected.category.displayName(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Amount(money(selected.amount), MaterialTheme.typography.titleMedium.fontSize.value, onSurface, Modifier.weight(1f, fill = false))
            Text(percent(selected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Amount(text: String, maxSp: Float, color: Color, modifier: Modifier) {
    BasicText(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = maxSp.sp),
    )
}

// Previews

@Composable
private fun DonutPreview(slices: List<CategorySlice>, selectedIndex: Int?) {
    FlowsyTheme {
        Surface {
            Donut(
                slices = slices,
                totalSpent = slices.sumOf { it.amount },
                money = rememberMoneyFormat("MAD"),
                selected = selectedIndex?.let { slices[it] },
                onSelect = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private val specSlices get() = PreviewData.normal.slices

/** A 0.10 category next to large ones: drawn and tapped with the minimum sweep. */
private val tinySlices get() = specSlices + CategorySlice(PreviewData.categories[BuiltInCategory.HEALTH.ordinal], 10, 0)

private fun longNameSlices(name: String?, builtIn: BuiltInCategory?) = listOf(
    CategorySlice(
        Category(99, name, builtIn?.key, builtIn?.iconKey ?: "heart", builtIn?.colorKey ?: "violet", false, 0),
        amount = 12_345_678, // 123,456.78
        percent = 88,
    ),
    CategorySlice(PreviewData.categories[0], 1_684_000, 12),
)

@Preview @Composable private fun DonutNoSelectionPreview() = DonutPreview(specSlices, null)

@Preview @Composable private fun DonutBillsSelectedPreview() = DonutPreview(specSlices, 0)

@Preview @Composable private fun DonutTinySelectedPreview() = DonutPreview(tinySlices, tinySlices.lastIndex)

@Preview(locale = "fr") @Composable private fun DonutLongFrenchPreview() =
    DonutPreview(longNameSlices(null, BuiltInCategory.ENTERTAINMENT), 0)

@Preview(locale = "fr", fontScale = 1.5f) @Composable private fun DonutLongFrenchScale15Preview() =
    DonutPreview(longNameSlices(null, BuiltInCategory.ENTERTAINMENT), 0)

@Preview(locale = "fr", fontScale = 2f) @Composable private fun DonutLongFrenchScale20Preview() =
    DonutPreview(longNameSlices(null, BuiltInCategory.ENTERTAINMENT), 0)

@Preview @Composable private fun DonutLongCustomPreview() =
    DonutPreview(longNameSlices("Abonnements logiciels et cloud", null), 0)

@Preview(fontScale = 1.5f) @Composable private fun DonutLongCustomScale15Preview() =
    DonutPreview(longNameSlices("Abonnements logiciels et cloud", null), 0)

@Preview(fontScale = 2f) @Composable private fun DonutLongCustomScale20Preview() =
    DonutPreview(longNameSlices("Abonnements logiciels et cloud", null), 0)
