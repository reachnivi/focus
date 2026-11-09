package com.reachnivi.focus.ui.launcher

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.MainActivity
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.data.InstalledApp
import com.reachnivi.focus.data.SettingsRepository
import com.reachnivi.focus.permissions.Permissions
import com.reachnivi.focus.ui.components.rememberResumeTick
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Overlay { NONE, ALL_APPS, EDIT }

/**
 * The minimal home screen: time, one honest line about today, and a handful of apps as words.
 * Everything else is one swipe away, never on display.
 */
@Composable
fun LauncherScreen(vm: LauncherViewModel, homePresses: Int) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val resumeTick = rememberResumeTick()

    LaunchedEffect(resumeTick) { vm.refresh() }
    // Pressing Home while already home closes any open list, like every launcher.
    LaunchedEffect(homePresses) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) overlay = Overlay.ALL_APPS },
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Clock()
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    !state.hasUsageAccess -> "Tap to finish setting up Focus"
                    state.today == null -> " "
                    else -> "${Format.duration(state.today!!.totalMs)} today · ${state.today!!.unlocks} unlocks"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { openFocus(context) },
            )
            Spacer(Modifier.weight(1f))
            if (state.favourites.isEmpty()) {
                Text(
                    "Choose up to ${SettingsRepository.MAX_HOME_APPS} apps for your home screen. Everything else stays a swipe away.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { overlay = Overlay.EDIT }, contentPadding = PaddingValues(0.dp)) { Text("Choose apps") }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.favourites.forEach { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.launch(app.packageName) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app.label,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Light,
                                color = if (app.pausedNow) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (app.pausedNow) {
                                Spacer(Modifier.width(10.dp))
                                Icon(Icons.Rounded.Lock, contentDescription = "Paused", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuietLink("All apps") { overlay = Overlay.ALL_APPS }
                QuietLink("Edit") { overlay = Overlay.EDIT }
                QuietLink("Focus") { openFocus(context) }
            }
        }

        AnimatedVisibility(
            visible = overlay != Overlay.NONE,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            AppList(
                apps = allApps,
                editing = overlay == Overlay.EDIT,
                favourites = state.favourites.map { it.packageName },
                onLaunch = {
                    vm.launch(it)
                    overlay = Overlay.NONE
                },
                onToggle = { pkg, current -> vm.toggleFavourite(pkg, current) },
                onClose = { overlay = Overlay.NONE },
                onLoad = vm::loadApps,
            )
        }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay((60 - value.second) * 1_000L) // tick on the minute
        }
    }
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    Text(now.format(DateTimeFormatter.ofPattern(pattern)), fontSize = 72.sp, fontWeight = FontWeight.Thin, color = MaterialTheme.colorScheme.onSurface)
    Text(
        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun QuietLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppList(
    apps: List<InstalledApp>?,
    editing: Boolean,
    favourites: List<String>,
    onLaunch: (String) -> Unit,
    onToggle: (String, List<String>) -> Unit,
    onClose: () -> Unit,
    onLoad: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(apps) { if (apps == null) onLoad() }
    LaunchedEffect(editing) { if (!editing) runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editing) "Home apps · ${favourites.size}/${SettingsRepository.MAX_HOME_APPS}" else "All apps",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(if (editing) "Done" else "Close") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focus),
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        val list = apps.orEmpty().filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.packageName }) { app ->
                val chosen = app.packageName in favourites
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (editing) onToggle(app.packageName, favourites) else onLaunch(app.packageName) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (editing) {
                        Icon(
                            if (chosen) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (chosen) "On home screen" else "Not on home screen",
                            tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
        if (!editing) {
            TextButton(onClick = { Permissions.openHomeSettings(context) }) {
                Text("Switch home screen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun openFocus(context: android.content.Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
    )
}
