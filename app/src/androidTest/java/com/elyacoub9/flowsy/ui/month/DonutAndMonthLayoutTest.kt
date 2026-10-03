package com.elyacoub9.flowsy.ui.month

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.money.MoneyFormatter
import com.elyacoub9.flowsy.domain.money.PercentFormatter
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Currency
import kotlin.math.cos
import kotlin.math.sin

/** Preview data only (spec October case); no app database. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DonutAndMonthLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private val locale get() = compose.activity.resources.configuration.locales[0]
    private fun money(minor: Long) = MoneyFormatter.format(minor, Currency.getInstance("MAD"), locale)

    private val slices = PreviewData.normal.slices
    private val total = slices.sumOf { it.amount }

    private fun setDonut() = compose.setContent {
        var selected by remember { mutableStateOf<Long?>(null) }
        FlowsyTheme {
            Donut(slices, total, ::money, slices.firstOrNull { it.category.id == selected }, onSelect = { selected = it })
        }
    }

    private val donut get() = compose.onNode(hasContentDescription(text(R.string.month_spending_title), substring = true, ignoreCase = true))

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    /** Centerline of the ring, [degrees] clockwise from 12 o'clock, for a 220 dp donut (8 dp margin, 28 dp ring). */
    private fun ringPoint(width: Float, degrees: Double): Offset {
        val radius = width / 2f - width * (8f + 14f) / 220f
        val rad = Math.toRadians(degrees - 90)
        return Offset(width / 2f + radius * cos(rad).toFloat(), width / 2f + radius * sin(rad).toFloat())
    }

    private fun billsText() = text(R.string.donut_slice, text(R.string.category_bills), money(484_250), PercentFormatter.format(slices[0].percent, locale))

    @Test
    fun tapSlice_showsItsDetails_tapAgain_backToTotal() {
        setDonut()
        val totalText = text(R.string.month_spent) + " " + money(total)
        donut.assert(stateIs(totalText))

        // Bills is the first slice (about 71 %): its middle is around 128° from 12 o'clock.
        donut.performTouchInput { click(ringPoint(width.toFloat(), 128.0)) }
        donut.assert(stateIs(billsText()))

        donut.performTouchInput { click(ringPoint(width.toFloat(), 128.0)) }
        donut.assert(stateIs(totalText))

        // Tapping the center also goes back to the total.
        donut.performTouchInput { click(ringPoint(width.toFloat(), 128.0)) }
        donut.performTouchInput { click(center) }
        donut.assert(stateIs(totalText))
    }

    @Test
    fun talkBackAction_selectsSlice() {
        setDonut()
        donut.performCustomAccessibilityActionWithLabel(text(R.string.donut_action_show, text(R.string.category_bills)))
        donut.assert(stateIs(billsText()))
        donut.performCustomAccessibilityActionWithLabel(text(R.string.donut_action_total))
        donut.assert(stateIs(text(R.string.month_spent) + " " + money(total)))
    }

    @Test
    fun lastListItem_scrollsFullyAboveTheAddButton() {
        compose.setContent {
            FlowsyTheme {
                MonthScreen(
                    state = PreviewData.normal,
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpenSettings = {}, onBack = null, onPreviousMonth = {}, onNextMonth = {},
                    onSaveExpense = { _, _ -> }, onDeleteExpense = {}, onSaveIncome = { _, _ -> }, onDeleteIncome = {},
                )
            }
        }
        val list = compose.onNode(hasScrollAction())
        repeat(6) { list.performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val last = list.onChildren().onLast().fetchSemanticsNode().boundsInRoot
        val fab = compose.onNodeWithContentDescription(text(R.string.action_add_record)).fetchSemanticsNode().boundsInRoot
        assertTrue("last item bottom ${last.bottom} must be above the button top ${fab.top}", last.bottom <= fab.top)
    }
}
