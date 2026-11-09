package com.reachnivi.focus.ui.block

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.data.InstalledApp
import com.reachnivi.focus.ui.components.AppIcon
import kotlin.math.roundToInt

@Composable
fun AppPickerScreen(forLimit: Boolean, onBack: () -> Unit, forPause: Boolean = false) {
    val vm = blockViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var limitFor by remember { mutableStateOf<InstalledApp?>(null) }
    LaunchedEffect(Unit) { vm.loadInstalled() }

    val blocked = if (forPause) state.pausedApps.toSet() else state.apps.toSet()
    val pauseMode = forPause
    val limits = state.limits.associate { it.packageName to it.minutesPerDay }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                when {
                    forLimit -> "Set a daily limit"
                    forPause -> "Pause before opening"
                    else -> "Choose apps to block"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        val apps = installed
        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            val (distracting, others) = filtered.partition { it.group.distracting }

            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                if (distracting.isNotEmpty()) {
                    item { GroupLabel("Often distracting") }
                    items(distracting, key = { "d" + it.packageName }) { app ->
                        AppRow(app, forLimit, blocked, limits, state.locked && !pauseMode, vm, pauseMode) { limitFor = app }
                    }
                }
                if (others.isNotEmpty()) {
                    item { GroupLabel(if (distracting.isEmpty()) "Apps" else "Everything else") }
                    items(others, key = { "o" + it.packageName }) { app ->
                        AppRow(app, forLimit, blocked, limits, state.locked && !pauseMode, vm, pauseMode) { limitFor = app }
                    }
                }
            }
        }
    }

    limitFor?.let { app ->
        LimitDialog(
            app = app,
            current = limits[app.packageName],
            onDismiss = { limitFor = null },
            onSave = {
                vm.setLimit(app.packageName, it)
                limitFor = null
            },
            onRemove = if (limits.containsKey(app.packageName) && !state.locked) {
                {
                    vm.removeLimit(app.packageName)
                    limitFor = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun AppRow(
    app: InstalledApp,
    forLimit: Boolean,
    blocked: Set<String>,
    limits: Map<String, Int>,
    locked: Boolean,
    vm: BlockViewModel,
    pauseMode: Boolean,
    onLimit: () -> Unit,
) {
    val isBlocked = app.packageName in blocked
    val toggle = {
        if (pauseMode) {
            vm.setAppPaused(app.packageName, !isBlocked)
        } else if (!(locked && isBlocked)) {
            vm.setAppBlocked(app.packageName, !isBlocked)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (forLimit) onLimit() else toggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.group.distracting) {
                Text(app.group.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (forLimit) {
            limits[app.packageName]?.let {
                Text(
                    "${Format.duration(it * 60_000L)}/day",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Switch(checked = isBlocked, onCheckedChange = { toggle() }, enabled = !(locked && isBlocked))
        }
    }
}

@Composable
private fun LimitDialog(
    app: InstalledApp,
    current: Int?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var minutes by remember { mutableFloatStateOf((current ?: 30).toFloat()) }
    val rounded = (minutes / 5f).roundToInt() * 5
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(app.packageName, size = 48.dp) },
        title = { Text(app.label) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Format.duration(rounded * 60_000L), style = MaterialTheme.typography.displaySmall)
                Text("a day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..240f, steps = 46)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rounded.coerceAtLeast(5)) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
