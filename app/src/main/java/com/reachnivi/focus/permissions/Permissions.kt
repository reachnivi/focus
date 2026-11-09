package com.reachnivi.focus.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.reachnivi.focus.block.FocusAccessibilityService
import com.reachnivi.focus.core.BlockerHealth
import com.reachnivi.focus.core.BlockerStatus

object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val ours = ComponentName(context, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun blockerStatus(context: Context): BlockerStatus =
        BlockerHealth.evaluate(isAccessibilityEnabled(context), FocusAccessibilityService.isConnected)

    /** True when Focus's minimal home screen is the default launcher. */
    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** System screen for choosing the default home app (works on realme/ColorOS too). */
    fun openHomeSettings(context: Context) =
        launch(context, Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openUsageAccess(context: Context) =
        launch(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), appDetails(context))

    fun openAccessibility(context: Context) =
        launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), appDetails(context))

    fun openAppDetails(context: Context) = launch(context, appDetails(context))

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) = launch(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(context),
    )

    /** Realme / OPPO (ColorOS) "Auto launch" screens; falls back to App info. */
    fun openAutoStart(context: Context) = launch(
        context,
        *AUTOSTART_COMPONENTS.map { Intent().setComponent(it) }.toTypedArray(),
        appDetails(context),
    )

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Starts the first intent that resolves and launches without error. */
    private fun launch(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // ActivityNotFoundException / SecurityException on some OEM builds: try the next one.
            }
        }
        return false
    }

    private val AUTOSTART_COMPONENTS = listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    )
}
