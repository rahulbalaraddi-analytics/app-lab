package com.applab.batterypilot.collect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** A stretch of time with the screen on. */
data class Interval(val start: Long, val end: Long) {
    val ms get() = (end - start).coerceAtLeast(0)
    fun overlap(a: Long, b: Long): Long = (minOf(end, b) - maxOf(start, a)).coerceAtLeast(0)
}

data class AppUsage(val packageName: String, val foregroundMs: Long)

data class UsageWindow(
    val screenOn: List<Interval>,
    val apps: List<AppUsage>,
    /** Times the screen was switched on. */
    val wakeups: Int,
)

/** Reads Android's own usage log (needs "Usage access"). Nothing is sent anywhere. */
object UsageReader {
    // UsageEvents.Event constants (named here for clarity; values are stable public API).
    private const val RESUMED = 1      // ACTIVITY_RESUMED
    private const val PAUSED = 2       // ACTIVITY_PAUSED
    private const val STOPPED = 23     // ACTIVITY_STOPPED
    private const val SCREEN_ON = 15   // SCREEN_INTERACTIVE
    private const val SCREEN_OFF = 16  // SCREEN_NON_INTERACTIVE

    fun read(context: Context, from: Long, to: Long = System.currentTimeMillis()): UsageWindow? {
        if (!Access.hasUsageAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        // Start a bit earlier so we know whether the screen was already on at `from`.
        val events = runCatching { usm.queryEvents(from - 6 * 3_600_000L, to) }.getOrNull() ?: return null
        val e = UsageEvents.Event()

        val screen = mutableListOf<Interval>()
        var screenStart: Long? = null
        var wakeups = 0
        val fgStart = HashMap<String, Long>()
        val fgTotal = HashMap<String, Long>()

        fun closeApp(pkg: String, t: Long) {
            val s = fgStart.remove(pkg) ?: return
            val a = maxOf(s, from)
            if (t > a) fgTotal[pkg] = (fgTotal[pkg] ?: 0L) + (t - a)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val t = e.timeStamp
            when (e.eventType) {
                SCREEN_ON -> {
                    if (screenStart == null) screenStart = t
                    if (t >= from) wakeups++
                }
                SCREEN_OFF -> {
                    screenStart?.let { s -> if (t > from) screen += Interval(maxOf(s, from), t) }
                    screenStart = null
                    // Screen off ends any foreground app time.
                    fgStart.keys.toList().forEach { closeApp(it, t) }
                }
                RESUMED -> {
                    val pkg = e.packageName ?: continue
                    // Only one app is in front at a time.
                    fgStart.keys.filter { it != pkg }.forEach { closeApp(it, t) }
                    if (!fgStart.containsKey(pkg)) fgStart[pkg] = t
                }
                PAUSED, STOPPED -> e.packageName?.let { closeApp(it, t) }
            }
        }
        screenStart?.let { s -> screen += Interval(maxOf(s, from), to) }
        fgStart.keys.toList().forEach { closeApp(it, to) }

        val apps = fgTotal.filter { it.value > 30_000 && it.key != context.packageName }
            .map { AppUsage(it.key, it.value) }
            .sortedByDescending { it.foregroundMs }
        return UsageWindow(screen, apps, wakeups)
    }

    fun screenOnMs(intervals: List<Interval>, a: Long, b: Long): Long =
        intervals.sumOf { it.overlap(a, b) }
}
