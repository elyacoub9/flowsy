package com.elyacoub9.flowsy.data

import androidx.room.withTransaction
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.PotEntity
import com.elyacoub9.flowsy.data.db.toDomain
import com.elyacoub9.flowsy.data.db.toEntity
import com.elyacoub9.flowsy.domain.date.RecordDateRule
import com.elyacoub9.flowsy.domain.pot.MovementType
import com.elyacoub9.flowsy.domain.pot.Pot
import com.elyacoub9.flowsy.domain.pot.PotMovement
import com.elyacoub9.flowsy.domain.pot.PotRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** A pot with its records; [balance] is computed by the domain, never stored. */
data class PotWithBalance(
    val pot: Pot,
    /** Newest date first; same day: newest created first. */
    val movements: List<PotMovement>,
    val balance: Long,
)

/** Values of the pot form, already validated by the UI. */
data class PotInput(
    val name: String,
    val colorKey: String,
    val iconKey: String,
    val goalAmount: Long?,
    val goalDate: LocalDate?,
    val openingBalance: Long,
)

/** Result of a change checked against the pot rules. */
sealed interface PotChange {
    data object Done : PotChange

    /** Refused: the pot's balance would go below 0. [balance] is the balance available now. */
    data class NotEnough(val balance: Long) : PotChange

    /** Refused: the opening balance must be at least [minimum] (more was withdrawn than deposited). */
    data class OpeningTooLow(val minimum: Long) : PotChange

    /** Refused: the pot is archived (read-only until restored). */
    data object ReadOnly : PotChange
}

sealed interface DeleteResult {
    data class Deleted(val movement: PotMovement) : DeleteResult
    data class Refused(val change: PotChange) : DeleteResult
}

