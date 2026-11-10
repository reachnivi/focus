package com.reachnivi.focus.ui.connect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.DuePerson
import com.reachnivi.focus.data.ChallengeStore
import com.reachnivi.focus.data.CirclePersonEntity
import com.reachnivi.focus.data.ConnectionsRepository
import com.reachnivi.focus.ui.components.EmptyHint
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.StatTile
import kotlinx.coroutines.launch

/** Opens the system contact picker for one phone number. Needs no contacts permission. */
private class PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private fun readPicked(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
}.getOrNull()?.takeIf { it.second.isNotBlank() }

@Composable
fun CircleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = LocalAppContainer.current.connections
    val people by repo.circle.collectAsStateWithLifecycle(emptyList())
    val summary by repo.summary.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(PickPhone()) { uri ->
        val picked = uri?.let { readPicked(context, it) } ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = if (repo.addPerson(picked.first, picked.second)) null else "Already in your circle, or the circle is full (${ConnectionsRepository.MAX_CIRCLE} people)."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Your circle", style = MaterialTheme.typography.headlineLarge)
            Text(
                "The people you want to stay close to. When the urge to scroll hits, Focus suggests a call instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary?.let { s ->
            if (s.circleSize > 0) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Week streak", "${s.weekStreak}", Modifier.weight(1f), Icons.Rounded.Favorite)
                        StatTile("Calls this month", "${s.callsThisMonth}", Modifier.weight(1f), Icons.Rounded.Call)
                        StatTile("XP", "${s.totalXp}", Modifier.weight(1f))
                    }
                }
            }
        }
        if (people.isEmpty()) {
            item {
                FocusCard {
                    EmptyHint(
                        Icons.Rounded.Favorite,
                        "Start with one person",
                        "Family, a friend from home, someone you miss. Calls of 3 minutes or more count, and Focus checks them on your phone.",
                    )
                }
            }
        }
        val due = summary?.ranked?.associateBy { it.person.id } ?: emptyMap()
        items(people, key = { it.id }) { person ->
            PersonCard(
                person = person,
                due = due[person.id],
                onCall = { repo.startChallenge(person.id, person.name, person.phone, ChallengeStore.SOURCE_CIRCLE) },
                onInterval = { days -> scope.launch { repo.setEveryDays(person.id, days) } },
                onRemove = { scope.launch { repo.removePerson(person.id) } },
            )
        }
        item {
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(8.dp))
            }
            if (people.size < ConnectionsRepository.MAX_CIRCLE) {
                FilledTonalButton(onClick = { picker.launch(Unit) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add someone (${people.size}/${ConnectionsRepository.MAX_CIRCLE})")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Focus only sees the one number you pick, never your whole contact list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: CirclePersonEntity,
    due: DuePerson?,
    onCall: () -> Unit,
    onInterval: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    FocusCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    lastTalkedText(due),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (due?.isDue != false) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onCall) { Icon(Icons.Rounded.Call, contentDescription = "Call ${person.name}") }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConnectionsRepository.INTERVALS.forEach { (days, label) ->
                FilterChip(selected = person.everyDays == days, onClick = { onInterval(days) }, label = { Text(label) })
            }
        }
    }
}

fun lastTalkedText(due: DuePerson?): String = when (val d = due?.daysSince) {
    null -> "No call logged yet"
    0 -> "Talked today"
    1 -> "Talked yesterday"
    else -> "$d days since you talked"
}

