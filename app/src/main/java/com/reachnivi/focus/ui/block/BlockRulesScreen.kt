package com.reachnivi.focus.ui.block

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reachnivi.focus.core.BlockMode
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.core.ScheduleRule
import com.reachnivi.focus.permissions.Permissions
import com.reachnivi.focus.ui.components.AppIcon
import com.reachnivi.focus.ui.components.EmptyHint
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.SectionHeader
import com.reachnivi.focus.ui.components.rememberResumeTick
import java.time.DayOfWeek

@Composable
fun blockViewModel(): BlockViewModel {
    val container = LocalAppContainer.current
    return viewModel { BlockViewModel(container) }
}

@Composable
fun BlockRulesScreen(onPickApps: () -> Unit, onPickLimitApp: () -> Unit, onFixPermissions: () -> Unit) {
    val vm = blockViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val blockerOn = remember(tick) { Permissions.isAccessibilityEnabled(context) }
    var editing by remember { mutableStateOf<ScheduleRule?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Block list", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Choose what to pause, and when.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!blockerOn) {
            item {
                FocusCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = onFixPermissions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.WarningAmber, background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Blocker is off", style = MaterialTheme.typography.titleSmall)
                            Text("Tap to turn on the accessibility service.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (state.locked) {
            item {
                FocusCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Lock, background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Strict focus is on. You can add blocks, but removing them waits until the session ends.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item { ModeCard(state.mode, enabled = !state.locked, onMode = vm::setMode) }
        if (state.mode == BlockMode.SCHEDULED) {
            item {
                SchedulesCard(
                    schedules = state.schedules,
                    locked = state.locked,
                    onAdd = { editing = ScheduleRule(name = "", days = DayOfWeek.entries.take(5).toSet(), startMinute = 9 * 60, endMinute = 17 * 60) },
                    onEdit = { if (!state.locked) editing = it },
                    onToggle = { rule, on -> if (on || !state.locked) vm.saveSchedule(rule.copy(enabled = on)) },
                )
            }
        }
        item { AppsCard(state.apps, state.locked, onPickApps, onRemove = { vm.setAppBlocked(it, false) }) }
        item { WebsitesCard(state.domains, state.locked, vm) }
        item { LimitsCard(state, onPickLimitApp, onRemove = vm::removeLimit) }
        item { Spacer(Modifier.height(8.dp)) }
    }

    editing?.let { rule ->
        ScheduleDialog(
            initial = rule,
            onDismiss = { editing = null },
            onSave = {
                vm.saveSchedule(it)
                editing = null
            },
            onDelete = if (rule.id != 0L && !state.locked) {
                {
                    vm.deleteSchedule(rule.id)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun ModeCard(mode: BlockMode, enabled: Boolean, onMode: (BlockMode) -> Unit) {
    FocusCard(contentPadding = 8.dp) {
        Text(
            "When is the block list active?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        )
        listOf(
            Triple(BlockMode.ALWAYS, "Always", "Blocked apps and sites stay paused all day"),
            Triple(BlockMode.SCHEDULED, "On a schedule", "Only during the times you set, like work hours"),
            Triple(BlockMode.FOCUS_ONLY, "Only during focus sessions", "Use the Focus tab to start one"),
        ).forEach { (m, title, body) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onMode(m) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = { onMode(m) }, enabled = enabled)
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SchedulesCard(
    schedules: List<ScheduleRule>,
    locked: Boolean,
    onAdd: () -> Unit,
    onEdit: (ScheduleRule) -> Unit,
    onToggle: (ScheduleRule, Boolean) -> Unit,
) {
    FocusCard {
        SectionHeader("Schedules", action = { AddButton(onAdd) })
        if (schedules.isEmpty()) {
            EmptyHint(Icons.Rounded.Schedule, "No schedules yet", "Add one, like weekdays from 9 to 5.")
        }
        schedules.forEachIndexed { i, rule ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(rule) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name.ifBlank { "Schedule" }, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${daysSummary(rule.days)} · ${Format.minuteOfDay(rule.startMinute)}–${Format.minuteOfDay(rule.endMinute)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) }, enabled = !locked || !rule.enabled)
            }
        }
    }
}

@Composable
private fun AppsCard(apps: List<String>, locked: Boolean, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val catalog = LocalAppContainer.current.catalog
    FocusCard {
        SectionHeader("Apps", subtitle = if (apps.isEmpty()) null else "${apps.size} blocked", action = { AddButton(onAdd) })
        if (apps.isEmpty()) {
            EmptyHint(Icons.Rounded.Apps, "No apps blocked", "Add the apps that pull you in most.")
        }
        apps.forEach { pkg ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(catalog.label(pkg), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RemoveButton(enabled = !locked) { onRemove(pkg) }
            }
        }
    }
}

@Composable
private fun WebsitesCard(domains: List<String>, locked: Boolean, vm: BlockViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val submit = {
        if (text.isNotBlank()) {
            vm.addDomain(text) { ok ->
                error = !ok
                if (ok) text = ""
            }
        }
    }
    FocusCard {
        SectionHeader("Websites", subtitle = "Works in Chrome, Edge, Brave, Firefox, Samsung Internet and more")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = false
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("youtube.com") },
                leadingIcon = { Icon(Icons.Rounded.Language, null) },
                singleLine = true,
                isError = error,
                supportingText = if (error) {
                    { Text("That doesn't look like a website") }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = submit, enabled = text.isNotBlank()) { Text("Add") }
        }
        AnimatedVisibility(domains.isNotEmpty()) {
            Column(Modifier.padding(top = 8.dp)) {
                domains.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Language, size = 36.dp, background = MaterialTheme.colorScheme.surfaceContainerHigh, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d, style = MaterialTheme.typography.bodyLarge)
                            Text("and all its subdomains", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RemoveButton(enabled = !locked) { vm.removeDomain(d) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitsCard(state: BlockUiState, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val catalog = LocalAppContainer.current.catalog
    FocusCard {
        SectionHeader("Daily limits", subtitle = "Apply every day, whatever the mode", action = { AddButton(onAdd) })
        if (state.limits.isEmpty()) {
            EmptyHint(Icons.Rounded.HourglassBottom, "No limits set", "Allow an app for a set time each day, then pause it.")
        }
        state.limits.forEach { limit ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(limit.packageName, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(catalog.label(limit.packageName), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Format.duration(limit.minutesPerDay * 60_000L)} a day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RemoveButton(enabled = !state.locked) { onRemove(limit.packageName) }
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add")
    }
}

@Composable
private fun RemoveButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f))
    }
}

fun daysSummary(days: Set<DayOfWeek>): String {
    val weekdays = DayOfWeek.entries.take(5).toSet()
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when (days) {
        DayOfWeek.entries.toSet() -> "Every day"
        weekdays -> "Weekdays"
        weekend -> "Weekends"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") {
            it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        }
    }
}