/** Every rule is checked inside the transaction that writes, so the total can never go below 0. */
class PotRepository(
    private val db: FlowsyDatabase,
    private val today: () -> LocalDate,
    private val clockMillis: () -> Long,
) {
    private val dao = db.potDao()

    /** All pots (archived included) with balances, in sort order. */
    fun observeAll(): Flow<List<PotWithBalance>> =
        combine(dao.observeAll(), dao.observeAllMovements()) { pots, movements ->
            val byPot = movements.map { it.toDomain() }.groupBy { it.potId }
            pots.map { entity ->
                val pot = entity.toDomain()
                val list = byPot[pot.id].orEmpty()
                PotWithBalance(pot, list, PotRules.balance(pot.openingBalance, list))
            }
        }

    fun observePot(id: Long): Flow<PotWithBalance?> =
        combine(dao.observe(id), dao.observeMovements(id)) { entity, movements ->
            entity?.toDomain()?.let { pot ->
                val list = movements.map { it.toDomain() }
                PotWithBalance(pot, list, PotRules.balance(pot.openingBalance, list))
            }
        }

    // Pots

    suspend fun add(input: PotInput): Long = db.withTransaction {
        validate(input)
        dao.insert(
            PotEntity(
                name = input.name.trim(),
                color = input.colorKey,
                icon = input.iconKey,
                goalAmount = input.goalAmount,
                goalDate = input.goalDate?.toEpochDay(),
                openingBalance = input.openingBalance,
                archived = false,
                sortOrder = dao.nextSortOrder(),
            )
        )
    }

    /** Refused for an archived pot, or when the new opening balance would make the balance negative. */
    suspend fun update(id: Long, input: PotInput): PotChange = db.withTransaction {
        validate(input)
        val pot = dao.get(id)?.toDomain() ?: return@withTransaction PotChange.Done
        if (PotRules.isReadOnly(pot)) return@withTransaction PotChange.ReadOnly
        val movements = movementsOf(id)
        if (!PotRules.canSetOpeningBalance(input.openingBalance, movements)) {
            return@withTransaction PotChange.OpeningTooLow(PotRules.minOpeningBalance(movements))
        }
        dao.update(
            pot.copy(
                name = input.name.trim(),
                colorKey = input.colorKey,
                iconKey = input.iconKey,
                goalAmount = input.goalAmount,
                goalDate = input.goalDate,
                openingBalance = input.openingBalance,
            ).toEntity()
        )
        PotChange.Done
    }

    suspend fun setArchived(id: Long, archived: Boolean) = db.withTransaction {
        val pot = dao.get(id) ?: return@withTransaction
        dao.update(pot.copy(archived = archived))
    }

    /** Swaps an active pot with its active neighbour above or below and renumbers all sort orders. */
    suspend fun move(id: Long, up: Boolean) = db.withTransaction {
        val all = dao.allInOrder()
        val active = all.filterNot { it.archived }
        val index = active.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in active.indices) return@withTransaction
        val a = all.indexOf(active[index])
        val b = all.indexOf(active[target])
        val list = all.toMutableList()
        list[a] = list[b].also { list[b] = list[a] }
        dao.updateAll(list.mapIndexed { i, pot -> pot.copy(sortOrder = i) })
    }

    suspend fun canDelete(id: Long): Boolean {
        val pot = dao.get(id)?.toDomain() ?: return false
        return PotRules.canDeletePot(pot, movementsOf(id))
    }

    /** Only an empty pot without movements; otherwise false and nothing is deleted (archive instead). */
    suspend fun delete(id: Long): Boolean = db.withTransaction {
        if (!canDelete(id)) return@withTransaction false
        dao.delete(id)
        true
    }

    private fun validate(input: PotInput) {
        require(input.name.isNotBlank()) { "Name required" }
        require(input.openingBalance >= 0) { "Opening balance below 0" }
        require(PotRules.isValidOptionalAmount(input.goalAmount)) { "Goal must be more than 0" }
    }

    // Movements

    suspend fun addMovement(potId: Long, type: MovementType, amount: Long, date: LocalDate, note: String?): PotChange =
        db.withTransaction {
            validateMovement(amount, date)
            val pot = requireNotNull(dao.get(potId)) { "Unknown pot $potId" }.toDomain()
            insertChecked(pot, PotMovement(0, potId, type, amount, date, note, clockMillis()))
        }

    /** Amount, date and note can change; the type stays. */
    suspend fun updateMovement(id: Long, amount: Long, date: LocalDate, note: String?): PotChange =
        db.withTransaction {
            validateMovement(amount, date)
            val old = dao.movement(id)?.toDomain() ?: return@withTransaction PotChange.Done
            val pot = requireNotNull(dao.get(old.potId)).toDomain()
            if (PotRules.isReadOnly(pot)) return@withTransaction PotChange.ReadOnly
            val movements = movementsOf(old.potId)
            val new = old.copy(amount = amount, date = date, note = note)
            if (!PotRules.canEdit(pot, movements, old, new)) {
                return@withTransaction PotChange.NotEnough(PotRules.balance(pot.openingBalance, movements))
            }
            dao.updateMovement(new.toEntity())
            PotChange.Done
        }

    /** Deleting a deposit that funds later withdrawals is refused. */
    suspend fun deleteMovement(id: Long): DeleteResult = db.withTransaction {
        val movement = dao.movement(id)?.toDomain() ?: return@withTransaction DeleteResult.Refused(PotChange.Done)
        val pot = requireNotNull(dao.get(movement.potId)).toDomain()
        if (PotRules.isReadOnly(pot)) return@withTransaction DeleteResult.Refused(PotChange.ReadOnly)
        val movements = movementsOf(movement.potId)
        if (!PotRules.canDeleteMovement(pot, movements, movement)) {
            return@withTransaction DeleteResult.Refused(PotChange.NotEnough(PotRules.balance(pot.openingBalance, movements)))
        }
        dao.deleteMovement(id)
        DeleteResult.Deleted(movement)
    }

    /** Undo: puts a deleted movement back exactly as it was, if the pot rules still allow it. */
    suspend fun restoreMovement(movement: PotMovement): PotChange = db.withTransaction {
        val pot = dao.get(movement.potId)?.toDomain() ?: return@withTransaction PotChange.Done
        insertChecked(pot, movement)
    }

    private suspend fun insertChecked(pot: Pot, movement: PotMovement): PotChange {
        if (PotRules.isReadOnly(pot)) return PotChange.ReadOnly
        val movements = movementsOf(pot.id)
        if (!PotRules.canAdd(pot, movements, movement)) {
            return PotChange.NotEnough(PotRules.balance(pot.openingBalance, movements))
        }
        dao.insertMovement(movement.toEntity())
        return PotChange.Done
    }

    private suspend fun movementsOf(potId: Long) = dao.movements(potId).map { it.toDomain() }

    private fun validateMovement(amount: Long, date: LocalDate) {
        require(PotRules.isValidAmount(amount)) { "Amount must be more than 0" }
        require(RecordDateRule.isAllowed(date, today())) { "Date in the future" }
    }
}
