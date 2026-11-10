package com.reachnivi.focus.stack

import android.content.Context
import com.reachnivi.focus.core.StackCode
import com.reachnivi.focus.core.StackInvite
import com.reachnivi.focus.core.StackPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveStack(val invite: StackInvite, val role: String, val phase: StackPhase)

/** The stack this phone is in, kept in SharedPreferences so it survives the process being killed. */
class StackStore(context: Context) {
    private val prefs = context.getSharedPreferences("phone_stack", Context.MODE_PRIVATE)
    private val _active = MutableStateFlow(read())
    val active: StateFlow<ActiveStack?> = _active.asStateFlow()

    fun set(stack: ActiveStack?) {
        val previous = _active.value
        _active.value = stack
        // Short-lived hold timers (faceDownSince/faceUpSince) stay in memory; persist on phase changes only.
        if (stack == null || previous == null || previous.phase::class != stack.phase::class) write(stack)
    }

    private fun write(stack: ActiveStack?) {
        val e = prefs.edit().clear()
        if (stack != null) {
            e.putString(K_CODE, StackCode.encode(stack.invite)).putString(K_ROLE, stack.role)
            when (val p = stack.phase) {
                is StackPhase.Waiting -> e.putString(K_TYPE, "WAITING")
                is StackPhase.Running -> e.putString(K_TYPE, "RUNNING").putLong(K_A, p.startedAt).putLong(K_B, p.endsAt)
                is StackPhase.Completed -> e.putString(K_TYPE, "COMPLETED").putLong(K_A, p.at)
                is StackPhase.Broken -> e.putString(K_TYPE, "BROKEN").putLong(K_A, p.at).putLong(K_B, p.lastedMs)
            }
        }
        e.apply()
    }

    private fun read(): ActiveStack? {
        val invite = StackCode.parse(prefs.getString(K_CODE, null)) ?: return null
        val a = prefs.getLong(K_A, 0)
        val b = prefs.getLong(K_B, 0)
        val phase = when (prefs.getString(K_TYPE, null)) {
            "RUNNING" -> StackPhase.Running(a, b)
            "COMPLETED" -> StackPhase.Completed(a)
            "BROKEN" -> StackPhase.Broken(a, b)
            else -> StackPhase.Waiting()
        }
        return ActiveStack(invite, prefs.getString(K_ROLE, ROLE_GUEST) ?: ROLE_GUEST, phase)
    }

    companion object {
        const val ROLE_HOST = "HOST"
        const val ROLE_GUEST = "GUEST"
        private const val K_CODE = "code"
        private const val K_ROLE = "role"
        private const val K_TYPE = "type"
        private const val K_A = "a"
        private const val K_B = "b"
    }
}
