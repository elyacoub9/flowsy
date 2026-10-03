package com.elyacoub9.flowsy.domain.month

import com.elyacoub9.flowsy.domain.month.DonutGeometry.Hit
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Screen coordinates (y down); 12 o'clock start; angles clockwise. */
class DonutHitTestTest {

    private val c = 110f
    private val inner = 70f
    private val outer = 100f
    private val ring = 85f

    /** Point on the ring [angle] degrees clockwise from 12 o'clock, at [radius]. */
    private fun at(angle: Double, radius: Float = ring): Pair<Float, Float> {
        val rad = Math.toRadians(angle - 90)
        return (c + radius * cos(rad)).toFloat() to (c + radius * sin(rad)).toFloat()
    }

    private fun hit(angle: Double, sweeps: List<Float>, radius: Float = ring, margin: Float = 0f): Hit {
        val (x, y) = at(angle, radius)
        return DonutGeometry.hitTest(x, y, c, c, inner, outer, sweeps, outerMargin = margin)
    }

    // Spec October: Bills 4,842.50, Groceries 1,460.40, Restaurants 389.60, Transport 150.00.
    private val sweeps = DonutGeometry.sweeps(listOf(484_250, 146_040, 38_960, 15_000))

    @Test
    fun middleOfEachSlice_isThatSlice() {
        var start = 0.0
        sweeps.forEachIndexed { index, sweep ->
            assertEquals(Hit.Slice(index), hit(start + sweep / 2, sweeps))
            start += sweep
        }
    }

    @Test
    fun clockwiseFromTwelve_boundaries() {
        assertEquals(Hit.Slice(0), hit(0.5, sweeps)) // just after 12 o'clock
        assertEquals(Hit.Slice(0), hit(sweeps[0] - 0.5, sweeps))
        assertEquals(Hit.Slice(1), hit(sweeps[0] + 0.5, sweeps))
        assertEquals(Hit.Slice(3), hit(359.5, sweeps)) // just before 12 o'clock
    }

    @Test
    fun tinySlice_tappableWithItsMinimumSweep() {
        val tiny = DonutGeometry.sweeps(listOf(1_000_000, 900_000, 10)) // 0.10 next to big amounts
        assertEquals(DonutGeometry.MIN_SWEEP, tiny[2], 0.001f)
        val tinyStart = 360.0 - tiny[2]
        assertEquals(Hit.Slice(2), hit(tinyStart + 1, tiny))
        assertEquals(Hit.Slice(2), hit(359.0, tiny))
    }

    @Test
    fun centerOutsideAndOuterMargin() {
        assertEquals(Hit.Center, DonutGeometry.hitTest(c, c, c, c, inner, outer, sweeps))
        assertEquals(Hit.Center, hit(45.0, sweeps, radius = inner - 1))
        assertEquals(Hit.Outside, hit(45.0, sweeps, radius = outer + 1))
        assertEquals(Hit.Slice(0), hit(45.0, sweeps, radius = outer + 5, margin = 8f))
    }

    @Test
    fun singleSlice_fullCircle_noSlices_outside() {
        assertEquals(Hit.Slice(0), hit(200.0, listOf(360f)))
        assertEquals(Hit.Outside, hit(200.0, emptyList()))
    }
}
