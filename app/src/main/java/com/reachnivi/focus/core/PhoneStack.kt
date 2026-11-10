package com.reachnivi.focus.core

import java.net.URLDecoder
import java.net.URLEncoder

/** What a phone-stack QR code carries. Everyone who scans it joins the same stack. */
data class StackInvite(val id: String, val minutes: Int, val name: String)

object StackCode {
    private const val PREFIX = "focus-stack"
    private const val VERSION = "1"
    val LENGTHS = listOf(30, 45, 60, 90)

    fun encode(invite: StackInvite): String =
        "$PREFIX:$VERSION:${invite.id}:${invite.minutes}:${URLEncoder.encode(invite.name, "UTF-8")}"

    fun parse(text: String?): StackInvite? {
        val parts = text?.trim()?.split(":", limit = 5) ?: return null
        if (parts.size != 5 || parts[0] != PREFIX || parts[1] != VERSION) return null
        val id = parts[2].takeIf { it.isNotBlank() && it.length <= 32 && it.all { c -> c.isLetterOrDigit() } } ?: return null
        val minutes = parts[3].toIntOrNull()?.takeIf { it in 5..240 } ?: return null
        val name = runCatching { URLDecoder.decode(parts[4], "UTF-8") }.getOrNull()?.take(40) ?: return null
        return StackInvite(id, minutes, name)
    }
}

sealed interface StackPhase {
    /** Waiting for the phone to be placed face-down. */
    data class Waiting(val faceDownSince: Long? = null) : StackPhase
    data class Running(val startedAt: Long, val endsAt: Long, val faceUpSince: Long? = null) : StackPhase
    data class Completed(val at: Long) : StackPhase
    data class Broken(val at: Long, val lastedMs: Long) : StackPhase
}

/**
 * The phone-stack rules, as a pure state machine. Face-down for a moment starts it; unlocking, or
 * lying face-up with the screen on for a few seconds, breaks it; reaching the end completes it.
 */
object StackMonitor {
    const val START_HOLD_MS = 2_000L
    const val BREAK_HOLD_MS = 3_000L
    /** Settling-in time after the start, so putting the phone down doesn't count against you. */
    const val GRACE_MS = 10_000L

    fun onOrientation(phase: StackPhase, faceDown: Boolean, screenOn: Boolean, now: Long, minutes: Int): StackPhase =
        when (phase) {
            is StackPhase.Waiting -> {
                if (!faceDown) {
                    StackPhase.Waiting(null)
                } else {
                    val since = phase.faceDownSince ?: now
                    if (now - since >= START_HOLD_MS) StackPhase.Running(now, now + minutes * 60_000L) else StackPhase.Waiting(since)
                }
            }
            is StackPhase.Running -> when {
                now >= phase.endsAt -> StackPhase.Completed(phase.endsAt)
                faceDown || !screenOn -> phase.copy(faceUpSince = null)
                else -> {
                    val since = phase.faceUpSince ?: now
                    if (now - phase.startedAt >= GRACE_MS && now - since >= BREAK_HOLD_MS) {
                        StackPhase.Broken(now, now - phase.startedAt)
                    } else {
                        phase.copy(faceUpSince = since)
                    }
                }
            }
            else -> phase
        }

    fun onUnlock(phase: StackPhase, now: Long): StackPhase = when {
        phase !is StackPhase.Running -> phase
        now >= phase.endsAt -> StackPhase.Completed(phase.endsAt)
        now - phase.startedAt < GRACE_MS -> phase
        else -> StackPhase.Broken(now, now - phase.startedAt)
    }

    fun tick(phase: StackPhase, now: Long): StackPhase =
        if (phase is StackPhase.Running && now >= phase.endsAt) StackPhase.Completed(phase.endsAt) else phase
}
