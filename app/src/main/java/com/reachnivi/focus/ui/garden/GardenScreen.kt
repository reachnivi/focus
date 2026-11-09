package com.reachnivi.focus.ui.garden

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.GardenSource
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.SectionHeader
import com.reachnivi.focus.ui.components.rememberResumeTick

private fun howToGrow(source: GardenSource) = when (source) {
    GardenSource.CONNECTIONS -> "Call someone from your circle for 3+ minutes: 20 XP, 30 instead of scrolling"
    GardenSource.LET_GO -> "Tap \"Not now\" on a mindful pause: 5 XP"
    GardenSource.FOCUS -> "Finish a focus session: 10 XP plus 1 per 5 minutes"
    GardenSource.UNDER_GOAL -> "End a day under your screen-time goal: 15 XP"
    GardenSource.STEPS -> "Walk: 5 XP per 1,000 steps, up to 50 a day"
}

@Composable
fun GardenScreen(onBack: () -> Unit) {
    val repo = LocalAppContainer.current.garden
    val state by repo.state.collectAsStateWithLifecycle(initialValue = null)
    val tick = rememberResumeTick()
    LaunchedEffect(tick) { repo.refreshToday() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Your garden", style = MaterialTheme.typography.headlineLarge)
            Text(
                "It grows from what you do off the screen. Reflexive scrolling makes it droop for the day, but it never loses what it has grown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val s = state
        if (s != null) {
            item {
                GardenCanvas(
                    level = s.level.level,
                    health = s.health,
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.large),
                )
            }
            item {
                FocusCard {
                    Text("Level ${s.level.level} · ${s.level.stage.label}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { s.level.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${s.level.xpForNext - s.level.xpIntoLevel} XP to level ${s.level.level + 1} · ${s.xp} XP in total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    s.healthReason?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Looking a little thirsty: $it. It recovers tomorrow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            item {
                FocusCard {
                    SectionHeader("Today's growth", subtitle = "+${s.today.values.sum()} XP so far")
                    Spacer(Modifier.height(6.dp))
                    GardenSource.entries.forEach { src ->
                        Row(Modifier.fillMaxWidth().padding6()) {
                            Column(Modifier.weight(1f)) {
                                Text(src.label, style = MaterialTheme.typography.bodyLarge)
                                Text(howToGrow(src), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "+${s.today[src] ?: 0}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if ((s.today[src] ?: 0) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.padding6(): Modifier = this.then(Modifier.padding(vertical = 6.dp))
