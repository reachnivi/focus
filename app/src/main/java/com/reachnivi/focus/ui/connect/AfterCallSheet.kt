package com.reachnivi.focus.ui.connect

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.CallOutcome
import com.reachnivi.focus.core.Connections
import com.reachnivi.focus.data.ActiveChallenge
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.rememberResumeTick
import kotlinx.coroutines.launch

private val moods = listOf(1 to "😔", 2 to "😐", 3 to "🙂", 4 to "😄", 5 to "🥰")

/**
 * Shows "How was it?" once the user is back in Focus after a challenge call.
 * Waits while a call is still in progress, and for a short moment after the dialer opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterCallHost() {
    val container = LocalAppContainer.current
    val repo = container.connections
    val active by repo.store.active.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    LaunchedEffect(tick) { repo.store.refresh() }

    // Swiping the sheet away hides it until the next time Focus resumes; the challenge stays pending.
    var dismissedTick by remember { mutableIntStateOf(-1) }

    val challenge = active ?: return
    val readyToAsk = !challenge.inCallNow && System.currentTimeMillis() - challenge.startedAt > 15_000
    if (!readyToAsk || dismissedTick == tick) return

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { dismissedTick = tick }, sheetState = sheet) {
        AfterCallContent(challenge)
    }
}

@Composable
private fun AfterCallContent(challenge: ActiveChallenge) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.connections
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val outcome = Connections.outcome(challenge.talkedSec)
    val minutes = (challenge.talkedSec + 59) / 60

    fun save(selfReported: Boolean? = null) {
        scope.launch {
            val event = repo.finishChallenge(mood, note, selfReported)
            container.connectionNotifier.cancel()
            if (event != null && event.xp > 0) {
                Toast.makeText(context, "+${event.xp} XP · connection logged", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("How was your call with ${challenge.personName}?", style = MaterialTheme.typography.titleLarge)
        val status = when {
            !challenge.sampled -> "Focus couldn't check this call because the blocker was off. Did you talk?"
            outcome == CallOutcome.COMPLETED -> "Verified · $minutes min. That counts as a real connection."
            outcome == CallOutcome.SHORT -> "Short call · $minutes min. Calls of 3 minutes or more count toward your streak."
            else -> "No call was detected. Maybe they didn't pick up?"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val talked = !challenge.sampled || outcome != CallOutcome.NO_CALL
        if (talked) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                moods.forEach { (value, emoji) ->
                    FilterChip(
                        selected = mood == value,
                        onClick = { mood = value },
                        label = { Text(emoji, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) },
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(140) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("A line to remember (optional)") },
                singleLine = true,
            )
        }

        when {
            !challenge.sampled -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { save(selfReported = false) }, modifier = Modifier.weight(1f)) { Text("We didn't talk") }
                Button(onClick = { save(selfReported = true) }, modifier = Modifier.weight(1f)) { Text("We talked") }
            }
            outcome == CallOutcome.NO_CALL -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("Not today") }
                Button(
                    onClick = { repo.startChallenge(challenge.personId, challenge.personName, challenge.phone, challenge.source) },
                    modifier = Modifier.weight(1f),
                ) { Text("Try again") }
            }
            else -> Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
        TextButton(
            onClick = {
                repo.cancelChallenge()
                container.connectionNotifier.cancel()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Discard", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
    }
}
