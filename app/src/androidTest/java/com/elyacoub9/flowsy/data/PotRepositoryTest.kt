package com.elyacoub9.flowsy.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.domain.pot.MovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PotRepositoryTest {

    private lateinit var db: FlowsyDatabase
    private val today = FakeToday(date(2026, 10, 2))
    private lateinit var pots: PotRepository

    private fun input(name: String = "Travel", opening: Long = 0) =
        PotInput(name, "blue", "plane", goalAmount = null, goalDate = null, openingBalance = opening)

    @Before
    fun setUp() = runBlocking {
        db = testDatabase()
        pots = PotRepository(db, today) { 0L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun pot(id: Long) = pots.observePot(id).first()!!

    private suspend inline fun <reified T : Throwable> assertThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }

    @Test
    fun specTravel_withdraw250Refused() = runBlocking<Unit> {
        val travel = pots.add(input())
        assertEquals("plane", pot(travel).pot.iconKey)
        assertEquals(PotChange.Done, pots.addMovement(travel, MovementType.DEPOSIT, 50_000, today(), null))
        assertEquals(PotChange.Done, pots.addMovement(travel, MovementType.WITHDRAWAL, 30_000, today(), null))
        assertEquals(20_000L, pot(travel).balance)
        assertEquals(PotChange.NotEnough(20_000), pots.addMovement(travel, MovementType.WITHDRAWAL, 25_000, today(), null))
        assertEquals(20_000L, pot(travel).balance)
    }

    @Test
    fun editAndDelete_checkedAgainstBalance() = runBlocking<Unit> {
        val id = pots.add(input())
        pots.addMovement(id, MovementType.DEPOSIT, 50_000, today(), null)
        pots.addMovement(id, MovementType.WITHDRAWAL, 30_000, today(), null)
        val deposit = pot(id).movements.first { it.type == MovementType.DEPOSIT }

        assertEquals(PotChange.NotEnough(20_000), pots.updateMovement(deposit.id, 29_999, today(), null))
        assertEquals(PotChange.Done, pots.updateMovement(deposit.id, 30_000, today(), "trip"))
        assertEquals(DeleteResult.Refused(PotChange.NotEnough(0)), pots.deleteMovement(deposit.id))
        assertEquals(0L, pot(id).balance)
    }

    @Test
    fun openingBalance_cannotGoBelowWithdrawn() = runBlocking<Unit> {
        val id = pots.add(input(opening = 10_000))
        pots.addMovement(id, MovementType.WITHDRAWAL, 8_000, today(), null)
        assertEquals(PotChange.OpeningTooLow(8_000), pots.update(id, input(opening = 7_999)))
        assertEquals(PotChange.Done, pots.update(id, input(opening = 8_000)))
        assertEquals(0L, pot(id).balance)
    }

    @Test
    fun deleteAndUndo() = runBlocking<Unit> {
        val id = pots.add(input())
        pots.addMovement(id, MovementType.DEPOSIT, 5_000, today(), "gift")
        val movement = pot(id).movements.single()
        val deleted = pots.deleteMovement(movement.id) as DeleteResult.Deleted
        assertEquals(0L, pot(id).balance)
        assertEquals(PotChange.Done, pots.restoreMovement(deleted.movement))
        assertEquals(listOf(movement), pot(id).movements)
    }

    @Test
    fun undoWithdrawal_rechecked() = runBlocking<Unit> {
        val id = pots.add(input(opening = 1_000))
        pots.addMovement(id, MovementType.WITHDRAWAL, 1_000, today(), null)
        val withdrawal = (pots.deleteMovement(pot(id).movements.single().id) as DeleteResult.Deleted).movement
        pots.addMovement(id, MovementType.WITHDRAWAL, 600, today(), null)
        assertEquals(PotChange.NotEnough(400), pots.restoreMovement(withdrawal))
    }

    @Test
    fun dates_futureRefused_pastAllowed() = runBlocking<Unit> {
        val id = pots.add(input())
        assertThrows<IllegalArgumentException> { pots.addMovement(id, MovementType.DEPOSIT, 1, today().plusDays(1), null) }
        assertEquals(PotChange.Done, pots.addMovement(id, MovementType.DEPOSIT, 1, date(2019, 3, 4), null))
        assertThrows<IllegalArgumentException> { pots.addMovement(id, MovementType.DEPOSIT, 0, today(), null) }
    }

    @Test
    fun deletePot_onlyEmptyWithoutMovements() = runBlocking<Unit> {
        val empty = pots.add(input(name = "Empty"))
        assertTrue(pots.delete(empty))

        val zeroWithHistory = pots.add(input(name = "Zero"))
        pots.addMovement(zeroWithHistory, MovementType.DEPOSIT, 500, today(), null)
        pots.addMovement(zeroWithHistory, MovementType.WITHDRAWAL, 500, today(), null)
        assertEquals(0L, pot(zeroWithHistory).balance)
        assertFalse(pots.delete(zeroWithHistory))

        assertFalse(pots.delete(pots.add(input(name = "Opening", opening = 1))))
    }

    @Test
    fun foreignKey_restrictsDeletingPotWithMovements() = runBlocking<Unit> {
        val id = pots.add(input())
        pots.addMovement(id, MovementType.DEPOSIT, 1, today(), null)
        try {
            db.potDao().delete(id)
            fail("Expected a foreign key error")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun archivedPot_readOnly_untilRestored() = runBlocking<Unit> {
        val id = pots.add(input(opening = 1_000))
        pots.addMovement(id, MovementType.DEPOSIT, 500, today(), null)
        val movement = pot(id).movements.single()
        pots.setArchived(id, true)

        assertEquals(PotChange.ReadOnly, pots.addMovement(id, MovementType.DEPOSIT, 1, today(), null))
        assertEquals(PotChange.ReadOnly, pots.updateMovement(movement.id, 1, today(), null))
        assertEquals(DeleteResult.Refused(PotChange.ReadOnly), pots.deleteMovement(movement.id))
        assertEquals(PotChange.ReadOnly, pots.update(id, input(opening = 2_000)))
        assertEquals(1_500L, pot(id).balance)

        pots.setArchived(id, false)
        assertEquals(PotChange.Done, pots.addMovement(id, MovementType.WITHDRAWAL, 1_500, today(), null))
        assertEquals(0L, pot(id).balance)
    }

    @Test
    fun history_newestDateFirst_sameDayNewestCreatedFirst() = runBlocking<Unit> {
        var clock = 0L
        val ordered = PotRepository(db, today) { ++clock }
        val id = ordered.add(input())
        ordered.addMovement(id, MovementType.DEPOSIT, 1, date(2026, 9, 1), "old")
        ordered.addMovement(id, MovementType.DEPOSIT, 2, today(), "first today")
        ordered.addMovement(id, MovementType.DEPOSIT, 3, today(), "second today")
        assertEquals(listOf("second today", "first today", "old"), pot(id).movements.map { it.note })
    }

    @Test
    fun move_reordersActivePots_skippingArchived() = runBlocking<Unit> {
        val a = pots.add(input(name = "A"))
        val b = pots.add(input(name = "B"))
        val c = pots.add(input(name = "C"))
        pots.setArchived(b, true)
        pots.move(c, up = true) // swaps with A, the active neighbour
        val names = { runBlocking { pots.observeAll().first().filterNot { it.pot.archived }.map { it.pot.name } } }
        assertEquals(listOf("C", "A"), names())
        pots.move(c, up = true) // already first: no change
        assertEquals(listOf("C", "A"), names())
        pots.move(a, up = false)
        assertEquals(listOf("C", "A"), names())
        assertEquals(listOf(c, b, a), pots.observeAll().first().map { it.pot.id })
    }
}
