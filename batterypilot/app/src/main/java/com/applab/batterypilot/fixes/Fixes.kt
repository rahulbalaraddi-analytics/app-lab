package com.applab.batterypilot.fixes

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import com.applab.batterypilot.analysis.DrainModel
import com.applab.batterypilot.collect.AccessState
import com.applab.batterypilot.collect.DrainReport
import com.applab.batterypilot.data.Prefs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

enum class Mode {
    /** The app changes the setting itself (and can undo it). */
    AUTO,
    /** The app opens the exact settings screen; you flip the switch. */
    GUIDED,
}

data class Fix(
    val id: String,
    val title: String,
    val why: String,
    /** True = already done, false = not done, null = can't tell. */
    val done: Boolean?,
    val mode: Mode,
    /** Estimated % of battery saved per day. */
    val savedPctPerDay: Double,
    /** Extra minutes of battery per day, from [savedPctPerDay] at your usual pace. */
    val extraMinutes: Int,
    /** What's needed for AUTO, if missing (we fall back to GUIDED). */
    val needs: String? = null,
    val packageName: String? = null,
)

data class ApplyResult(val ok: Boolean, val message: String, val openedSettings: Boolean = false)

/**
 * The tick-box fixes. Each one knows: is it already done, how much it would likely save
 * (from your own drain model), and how to apply / undo it.
 */
object Fixes {

    // Samsung One UI keys (best effort; detection falls back to "can't tell").
    private const val SAMSUNG_REFRESH = "refresh_rate_mode" // Secure: 0 = Standard 60 Hz
    private const val SAMSUNG_AOD = "aod_mode"              // System: 1 = on

    fun build(
        context: Context,
        access: AccessState,
        model: DrainModel,
        report: DrainReport?,
        topBackgroundApps: List<Pair<String, Double>>,
    ): List<Fix> {
        val cr = context.contentResolver
        val S = model.screenHoursPerDay
        val screenDrainPerDay = model.screenOnRate * S
        val offHours = max(0.0, 16.0 - S)
        fun minutes(pct: Double) = (pct / max(model.avgRate, 0.5) * 60).roundToInt()
        val list = ArrayList<Fix>()

        // 1. Adaptive brightness
        val autoBright = runCatching {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrNull()
        val sBright = screenDrainPerDay * 0.45 * 0.18
        list += Fix(
            "adaptive_brightness", "Turn on Adaptive brightness",
            "The screen is the biggest drain. Auto brightness keeps it no brighter than needed.",
            autoBright, if (access.writeSettings) Mode.AUTO else Mode.GUIDED, sBright, minutes(sBright),
            needs = if (access.writeSettings) null else "Modify system settings",
        )

        // 2. Screen timeout 30 s
        val timeout = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT) }.getOrNull()
        val sTimeout = if (timeout != null && timeout > 30_000)
            screenDrainPerDay * (0.025 * log2(timeout / 30_000.0)).coerceAtMost(0.1) else 0.0
        list += Fix(
            "screen_timeout", "Screen timeout → 30 seconds",
            "Now ${timeout?.let { fmtTimeout(it) } ?: "unknown"}. The screen stays lit after every glance until it times out.",
            timeout?.let { it <= 30_000 }, if (access.writeSettings) Mode.AUTO else Mode.GUIDED, sTimeout, minutes(sTimeout),
            needs = if (access.writeSettings) null else "Modify system settings",
        )

