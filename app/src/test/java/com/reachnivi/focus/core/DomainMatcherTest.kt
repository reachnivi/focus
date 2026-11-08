package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {
    @Test
    fun `normalizes urls and url bar text`() {
        assertEquals("youtube.com", DomainMatcher.normalize("https://www.YouTube.com/watch?v=1"))
        assertEquals("youtube.com", DomainMatcher.normalize("youtube.com/shorts/abc"))
        assertEquals("m.facebook.com", DomainMatcher.normalize("m.facebook.com"))
        assertEquals("reddit.com", DomainMatcher.normalize("http://user@reddit.com:443/r/x"))
        assertEquals("192.168.1.1", DomainMatcher.normalize("192.168.1.1"))
    }

    @Test
    fun `rejects search queries and junk`() {
        assertNull(DomainMatcher.normalize("how to focus"))
        assertNull(DomainMatcher.normalize("youtube"))
        assertNull(DomainMatcher.normalize(""))
        assertNull(DomainMatcher.normalize("1.5"))
        assertNull(DomainMatcher.normalize("a..b.com"))
    }

    @Test
    fun `matches subdomains but not lookalikes`() {
        assertTrue(DomainMatcher.matches("youtube.com", "youtube.com"))
        assertTrue(DomainMatcher.matches("m.youtube.com", "youtube.com"))
        assertFalse(DomainMatcher.matches("notyoutube.com", "youtube.com"))
        assertFalse(DomainMatcher.matches("youtube.com.evil.io", "youtube.com"))
    }
}
