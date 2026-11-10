package com.reachnivi.focus.data

import android.graphics.Bitmap
import com.reachnivi.focus.core.PhotoQuest
import com.reachnivi.focus.core.PhotoQuests
import com.reachnivi.focus.core.QuestResult
import com.reachnivi.focus.quests.PhotoQuestVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class TodayQuest(val quest: PhotoQuest, val done: Boolean)

class PhotoQuestRepository(
    private val dao: FocusDao,
    private val wallet: StepStore,
    private val verifier: PhotoQuestVerifier = PhotoQuestVerifier(),
) {
    /** Today's three quests and which are already done. */
    val today: Flow<List<TodayQuest>> = dao.observePhotoQuests().map { rows ->
        val date = LocalDate.now()
        val done = rows.filter { it.date == date.toString() }.map { it.questId }.toSet()
        PhotoQuests.dailyQuests(date).map { TodayQuest(it, it.id in done) }
    }

    /**
     * Checks the photo on the phone and, if it matches, records the quest and pays out the reward.
     * The bitmap is not kept anywhere.
     */
    suspend fun attempt(quest: PhotoQuest, photo: Bitmap): QuestResult {
        val result = PhotoQuests.verify(quest, verifier.labels(photo))
        if (result is QuestResult.Verified) {
            val date = LocalDate.now().toString()
            dao.insertPhotoQuest(
                PhotoQuestEntity(
                    questId = quest.id,
                    date = date,
                    completedAt = System.currentTimeMillis(),
                    label = result.label,
                    confidence = result.confidence,
                ),
            )
            wallet.addBonus(PhotoQuests.REWARD_MINUTES)
        }
        return result
    }
}
