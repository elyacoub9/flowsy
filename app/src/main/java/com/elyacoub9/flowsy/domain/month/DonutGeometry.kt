package com.elyacoub9.flowsy.domain.month

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.atan2
import kotlin.math.hypot

/** Slice angles and tap hit testing for the spending donut. Geometry only: the real percentages are domain values shown as text. */
object DonutGeometry {
    const val FULL_CIRCLE = 360f
    const val MIN_SWEEP = 6f

    /**
     * Sweep in degrees for each amount, in the same order, adding up to 360. Every slice gets at least
     * [minSweep] so tiny categories stay visible; the others share the rest in proportion to their amounts.
     */
    fun sweeps(amounts: List<Long>, minSweep: Float = MIN_SWEEP): List<Float> {
        require(amounts.all { it > 0 }) { "Amounts must be more than 0" }
        if (amounts.isEmpty()) return emptyList()
        if (amounts.size * minSweep >= FULL_CIRCLE) return List(amounts.size) { FULL_CIRCLE / amounts.size }

        // Slices pinned to the minimum; repeat because shrinking the others can push more below it.
        val pinned = BooleanArray(amounts.size)
        while (true) {
            val free = amounts.indices.filterNot { pinned[it] }
            val freeTotal = free.fold(BigDecimal.ZERO) { sum, i -> sum + BigDecimal.valueOf(amounts[i]) }
            val freeDegrees = FULL_CIRCLE - minSweep * (amounts.size - free.size)
            val newlyPinned = free.filter { share(amounts[it], freeTotal, freeDegrees) < minSweep }
            if (newlyPinned.isEmpty()) {
                return amounts.indices.map { if (pinned[it]) minSweep else share(amounts[it], freeTotal, freeDegrees) }
            }
            newlyPinned.forEach { pinned[it] = true }
        }
    }

    /** Where a tap landed on the donut. */
    sealed interface Hit {
        data class Slice(val index: Int) : Hit
        data object Center : Hit
        data object Outside : Hit
    }

    /**
     * Which slice (or the center) is at ([x], [y]) for a ring around ([centerX], [centerY]) between
     * [innerRadius] and [outerRadius] (plus [outerMargin] for the thicker selected slice). Screen
     * coordinates: y grows downwards. Angles go clockwise from [startAngle] (-90 = 12 o'clock), using
     * the drawn [sweeps], so tiny slices are tapped with their minimum visible sweep.
     */
    fun hitTest(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        innerRadius: Float,
        outerRadius: Float,
        sweeps: List<Float>,
        outerMargin: Float = 0f,
        startAngle: Float = -90f,
    ): Hit {
        val dx = (x - centerX).toDouble()
        val dy = (y - centerY).toDouble()
        val distance = hypot(dx, dy)
        if (distance < innerRadius) return Hit.Center
        if (distance > outerRadius + outerMargin || sweeps.isEmpty()) return Hit.Outside
        // Screen angle (0 = 3 o'clock, clockwise since y points down), then relative to the start.
        val angle = Math.toDegrees(atan2(dy, dx))
        var fromStart = (angle - startAngle) % FULL_CIRCLE
        if (fromStart < 0) fromStart += FULL_CIRCLE
        var end = 0.0
        sweeps.forEachIndexed { index, sweep ->
            end += sweep
            if (fromStart < end) return Hit.Slice(index)
        }
        return Hit.Slice(sweeps.lastIndex) // rounding at exactly 360
    }

    private fun share(amount: Long, total: BigDecimal, degrees: Float): Float =
        (BigDecimal.valueOf(amount).multiply(BigDecimal(degrees.toString())).divide(total, MathContext.DECIMAL64)).toFloat()
}
