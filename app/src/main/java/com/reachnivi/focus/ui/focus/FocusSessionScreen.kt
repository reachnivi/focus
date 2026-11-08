package com.reachnivi.focus.ui.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reachnivi.focus.core.ActiveSession
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.ProgressRing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val presets = listOf(15, 25, 45, 60, 90)

@Composable
fun FocusSessionScreen(onEditBlockList: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FocusViewModel = viewModel { FocusViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Focus", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Pause your block list for a stretch of deep, unhurried attention.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AnimatedContent(state.session, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "session", contentKey = { it?.id }) { session ->
                if (session == null) Setup(state, vm, onEditBlockList) else Running(session, state.now, vm::stop)
            }
        }
        item { TodaySummary(state) }
    }
}

@Composable
private fun Setup(state: FocusUiState, vm: FocusViewModel, onEditBlockList: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FocusCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { vm.setMinutes(state.minutes - 5) }) { Icon(Icons.Rounded.Remove, "Less") }
                    Spacer(Modifier.width(16.dp))
                    ProgressRing(progress = state.minutes / 120f, modifier = Modifier.size(180.dp), strokeWidth = 10.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.minutes}", style = MaterialTheme.typography.displayLarge)
                            Text("minutes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    FilledTonalIconButton(onClick = { vm.setMinutes(state.minutes + 5) }) { Icon(Icons.Rounded.Add, "More") }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { m ->
                        FilterChip(selected = state.minutes == m, onClick = { vm.setMinutes(m) }, label = { Text("$m") })
                    }
                }
            }
        }
        FocusCard(contentPadding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "No ending early, no 5-minute passes, no loosening the block list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = state.strict, onCheckedChange = vm::setStrict)
            }
        }
        val empty = state.blockedApps == 0 && state.blockedSites == 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (empty) "Your block list is empty. Add apps first." else "Pauses ${plural(state.blockedApps, "app")} and ${plural(state.blockedSites, "website")}",
                style = MaterialTheme.typography.bodySmall,
                color = if (empty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditBlockList) { Text("Edit") }
        }
        Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !empty) {
            Text("Start ${state.minutes}-minute focus")
        }
    }
}

@Composable
private fun Running(session: ActiveSession, now: Long, onStop: () -> Unit) {
    val total = (session.endsAt - session.startedAt).coerceAtLeast(1)
    val remaining = (session.endsAt - now).coerceAtLeast(0)
    val ends = Instant.ofEpochMilli(session.endsAt).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FocusCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(
                    progress = remaining.toFloat() / total,
                    modifier = Modifier.size(240.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Format.clock(remaining), style = MaterialTheme.typography.displayMedium)
                        Text("until $ends", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Distractions are paused. Put the phone down and enjoy the quiet.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (session.strict) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Strict session: stay with it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("End session early") }
        }
    }
}

@Composable
private fun TodaySummary(state: FocusUiState) {
    val count = state.todaySessions.size
    if (count == 0) return
    val focused = state.todaySessions.sumOf { it.focusedMs(state.now).coerceAtLeast(0) }
    val completed = state.todaySessions.count { it.completed }
    Text(
        "Today: ${plural(count, "session")} · ${Format.duration(focused)} focused · $completed completed",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
