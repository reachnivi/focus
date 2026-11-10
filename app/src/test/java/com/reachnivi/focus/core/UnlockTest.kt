package com.reachnivi.focus.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTest {
    // RFC 6238 appendix B, SHA-1 secret "12345678901234567890"; 6-digit codes are the last 6 digits.
    private val rfcSecret = "12345678901234567890".toByteArray()

    @Test
    fun `totp matches RFC 6238 vectors`() {
        assertEquals("94287082", Totp.code(rfcSecret, Totp.stepAt(59_000), digits = 8))
        assertEquals("287082", Totp.code(rfcSecret, Totp.stepAt(59_000)))
        assertEquals("081804", Totp.code(rfcSecret, Totp.stepAt(1_111_111_109_000)))
        assertEquals("050471", Totp.code(rfcSecret, Totp.stepAt(1_111_111_111_000)))
        assertEquals("005924", Totp.code(rfcSecret, Totp.stepAt(1_234_567_890_000)))
        assertEquals("279037", Totp.code(rfcSecret, Totp.stepAt(2_000_000_000_000)))
    }

    @Test
    fun `verify allows one step of drift and rejects reuse`() {
        val now = 1_234_567_890_000L
        val step = Totp.stepAt(now)
        assertEquals(step, Totp.verify(rfcSecret, "005924", now))
        val previous = Totp.code(rfcSecret, step - 1)
        assertEquals(step - 1, Totp.verify(rfcSecret, previous, now))
        assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 3), now))
        assertNull(Totp.verify(rfcSecret, "005924", now, lastUsedStep = step))
        assertNull(Totp.verify(rfcSecret, "12345", now))
    }

    @Test
    fun `base32 round trip and otpauth link`() {
        val bytes = ByteArray(20) { (it * 13).toByte() }
        assertArrayEquals(bytes, Totp.base32Decode(Totp.base32Encode(bytes)))
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32Encode(rfcSecret))
        assertNull(Totp.base32Decode("not base32!"))
        assertTrue(Totp.otpauthUri(rfcSecret, "Sam").startsWith("otpauth://totp/Focus%3ASam?secret=GEZDG"))
    }

    @Test
    fun `five wrong codes lock for ten minutes`() {
        var g = GuardState()
        repeat(4) { g = UnlockGuard.onFailure(g, 0) }
        assertTrue(UnlockGuard.canTry(g, 0))
        g = UnlockGuard.onFailure(g, 1_000)
        assertFalse(UnlockGuard.canTry(g, 1_000))
        assertTrue(UnlockGuard.canTry(g, 1_000 + UnlockGuard.LOCKOUT_MS))
    }

    @Test
    fun `qr tokens and tag hashes`() {
        val token = "a1b2c3d4e5f6a7b8c9d0"
        assertEquals(token, UnlockKey.tokenFrom(UnlockKey.qrText(token)))
        assertNull(UnlockKey.tokenFrom("focus-stack:1:abc:30:x"))
        assertNull(UnlockKey.tokenFrom("focus-unlock:1:short"))
        val h = UnlockKey.hashTag(byteArrayOf(1, 2, 3, 4))
        assertEquals(64, h.length)
        assertTrue(UnlockKey.matches(h, UnlockKey.hashTag(byteArrayOf(1, 2, 3, 4))))
        assertFalse(UnlockKey.matches(h, UnlockKey.hashTag(byteArrayOf(1, 2, 3, 5))))
        assertFalse(UnlockKey.matches(h, null))
    }
}
