package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerHealthTest {
    private val hour = 3_600_000L

    @Test
    fun `status combines setting and connection`() {
        assertEquals(BlockerStatus.OFF, BlockerHealth.evaluate(enabledInSettings = false, serviceConnected = false))
        assertEquals(BlockerStatus.OFF, BlockerHealth.evaluate(enabledInSettings = false, serviceConnected = true))
        assertEquals(BlockerStatus.ACTIVE, BlockerHealth.evaluate(enabledInSettings = true, serviceConnected = true))
        assertEquals(BlockerStatus.NOT_RUNNING, BlockerHealth.evaluate(enabledInSettings = true, serviceConnected = false))
    }

    @Test
    fun `alerts only when rules exist and blocker is down`() {
        val now = 10 * hour
        assertTrue(BlockerHealth.shouldAlert(BlockerStatus.OFF, hasRules = true, now = now, lastAlertAt = 0))
        assertTrue(BlockerHealth.shouldAlert(BlockerStatus.NOT_RUNNING, hasRules = true, now = now, lastAlertAt = 0))
        assertFalse(BlockerHealth.shouldAlert(BlockerStatus.ACTIVE, hasRules = true, now = now, lastAlertAt = 0))
        assertFalse(BlockerHealth.shouldAlert(BlockerStatus.OFF, hasRules = false, now = now, lastAlertAt = 0))
    }

    @Test
    fun `alerts are rate limited`() {
        val now = 10 * hour
        assertFalse(BlockerHealth.shouldAlert(BlockerStatus.OFF, true, now, lastAlertAt = now - hour))
        assertTrue(BlockerHealth.shouldAlert(BlockerStatus.OFF, true, now, lastAlertAt = now - 3 * hour))
    }
}
