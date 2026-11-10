package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenTest {

    @Test
    fun `xp adds every source with caps`() {
        val inputs = GardenInputs(
            connectionXp = 30,
            letGos = 3,
            sessions = listOf(SessionInput(true, 25), SessionInput(true, 200), SessionInput(false, 40)),
            underGoalDays = 2,
            stepsByDay = listOf(3_400, 25_000),
        )
        val b = Garden.breakdown(inputs)
        assertEquals(30, b[GardenSource.CONNECTIONS])
        assertEquals(15, b[GardenSource.LET_GO])
        assertEquals(15 + 30, b[GardenSource.FOCUS]) // 10+5, capped at 30, incomplete = 0
        assertEquals(30, b[GardenSource.UNDER_GOAL])
        assertEquals(15 + 50, b[GardenSource.STEPS]) // 3 thousand = 15, capped at 50
        assertEquals(185, Garden.xp(inputs))
    }

    @Test
    fun `level curve`() {
        assertEquals(GardenLevel(1, 0, 40, GardenStage.SEED), Garden.level(0))
        assertEquals(GardenLevel(1, 39, 40, GardenStage.SEED), Garden.level(39))
        assertEquals(GardenLevel(2, 0, 80, GardenStage.SPROUT), Garden.level(40))
        assertEquals(4, Garden.level(40 + 80 + 120).level) // 240 XP reaches level 4
        assertEquals(GardenStage.SAPLING, Garden.level(240).stage)
        assertEquals(GardenStage.GROVE, Garden.stageFor(20))
    }

    @Test
    fun `health droops with overuse and reflexive opens, within bounds`() {
        val h = 3_600_000L
        assertEquals(1f, Garden.health(2 * h, 3 * h, reflexiveOpens = 2, letGos = 2), 0.001f)
        assertEquals(0.75f, Garden.health(6 * h / 4 * 2, 2 * h, 0, 0), 0.001f) // 50% over goal
        assertEquals(0.76f, Garden.health(h, 3 * h, reflexiveOpens = 4, letGos = 1), 0.001f)
        assertEquals(0.25f, Garden.health(10 * h, 2 * h, 20, 0), 0.001f)
    }

    @Test
    fun `plants are deterministic and only grow`() {
        assertEquals(Garden.plants(5), Garden.plants(5))
        val small = Garden.plants(2).toSet()
        val big = Garden.plants(3)
        assertTrue(big.size > small.size)
        assertTrue(Garden.plants(1).none { it.kind == PlantKind.TREE })
        assertTrue(Garden.plants(10).any { it.kind == PlantKind.TREE })
        assertTrue(Garden.plants(10).all { it.x in 0f..1f && it.height > 0f })
    }
}
