package com.reachnivi.focus.stack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reachnivi.focus.R
import com.reachnivi.focus.core.StackCode
import com.reachnivi.focus.core.StackInvite
import com.reachnivi.focus.core.StackMonitor
import com.reachnivi.focus.core.StackPhase
import com.reachnivi.focus.data.FocusDao
import com.reachnivi.focus.data.FocusSessionManager
import com.reachnivi.focus.data.PhoneStackEntity
import com.reachnivi.focus.data.SettingsRepository
import com.reachnivi.focus.permissions.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Runs a phone stack on this phone: watches orientation and unlocks (fed by the blocker service),
 * applies [StackMonitor], and reacts to the result: rings and shames on a break, celebrates a finish.
 */
class StackController(
    private val context: Context,
    val store: StackStore,
    private val dao: FocusDao,
    private val sessions: FocusSessionManager,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private var sensorListener: SensorEventListener? = null
    private var receiver: BroadcastReceiver? = null
    private var sensingContext: Context? = null

    /** @return false if a strict focus session is running (a stack can't override it). */
    suspend fun host(minutes: Int, name: String): Boolean {
        if (strictSessionRunning()) return false
        val id = UUID.randomUUID().toString().replace("-", "").take(10)
        store.set(ActiveStack(StackInvite(id, minutes, name.trim().take(40)), StackStore.ROLE_HOST, StackPhase.Waiting()))
        return true
    }

    /** @return null on success, or why joining failed. */
    suspend fun join(code: String?): String? {
        val invite = StackCode.parse(code) ?: return "That isn't a Focus phone-stack code."
        if (strictSessionRunning()) return "Finish your strict focus session first."
        store.set(ActiveStack(invite, StackStore.ROLE_GUEST, StackPhase.Waiting()))
        return null
    }

    /** Cancel while waiting is free; giving up while running counts as a break (no ringing). */
    fun cancel() {
        val s = store.active.value ?: return
        when (val p = s.phase) {
            is StackPhase.Running -> {
                val now = System.currentTimeMillis()
                transition(s, StackPhase.Broken(now, now - p.startedAt), ring = false)
            }
            else -> store.set(null)
        }
    }

    fun dismiss() = store.set(null)

    fun tick() {
        val s = store.active.value ?: return
        transition(s, StackMonitor.tick(s.phase, System.currentTimeMillis()))
    }

    // --- Sensing, owned by the always-running blocker service ---

    fun ensureSensing(serviceContext: Context) {
        val needed = store.active.value?.phase.let { it is StackPhase.Waiting || it is StackPhase.Running }
        if (needed && sensorListener == null) startSensing(serviceContext) else if (!needed && sensorListener != null) stopSensing()
    }

    fun stopSensing() {
        val ctx = sensingContext ?: return
        sensorListener?.let { ctx.getSystemService(SensorManager::class.java)?.unregisterListener(it) }
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        sensorListener = null
        receiver = null
        sensingContext = null
    }

    private fun startSensing(ctx: Context) {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val power = ctx.getSystemService(PowerManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values.getOrNull(2) ?: return
                val s = store.active.value ?: return
                val next = StackMonitor.onOrientation(s.phase, faceDown = z < -7f, screenOn = power?.isInteractive != false, now = System.currentTimeMillis(), minutes = s.invite.minutes)
                transition(s, next)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val unlock = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                val s = store.active.value ?: return
                transition(s, StackMonitor.onUnlock(s.phase, System.currentTimeMillis()))
            }
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL)
        ContextCompat.registerReceiver(ctx, unlock, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        sensorListener = listener
        receiver = unlock
        sensingContext = ctx
    }

    private fun transition(current: ActiveStack, next: StackPhase, ring: Boolean = true) {
        if (next == current.phase) return
        val updated = current.copy(phase = next)
        store.set(updated)
        val wasRunning = current.phase is StackPhase.Running
        when {
            current.phase is StackPhase.Waiting && next is StackPhase.Running -> scope.launch {
                val s = settings.current()
                if (s.session?.isRunning(System.currentTimeMillis()) != true) sessions.start(current.invite.minutes, strict = false)
            }
            wasRunning && next is StackPhase.Broken -> {
                val started = (current.phase as StackPhase.Running).startedAt
                record(updated, started, next.at, "BROKEN")
                scope.launch { sessions.stop() }
                if (ring) ringAndShake()
                context.startActivity(Intent(context, StackBrokenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            wasRunning && next is StackPhase.Completed -> {
                val started = (current.phase as StackPhase.Running).startedAt
                record(updated, started, next.at, "COMPLETED")
                scope.launch { sessions.completeIfExpired() }
                notifyComplete(current.invite)
            }
        }
        if (next is StackPhase.Completed || next is StackPhase.Broken) stopSensing()
    }

    private fun record(stack: ActiveStack, startedAt: Long, endedAt: Long, outcome: String) {
        scope.launch {
            dao.insertStack(
                PhoneStackEntity(
                    code = stack.invite.id,
                    groupName = stack.invite.name,
                    role = stack.role,
                    startedAt = startedAt,
                    minutes = stack.invite.minutes,
                    endedAt = endedAt,
                    outcome = outcome,
                ),
            )
        }
    }

    private fun ringAndShake() {
        runCatching {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 900), -1))
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        val ringtone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull() ?: return
        ringtone.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        runCatching { ringtone.play() }
        scope.launch {
            delay(5_000)
            runCatching { ringtone.stop() }
        }
    }

    private fun notifyComplete(invite: StackInvite) {
        if (!Permissions.hasNotificationPermission(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Stack complete")
            .setContentText("${invite.minutes} minutes together${if (invite.name.isNotBlank()) " · ${invite.name}" else ""}. +25 garden XP")
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    fun createChannel() {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL_ID, "Phone stack", android.app.NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private suspend fun strictSessionRunning(): Boolean =
        settings.current().session?.let { it.strict && it.isRunning(System.currentTimeMillis()) } == true

    private companion object {
        const val CHANNEL_ID = "phone_stack"
        const val NOTIFICATION_ID = 47
    }
}
