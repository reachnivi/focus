package com.reachnivi.focus.move

import com.reachnivi.focus.core.Exercise
import com.reachnivi.focus.core.Reps
import com.reachnivi.focus.data.FocusDao
import com.reachnivi.focus.data.MoveSessionEntity
import com.reachnivi.focus.data.StepStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class MoveToday(val reps: Int, val minutesEarned: Int) {
    val minutesLeftToEarn: Int get() = (Reps.MAX_MINUTES_PER_DAY - minutesEarned).coerceAtLeast(0)
}

class MoveRepository(private val dao: FocusDao, private val wallet: StepStore) {

    val today: Flow<MoveToday> = dao.observeMoves().map { rows ->
        val t = rows.filter { it.date == LocalDate.now().toString() }
        MoveToday(t.sumOf { it.reps }, t.sumOf { it.minutesEarned })
    }

    /** Banks a finished workout: records it and pays earned minutes into the wallet. Returns minutes earned. */
    suspend fun record(exercise: Exercise, reps: Int): Int {
        if (reps <= 0) return 0
        val date = LocalDate.now().toString()
        val already = dao.movesOn(date).sumOf { it.minutesEarned }
        val minutes = Reps.minutesFor(reps, already)
        dao.insertMove(MoveSessionEntity(date = date, exercise = exercise.name, reps = reps, minutesEarned = minutes, completedAt = System.currentTimeMillis()))
        if (minutes > 0) wallet.addBonus(minutes)
        return minutes
    }
}
