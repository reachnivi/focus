package com.reachnivi.focus.ui.move

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reachnivi.focus.core.Exercise
import com.reachnivi.focus.core.Reps
import com.reachnivi.focus.move.PoseRepAnalyzer
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.ProgressRing
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Move to unlock: squats or pushups, counted live by the camera on the phone. */
@Composable
fun MoveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = LocalAppContainer.current.move
    val today by repo.today.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var exercise by remember { mutableStateOf(Exercise.SQUAT) }
    var target by remember { mutableIntStateOf(10) }
    var running by remember { mutableStateOf(false) }
    var reps by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (granted) running = true
    }

    fun finish() {
        val done = reps
        val ex = exercise
        running = false
        reps = 0
        scope.launch {
            val minutes = repo.record(ex, done)
            val msg = when {
                done == 0 -> "No reps counted this time."
                minutes > 0 -> "$done ${ex.label.lowercase()} · +$minutes min earned"
                else -> "$done ${ex.label.lowercase()} logged. Today's move minutes are maxed out."
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconButton(onClick = { if (running) finish(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text("Move to unlock", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Every ${Reps.REPS_PER_REWARD} reps earns ${Reps.MINUTES_PER_REWARD} minutes, up to ${Reps.MAX_MINUTES_PER_DAY} a day. Counted on your phone; the camera feed is never saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!running) {
            FocusCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Exercise.entries.forEach { e -> FilterChip(selected = exercise == e, onClick = { exercise = e }, label = { Text(e.label) }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20).forEach { t -> FilterChip(selected = target == t, onClick = { target = t }, label = { Text("$t reps") }) }
                }
                Spacer(Modifier.height(10.dp))
                Text(exercise.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                today?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Today: ${it.reps} reps · ${it.minutesEarned} of ${Reps.MAX_MINUTES_PER_DAY} min earned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (hasCamera) running = true else permission.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Start ${exercise.label.lowercase()}") }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.large).background(Color.Black)) {
                CameraFeed(exercise) { r, v ->
                    reps = r
                    visible = v
                }
                Column(
                    Modifier.align(Alignment.TopCenter).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProgressRing(progress = reps / target.toFloat(), modifier = Modifier.size(140.dp), strokeWidth = 10.dp) {
                        Text("$reps", fontSize = 48.sp, fontWeight = FontWeight.Light, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (visible) "of $target ${exercise.label.lowercase()}" else "Step back so I can see you",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (reps >= target) "Done, bank it" else "Finish")
            }
        }
    }
}

@Composable
private fun CameraFeed(exercise: Exercise, onUpdate: (Int, Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(exercise) {
        val analyzer = PoseRepAnalyzer(exercise) { r, v -> main.execute { onUpdate(r, v) } }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            provider?.unbindAll()
            analyzer.close()
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
