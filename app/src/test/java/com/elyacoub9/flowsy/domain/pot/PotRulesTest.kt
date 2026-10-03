package com.elyacoub9.flowsy.domain.pot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PotRulesTest {

    private val today = LocalDate.of(2026, 10, 2)
    private val pot = Pot(1, "Travel", "blue", "other", null, null, openingBalance = 10_000, archived = false, sortOrder = 0)

    private fun m(id: Long, type: MovementType, amount: Long, date: LocalDate = today) =
        PotMovement(id, 1, type, amount, date, null, 0)

    private val deposit = m(1, MovementType.DEPOSIT, 50_000)
    private val withdrawal = m(2, MovementType.WITHDRAWAL, 55_000)
    private val movements = listOf(deposit, withdrawal) // balance 10,000 + 50,000 − 55,000 = 5,000

    @Test
    fun balance() {
        assertEquals(5_000L, PotRules.balance(pot.openingBalance, movements))
    }

    @Test
    fun withdrawal_cannotGoNegative() {
        assertTrue(PotRules.canAdd(pot, movements, m(3, MovementType.WITHDRAWAL, 5_000)))
        assertFalse(PotRules.canAdd(pot, movements, m(3, MovementType.WITHDRAWAL, 5_001)))
    }

    @Test
    fun editDeposit_down_checkedOnTotal() {
        assertTrue(PotRules.canEdit(pot, movements, deposit, deposit.copy(amount = 45_000)))
        assertFalse(PotRules.canEdit(pot, movements, deposit, deposit.copy(amount = 44_999)))
        // Raising a withdrawal is checked the same way.
        assertFalse(PotRules.canEdit(pot, movements, withdrawal, withdrawal.copy(amount = 60_001)))
    }

    @Test
    fun checkedOnTotal_notDayByDay() {
        // Withdrawal dated before the deposit is fine as long as the total stays ≥ 0.
        val empty = pot.copy(openingBalance = 0)
        val early = m(1, MovementType.WITHDRAWAL, 100, LocalDate.of(2026, 9, 26))
        val later = m(2, MovementType.DEPOSIT, 100, LocalDate.of(2026, 10, 1))
        assertTrue(PotRules.canAdd(empty, listOf(later), early))
    }

    @Test
    fun deleteDeposit_refusedWhenItFundsWithdrawals() {
        assertFalse(PotRules.canDeleteMovement(pot, movements, deposit))
        assertTrue(PotRules.canDeleteMovement(pot, movements, withdrawal))
    }

    @Test
    fun openingBalance_cannotMakeBalanceNegative() {
        assertTrue(PotRules.canSetOpeningBalance(5_000, movements))
        assertFalse(PotRules.canSetOpeningBalance(4_999, movements))
        assertFalse(PotRules.canSetOpeningBalance(-1, emptyList()))
    }

    @Test
    fun deletePot_onlyWhenEmptyWithoutMovements() {
        assertTrue(PotRules.canDeletePot(pot.copy(openingBalance = 0), emptyList()))
        assertFalse(PotRules.canDeletePot(pot, emptyList()))
        val zeroBalance = listOf(m(1, MovementType.DEPOSIT, 500), m(2, MovementType.WITHDRAWAL, 500))
        assertFalse(PotRules.canDeletePot(pot.copy(openingBalance = 0), zeroBalance))
    }

    // Goal

    @Test
    fun goal_belowAtAbove() {
        val below = GoalProgress.of(146_040, 150_000, null, today)!!
        assertEquals(97L, below.percent)
        assertEquals(97, below.barPercent)
        assertFalse(below.reached)

        val at = GoalProgress.of(150_000, 150_000, null, today)!!
        assertTrue(at.reached)
        assertEquals(100, at.barPercent)

        val above = GoalProgress.of(300_000, 150_000, null, today)!!
        assertEquals(200L, above.percent)
        assertEquals(100, above.barPercent)
        assertTrue(above.reached)
    }

    @Test
    fun goal_datePassed_onlyWhenNotReached() {
        val yesterday = today.minusDays(1)
        assertTrue(GoalProgress.of(1, 150_000, yesterday, today)!!.datePassed)
        assertFalse(GoalProgress.of(1, 150_000, today, today)!!.datePassed)
        assertFalse(GoalProgress.of(150_000, 150_000, yesterday, today)!!.datePassed)
        assertNull(GoalProgress.of(1, null, yesterday, today))
    }

    @Test
    fun editDepositDown_orDeleteDeposit_refusedWhenItWouldGoNegative() {
        // Spec Travel: deposit 500.00, withdrawal 300.00 → 200.00.
        val travel = pot.copy(openingBalance = 0)
        val dep = m(1, MovementType.DEPOSIT, 50_000)
        val wd = m(2, MovementType.WITHDRAWAL, 30_000)
        val list = listOf(dep, wd)
        assertFalse(PotRules.canEdit(travel, list, dep, dep.copy(amount = 29_999)))
        assertTrue(PotRules.canEdit(travel, list, dep, dep.copy(amount = 30_000)))
        assertFalse(PotRules.canDeleteMovement(travel, list, dep))
        assertTrue(PotRules.canDeleteMovement(travel, list, wd))
    }

    @Test
    fun minOpeningBalance_coversNetWithdrawals() {
        assertEquals(5_000L, PotRules.minOpeningBalance(movements)) // 50,000 in, 55,000 out
        assertEquals(0L, PotRules.minOpeningBalance(listOf(deposit)))
        assertEquals(0L, PotRules.minOpeningBalance(emptyList()))
    }

    @Test
    fun availableForWithdrawal_includesTheEditedWithdrawal() {
        assertEquals(5_000L, PotRules.availableForWithdrawal(5_000, null))
        assertEquals(60_000L, PotRules.availableForWithdrawal(5_000, withdrawal))
        assertEquals(5_000L, PotRules.availableForWithdrawal(5_000, deposit))
    }

    @Test
    fun archivedPot_readOnly_andNotInTotal() {
        assertFalse(PotRules.isReadOnly(pot))
        assertTrue(PotRules.isReadOnly(pot.copy(archived = true)))
        val total = SavingsCalculator.total(listOf(pot to 5_000L, pot.copy(id = 2, archived = true) to 9_999L))
        assertEquals(5_000L, total)
        assertEquals(0L, SavingsCalculator.total(emptyList()))
    }

    @Test
    fun goalLoweredBelowBalance_reached_andFlooredPercent() {
        val lowered = GoalProgress.of(balance = 120_000, goalAmount = 100_000, goalDate = null, today = today)!!
        assertTrue(lowered.reached)
        assertEquals(120L, lowered.percent)
        assertEquals(100, lowered.barPercent)
        assertEquals(66L, GoalProgress.of(2, 3, null, today)!!.percent) // 66.67 % floored
    }

    @Test
    fun goalDate_mayBeInTheFuture() {
        val goalDate = today.plusYears(3)
        assertTrue(!goalDate.isBefore(GoalDateRule.EARLIEST) && !goalDate.isAfter(GoalDateRule.latest(today)))
        assertFalse(GoalProgress.of(1, 150_000, goalDate, today)!!.datePassed)
    }
}
