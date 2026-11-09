package com.reachnivi.focus.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class AppGroup(val label: String, val distracting: Boolean) {
    SOCIAL("Social", true),
    VIDEO("Video", true),
    GAME("Games", true),
    NEWS("News", true),
    OTHER("Other", false),
}

data class InstalledApp(val packageName: String, val label: String, val group: AppGroup)

/** Lists launchable apps and resolves labels and icons, with caching. */
class AppCatalog(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = LruCache<String, ImageBitmap>(80)

    /** Packages that must never be blocked or listed as blockable. */
    val protectedPackages: Set<String> by lazy {
        buildSet {
            add(context.packageName)
            add("com.android.systemui")
            add("com.android.settings")
            add("com.android.dialer")
            add("com.google.android.dialer")
            add("com.android.phone")
            add("com.android.emergency")
            addAll(homePackages())
        }
    }

    fun homePackages(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()

    /** Apps the user may block: everything launchable except [protectedPackages]. */
    suspend fun launchableApps(): List<InstalledApp> = queryLaunchable(includeProtected = false)

    /** Every launchable app, including Phone, Settings and Focus itself (for the home screen). */
    suspend fun allLaunchableApps(): List<InstalledApp> = queryLaunchable(includeProtected = true)

    private suspend fun queryLaunchable(includeProtected: Boolean): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { includeProtected || it.packageName !in protectedPackages }
            .map { info ->
                val label = info.loadLabel(pm).toString()
                labels[info.packageName] = label
                InstalledApp(info.packageName, label, groupOf(info))
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Opens an app's main screen. Returns false if it can't be launched (e.g. uninstalled). */
    fun launch(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isInstalled(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    fun label(packageName: String): String = labels.getOrPut(packageName) {
        try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    suspend fun icon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                pm.getApplicationIcon(packageName).toBitmap(width = 96, height = 96).asImageBitmap()
                    .also { icons.put(packageName, it) }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    private fun groupOf(info: ApplicationInfo): AppGroup = when (info.category) {
        ApplicationInfo.CATEGORY_SOCIAL -> AppGroup.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO -> AppGroup.VIDEO
        ApplicationInfo.CATEGORY_GAME -> AppGroup.GAME
        ApplicationInfo.CATEGORY_NEWS -> AppGroup.NEWS
        else -> if (info.packageName in KNOWN_SOCIAL) AppGroup.SOCIAL else AppGroup.OTHER
    }

    private companion object {
        /** Popular apps that don't declare a category. */
        val KNOWN_SOCIAL = setOf(
            "com.instagram.android", "com.facebook.katana", "com.twitter.android",
            "com.snapchat.android", "com.zhiliaoapp.musically", "com.reddit.frontpage",
            "com.pinterest", "com.linkedin.android", "com.whatsapp", "org.telegram.messenger",
            "in.mohalla.sharechat", "com.threads.android", "com.instagram.barcelona",
        )
    }
}
