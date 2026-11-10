package com.reachnivi.focus.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.data.ChallengeStore
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.LocalAppContainer

/** Today's Connection challenge: who to call, and the streak it keeps alive. */
@Composable
fun ConnectionCard(onOpenCircle: () -> Unit) {
    val repo = LocalAppContainer.current.connections
    val summary by repo.summary.collectAsStateWithLifecycle(null)
    val circle by repo.circle.collectAsStateWithLifecycle(emptyList())
    val s = summary ?: return

    FocusCard(onClick = onOpenCircle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Favorite)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Connection challenge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val next = s.next
                when {
                    s.circleSize == 0 -> {
                        Text("Swap a scroll for a call", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Add the people you want to stay close to.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    next == null -> {
                        Text("Everyone's covered today", style = MaterialTheme.typography.titleSmall)
                        Text(streakText(s.weekStreak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    else -> {
                        Text("Call ${next.person.name}", style = MaterialTheme.typography.titleSmall)
                        Text(lastTalkedText(next), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.weekStreak > 0) {
                            Text(streakText(s.weekStreak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        val next = s.next
        val entity = next?.let { n -> circle.firstOrNull { it.id == n.person.id } }
        if (entity != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { repo.store.skipToday(entity.id) }) { Text("Skip today") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { repo.startChallenge(entity.id, entity.name, entity.phone, ChallengeStore.SOURCE_TODAY) }) {
                    Icon(Icons.Rounded.Call, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Call")
                }
            }
        }
    }
}

fun streakText(weeks: Int): String =
    if (weeks <= 0) "Start a weekly streak" else "$weeks-week connection streak"
