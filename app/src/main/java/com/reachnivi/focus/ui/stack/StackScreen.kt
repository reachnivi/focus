package com.reachnivi.focus.ui.stack

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.reachnivi.focus.block.FocusAccessibilityService
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.core.StackCode
import com.reachnivi.focus.core.StackPhase
import com.reachnivi.focus.stack.ActiveStack
import com.reachnivi.focus.stack.StackStore
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.StatTile
import com.reachnivi.focus.ui.components.qrBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Phone stack: friends put phones face-down together; the first one picked up buys the coffee. */
@Composable
fun StackScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val controller = container.stack
    val active by controller.store.active.collectAsStateWithLifecycle()
    val history by container.stackHistory.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Phone stack", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Phones face-down, together, for a while. The first one picked up buys the coffee.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val s = active
        item {
            when {
                s == null -> Setup()
                s.phase is StackPhase.Waiting -> Waiting(s)
                s.phase is StackPhase.Running -> Running(s, s.phase)
                s.phase is StackPhase.Completed -> Finished(s, completed = true)
                else -> Finished(s, completed = false)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Stacks finished", "${history.count { it.outcome == "COMPLETED" }}", Modifier.weight(1f))
                StatTile("Coffees owed", "${history.count { it.outcome == "BROKEN" }}", Modifier.weight(1f))
            }
        }
        if (!FocusAccessibilityService.isConnected) {
            item {
                Text(
                    "The Focus blocker needs to be on for stacks to notice when a phone is picked up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun Setup() {
    val context = LocalContext.current
    val controller = LocalAppContainer.current.stack
    val scope = rememberCoroutineScope()
    var minutes by rememberSaveable { mutableStateOf(45) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FocusCard {
        Text("Start a stack", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StackCode.LENGTHS.forEach { m ->
                FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Friday dinner") },
            label = { Text("Name (optional)") },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { scope.launch { if (!controller.host(minutes, name)) error = "Finish your strict focus session first." } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Create stack") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { code -> scope.launch { error = controller.join(code.rawValue) } }
                    .addOnFailureListener { error = "Couldn't open the scanner. Make sure Google Play services is up to date." }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Join a friend's stack") }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun Waiting(stack: ActiveStack) {
    val controller = LocalAppContainer.current.stack
    FocusCard {
        if (stack.role == StackStore.ROLE_HOST) {
            Text("Friends scan this", style = MaterialTheme.typography.titleMedium)
            Text(
                "In Focus: Focus tab → Phone stack → Join a friend's stack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val qr = remember(stack.invite) { qrBitmap(StackCode.encode(stack.invite)).asImageBitmap() }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large).background(Color.White).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(qr, contentDescription = "Stack code", modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Text("You're in", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "${stack.invite.minutes} minutes${if (stack.invite.name.isNotBlank()) " · ${stack.invite.name}" else ""}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Place your phone face-down to start.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { controller.cancel() }) { Text("Cancel") }
    }
}

@Composable
private fun Running(stack: ActiveStack, phase: StackPhase.Running) {
    val controller = LocalAppContainer.current.stack
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    FocusCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.clock(phase.endsAt - now), fontSize = 56.sp, style = MaterialTheme.typography.displayMedium)
            Text(stack.invite.name.ifBlank { "Stack in progress" }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Don't touch it! Unlocking breaks the stack.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { controller.cancel() }) { Text("Give up (coffee's on you)") }
        }
    }
}

@Composable
private fun Finished(stack: ActiveStack, completed: Boolean) {
    val controller = LocalAppContainer.current.stack
    FocusCard(color = if (completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        Text(if (completed) "Stack complete" else "You broke the stack", style = MaterialTheme.typography.titleLarge)
        Text(
            if (completed) "${stack.invite.minutes} minutes together. +25 garden XP." else "Coffee's on you this time.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { controller.dismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
