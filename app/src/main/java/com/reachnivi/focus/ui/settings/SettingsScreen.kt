package com.reachnivi.focus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.BuildConfig
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.data.AppSettings
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.rememberResumeTick
import com.reachnivi.focus.permissions.Permissions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onPermissions: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

    var goal by remember { mutableFloatStateOf(settings.dailyGoalMinutes.toFloat()) }
    LaunchedEffect(settings.dailyGoalMinutes) { goal = settings.dailyGoalMinutes.toFloat() }
    val goalRounded = (goal / 15f).roundToInt() * 15

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge) }
        item {
            FocusCard {
                Text("Daily screen-time goal", style = MaterialTheme.typography.titleSmall)
                Text(Format.duration(goalRounded * 60_000L), style = MaterialTheme.typography.displaySmall)
                Slider(
                    value = goal,
                    onValueChange = { goal = it },
                    onValueChangeFinished = { scope.launch { container.settings.setDailyGoal(goalRounded) } },
                    valueRange = 30f..600f,
                    steps = 37,
                )
                Text(
                    "The average person spends over 4 hours a day on their phone. Pick a goal that feels kind but honest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            FocusCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow 5-minute passes", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "After a 10-second pause, let me open a blocked app briefly. Never during strict focus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.allowPass,
                        onCheckedChange = { on -> scope.launch { container.settings.setAllowPass(on) } },
                    )
                }
            }
        }
        item { HomeScreenCard() }
        item {
            FocusCard(onClick = onPermissions, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Shield)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Permissions", style = MaterialTheme.typography.titleSmall)
                        Text("Check that blocking and tracking are set up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }
        }
        item {
            FocusCard(color = MaterialTheme.colorScheme.primaryContainer, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Spa, background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Private by design", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Your usage data never leaves this phone. No accounts, no ads, no tracking.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Focus ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeScreenCard() {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val isHome = remember(tick) { Permissions.isDefaultHome(context) }
    FocusCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Home)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Minimal home screen", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (isHome) {
                        "Focus is your home screen. Swipe up there for all apps."
                    } else {
                        "Replace the busy app grid with the time, your day and a few apps as plain words."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = { Permissions.openHomeSettings(context) }) {
            Text(if (isHome) "Switch back" else "Set as home screen")
        }
        if (!isHome) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick \"Focus Home\" in the list that opens. You can switch back anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
