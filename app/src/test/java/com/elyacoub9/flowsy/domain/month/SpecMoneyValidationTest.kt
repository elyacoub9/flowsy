package com.elyacoub9.flowsy.domain.month

import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.expense.Expense
import com.elyacoub9.flowsy.domain.income.Income
import com.elyacoub9.flowsy.domain.money.MoneyFormatter
import com.elyacoub9.flowsy.domain.money.PercentFormatter
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import com.elyacoub9.flowsy.domain.pot.SavingsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

/**
 * APP_SPEC "Validation test cases → Money": MAD, October 2026.
 * Income 12,550.00 − Spent 6,842.50 = Left 5,707.50.
 */
class SpecMoneyValidationTest {

    private val october = YearMonth.of(2026, 10)
    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 10, dayOfMonth)

    private val income = listOf(
        Income(1, "Salary", 1_235_000, day(1), null, 0),
        Income(2, "Refund", 20_000, day(10), null, 0),
    )

    private val bills = 1L
    private val groceries = 2L
    private val transport = 3L
    private val restaurants = 4L

    private fun expense(amount: Long, categoryId: Long, note: String? = null) = Expense(0, amount, categoryId, day(5), note, 0)

    private val expenses = listOf(
        expense(450_000, bills, "Rent"),
        expense(34_250, bills, "Electricity"),
        expense(82_040, groceries),
        expense(15_000, transport),
        expense(38_960, restaurants),
        expense(64_000, groceries),
    )

    private fun totals(extra: List<Expense> = emptyList()) = MonthCalculator.totals(october, income, expenses + extra)

    @Test
    fun income_12550() {
        assertEquals(1_255_000L, totals().income)
    }

    @Test
    fun spent_6842_50() {
        assertEquals(684_250L, totals().spent)
    }

    @Test
    fun left_5707_50() {
        val totals = totals()
        assertEquals(570_750L, totals.left)
        assertFalse(totals.isOver)
        assertTrue(MoneyFormatter.format(totals.left, Currency.getInstance("MAD"), Locale.ENGLISH).contains("5,707.50"))
    }

    @Test
    fun categoryTotals() {
        assertEquals(
            mapOf(bills to 484_250L, groceries to 146_040L, restaurants to 38_960L, transport to 15_000L),
            MonthCalculator.spentByCategory(october, expenses),
        )
    }

    @Test
    fun spentRatio_54() {
        assertEquals(SpentRatio.Ratio(percent = 54, barPercent = 54, over = false), MonthCalculator.spentRatio(totals()))
        assertEquals("54%", PercentFormatter.format(54, Locale.ENGLISH))
    }

    @Test
    fun categoryBreakdown_largestFirst_percentagesAddUpTo100() {
        val categories = listOf(
            Category(groceries, null, "groceries", "cart", "green", false, 0),
            Category(restaurants, null, "restaurants", "restaurant", "orange", false, 1),
            Category(transport, null, "transport", "bus", "blue", false, 2),
            Category(bills, null, "bills", "bolt", "yellow", false, 3),
        )
        val slices = MonthCalculator.categoryBreakdown(october, expenses, categories)
        assertEquals(
            listOf(
                Triple(bills, 484_250L, 71), // 70.77 %
                Triple(groceries, 146_040L, 21), // 21.34 %
                Triple(restaurants, 38_960L, 6), // 5.69 %
                Triple(transport, 15_000L, 2), // 2.19 %
            ),
            slices.map { Triple(it.category.id, it.amount, it.percent) },
        )
        assertEquals(100, slices.sumOf { it.percent })
    }

    @Test
    fun variation_expense6000_overBy292_50() {
        val totals = totals(extra = listOf(expense(600_000, groceries)))
        assertEquals(-29_250L, totals.left)
        assertTrue(totals.isOver)
        assertEquals(29_250L, totals.overBy)
    }

    // Savings: pot balances only. The month totals never take movements, so savings cannot change Left.

    private val emergency = Pot(1, "Emergency", "green", "other", null, null, 1_000_000, false, 0)
    private val travel = Pot(2, "Travel", "blue", "other", null, null, 0, false, 1)

    private fun movement(id: Long, potId: Long, type: MovementType, amount: Long) =
        PotMovement(id, potId, type, amount, day(3), null, 0)

    private val emergencyMovements = listOf(movement(1, 1, MovementType.DEPOSIT, 100_000))
    private val travelMovements = listOf(
        movement(2, 2, MovementType.DEPOSIT, 50_000),
        movement(3, 2, MovementType.WITHDRAWAL, 30_000),
    )

    @Test
    fun savings_balances_total11200() {
        val emergencyBalance = PotRules.balance(emergency.openingBalance, emergencyMovements)
        val travelBalance = PotRules.balance(travel.openingBalance, travelMovements)
        assertEquals(1_100_000L, emergencyBalance)
        assertEquals(20_000L, travelBalance)
        val archived = Pot(3, "Old", "red", "other", null, null, 50_000, true, 2)
        val total = SavingsCalculator.total(listOf(emergency to emergencyBalance, travel to travelBalance, archived to 50_000L))
        assertEquals(1_120_000L, total) // the archived pot is not counted
    }

    @Test
    fun savings_withdraw250FromTravel_refused() {
        val withdraw250 = movement(9, 2, MovementType.WITHDRAWAL, 25_000)
        assertFalse(PotRules.canAdd(travel, travelMovements, withdraw250))
        assertTrue(PotRules.canAdd(travel, travelMovements, withdraw250.copy(amount = 20_000)))
    }
}
