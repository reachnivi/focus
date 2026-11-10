package com.reachnivi.focus.core

import kotlin.random.Random

enum class GardenSource(val label: String) {
    CONNECTIONS("Calls with your circle"),
    LET_GO("Let-gos on mindful pauses"),
    FOCUS("Focus sessions"),
    UNDER_GOAL("Days under your goal"),
    STEPS("Steps walked"),
    QUESTS("Photo quests"),
}

enum class GardenStage(val label: String, val fromLevel: Int) {
    SEED("Seed", 1),
    SPROUT("Sprout", 2),
    SAPLING("Sapling", 4),
    YOUNG_TREE("Young tree", 7),
    IN_BLOOM("In bloom", 11),
    GROVE("Grove", 16),
}

enum class PlantKind { GRASS, FLOWER, SHRUB, TREE }

/** A plant's place in the garden: [x] across and [height] as 0..1 fractions of the scene. */
data class Plant(val x: Float, val height: Float, val kind: PlantKind, val tint: Int)

data class GardenLevel(val level: Int, val xpIntoLevel: Int, val xpForNext: Int, val stage: GardenStage) {
    val progress: Float get() = if (xpForNext == 0) 0f else xpIntoLevel.toFloat() / xpForNext
}

data class SessionInput(val completed: Boolean, val focusedMinutes: Int)

/** Everything that earns XP, as counts already recorded elsewhere (no separate XP ledger). */
data class GardenInputs(
    val connectionXp: Int = 0,
    val letGos: Int = 0,
    val sessions: List<SessionInput> = emptyList(),
    val underGoalDays: Int = 0,
    val stepsByDay: List<Int> = emptyList(),
    val photoQuests: Int = 0,
)

object Garden {
    const val LET_GO_XP = 5
    const val UNDER_GOAL_XP = 15
    const val XP_PER_LEVEL_STEP = 40
    private const val MAX_PLANTS = 40

    fun sessionXp(s: SessionInput): Int = if (!s.completed) 0 else minOf(10 + s.focusedMinutes / 5, 30)

    fun stepsXp(steps: Int): Int = minOf(steps / 1000 * 5, 50)

    fun breakdown(inputs: GardenInputs): Map<GardenSource, Int> = mapOf(
        GardenSource.CONNECTIONS to inputs.connectionXp,
        GardenSource.LET_GO to inputs.letGos * LET_GO_XP,
        GardenSource.FOCUS to inputs.sessions.sumOf(::sessionXp),
        GardenSource.UNDER_GOAL to inputs.underGoalDays * UNDER_GOAL_XP,
        GardenSource.STEPS to inputs.stepsByDay.sumOf(::stepsXp),
        GardenSource.QUESTS to inputs.photoQuests * PhotoQuests.REWARD_XP,
    )

    fun xp(inputs: GardenInputs): Int = breakdown(inputs).values.sum()

    /** Level n needs 40·n more XP than level n-1, so the first levels arrive quickly. */
    fun level(xp: Int): GardenLevel {
        var level = 1
        var remaining = xp.coerceAtLeast(0)
        var need = XP_PER_LEVEL_STEP
        while (remaining >= need) {
            remaining -= need
            level++
            need = XP_PER_LEVEL_STEP * level
        }
        return GardenLevel(level, remaining, need, stageFor(level))
    }

    fun stageFor(level: Int): GardenStage = GardenStage.entries.last { level >= it.fromLevel }

    /**
     * How the garden looks today, 0.25..1. Time over the goal and reflexive opens (pauses that
     * ended in "open" beyond the ones let go) make it droop. It recovers tomorrow; XP never drops.
     */
    fun health(todayMs: Long, goalMs: Long, reflexiveOpens: Int, letGos: Int): Float {
        var h = 1f
        if (goalMs > 0 && todayMs > goalMs) {
            h -= ((todayMs - goalMs).toFloat() / goalMs).coerceAtMost(1f) * 0.5f
        }
        h -= 0.08f * (reflexiveOpens - letGos).coerceAtLeast(0)
        return h.coerceIn(0.25f, 1f)
    }

    /** Same level, same garden: positions come from a fixed seed, and new plants are only added. */
    fun plants(level: Int): List<Plant> {
        val count = minOf(3 + level * 2, MAX_PLANTS)
        return (0 until count).map { i ->
            val r = Random(1_000 + i)
            val kind = when {
                i % 9 == 4 && level >= GardenStage.YOUNG_TREE.fromLevel -> PlantKind.TREE
                i % 5 == 2 && level >= GardenStage.SAPLING.fromLevel -> PlantKind.SHRUB
                i % 3 == 1 && level >= GardenStage.SPROUT.fromLevel -> PlantKind.FLOWER
                else -> PlantKind.GRASS
            }
            val base = when (kind) {
                PlantKind.GRASS -> 0.12f
                PlantKind.FLOWER -> 0.22f
                PlantKind.SHRUB -> 0.26f
                PlantKind.TREE -> 0.55f
            }
            Plant(
                x = 0.06f + r.nextFloat() * 0.88f,
                height = base * (0.75f + r.nextFloat() * 0.5f),
                kind = kind,
                tint = r.nextInt(4),
            )
        }.sortedBy { it.kind.ordinal * -1 } // trees drawn first, grass in front
    }
}