        // 3. Refresh rate 60 Hz
        val hz = runCatching {
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).refreshRate
        }.getOrNull()
        val maxHz = runCatching {
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                .supportedModes.maxOf { it.refreshRate }
        }.getOrNull() ?: 60f
        val samsungRefresh = readSecureInt(context, SAMSUNG_REFRESH)
        if (maxHz > 61f) {
            val isStandard = samsungRefresh?.let { it == 0 } ?: hz?.let { it < 61f }
            val s = screenDrainPerDay * 0.10
            list += Fix(
                "refresh_60", "Motion smoothness → Standard (60 Hz)",
                "Your screen can run at ${maxHz.roundToInt()} Hz. 60 Hz uses noticeably less power and still looks smooth.",
                isStandard, if (access.secureSettings) Mode.AUTO else Mode.GUIDED, s, minutes(s),
                needs = if (access.secureSettings) null else "USB setup",
            )
        }

        // 4. Dark mode
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val sDark = screenDrainPerDay * 0.45 * 0.2
        list += Fix(
            "dark_mode", "Turn on Dark mode",
            "Samsung AMOLED screens use almost no power for black pixels.",
            dark, Mode.GUIDED, sDark, minutes(sDark),
        )

        // 5. Always On Display
        val aod = runCatching { Settings.System.getInt(cr, SAMSUNG_AOD) }.getOrNull()
        if (aod != null) {
            val sAod = if (aod == 1) 0.6 * offHours else 0.0
            list += Fix(
                "aod_off", "Turn off Always On Display",
                "AOD keeps part of the screen lit all day while the phone sits on a desk (~0.5–1%/hr).",
                aod == 0, Mode.GUIDED, sAod, minutes(sAod),
            )
        }

        // 6. Smart saver (our own): Power saving only when you won't make it
        val prefs = Prefs(context)
        val sSmart = model.avgRate * 2 * 0.25 // ~25% less drain over the last ~2 hrs of the day
        list += Fix(
            "smart_saver", "Smart saver: Power saving only when needed",
            "BatteryPilot switches Power saving on by itself when the forecast says you won't reach ${hourLabel(prefs.targetHour)}.",
            prefs.smartSaver, if (access.secureSettings) Mode.AUTO else Mode.GUIDED, sSmart, minutes(sSmart),
            needs = if (access.secureSettings) null else "USB setup",
        )

        // 7. Power saving now
        val ps = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode
        val sPs = model.avgRate * 6 * 0.2
        list += Fix(
            "power_saving", "Power saving mode (now)",
            "Limits background work, 5G and screen. Biggest single switch, slight trade-offs.",
            ps, if (access.secureSettings) Mode.AUTO else Mode.GUIDED, sPs, minutes(sPs),
            needs = if (access.secureSettings) null else "USB setup",
        )

        // 8. Wi-Fi / Bluetooth scanning (location accuracy) off
        val wifiScan = readGlobalInt(context, "wifi_scan_always_enabled")
        val bleScan = readGlobalInt(context, "ble_scan_always_enabled")
        val scanningOn = if (wifiScan == null && bleScan == null) null else (wifiScan == 1 || bleScan == 1)
        val sScan = if (scanningOn != false) 0.08 * offHours else 0.0
        list += Fix(
            "scanning_off", "Turn off Wi-Fi & Bluetooth scanning",
            "Even with Wi-Fi/Bluetooth off, the phone keeps scanning for location. Rarely needed.",
            scanningOn?.not(), if (access.secureSettings) Mode.AUTO else Mode.GUIDED, sScan, minutes(sScan),
            needs = if (access.secureSettings) null else "USB setup",
        )

        // 9. Location
        val loc = runCatching { context.getSystemService(LocationManager::class.java).isLocationEnabled }.getOrNull()
        val gps = report?.parts?.firstOrNull { it.key == "part:gnss" }
        val sLoc = if (loc == true) (gps?.let { pctOf(it.mah, report) } ?: 1.0) else 0.0
        list += Fix(
            "location_off", "Location off when not navigating",
            "Apps can wake GPS in the background. Turn it on only for Maps / cab apps.",
            loc?.not(), Mode.GUIDED, sLoc, minutes(sLoc),
        )

        // 10. Bluetooth
        val bt = readGlobalInt(context, "bluetooth_on")
        if (bt != null) {
            val sBt = if (bt != 0) 0.05 * 16 else 0.0
            list += Fix(
                "bluetooth_off", "Bluetooth off when no earbuds/watch",
                "Small but constant drain while searching for devices.",
                bt == 0, Mode.GUIDED, sBt, minutes(sBt),
            )
        }

        // 11. 5G → 4G
        val cell = report?.parts?.firstOrNull { it.key == "part:cell" }
        val sCell = cell?.let { pctOf(it.mah, report) * 0.3 } ?: 1.5
        list += Fix(
            "prefer_4g", "Network mode → LTE/4G instead of 5G",
            cell?.let { "Mobile network used ${pctOf(it.mah, report).roundToInt()}% of your battery since the last charge. 5G with weak signal is a big drain." }
                ?: "5G with a weak signal (common indoors in Bengaluru) drains much faster than 4G.",
            null, Mode.GUIDED, sCell, minutes(sCell),
        )

        // 12. Haptics
        val haptic = runCatching { Settings.System.getInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED) }.getOrNull()
        list += Fix(
            "haptics_off", "Turn off touch vibration",
            "Tiny saving, but free if you don't care for it.",
            haptic?.let { it == 0 }, if (access.writeSettings) Mode.AUTO else Mode.GUIDED, 0.5, minutes(0.5),
            needs = if (access.writeSettings) null else "Modify system settings",
        )

        // 13+. Background-heavy apps → put to sleep
        topBackgroundApps.take(4).forEach { (pkg, pct) ->
            val label = com.applab.batterypilot.collect.BatteryStatsReader.appLabel(context.packageManager, pkg)
            val s = pct * 0.7
            list += Fix(
                "sleep:$pkg", "Put $label to sleep",
                "Used ${pct.roundToInt()}% of your battery mostly in the background. Set its battery to “Restricted” / add to Sleeping apps.",
                null, Mode.GUIDED, s, minutes(s), packageName = pkg,
            )
        }

        return list.sortedWith(compareBy<Fix> { it.done == true }.thenByDescending { it.savedPctPerDay })
    }

    private fun pctOf(mah: Double, report: DrainReport?) = if (report != null && report.totalMah > 0) mah / report.totalMah * 100 else 0.0

    // ---------- apply / undo ----------

    fun apply(context: Context, fix: Fix): ApplyResult {
        val cr = context.contentResolver
        val prefs = Prefs(context)
        return try {
            if (fix.mode == Mode.AUTO) when (fix.id) {
                "adaptive_brightness" -> {
                    prefs.savePrevious(fix.id, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0).toString())
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    return ApplyResult(true, "Adaptive brightness is on")
                }
                "screen_timeout" -> {
                    prefs.savePrevious(fix.id, Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30_000).toString())
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
                    return ApplyResult(true, "Screen timeout set to 30 seconds")
                }
                "haptics_off" -> {
                    prefs.savePrevious(fix.id, Settings.System.getInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1).toString())
                    Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
                    return ApplyResult(true, "Touch vibration is off")
                }
                "refresh_60" -> {
                    prefs.savePrevious(fix.id, (readSecureInt(context, SAMSUNG_REFRESH) ?: 1).toString())
                    Settings.Secure.putInt(cr, SAMSUNG_REFRESH, 0)
                    // Stock Android fallback keys (harmless on Samsung).
                    runCatching { Settings.System.putFloat(cr, "peak_refresh_rate", 60f) }
                    runCatching { Settings.System.putFloat(cr, "min_refresh_rate", 60f) }
                    return ApplyResult(true, "Motion smoothness set to Standard (60 Hz)")
                }
                "smart_saver" -> {
                    prefs.smartSaver = true
                    return ApplyResult(true, "Smart saver is on — checks every ~15 min")
                }
                "power_saving" -> {
                    return if (setPowerSaving(context, true)) ApplyResult(true, "Power saving is on")
                    else openGuided(context, fix)
                }
                "scanning_off" -> {
                    prefs.savePrevious(fix.id, "${readGlobalInt(context, "wifi_scan_always_enabled") ?: 1},${readGlobalInt(context, "ble_scan_always_enabled") ?: 1}")
                    Settings.Global.putInt(cr, "wifi_scan_always_enabled", 0)
                    Settings.Global.putInt(cr, "ble_scan_always_enabled", 0)
                    return ApplyResult(true, "Wi-Fi & Bluetooth scanning are off")
                }
            }
            openGuided(context, fix)
        } catch (e: SecurityException) {
            val r = openGuided(context, fix)
            r.copy(message = "Couldn't change it directly (${e.message?.take(60)}). " + r.message)
        }
    }

    fun canUndo(context: Context, fix: Fix): Boolean =
        fix.id == "smart_saver" && Prefs(context).smartSaver ||
            fix.id == "power_saving" && fix.done == true ||
            Prefs(context).previous(fix.id) != null

    fun undo(context: Context, fix: Fix): ApplyResult {
        val cr = context.contentResolver
        val prefs = Prefs(context)
        return try {
            when (fix.id) {
                "smart_saver" -> { prefs.smartSaver = false; return ApplyResult(true, "Smart saver is off") }
                "power_saving" -> {
                    return if (setPowerSaving(context, false)) ApplyResult(true, "Power saving is off")
                    else openGuided(context, fix)
                }
            }
            val prev = prefs.previous(fix.id) ?: return ApplyResult(false, "Nothing to undo")
            when (fix.id) {
                "adaptive_brightness" -> Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, prev.toInt())
                "screen_timeout" -> Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, prev.toInt())
                "haptics_off" -> Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, prev.toInt())
                "refresh_60" -> {
                    Settings.Secure.putInt(cr, SAMSUNG_REFRESH, prev.toInt())
                    runCatching { Settings.System.putFloat(cr, "peak_refresh_rate", 120f) }
                    runCatching { Settings.System.putFloat(cr, "min_refresh_rate", 0f) }
                }
                "scanning_off" -> {
                    val (w, b) = prev.split(',').map { it.toInt() }
                    Settings.Global.putInt(cr, "wifi_scan_always_enabled", w)
                    Settings.Global.putInt(cr, "ble_scan_always_enabled", b)
                }
            }
            prefs.clearPrevious(fix.id)
            ApplyResult(true, "Undone")
        } catch (e: Exception) {
            ApplyResult(false, "Couldn't undo: ${e.message?.take(60)}")
        }
    }

    /** Needs WRITE_SECURE_SETTINGS (USB setup). Same switch as the Power saving tile. */
    fun setPowerSaving(context: Context, on: Boolean): Boolean = runCatching {
        Settings.Global.putInt(context.contentResolver, "low_power", if (on) 1 else 0)
        Thread.sleep(400)
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == on
    }.getOrDefault(false)

    // ---------- guided: open the right screen ----------

    fun openGuided(context: Context, fix: Fix): ApplyResult {
        val pkg = context.packageName
        val candidates: List<Intent> = when {
            fix.packageName != null -> listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${fix.packageName}")),
            )
            else -> when (fix.id) {
                "adaptive_brightness", "screen_timeout", "dark_mode", "refresh_60" -> listOf(
                    Intent(Settings.ACTION_DISPLAY_SETTINGS),
                )
                "haptics_off" -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))
                "aod_off" -> listOf(
                    Intent().setComponent(ComponentName("com.samsung.android.app.aodservice", "com.samsung.android.app.aod.settings.AODSettingsActivity")),
                    Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$LockscreenSettingsActivity")),
                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                )
                "power_saving", "smart_saver" -> listOf(
                    Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                )
                "scanning_off", "location_off" -> listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                "bluetooth_off" -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "prefer_4g" -> listOf(
                    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS),
                )
                else -> listOf(Intent(Settings.ACTION_SETTINGS))
            }
        } + Intent(Settings.ACTION_SETTINGS)

        for (i in candidates) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ApplyResult(true, guidedHint(fix), openedSettings = true)
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
        return ApplyResult(false, "Couldn't open settings on this phone")
    }

    private fun guidedHint(fix: Fix): String = when (fix.id) {
        "adaptive_brightness" -> "Turn on Adaptive brightness"
        "screen_timeout" -> "Screen timeout → 30 seconds"
        "dark_mode" -> "Tap Dark"
        "refresh_60" -> "Motion smoothness → Standard"
        "aod_off" -> "Turn Always On Display off"
        "power_saving", "smart_saver" -> "Battery → Power saving"
        "scanning_off" -> "Location services → turn off Wi-Fi and Bluetooth scanning"
        "location_off" -> "Turn Location off"
        "bluetooth_off" -> "Turn Bluetooth off"
        "prefer_4g" -> "Mobile networks → Network mode → LTE/3G/2G"
        "haptics_off" -> "Vibration → turn off touch/keyboard vibration"
        else -> if (fix.packageName != null) "Battery → Restricted (or Optimised)" else "Change it here"
    }

    // ---------- helpers ----------

    fun readSecureInt(context: Context, key: String): Int? = runCatching {
        Settings.Secure.getString(context.contentResolver, key)?.toIntOrNull()
    }.getOrNull()

    fun readGlobalInt(context: Context, key: String): Int? = runCatching {
        Settings.Global.getString(context.contentResolver, key)?.toIntOrNull()
    }.getOrNull()

    fun fmtTimeout(ms: Int): String = when {
        ms >= 60 * 60_000 -> "${ms / 3_600_000} hr"
        ms >= 60_000 -> "${ms / 60_000} min"
        else -> "${ms / 1000} sec"
    }

    fun hourLabel(h: Int): String = when {
        h == 0 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }
}
