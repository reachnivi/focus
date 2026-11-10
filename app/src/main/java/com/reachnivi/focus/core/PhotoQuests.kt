package com.reachnivi.focus.core

import java.time.LocalDate
import kotlin.random.Random

data class PhotoQuest(
    val id: String,
    val title: String,
    val prompt: String,
    /** ML Kit base-model labels that count as success (case-insensitive). */
    val accepts: Set<String>,
)

data class Label(val text: String, val confidence: Float)

sealed interface QuestResult {
    data class Verified(val label: String, val confidence: Float) : QuestResult
    data class NotFound(val seen: List<String>) : QuestResult
    data object ScreenDetected : QuestResult
}

/** Real-world tasks the phone checks on-device. No photo is ever stored. */
object PhotoQuests {
    const val REWARD_MINUTES = 10
    const val REWARD_XP = 15
    const val MIN_CONFIDENCE = 0.6f

    /** A photo of a picture on another screen doesn't count. */
    private val screenLabels = setOf("television", "monitor", "mobile phone", "computer", "screen", "laptop", "display device")

    val GREEN = PhotoQuest("green", "Find something growing", "A plant, a tree, a flower, grass.", setOf("Plant", "Tree", "Flower", "Leaf", "Grass", "Garden", "Houseplant"))

    val catalogue: List<PhotoQuest> = listOf(
        GREEN,
        PhotoQuest("sky", "Look up for a moment", "Step to a window or outside and photograph the sky.", setOf("Sky", "Cloud", "Sunset")),
        PhotoQuest("desk", "Show your study setup", "Your desk, notes or books, ready to work.", setOf("Desk", "Paper", "Pen", "Book", "Notebook")),
        PhotoQuest("read", "Open a real book", "Any book, any page. Read one before you scroll.", setOf("Book", "Paper")),
        PhotoQuest("meal", "Share a meal or cook", "Food on a plate, or something you're cooking.", setOf("Food", "Cuisine", "Vegetable", "Fruit", "Bread", "Dish", "Meal")),
        PhotoQuest("animal", "Say hello to an animal", "A pet, a bird, a street dog you know.", setOf("Dog", "Cat", "Bird", "Pet", "Animal")),
        PhotoQuest("outside", "Step outside", "Anything outdoors: a road, a building, the sky.", setOf("Sky", "Road", "Building", "Grass", "Tree", "Sand", "Skyscraper")),
        PhotoQuest("water", "Drink a glass of water", "Pour one, photograph it, drink it.", setOf("Cup", "Drink", "Glass", "Bottle")),
        PhotoQuest("shoes", "Put your shoes on", "Shoes on, then go for a short walk.", setOf("Shoe", "Sneakers", "Footwear")),
        PhotoQuest("music", "Play or sing something", "An instrument, or the songbook you sing from.", setOf("Musical instrument", "Guitar", "Piano")),
    )

    fun byId(id: String): PhotoQuest? = catalogue.firstOrNull { it.id == id }

    /** Same three quests for everyone on a given day; "Find something growing" is always one of them. */
    fun dailyQuests(date: LocalDate, count: Int = 3): List<PhotoQuest> {
        val others = catalogue.drop(1).shuffled(Random(date.toEpochDay()))
        return listOf(GREEN) + others.take((count - 1).coerceAtLeast(0))
    }

    fun verify(quest: PhotoQuest, labels: List<Label>): QuestResult {
        val confident = labels.filter { it.confidence >= MIN_CONFIDENCE }
        if (confident.any { it.text.lowercase() in screenLabels }) return QuestResult.ScreenDetected
        val accepted = quest.accepts.map { it.lowercase() }.toSet()
        val match = confident.filter { it.text.lowercase() in accepted }.maxByOrNull { it.confidence }
        return if (match != null) {
            QuestResult.Verified(match.text, match.confidence)
        } else {
            QuestResult.NotFound(labels.sortedByDescending { it.confidence }.take(3).map { it.text })
        }
    }
}
