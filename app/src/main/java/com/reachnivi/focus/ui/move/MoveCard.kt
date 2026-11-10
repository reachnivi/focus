package com.reachnivi.focus.ui.move

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.Reps
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.LocalAppContainer

/** Today: Move to unlock. */
@Composable
fun MoveCard(onOpen: () -> Unit) {
    val today by LocalAppContainer.current.move.today.collectAsStateWithLifecycle(initialValue = null)
    val t = today ?: return
    FocusCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.FitnessCenter)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Move quest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (t.minutesLeftToEarn > 0) "Do ${Reps.REPS_PER_REWARD} squats or pushups: ${Reps.MINUTES_PER_REWARD} min" else "Today's move minutes are earned",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${t.reps} reps today · ${t.minutesEarned} of ${Reps.MAX_MINUTES_PER_DAY} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = onOpen) { Text("Go") }
        }
    }
}
