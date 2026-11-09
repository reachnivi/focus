package com.reachnivi.focus.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure decision logic: no Android dependencies, fully unit-tested. */
object BlockPolicyEngine {

    fun decideApp(
        policy: PolicySnapshot,
        packageName: String,
        now: Long,
        zone: ZoneId,
        usedTodayMs: Long? = null,
    ): BlockDecision {
        if (packageName in policy.blockedApps) {
            blockListDecision(policy, now, zone)?.let { return it }
        }
        val limit = policy.dailyLimitsMs[packageName]
        if (limit != null && usedTodayMs != null && usedTodayMs >= limit) {
            val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
            return BlockDecision.Block(
                reason = BlockReason.DAILY_LIMIT,
                untilMillis = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli(),
                strict = false,
            )
        }
        // Softest rule last: a pause, not a wall. Applies in every mode.
        if (packageName in policy.pausedApps) {
            return BlockDecision.Block(BlockReason.MINDFUL_PAUSE, untilMillis = null, strict = false)
        }
        return BlockDecision.Allow
    }

    fun decideDomain(policy: PolicySnapshot, host: String, now: Long, zone: ZoneId): BlockDecision {
        if (DomainMatcher.firstMatch(host, policy.blockedDomains) == null) return BlockDecision.Allow
        return blockListDecision(policy, now, zone) ?: BlockDecision.Allow
    }

    /** Whether the block list is being enforced right now, and why. Null when it isn't. */
    fun blockListDecision(policy: PolicySnapshot, now: Long, zone: ZoneId): BlockDecision.Block? {
        val session = policy.session
        if (session != null && session.isRunning(now)) {
            return BlockDecision.Block(BlockReason.FOCUS_SESSION, session.endsAt, session.strict)
        }
        return when (policy.mode) {
            BlockMode.ALWAYS -> BlockDecision.Block(BlockReason.ALWAYS_ON, null, strict = false)
            BlockMode.FOCUS_ONLY -> null
            BlockMode.SCHEDULED -> {
                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
                val active = policy.schedules.filter { it.isActiveAt(t) }
                if (active.isEmpty()) {
                    null
                } else {
                    val end = active.maxOf { it.currentWindowEnd(t) }
                    BlockDecision.Block(
                        reason = BlockReason.SCHEDULE,
                        untilMillis = end.atZone(zone).toInstant().toEpochMilli(),
                        strict = false,
                    )
                }
            }
        }
    }
}
