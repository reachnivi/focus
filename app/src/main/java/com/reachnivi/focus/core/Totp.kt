package com.reachnivi.focus.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 time-based one-time codes (HMAC-SHA1, 30-second steps, 6 digits), the same as Google
 * Authenticator. A friend's authenticator becomes the key to unlock.
 */
object Totp {
    const val STEP_SECONDS = 30L
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun stepAt(nowMs: Long): Long = nowMs / 1000 / STEP_SECONDS

    fun code(secret: ByteArray, step: Long, digits: Int = 6): String {
        val msg = ByteArray(8) { i -> (step ushr (8 * (7 - i))).toByte() }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(msg)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    /**
     * Checks [code] against the current step ± [window]. Returns the matching step, or null.
     * A step at or before [lastUsedStep] is rejected, so a code can't be used twice.
     */
    fun verify(secret: ByteArray, code: String, nowMs: Long, window: Int = 1, lastUsedStep: Long? = null): Long? {
        val clean = code.filter { it.isDigit() }
        if (clean.length != 6) return null
        val now = stepAt(nowMs)
        for (s in (now - window)..(now + window)) {
            if (lastUsedStep != null && s <= lastUsedStep) continue
            if (code(secret, s) == clean) return s
        }
        return null
    }

    fun base32Encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun base32Decode(text: String): ByteArray? {
        val clean = text.uppercase().filter { it != '=' && !it.isWhitespace() }
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.add(((buffer shr (bits - 8)) and 0xff).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    /** The link Google Authenticator (and others) import from a QR code. */
    fun otpauthUri(secret: ByteArray, account: String, issuer: String = "Focus"): String {
        val label = java.net.URLEncoder.encode("$issuer:$account", "UTF-8").replace("+", "%20")
        return "otpauth://totp/$label?secret=${base32Encode(secret)}&issuer=${java.net.URLEncoder.encode(issuer, "UTF-8")}&digits=6&period=30"
    }
}

/** Wrong-code throttling: after [MAX_FAILURES] misses, unlocking by code pauses for [LOCKOUT_MS]. */
data class GuardState(val failures: Int = 0, val lockedUntil: Long = 0)

object UnlockGuard {
    const val MAX_FAILURES = 5
    const val LOCKOUT_MS = 10 * 60 * 1000L

    fun canTry(state: GuardState, now: Long): Boolean = now >= state.lockedUntil

    fun onFailure(state: GuardState, now: Long): GuardState {
        val failures = state.failures + 1
        return if (failures >= MAX_FAILURES) GuardState(0, now + LOCKOUT_MS) else state.copy(failures = failures)
    }

    fun onSuccess(): GuardState = GuardState()
}

/** Personal unlock keys: a printable QR token and a registered NFC tag. */
object UnlockKey {
    private const val PREFIX = "focus-unlock:1:"

    fun qrText(token: String): String = PREFIX + token

    fun tokenFrom(scanned: String?): String? =
        scanned?.trim()?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { t -> t.length in 16..64 && t.all { it.isLetterOrDigit() } }

    /** Tags are stored only as a SHA-256 hash of their ID. */
    fun hashTag(id: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(id).joinToString("") { "%02x".format(it) }

    /** Constant-time comparison, so timing reveals nothing about the stored value. */
    fun matches(a: String?, b: String?): Boolean =
        a != null && b != null && java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
