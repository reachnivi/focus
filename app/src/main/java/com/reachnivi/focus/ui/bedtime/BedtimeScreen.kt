package com.reachnivi.focus.ui.bedtime

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.data.AppSettings
import com.reachnivi.focus.permissions.Permissions
import com.reachnivi.focus.ui.block.TimePickerDialog
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.rememberResumeTick
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val windDownChoices = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour")

@Composable
fun BedtimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val s by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val tick = rememberResumeTick()
    val dndAccess = remember(tick) { Permissions.hasDndAccess(context) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val use24 = android.text.format.DateFormat.is24HourFormat(context)

    fun save(
        enabled: Boolean = s.bedtimeEnabled,
        start: Int = s.bedtimeStart,
        end: Int = s.bedtimeEnd,
        days: Int = s.bedtimeDaysMask,
        dnd: Boolean = s.bedtimeDnd,
        windDown: Int = s.windDownMinutes,
    ) {
        scope.launch { container.settings.setBedtime(enabled, start, end, days, dnd, windDown) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Bedtime", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Every night your block list switches on, whatever the mode, so the last hour belongs to sleep.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FocusCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bedtime", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (s.bedtimeEnabled) "${Format.timeOfDay(s.bedtimeStart, use24)} to ${Format.timeOfDay(s.bedtimeEnd, use24)}" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.bedtimeEnabled, onCheckedChange = { save(enabled = it) })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeButton("Lights out", s.bedtimeStart, Modifier.weight(1f)) { picking = true }
                    TimeButton("Wake up", s.bedtimeEnd, Modifier.weight(1f)) { picking = false }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val bit = 1 shl (d.value - 1)
                        FilterChip(
                            selected = s.bedtimeDaysMask and bit != 0,
                            onClick = {
                                val next = s.bedtimeDaysMask xor bit
                                if (next != 0) save(days = next)
                            },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
            }
        }
        item {
            FocusCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Do Not Disturb", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Silence notifications during bedtime. Calls from starred contacts and alarms still come through.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = s.bedtimeDnd && dndAccess,
                        onCheckedChange = { on ->
                            if (on && !dndAccess) Permissions.openDndAccess(context)
                            save(dnd = on)
                        },
                    )
                }
                if (s.bedtimeDnd && !dndAccess) {
                    TextButton(onClick = { Permissions.openDndAccess(context) }) { Text("Allow Focus to manage Do Not Disturb") }
                }
            }
        }
        item {
            FocusCard(contentPadding = 16.dp) {
                Text("Wind-down reminder", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A gentle nudge before bedtime to put the phone down and get ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    windDownChoices.forEach { (minutes, label) ->
                        FilterChip(selected = s.windDownMinutes == minutes, onClick = { save(windDown = minutes) }, label = { Text(label) })
                    }
                }
            }
        }
    }

    picking?.let { isStart ->
        TimePickerDialog(
            initialMinute = if (isStart) s.bedtimeStart else s.bedtimeEnd,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                if (isStart) save(start = minute) else save(end = minute)
                picking = null
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    val use24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(Format.timeOfDay(minute, use24), style = MaterialTheme.typography.titleMedium)
        }
    }
}
