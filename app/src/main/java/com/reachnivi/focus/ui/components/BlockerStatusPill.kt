package com.reachnivi.focus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reachnivi.focus.core.BlockerStatus
import com.reachnivi.focus.permissions.Permissions
import com.reachnivi.focus.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Live blocker status. Re-checked on every resume, and every few seconds while it isn't
 * active, so the screen catches the service coming back (or Android re-binding it).
 */
@Composable
fun rememberBlockerStatus(): BlockerStatus {
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    var status by remember { mutableStateOf(Permissions.blockerStatus(context)) }
    LaunchedEffect(resumeTick) {
        status = Permissions.blockerStatus(context)
        while (status != BlockerStatus.ACTIVE) {
            delay(3_000)
            status = Permissions.blockerStatus(context)
        }
    }
    LaunchedEffect(status) {
        if (status == BlockerStatus.ACTIVE) {
            (context.applicationContext as com.reachnivi.focus.FocusApp).container.healthNotifier.cancel()
        }
    }
    return status
}

/** "Blocker on" with a green dot, or a tappable warning that leads to the fix. */
@Composable
fun BlockerStatusPill(status: BlockerStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ok = status == BlockerStatus.ACTIVE
    val label = when (status) {
        BlockerStatus.ACTIVE -> "Blocker on"
        BlockerStatus.OFF -> "Blocker off · tap to turn on"
        BlockerStatus.NOT_RUNNING -> "Blocker stopped · tap to restart"
    }
    val dot = if (ok) Palette.Sage500 else MaterialTheme.colorScheme.tertiary
    val bg = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (ok) {
        Surface(modifier = modifier, shape = CircleShape, color = bg, content = content)
    } else {
        Surface(onClick = { Permissions.openAccessibility(context) }, modifier = modifier, shape = CircleShape, color = bg, content = content)
    }
}

/** One-line explanation for cards that need more than the pill. */
fun blockerStatusHelp(status: BlockerStatus): String = when (status) {
    BlockerStatus.ACTIVE -> "Blocking is working."
    BlockerStatus.OFF -> "Tap to turn on the accessibility service. Your block list does nothing until you do."
    BlockerStatus.NOT_RUNNING ->
        "Android stopped the service, often to save battery. Turn Focus blocker off and on again, and allow Focus to run in the background."
}
