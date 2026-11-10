package com.reachnivi.focus.ui.quests

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.PhotoQuest
import com.reachnivi.focus.core.PhotoQuests
import com.reachnivi.focus.core.QuestResult
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.LocalAppContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface Attempt {
    data class Checking(val quest: PhotoQuest) : Attempt
    data class Done(val quest: PhotoQuest, val result: QuestResult) : Attempt
    data class Failed(val quest: PhotoQuest) : Attempt
}

/** Today's photo quests: small real-world tasks the phone checks on-device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoQuestCard() {
    val repo = LocalAppContainer.current.photoQuests
    val quests by repo.today.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<PhotoQuest?>(null) }
    var attempt by remember { mutableStateOf<Attempt?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        val quest = active ?: return@rememberLauncherForActivityResult
        if (bmp == null) return@rememberLauncherForActivityResult
        attempt = Attempt.Checking(quest)
        scope.launch {
            attempt = runCatching { Attempt.Done(quest, repo.attempt(quest, bmp)) }.getOrElse { Attempt.Failed(quest) }
        }
    }
    fun start(q: PhotoQuest) {
        active = q
        runCatching { camera.launch(null) }.onFailure { attempt = Attempt.Failed(q) }
    }

    if (quests.isEmpty()) return
    val doneCount = quests.count { it.done }

    FocusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.CameraAlt)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Photo quests · $doneCount/${quests.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Earn ${PhotoQuests.REWARD_MINUTES} minutes each, in real life", style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quests.forEach { tq ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tq.quest.title, style = MaterialTheme.typography.bodyLarge)
                        Text(tq.quest.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (tq.done) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        FilledTonalButton(onClick = { start(tq.quest) }) { Text("Go") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Checked on your phone. Photos are never saved or uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    attempt?.let { a ->
        ModalBottomSheet(onDismissRequest = { attempt = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (a) {
                    is Attempt.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Checking your photo…", style = MaterialTheme.typography.titleMedium)
                    }
                    is Attempt.Failed -> {
                        Text("Couldn't check that photo", style = MaterialTheme.typography.titleLarge)
                        Text("Something went wrong with the camera or the check. Try once more.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { start(a.quest) }, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                    }
                    is Attempt.Done -> when (val r = a.result) {
                        is QuestResult.Verified -> {
                            Text("Quest complete", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Verified: ${r.label.lowercase()} (${(r.confidence * 100).roundToInt()}%) · +${PhotoQuests.REWARD_MINUTES} min · +${PhotoQuests.REWARD_XP} XP",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text("Your garden grew a little.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { attempt = null }, modifier = Modifier.fillMaxWidth()) { Text("Nice") }
                        }
                        is QuestResult.ScreenDetected -> {
                            Text("That looks like a screen", style = MaterialTheme.typography.titleLarge)
                            Text("Point the camera at the real thing, not a picture of it.", style = MaterialTheme.typography.bodyMedium)
                            RetryRow(onRetry = { start(a.quest) }, onClose = { attempt = null })
                        }
                        is QuestResult.NotFound -> {
                            Text("Not quite", style = MaterialTheme.typography.titleLarge)
                            Text(
                                buildString {
                                    append("I couldn't spot it for \"${a.quest.title.lowercase()}\".")
                                    if (r.seen.isNotEmpty()) append(" I saw: ${r.seen.joinToString(", ")}.")
                                    append(" Try closer, or somewhere brighter.")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            RetryRow(onRetry = { start(a.quest) }, onClose = { attempt = null })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryRow(onRetry: () -> Unit, onClose: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Later") }
        Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
    }
}
