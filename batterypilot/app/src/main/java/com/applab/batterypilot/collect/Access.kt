package com.applab.batterypilot.collect

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/** What the app is allowed to read / change. Checked live, never cached. */
data class AccessState(
    /** Screen-on time + per-app usage time. */
    val usage: Boolean,
    /** Brightness, screen timeout, haptics. */
    val writeSettings: Boolean,
    /** Exact per-app battery (from the one-time USB setup). */
    val batteryStats: Boolean,
    /** Power saving, refresh rate, scanning (from the one-time USB setup). */
    val secureSettings: Boolean,
    val notifications: Boolean,
) {
    val usbSetupDone get() = batteryStats && secureSettings
    val allDone get() = usage && writeSettings && usbSetupDone && notifications
}

object Access {
    const val PKG = "com.applab.batterypilot"

    /** Commands for the one-time USB setup (run from a laptop with adb). */
    val ADB_COMMANDS = listOf(
        "adb shell pm grant $PKG android.permission.DUMP",
        "adb shell pm grant $PKG android.permission.BATTERY_STATS",
        "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS",
        "adb shell appops set $PKG GET_USAGE_STATS allow",
    )

    fun state(context: Context): AccessState = AccessState(
        usage = hasUsageAccess(context),
        writeSettings = Settings.System.canWrite(context),
        batteryStats = granted(context, Manifest.permission.DUMP) ||
            granted(context, Manifest.permission.BATTERY_STATS),
        secureSettings = granted(context, Manifest.permission.WRITE_SECURE_SETTINGS),
        notifications = Build.VERSION.SDK_INT < 33 ||
            granted(context, Manifest.permission.POST_NOTIFICATIONS),
    )

    fun canDump(context: Context) = granted(context, Manifest.permission.DUMP)

    private fun granted(context: Context, perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(context: Context) =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun writeSettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
}
