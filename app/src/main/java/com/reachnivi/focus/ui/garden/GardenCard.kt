package com.reachnivi.focus.ui.garden

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.rememberResumeTick

/** Small garden preview for the Today screen. */
@Composable
fun GardenCard(onOpen: () -> Unit) {
    val repo = LocalAppContainer.current.garden
    val state by repo.state.collectAsStateWithLifecycle(initialValue = null)
    val tick = rememberResumeTick()
    LaunchedEffect(tick) { repo.refreshToday() }
    val s = state ?: return
    FocusCard(onClick = onOpen, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GardenCanvas(
                level = s.level.level,
                health = s.health,
                modifier = Modifier.size(width = 112.dp, height = 76.dp).clip(MaterialTheme.shapes.medium),
                animate = false,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Your garden", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Level ${s.level.level} · ${s.level.stage.label}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "+${s.today.values.sum()} XP today · ${s.level.xpForNext - s.level.xpIntoLevel} to next level",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
