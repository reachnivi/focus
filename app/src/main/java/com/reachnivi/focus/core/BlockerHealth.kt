package com.reachnivi.focus.core

/** Whether the blocker can actually block right now. */
enum class BlockerStatus {
    /** Enabled in system settings and the service is connected. */
    ACTIVE,

    /** Turned off in system settings (or never turned on). */
    OFF,

    /** Enabled in settings, but Android isn't running the service (e.g. killed by the battery manager). */
    NOT_RUNNING,
}

object BlockerHealth {

    fun evaluate(enabledInSettings: Boolean, serviceConnected: Boolean): BlockerStatus = when {
        !enabledInSettings -> BlockerStatus.OFF
        serviceConnected -> BlockerStatus.ACTIVE
        else -> BlockerStatus.NOT_RUNNING
    }

    /**
     * Nag only when it matters: something is set up to be blocked and the blocker can't do it.
     * [lastAlertAt] rate-limits background notifications so a dismissed alert doesn't return every 15 minutes.
     */
    fun shouldAlert(
        status: BlockerStatus,
        hasRules: Boolean,
        now: Long,
        lastAlertAt: Long,
        minIntervalMs: Long = ALERT_INTERVAL_MS,
    ): Boolean = status != BlockerStatus.ACTIVE && hasRules && now - lastAlertAt >= minIntervalMs

    const val ALERT_INTERVAL_MS = 3 * 60 * 60 * 1000L
}
