package com.elyacoub9.flowsy.domain.pot

import java.time.LocalDate

enum class MovementType { DEPOSIT, WITHDRAWAL }

data class Pot(
    val id: Long,
    val name: String,
    val colorKey: String,
    val iconKey: String,
    val goalAmount: Long?,
    val goalDate: LocalDate?,
    val openingBalance: Long,
    val archived: Boolean,
    val sortOrder: Int,
)

data class PotMovement(
    val id: Long,
    val potId: Long,
    val type: MovementType,
    val amount: Long,
    val date: LocalDate,
    val note: String?,
    val createdAt: Long,
)

/** Balance rules. The pot's total balance can never go below 0; checked on the total, not day by day. */
object PotRules {

    fun effect(movement: PotMovement): Long = when (movement.type) {
        MovementType.DEPOSIT -> movement.amount
        MovementType.WITHDRAWAL -> Math.negateExact(movement.amount)
    }

    /** Opening balance + deposits − withdrawals. Exact arithmetic. */
    fun balance(openingBalance: Long, movements: List<PotMovement>): Long =
        movements.fold(openingBalance) { sum, m -> Math.addExact(sum, effect(m)) }

    /** The balance after replacing [removed] (if any) by [added] (if any). */
    fun balanceAfter(
        openingBalance: Long,
        movements: List<PotMovement>,
        removed: PotMovement? = null,
        added: PotMovement? = null,
    ): Long {
        var result = balance(openingBalance, movements)
        if (removed != null) result = Math.subtractExact(result, effect(removed))
        if (added != null) result = Math.addExact(result, effect(added))
        return result
    }

    fun canAdd(pot: Pot, movements: List<PotMovement>, movement: PotMovement): Boolean =
        balanceAfter(pot.openingBalance, movements, added = movement) >= 0

    fun canEdit(pot: Pot, movements: List<PotMovement>, old: PotMovement, new: PotMovement): Boolean =
        balanceAfter(pot.openingBalance, movements, removed = old, added = new) >= 0

    fun canDeleteMovement(pot: Pot, movements: List<PotMovement>, movement: PotMovement): Boolean =
        balanceAfter(pot.openingBalance, movements, removed = movement) >= 0

    fun canSetOpeningBalance(newOpening: Long, movements: List<PotMovement>): Boolean =
        newOpening >= 0 && balance(newOpening, movements) >= 0

    /** Lowest opening balance that keeps the total ≥ 0 (more has been withdrawn than deposited). */
    fun minOpeningBalance(movements: List<PotMovement>): Long = maxOf(0L, Math.negateExact(balance(0, movements)))

    /**
     * Most that can be withdrawn: the balance, plus the amount of [editing] when it is a withdrawal
     * being changed (its old amount comes back first).
     */
    fun availableForWithdrawal(balance: Long, editing: PotMovement?): Long =
        if (editing?.type == MovementType.WITHDRAWAL) Math.addExact(balance, editing.amount) else balance

    /** Archived pots are read-only until restored: no movements, no edits. */
    fun isReadOnly(pot: Pot): Boolean = pot.archived

    /** Only an empty pot with no movements; otherwise archive, so its history stays. */
    fun canDeletePot(pot: Pot, movements: List<PotMovement>): Boolean =
        movements.isEmpty() && pot.openingBalance == 0L

    fun isValidAmount(amount: Long): Boolean = amount > 0

    /** The goal is optional; when set it must be more than 0. */
    fun isValidOptionalAmount(amount: Long?): Boolean = amount == null || amount > 0
}

object SavingsCalculator {
    /** Sum of the balances of non-archived pots. Archived pots are not counted. */
    fun total(pots: List<Pair<Pot, Long>>): Long =
        pots.filterNot { it.first.archived }.fold(0L) { sum, (_, balance) -> Math.addExact(sum, balance) }
}
