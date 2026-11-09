package com.reachnivi.focus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.reachnivi.focus.core.CallOutcome
import com.reachnivi.focus.core.CirclePerson
import com.reachnivi.focus.core.Connections
import com.reachnivi.focus.core.DuePerson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ConnectionSummary(
    val ranked: List<DuePerson>,
    val next: DuePerson?,
    val weekStreak: Int,
    val totalXp: Int,
    val callsThisMonth: Int,
    val talkMinutesThisMonth: Int,
    val circleSize: Int,
)

class ConnectionsRepository(
    private val context: Context,
    private val dao: FocusDao,
    val store: ChallengeStore,
) {
    val circle: Flow<List<CirclePersonEntity>> = dao.observeCircle()
    val events: Flow<List<ConnectionEventEntity>> = dao.observeConnectionEvents()

    val summary: Flow<ConnectionSummary> = combine(circle, events, store.skippedToday) { people, evts, skipped ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val completed = evts.filter { it.outcome == CallOutcome.COMPLETED.name }
        val dayOf = { e: ConnectionEventEntity -> Instant.ofEpochMilli(e.startedAt).atZone(zone).toLocalDate() }
        val lastTalked = completed.groupBy { it.personId }.mapValues { (_, list) -> list.maxOf(dayOf) }
        val circlePeople = people.map { CirclePerson(it.id, it.name, it.everyDays) }
        val monthStart = today.withDayOfMonth(1)
        val thisMonth = completed.filter { !dayOf(it).isBefore(monthStart) }
        ConnectionSummary(
            ranked = Connections.ranked(circlePeople, lastTalked, today, skipped),
            next = Connections.nextDue(circlePeople, lastTalked, today, skipped),
            weekStreak = Connections.weekStreak(completed.map(dayOf), today),
            totalXp = evts.sumOf { it.xp },
            callsThisMonth = thisMonth.size,
            talkMinutesThisMonth = thisMonth.sumOf { it.talkedSec } / 60,
            circleSize = people.size,
        )
    }

    fun personById(id: Long): Flow<CirclePersonEntity?> = circle.map { list -> list.firstOrNull { it.id == id } }

    /** @return false when the circle is full or the number is already in it. */
    suspend fun addPerson(name: String, phone: String, everyDays: Int = 7): Boolean {
        val people = dao.getCircle()
        val normalized = phone.filter { it.isDigit() || it == '+' }
        if (people.size >= MAX_CIRCLE || people.any { it.phone.filter { c -> c.isDigit() || c == '+' } == normalized }) return false
        dao.insertPerson(CirclePersonEntity(name = name.ifBlank { phone }, phone = phone, everyDays = everyDays))
        return true
    }

    suspend fun removePerson(id: Long) = dao.deletePerson(id)

    suspend fun setEveryDays(id: Long, days: Int) = dao.setEveryDays(id, days)

    /** Starts the challenge and opens the dialer with the number filled in (no call permission needed). */
    fun startChallenge(personId: Long, name: String, phone: String, source: String): Boolean {
        store.start(personId, name, phone, source)
        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: Exception) {
            store.clear()
            false
        }
    }

    /** Saves how the challenge went and clears it. [selfReported] is used only when nothing was measured. */
    suspend fun finishChallenge(mood: Int?, note: String?, selfReportedTalked: Boolean? = null): ConnectionEventEntity? {
        val active = store.active.value ?: return null
        val measured = Connections.outcome(active.talkedSec)
        val (outcome, verified) = when {
            active.sampled -> measured to true
            selfReportedTalked == true -> CallOutcome.COMPLETED to false
            else -> CallOutcome.NO_CALL to false
        }
        val instead = active.source == ChallengeStore.SOURCE_PAUSE || active.source == ChallengeStore.SOURCE_BLOCK
        val event = ConnectionEventEntity(
            personId = active.personId,
            startedAt = active.startedAt,
            talkedSec = active.talkedSec,
            verified = verified,
            outcome = outcome.name,
            mood = mood,
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(140),
            source = active.source,
            xp = Connections.xpFor(outcome, insteadOfScrolling = instead),
        )
        dao.insertConnectionEvent(event)
        store.clear()
        return event
    }

    fun cancelChallenge() = store.clear()

    companion object {
        const val MAX_CIRCLE = 8
        val INTERVALS = listOf(3 to "Every 3 days", 7 to "Weekly", 14 to "Every 2 weeks", 30 to "Monthly")
    }
}
