package com.reachnivi.focus.stack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.FocusApp
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.core.StackPhase
import com.reachnivi.focus.ui.theme.FocusTheme

/** "You broke the stack!" Shown on the phone that was picked up first. */
class StackBrokenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = (application as FocusApp).container.stack
        setContent {
            FocusTheme {
                val active by controller.store.active.collectAsStateWithLifecycle()
                val stack = active
                val lasted = (stack?.phase as? StackPhase.Broken)?.lastedMs ?: 0L
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.background)))
                        .systemBarsPadding()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("☕", fontSize = 72.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("You broke the stack!", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Coffee's on you.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        buildString {
                            append("You lasted ${Format.duration(lasted)}")
                            stack?.invite?.name?.takeIf { it.isNotBlank() }?.let { append(" in \"$it\"") }
                            append(". Next time, leave it face-down.")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            controller.dismiss()
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Okay, okay") }
                }
            }
        }
    }
}
