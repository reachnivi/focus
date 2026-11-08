package com.reachnivi.focus.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reachnivi.focus.permissions.Permissions
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.rememberResumeTick

@Composable
fun PermissionsScreen(onDone: () -> Unit, standalone: Boolean) {
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    var notifTick by remember { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifTick++ }

    // Re-read everything whenever we come back from a system settings screen.
    val usage = remember(resumeTick) { Permissions.hasUsageAccess(context) }
    val accessibility = remember(resumeTick) { Permissions.isAccessibilityEnabled(context) }
    val notifications = remember(resumeTick, notifTick) { Permissions.hasNotificationPermission(context) }
    val battery = remember(resumeTick) { Permissions.isIgnoringBatteryOptimizations(context) }
    var autoStartOpened by rememberSaveable { mutableStateOf(false) }
    val essentialsDone = usage && accessibility

    Column(Modifier.fillMaxSize().then(if (standalone) Modifier else Modifier.systemBarsPadding())) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (standalone) {
                    IconButton(onClick = onDone, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
                Text("A few permissions", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Focus works entirely on your phone. Nothing you see or type is stored or sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                PermissionCard(
                    icon = Icons.Rounded.QueryStats,
                    title = "Usage access",
                    body = "Shows how you spend time on your phone and powers daily limits.",
                    granted = usage,
                    required = true,
                    onAllow = { Permissions.openUsageAccess(context) },
                )
            }
            item {
                PermissionCard(
                    icon = Icons.Rounded.Accessibility,
                    title = "Accessibility",
                    body = "Lets Focus notice when a blocked app or website opens, and pause it.",
                    granted = accessibility,
                    required = true,
                    onAllow = { Permissions.openAccessibility(context) },
                    help = {
                        RestrictedSettingsHelp(onOpenAppInfo = { Permissions.openAppDetails(context) })
                    },
                )
            }
            item {
                PermissionCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Run in background",
                    body = "Stops the battery saver from closing Focus, so blocks stay reliable.",
                    granted = battery,
                    required = false,
                    onAllow = { Permissions.requestIgnoreBatteryOptimizations(context) },
                )
            }
            item {
                PermissionCard(
                    icon = Icons.Rounded.RocketLaunch,
                    title = "Auto launch (realme / OPPO)",
                    body = "On realme phones, turn on Auto launch for Focus so it restarts after a reboot.",
                    granted = autoStartOpened,
                    grantedLabel = "Opened",
                    required = false,
                    onAllow = {
                        autoStartOpened = true
                        Permissions.openAutoStart(context)
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionCard(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        body = "Shows a quiet countdown while a focus session runs.",
                        granted = notifications,
                        required = false,
                        onAllow = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = essentialsDone || standalone) {
                Text(if (standalone) "Done" else "Continue")
            }
            if (!essentialsDone && !standalone) {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("I'll do this later")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    required: Boolean,
    onAllow: () -> Unit,
    grantedLabel: String = "Allowed",
    help: (@Composable () -> Unit)? = null,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    FocusCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (required && !granted) {
                        Spacer(Modifier.size(8.dp))
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                "Needed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(12.dp))
            if (granted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = grantedLabel, tint = MaterialTheme.colorScheme.primary)
                    Text(grantedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                FilledTonalButton(onClick = onAllow) { Text("Allow") }
            }
        }
        if (help != null && !granted) {
            TextButton(onClick = { showHelp = !showHelp }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showHelp) "Hide help" else "Option greyed out?")
            }
            AnimatedVisibility(showHelp) { help() }
        }
    }
}

@Composable
private fun RestrictedSettingsHelp(onOpenAppInfo: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Android 13+ protects accessibility for apps installed outside the Play Store. To unlock it:",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("1. Tap Open App info below", style = MaterialTheme.typography.bodySmall)
            Text("2. Tap ⋮ (top right) → Allow restricted settings", style = MaterialTheme.typography.bodySmall)
            Text("3. Come back and tap Allow next to Accessibility", style = MaterialTheme.typography.bodySmall)
            Text(
                "On realme, the service is under Settings → Additional settings → Accessibility → Downloaded apps → Focus blocker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenAppInfo) { Text("Open App info") }
        }
    }
}
