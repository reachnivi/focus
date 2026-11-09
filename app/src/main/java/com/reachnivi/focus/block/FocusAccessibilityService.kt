package com.reachnivi.focus.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.FocusApp
import com.reachnivi.focus.core.BlockDecision
import com.reachnivi.focus.core.BlockPolicyEngine
import com.reachnivi.focus.core.DomainMatcher
import com.reachnivi.focus.core.PolicySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Watches which app is in front (and which site a browser shows) and covers blocked
 * ones with [BlockActivity]. All decisions are delegated to [BlockPolicyEngine].
 */
class FocusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var container: AppContainer? = null

    @Volatile private var policy = PolicySnapshot()
    private var foreground: String? = null
    private var imePackages: Set<String> = emptySet()
    private var browserJob: Job? = null

    private var lastShownKey: String? = null
    private var lastShownAt = 0L

    private var usageCache: Map<String, Long> = emptyMap()
    private var usageCacheAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val c = (application as FocusApp).container
        container = c
        isConnected = true
        c.healthNotifier.cancel()
        imePackages = loadImePackages()
        scope.launch {
            c.policy.snapshot.collect {
                policy = it
                recheckForeground()
            }
        }
        // Catch schedules starting, limits being reached or passes expiring while an app stays open.
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                recheckForeground()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val c = container ?: return
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg in imePackages || pkg == SYSTEM_UI) return
                foreground = pkg
                if (pkg in c.catalog.protectedPackages) return
                scope.launch { checkApp(pkg) }
                if (BrowserUrlReader.isBrowser(pkg)) scheduleBrowserCheck(pkg, delayMs = 250)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == foreground && BrowserUrlReader.isBrowser(pkg)) scheduleBrowserCheck(pkg, delayMs = 400)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun recheckForeground() {
        val c = container ?: return
        val pkg = foreground ?: return
        if (pkg in c.catalog.protectedPackages) return
        checkApp(pkg)
        if (BrowserUrlReader.isBrowser(pkg)) checkBrowser(pkg)
    }

    private suspend fun checkApp(pkg: String) {
        if (TemporaryPasses.isActive(TemporaryPasses.appKey(pkg))) return
        val p = policy
        val used = if (p.dailyLimitsMs.containsKey(pkg)) usedToday(pkg) else null
        val decision = BlockPolicyEngine.decideApp(p, pkg, System.currentTimeMillis(), ZoneId.systemDefault(), used)
        if (decision is BlockDecision.Block && foreground == pkg) {
            show(BlockActivity.Target.App(pkg), decision)
        }
    }

    /** Debounced: browsers fire content-changed events constantly while pages load. */
    private fun scheduleBrowserCheck(pkg: String, delayMs: Long) {
        if (browserJob?.isActive == true) return
        browserJob = scope.launch {
            delay(delayMs)
            checkBrowser(pkg)
        }
    }

    private fun checkBrowser(pkg: String) {
        val p = policy
        if (p.blockedDomains.isEmpty() || foreground != pkg) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val url = BrowserUrlReader.readUrl(root, pkg) ?: return
        val host = DomainMatcher.normalize(url) ?: return
        val matched = DomainMatcher.firstMatch(host, p.blockedDomains) ?: return
        if (TemporaryPasses.isActive(TemporaryPasses.siteKey(matched))) return
        val decision = BlockPolicyEngine.decideDomain(p, host, System.currentTimeMillis(), ZoneId.systemDefault())
        if (decision is BlockDecision.Block) {
            // Leave the page so it isn't sitting there when the block screen closes.
            performGlobalAction(GLOBAL_ACTION_BACK)
            show(BlockActivity.Target.Site(matched), decision)
        }
    }

    private suspend fun usedToday(pkg: String): Long {
        val c = container ?: return 0
        val now = System.currentTimeMillis()
        if (now - usageCacheAt > USAGE_CACHE_MS) {
            usageCache = runCatching { c.usage.day(LocalDate.now()).perAppMs }.getOrDefault(usageCache)
            usageCacheAt = now
        }
        return usageCache[pkg] ?: 0L
    }

    private fun show(target: BlockActivity.Target, decision: BlockDecision.Block) {
        val now = SystemClock.uptimeMillis()
        if (target.key == lastShownKey && now - lastShownAt < REPEAT_GUARD_MS) return
        lastShownKey = target.key
        lastShownAt = now
        startActivity(BlockActivity.intent(this, target, decision))
    }

    private fun loadImePackages(): Set<String> = runCatching {
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    companion object {
        /** True while Android has this service bound. Same process as the UI, so screens can read it. */
        @Volatile
        var isConnected: Boolean = false
            private set

        private const val SYSTEM_UI = "com.android.systemui"
        private const val TICK_MS = 15_000L
        private const val USAGE_CACHE_MS = 60_000L
        private const val REPEAT_GUARD_MS = 1_500L
    }
}
