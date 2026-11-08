package com.reachnivi.focus.block

import android.view.accessibility.AccessibilityNodeInfo
import com.reachnivi.focus.core.DomainMatcher

/** Reads the address bar of common browsers through the accessibility tree. */
object BrowserUrlReader {

    private val urlBarIds: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.heytap.browser" to listOf("url_bar", "search_box_text", "title_bar_url"),
        "com.coloros.browser" to listOf("url_bar"),
        "com.mi.globalbrowser" to listOf("url"),
        "com.UCMobile.intl" to listOf("titlebar_text"),
    )

    fun isBrowser(packageName: String): Boolean = packageName in urlBarIds

    /** The URL currently shown, or null if unavailable or the user is typing in the bar. */
    fun readUrl(root: AccessibilityNodeInfo, packageName: String): String? {
        val ids = urlBarIds[packageName] ?: return null
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/$id")
            for (node in nodes) {
                if (node.isFocused) return null
                val text = node.text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        return scanForUrl(root)
    }

    /** Fallback for browsers whose ids we don't know: first unfocused text that parses as a host. */
    private fun scanForUrl(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val cls = node.className?.toString().orEmpty()
            if (cls.endsWith("EditText") && !node.isFocused) {
                val text = node.text?.toString()
                if (text != null && DomainMatcher.normalize(text) != null) return text
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private const val MAX_NODES = 250
}
