package com.elyacoub9.flowsy.domain.pot

import java.math.BigInteger
import java.time.LocalDate

data class GoalProgress(
    val balance: Long,
    val goal: Long,
    /** balance / goal, rounded down. Can exceed 100. */
    val percent: Long,
    /** For the progress bar: 0..100. */
    val barPercent: Int,
    val reached: Boolean,
    /** Goal date before today and goal not reached. */
    val datePassed: Boolean,
) {
    companion object {
        /** Null when the pot has no goal amount. */
        fun of(balance: Long, goalAmount: Long?, goalDate: LocalDate?, today: LocalDate): GoalProgress? {
            if (goalAmount == null || goalAmount <= 0) return null
            val percent = (BigInteger.valueOf(maxOf(balance, 0)) * BigInteger.valueOf(100) / BigInteger.valueOf(goalAmount)).toLong()
            val reached = balance >= goalAmount
            return GoalProgress(
                balance = balance,
                goal = goalAmount,
                percent = percent,
                barPercent = percent.coerceIn(0, 100).toInt(),
                reached = reached,
                datePassed = !reached && goalDate != null && goalDate.isBefore(today),
            )
        }
    }
}


/** Goal dates are targets: past or future, so they do not use RecordDateRule. Range offered by the picker. */
object GoalDateRule {
    val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)
    const val YEARS_AHEAD = 50L

    fun latest(today: LocalDate): LocalDate = today.plusYears(YEARS_AHEAD)
}
