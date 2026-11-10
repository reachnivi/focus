package com.reachnivi.focus.ui.unlock

import android.app.Activity
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.UnlockKey
import com.reachnivi.focus.data.CodeResult
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.qrBitmap
import com.reachnivi.focus.ui.connect.PickPhone
import com.reachnivi.focus.ui.connect.readPicked

/** Settings → Ways to unlock: a printable QR, an NFC tag, and a friend's code. */
@Composable
fun UnlockMethodsScreen(onBack: () -> Unit) {
    val store = LocalAppContainer.current.unlock
    val m by store.methods.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Ways to unlock", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Make unlocking take real effort: walk to a code in another room, tap a tag, or ask a friend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FocusCard {
                Text("Unlock for", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30).forEach { min ->
                        FilterChip(selected = m.unlockMinutes == min, onClick = { store.setUnlockMinutes(min) }, label = { Text("$min min") })
                    }
                }
            }
        }
        item { QrCard(m.qrToken) }
        item { TagCard(m.tagHash != null) }
        item { FriendCard() }
    }
}

@Composable
private fun QrCard(token: String?) {
    val store = LocalAppContainer.current.unlock
    FocusCard {
        Text("Unlock QR code", style = MaterialTheme.typography.titleMedium)
        Text(
            "Print it, or show it on another device, and keep it somewhere you have to walk to: another room, your bag, the gym. Not for strict sessions or bedtime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (token == null) {
            FilledTonalButton(onClick = { store.newQrToken() }, modifier = Modifier.fillMaxWidth()) { Text("Create my unlock code") }
        } else {
            val qr = remember(token) { qrBitmap(UnlockKey.qrText(token)).asImageBitmap() }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large).background(Color.White).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { Image(qr, contentDescription = "Unlock code", modifier = Modifier.fillMaxSize()) }
            Row {
                TextButton(onClick = { store.newQrToken() }) { Text("New code") }
                TextButton(onClick = { store.removeQr() }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun TagCard(registered: Boolean) {
    val context = LocalContext.current
    val store = LocalAppContainer.current.unlock
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var listening by remember { mutableStateOf(false) }
    if (adapter == null) return

    if (listening) {
        val activity = context as? Activity
        DisposableEffect(activity) {
            activity?.let { act ->
                adapter.enableReaderMode(
                    act,
                    { tag ->
                        store.registerTag(tag.id)
                        act.runOnUiThread { listening = false }
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                    null,
                )
            }
            onDispose { activity?.let { runCatching { adapter.disableReaderMode(it) } } }
        }
    }

    FocusCard {
        Text("NFC tag", style = MaterialTheme.typography.titleMedium)
        Text(
            "Any NFC sticker, keyring or transit card works. Stick it somewhere out of reach. Only a fingerprint of its ID is kept.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when {
            listening -> Text("Hold the tag to the back of your phone…", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            registered -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tag registered", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { listening = true }) { Text("Replace") }
                TextButton(onClick = { store.removeTag() }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
            else -> FilledTonalButton(onClick = { listening = true }, modifier = Modifier.fillMaxWidth()) { Text("Register a tag") }
        }
        if (!adapter.isEnabled) {
            Text("Turn on NFC in your phone's settings first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun FriendCard() {
    val context = LocalContext.current
    val store = LocalAppContainer.current.unlock
    val m by store.methods.collectAsStateWithLifecycle()
    var pairingLink by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickPhone()) { uri ->
        val picked = uri?.let { readPicked(context, it) } ?: return@rememberLauncherForActivityResult
        pairingLink = store.startFriendPairing(picked.first, picked.second)
        message = null
    }

    FocusCard {
        Text("Friend code", style = MaterialTheme.typography.titleMedium)
        Text(
            "A friend holds your key in Google Authenticator (or any authenticator app). To unlock, you call or text them for the current 6-digit code. It even works during strict focus: they're the only way out.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val link = pairingLink
        when {
            link != null -> {
                Text("Ask ${m.friendName} to open their authenticator app, tap + and scan this:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val qr = remember(link) { qrBitmap(link).asImageBitmap() }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large).background(Color.White).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { Image(qr, contentDescription = "Pairing code for your friend", modifier = Modifier.fillMaxSize()) }
                Spacer(Modifier.height(8.dp))
                Text("Then type the code their app shows, to check it worked. This QR won't be shown again.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        message = when (val r = store.confirmPairing(code)) {
                            CodeResult.Ok -> { pairingLink = null; "Paired with ${m.friendName}." }
                            CodeResult.Wrong -> "That code didn't match. Wait for a fresh one and try again."
                            is CodeResult.LockedOut -> "Too many tries. Try again in a few minutes."
                        }
                        code = ""
                    },
                    enabled = code.length == 6,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check pairing") }
            }
            m.hasFriend -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paired with ${m.friendName}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { picker.launch(Unit) }) { Text("Change") }
                TextButton(onClick = { store.removeFriend() }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
            else -> FilledTonalButton(onClick = { picker.launch(Unit) }, modifier = Modifier.fillMaxWidth()) { Text("Choose a friend") }
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
