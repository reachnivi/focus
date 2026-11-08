package com.reachnivi.focus.ui.block

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.block.BlockActivity
import com.reachnivi.focus.block.TemporaryPasses
import com.reachnivi.focus.core.BlockReason
import com.reachnivi.focus.ui.components.AppIcon
import com.reachnivi.focus.ui.components.LocalAppContainer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val prompts = listOf(
    "What did you open this for?",
    "Is this how you want to spend the next ten minutes?",
    "Notice the urge. Let it pass.",
    "Your attention is precious. Where should it go?",
    "One deep breath before you decide.",
    "The moment you're in is enough.",
)

private const val WAIT_SECONDS = 10

@Composable
fun BlockScreen(
    args: BlockActivity.Args,
    container: AppContainer,
    onClose: () -> Unit,
    onPass: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppContainer provides container) {
        BlockContent(args, container, onClose, onPass)
    }
}

@Composable
private fun BlockContent(args: BlockActivity.Args, container: AppContainer, onClose: () -> Unit, onPass: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val isSite = args.target is BlockActivity.Target.Site
    val name = if (isSite) args.target.id else container.catalog.label(args.target.id)
    val prompt = remember(args) { prompts.random() }
    val canPass = !args.strict && settings?.allowPass == true

    var secondsLeft by remember(args) { mutableIntStateOf(WAIT_SECONDS) }
    LaunchedEffect(args) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    val breathe = rememberInfiniteTransition(label = "breathe")
    val scale by breathe.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val phaseIn = scale > 0.91f

    val bg = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background),
    )

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Box(
                    Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                )
                Box(
                    Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                )
                if (isSite) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    AppIcon(args.target.id, size = 64.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (phaseIn) "breathe in" else "breathe out",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Text(headline(args.reason, name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                detail(args),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "“$prompt”",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (isSite) "Leave $name" else "Close $name")
                }
                when {
                    args.strict -> Row {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Strict focus: this can't be skipped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    canPass -> OutlinedButton(
                        onClick = onPass,
                        enabled = secondsLeft == 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            if (secondsLeft > 0) {
                                "Wait ${secondsLeft}s for ${TemporaryPasses.PASS_MINUTES} more minutes"
                            } else {
                                "Use ${TemporaryPasses.PASS_MINUTES} more minutes"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )

private fun headline(reason: BlockReason, name: String) = when (reason) {
    BlockReason.FOCUS_SESSION -> "You're in focus mode"
    BlockReason.SCHEDULE -> "$name is resting right now"
    BlockReason.ALWAYS_ON -> "You chose to pause $name"
    BlockReason.DAILY_LIMIT -> "That's today's time for $name"
}

private fun detail(args: BlockActivity.Args): String {
    val until = args.untilMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    return when (args.reason) {
        BlockReason.FOCUS_SESSION -> "Your session ends at $until. Stay with it, you're doing well."
        BlockReason.SCHEDULE -> "Your schedule keeps it paused until $until."
        BlockReason.ALWAYS_ON -> "It's on your block list. Change that anytime in Focus."
        BlockReason.DAILY_LIMIT -> "Your daily limit is used up. It resets at midnight."
    }
}
