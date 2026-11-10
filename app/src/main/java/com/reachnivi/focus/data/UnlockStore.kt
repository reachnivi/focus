package com.reachnivi.focus.data

import android.content.Context
import com.reachnivi.focus.core.GuardState
import com.reachnivi.focus.core.Totp
import com.reachnivi.focus.core.UnlockGuard
import com.reachnivi.focus.core.UnlockKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

data class UnlockMethods(
    val qrToken: String? = null,
    val tagHash: String? = null,
    val friendName: String? = null,
    val friendPhone: String? = null,
    val friendPaired: Boolean = false,
    val unlockMinutes: Int = 15,
) {
    val hasFriend: Boolean get() = friendPaired && friendName != null
}

sealed interface CodeResult {
    data object Ok : CodeResult
    data object Wrong : CodeResult
    data class LockedOut(val untilMs: Long) : CodeResult
}

/** Ways to unlock: a printable QR, an NFC tag, and a friend's authenticator code. Kept on the phone only. */
class UnlockStore(context: Context) {
    private val prefs = context.getSharedPreferences("unlock_methods", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val _methods = MutableStateFlow(read())
    val methods: StateFlow<UnlockMethods> = _methods.asStateFlow()

    fun newQrToken(): String {
        val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        prefs.edit().putString(K_TOKEN, token).apply()
        refresh()
        return token
    }

    fun removeQr() = edit { remove(K_TOKEN) }

    fun matchesQr(scanned: String?): Boolean = UnlockKey.matches(UnlockKey.tokenFrom(scanned), prefs.getString(K_TOKEN, null))

    fun registerTag(id: ByteArray) = edit { putString(K_TAG, UnlockKey.hashTag(id)) }

    fun removeTag() = edit { remove(K_TAG) }

    fun matchesTag(id: ByteArray): Boolean = UnlockKey.matches(UnlockKey.hashTag(id), prefs.getString(K_TAG, null))

    /** Starts pairing a friend: a new secret, not yet confirmed. Returns the otpauth link for the QR. */
    fun startFriendPairing(name: String, phone: String): String {
        val secret = ByteArray(20).also(random::nextBytes)
        prefs.edit()
            .putString(K_FRIEND_NAME, name).putString(K_FRIEND_PHONE, phone)
            .putString(K_SECRET, Totp.base32Encode(secret)).putBoolean(K_PAIRED, false)
            .remove(K_LAST_STEP).apply()
        refresh()
        return Totp.otpauthUri(secret, "unlock code for you")
    }

    /** Confirms pairing with one code from the friend's app. */
    fun confirmPairing(code: String): CodeResult {
        val r = checkCode(code)
        if (r == CodeResult.Ok) edit { putBoolean(K_PAIRED, true) }
        return r
    }

    fun removeFriend() = edit {
        remove(K_FRIEND_NAME); remove(K_FRIEND_PHONE); remove(K_SECRET); remove(K_PAIRED); remove(K_LAST_STEP)
    }

    /** Checks a friend's code; each code works once, and five misses pause tries for ten minutes. */
    fun checkCode(code: String): CodeResult {
        val now = System.currentTimeMillis()
        val guard = GuardState(prefs.getInt(K_FAILS, 0), prefs.getLong(K_LOCKED, 0))
        if (!UnlockGuard.canTry(guard, now)) return CodeResult.LockedOut(guard.lockedUntil)
        val secret = prefs.getString(K_SECRET, null)?.let { Totp.base32Decode(it) } ?: return CodeResult.Wrong
        val last = if (prefs.contains(K_LAST_STEP)) prefs.getLong(K_LAST_STEP, 0) else null
        val step = Totp.verify(secret, code, now, lastUsedStep = last)
        return if (step != null) {
            val ok = UnlockGuard.onSuccess()
            prefs.edit().putLong(K_LAST_STEP, step).putInt(K_FAILS, ok.failures).putLong(K_LOCKED, ok.lockedUntil).apply()
            CodeResult.Ok
        } else {
            val next = UnlockGuard.onFailure(guard, now)
            prefs.edit().putInt(K_FAILS, next.failures).putLong(K_LOCKED, next.lockedUntil).apply()
            if (next.lockedUntil > now) CodeResult.LockedOut(next.lockedUntil) else CodeResult.Wrong
        }
    }

    fun setUnlockMinutes(minutes: Int) = edit { putInt(K_MINUTES, minutes) }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        refresh()
    }

    private fun refresh() {
        _methods.value = read()
    }

    private fun read() = UnlockMethods(
        qrToken = prefs.getString(K_TOKEN, null),
        tagHash = prefs.getString(K_TAG, null),
        friendName = prefs.getString(K_FRIEND_NAME, null),
        friendPhone = prefs.getString(K_FRIEND_PHONE, null),
        friendPaired = prefs.getBoolean(K_PAIRED, false),
        unlockMinutes = prefs.getInt(K_MINUTES, 15),
    )

    private companion object {
        const val K_TOKEN = "qr_token"
        const val K_TAG = "tag_hash"
        const val K_FRIEND_NAME = "friend_name"
        const val K_FRIEND_PHONE = "friend_phone"
        const val K_SECRET = "friend_secret"
        const val K_PAIRED = "friend_paired"
        const val K_LAST_STEP = "last_step"
        const val K_FAILS = "failures"
        const val K_LOCKED = "locked_until"
        const val K_MINUTES = "unlock_minutes"
    }
}
