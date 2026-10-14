package com.applab.batterypilot.data

import android.content.Context
import android.content.SharedPreferences

/** Small user settings. Everything stays on the phone. */
class Prefs(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("batterypilot", Context.MODE_PRIVATE)

    /** Hour (0–23) the battery must last until. Default 11 PM. */
    var targetHour: Int
        get() = p.getInt("targetHour", 23)
        set(v) = p.edit().putInt("targetHour", v.coerceIn(12, 23)).apply()

    /** Percent to keep in hand at the target hour. */
    var bufferPct: Int
        get() = p.getInt("bufferPct", 10)
        set(v) = p.edit().putInt("bufferPct", v.coerceIn(0, 30)).apply()

    var alertsOn: Boolean
        get() = p.getBoolean("alertsOn", true)
        set(v) = p.edit().putBoolean("alertsOn", v).apply()

    /** Smart saver: turn on Power saving by itself when the forecast misses the target. Needs USB setup. */
    var smartSaver: Boolean
        get() = p.getBoolean("smartSaver", false)
        set(v) = p.edit().putBoolean("smartSaver", v).apply()

    /** Also switch Power saving on below this level (0 = off). Needs USB setup. */
    var saverBelowPct: Int
        get() = p.getInt("saverBelowPct", 0)
        set(v) = p.edit().putInt("saverBelowPct", v.coerceIn(0, 50)).apply()

    /** Day (yyyyDDD) we last sent the "won't make it" alert, to send at most one per day. */
    var lastAlertDay: Int
        get() = p.getInt("lastAlertDay", 0)
        set(v) = p.edit().putInt("lastAlertDay", v).apply()

    /** Day we last switched Power saving on automatically. */
    var lastAutoSaverDay: Int
        get() = p.getInt("lastAutoSaverDay", 0)
        set(v) = p.edit().putInt("lastAutoSaverDay", v).apply()

    /** Previous value of a setting before a fix changed it, so it can be undone. */
    fun savePrevious(key: String, value: String) = p.edit().putString("prev_$key", value).apply()
    fun previous(key: String): String? = p.getString("prev_$key", null)
    fun clearPrevious(key: String) = p.edit().remove("prev_$key").apply()

    var setupSeen: Boolean
        get() = p.getBoolean("setupSeen", false)
        set(v) = p.edit().putBoolean("setupSeen", v).apply()
}
