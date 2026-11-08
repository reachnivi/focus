package com.reachnivi.focus.core

object DomainMatcher {

    /**
     * Turns user input or browser URL-bar text into a bare host, e.g.
     * "https://www.YouTube.com/watch?v=1" -> "youtube.com". Returns null for search
     * queries or anything that doesn't look like a host.
     */
    fun normalize(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':')
        s = s.removePrefix("www.").trimEnd('.')
        if (!s.contains('.') || s.startsWith('.') || s.contains("..")) return null
        if (!s.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return null
        val parts = s.split('.')
        if (parts.all { p -> p.isNotEmpty() && p.all { it.isDigit() } }) {
            return if (parts.size == 4) s else null // IPv4 literal
        }
        val tld = parts.last()
        if (tld.length < 2 || tld.all { it.isDigit() }) return null
        return s
    }

    /** True if [host] is [blocked] itself or any of its subdomains. */
    fun matches(host: String, blocked: String): Boolean =
        host == blocked || host.endsWith(".$blocked")

    fun firstMatch(host: String, blocked: Collection<String>): String? =
        blocked.firstOrNull { matches(host, it) }
}
