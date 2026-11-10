package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhotoQuestsTest {
    private val day = LocalDate.of(2026, 11, 10)

    @Test
    fun `daily rotation is deterministic, distinct and always includes green`() {
        val a = PhotoQuests.dailyQuests(day)
        assertEquals(a, PhotoQuests.dailyQuests(day))
        assertEquals(3, a.size)
        assertEquals(3, a.map { it.id }.toSet().size)
        assertEquals("green", a.first().id)
        val week = (0L..6L).map { PhotoQuests.dailyQuests(day.plusDays(it)).map { q -> q.id } }.toSet()
        assertTrue("rotation should vary across a week", week.size > 1)
    }

    @Test
    fun `verifies accepted labels above the confidence threshold, case-insensitively`() {
        val sky = PhotoQuests.byId("sky")!!
        val ok = PhotoQuests.verify(sky, listOf(Label("Building", 0.8f), Label("sky", 0.91f)))
        assertEquals(QuestResult.Verified("sky", 0.91f), ok)
        val weak = PhotoQuests.verify(sky, listOf(Label("Sky", 0.4f), Label("Wall", 0.7f)))
        assertTrue(weak is QuestResult.NotFound)
        assertEquals(listOf("Wall", "Sky"), (weak as QuestResult.NotFound).seen)
    }

    @Test
    fun `a picture on a screen is rejected`() {
        val green = PhotoQuests.GREEN
        val r = PhotoQuests.verify(green, listOf(Label("Plant", 0.9f), Label("Monitor", 0.75f)))
        assertEquals(QuestResult.ScreenDetected, r)
        val lowScreen = PhotoQuests.verify(green, listOf(Label("Plant", 0.9f), Label("Monitor", 0.3f)))
        assertTrue(lowScreen is QuestResult.Verified)
    }
}
