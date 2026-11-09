package com.reachnivi.focus.ui.walk

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.Steps
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.Meter
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.rememberResumeTick
import java.text.NumberFormat

/** Walk quest: steps today earn minutes for paused or blocked apps. */
@Composable
fun WalkCard() {
    val steps = LocalAppContainer.current.steps
    val tick = rememberResumeTick()
    var permissionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick, permissionTick) { steps.refresh() }
    val status by steps.status.collectAsStateWithLifecycle(initialValue = null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val s = status ?: return
    if (!s.available) return

    FocusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.AutoMirrored.Rounded.DirectionsWalk)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Walk quest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!s.permitted) {
                    Text("Earn screen time by walking", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Every ${NumberFormat.getIntegerInstance().format(Steps.STEPS_PER_REWARD)} steps earns ${Steps.MINUTES_PER_REWARD} minutes for paused apps, and grows your garden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${NumberFormat.getIntegerInstance().format(s.stepsToday)} steps · ${s.minutesLeft} min to spend",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        s.stepsToNext?.let { "${NumberFormat.getIntegerInstance().format(it)} steps to the next ${Steps.MINUTES_PER_REWARD} minutes" }
                            ?: "Today's ${Steps.MAX_EARNED_MINUTES} minutes are all earned. Nice walking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!s.permitted) {
            FilledTonalButton(
                onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Turn on step counting") }
        } else {
            val within = s.stepsToday % Steps.STEPS_PER_REWARD
            Meter(if (s.stepsToNext == null) 1f else within / Steps.STEPS_PER_REWARD.toFloat())
        }
    }
}
