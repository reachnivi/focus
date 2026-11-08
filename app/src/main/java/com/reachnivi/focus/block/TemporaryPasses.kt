package com.reachnivi.focus.block

import java.util.concurrent.ConcurrentHashMap

/** Short, user-granted exceptions ("5 more minutes"). In memory only, on purpose. */
object TemporaryPasses {
    const val PASS_MINUTES = 5
    private val passes = ConcurrentHashMap<String, Long>()

    fun grant(key: String, minutes: Int = PASS_MINUTES) {
        passes[key] = System.currentTimeMillis() + minutes * 60_000L
    }

    fun isActive(key: String): Boolean {
        val until = passes[key] ?: return false
        if (System.currentTimeMillis() < until) return true
        passes.remove(key)
        return false
    }

    fun appKey(packageName: String) = "app:$packageName"
    fun siteKey(domain: String) = "site:$domain"
}
