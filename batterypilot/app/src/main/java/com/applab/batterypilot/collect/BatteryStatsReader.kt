package com.applab.batterypilot.collect

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

/** One line of "where the battery went": an app, or a part of the phone (screen, mobile signal...). */
data class DrainItem(
    val key: String,
    val label: String,
    val mah: Double,
    /** Package for apps, null for phone parts. */
    val packageName: String?,
    val uid: Int?,
)

/** What an app did since the last full charge, split by foreground / background. */
data class AppActivity(
    /** On screen, in front of you (Android "top" state), ms. */
    val topMs: Long = 0,
    /** Foreground service: music, navigation, calls, uploads — visible notification, ms. */
    val fgServiceMs: Long = 0,
    /** Other foreground state (e.g. bound to a visible app), ms. */
    val foregroundMs: Long = 0,
    /** Running in the background, ms. */
    val backgroundMs: Long = 0,
    /** Kept the phone awake with the screen off (partial wakelocks), ms. */
    val wakelockMs: Long = 0,
    val wakelockCount: Int = 0,
    /** Background jobs run by the system for this app. */
    val jobCount: Int = 0,
    val jobMs: Long = 0,
    val mobileBytes: Long = 0,
    val wifiBytes: Long = 0,
    val cpuMs: Long = 0,
) {
    val hiddenMs get() = backgroundMs + fgServiceMs
}

/** Battery split by process state, mAh (Android 13+; null if the phone doesn't report it). */
data class PowerSplit(val fg: Double, val bg: Double, val fgs: Double, val cached: Double) {
    val background get() = bg + fgs + cached
}

data class DrainReport(
    /** Sum of all items, mAh, since the last full charge. */
    val totalMah: Double,
    val items: List<DrainItem>,
    val activity: Map<Int, AppActivity> = emptyMap(),
    val split: Map<Int, PowerSplit> = emptyMap(),
) {
    val apps get() = items.filter { it.packageName != null }
    val parts get() = items.filter { it.packageName == null }
}

/** Raw line from Android's battery stats: (uid or null, label, mAh). */
data class RawPower(val uid: Int?, val label: String, val mah: Double, val split: PowerSplit? = null)

/**
 * Exact per-app battery use, the same numbers as the phone's own Battery screen,
 * plus foreground/background activity per app.
 * Needs the DUMP permission from the one-time USB setup; returns null without it.
 */
object BatteryStatsReader {

    @Volatile private var cache: Pair<Long, DrainReport>? = null

    suspend fun read(context: Context, maxAgeMs: Long = 120_000): DrainReport? = withContext(Dispatchers.IO) {
        if (!Access.canDump(context)) return@withContext null
        cache?.let { (t, r) -> if (System.currentTimeMillis() - t < maxAgeMs) return@withContext r }

        val checkin = run(listOf("dumpsys", "batterystats", "--charged", "--checkin"))
            ?.takeIf { lines -> parseCheckin(lines).isNotEmpty() }
            ?: run(listOf("dumpsys", "batterystats", "--checkin"))
        // Human-readable dump: fallback for totals, and the only place with fg/bg mAh split.
        val human = run(listOf("dumpsys", "batterystats", "--charged"))

        val rawCheckin = checkin?.let { parseCheckin(it) }.orEmpty()
        val rawHuman = human?.let { parseHuman(it) }.orEmpty()
        val raw = rawCheckin.ifEmpty { rawHuman }
        if (raw.isEmpty()) return@withContext null

        val activity = checkin?.let { parseActivity(it) }.orEmpty()
        val split = rawHuman.filter { it.uid != null && it.split != null }
            .groupBy { it.uid!! }
            .mapValues { (_, l) ->
                PowerSplit(l.sumOf { it.split!!.fg }, l.sumOf { it.split!!.bg }, l.sumOf { it.split!!.fgs }, l.sumOf { it.split!!.cached })
            }
        val report = buildReport(context, raw).copy(activity = activity, split = split)
        cache = System.currentTimeMillis() to report
        report
    }

    private val keepTypes = setOf("pwi", "pws", "st", "wl", "jb", "nt", "cpu")

    private suspend fun run(cmd: List<String>): List<String>? = withTimeoutOrNull(25_000) {
        runCatching {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val keep = ArrayList<String>()
            BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                var inPowerSection = false
                while (true) {
                    val line = r.readLine() ?: break
                    // Keep only what we parse: the output can be several MB of history.
                    if (line.length > 6 && line[0].isDigit() && line.contains(",l,")) {
                        val f = line.split(',', limit = 5)
                        if (f.size >= 4 && f[2] == "l" && f[3] in keepTypes) keep += line
                    } else if (line.contains("Estimated power use")) {
                        inPowerSection = true
                        keep += line
                    } else if (inPowerSection) {
                        if (line.isBlank()) inPowerSection = false else keep += line
                    }
                }
            }
            p.waitFor()
            keep
        }.getOrNull()
    }

    /**
     * Checkin format, e.g.
     *   9,10234,l,pwi,uid,123.4,0,10.2,0
     *   9,0,l,pwi,scrn,456.7,...
     */
    fun parseCheckin(lines: List<String>): List<RawPower> {
        val out = ArrayList<RawPower>()
        for (line in lines) {
            val f = line.split(',')
            val i = f.indexOf("pwi")
            if (i < 2 || i + 2 >= f.size) continue
            // Prefer "since charged" (l) rows; other sections repeat the same items.
            if (f.getOrNull(i - 1) != "l") continue
            val label = f[i + 1]
            val mah = f[i + 2].toDoubleOrNull() ?: continue
            if (mah <= 0.0) continue
            val uid = f[1].toIntOrNull()
            out += if (label == "uid") RawPower(uid, label, mah) else RawPower(null, label, mah)
        }
        return out
    }

    /**
     * Per-app activity rows (checkin, "since charged"):
     *   st  : top, fgService, foreground, background, topSleeping, heavy, cached (ms)
     *   wl  : name, fullMs, f, count, partialMs, p, count, ...
     *   jb  : name, totalMs, count
     *   nt  : mobileRx, mobileTx, wifiRx, wifiTx, ... (bytes)
     *   cpu : userMs, systemMs, ...
     */
    fun parseActivity(lines: List<String>): Map<Int, AppActivity> {
        val m = HashMap<Int, AppActivity>()
        for (line in lines) {
            val f = line.split(',')
            if (f.size < 5 || f[2] != "l") continue
            val uid = f[1].toIntOrNull() ?: continue
            if (uid % 100_000 < 10_000) continue
            val d = f.subList(4, f.size)
            fun L(i: Int) = d.getOrNull(i)?.toLongOrNull() ?: 0L
            val a = m[uid] ?: AppActivity()
            m[uid] = when (f[3]) {
                "st" -> a.copy(topMs = a.topMs + L(0), fgServiceMs = a.fgServiceMs + L(1),
                    foregroundMs = a.foregroundMs + L(2), backgroundMs = a.backgroundMs + L(3))
                "wl" -> {
                    val pi = d.indexOf("p")
                    if (pi >= 1) a.copy(
                        wakelockMs = a.wakelockMs + (d[pi - 1].toLongOrNull() ?: 0L),
                        wakelockCount = a.wakelockCount + (d.getOrNull(pi + 1)?.toIntOrNull() ?: 0),
                    ) else a
                }
                "jb" -> a.copy(jobMs = a.jobMs + L(1), jobCount = a.jobCount + L(2).toInt())
                "nt" -> a.copy(mobileBytes = a.mobileBytes + L(0) + L(1), wifiBytes = a.wifiBytes + L(2) + L(3))
                "cpu" -> a.copy(cpuMs = a.cpuMs + L(0) + L(1))
                else -> a
            }
        }
        return m
    }

    private val uidLine = Regex("""^\s+(?:UID|Uid)\s+(\S+):\s+([\d.]+)""")
    private val partLine = Regex("""^\s+([A-Za-z][A-Za-z /]+?):\s+([\d.]+)""")
    private fun splitValue(line: String, key: String): Double? =
        Regex("""\b$key:\s*([\d.]+)""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()

    /** Human-readable "Estimated power use (mAh):" section (fallback + fg/bg split). */
    fun parseHuman(lines: List<String>): List<RawPower> {
        val out = ArrayList<RawPower>()
        for (line in lines) {
            uidLine.find(line)?.let { m ->
                val uid = parseUid(m.groupValues[1])
                val mah = m.groupValues[2].toDoubleOrNull()
                // Only look for fg/bg before the "( cpu=... )" details.
                val head = line.substringBefore("(")
                val fg = splitValue(head, "fg")
                val bg = splitValue(head, "bg")
                val split = if (fg != null || bg != null) PowerSplit(
                    fg ?: 0.0, bg ?: 0.0, splitValue(head, "fgs") ?: 0.0, splitValue(head, "cached") ?: 0.0,
                ) else null
                if (uid != null && mah != null && mah > 0) out += RawPower(uid, "uid", mah, split)
                return@let
            } ?: partLine.find(line)?.let { m ->
                val name = m.groupValues[1].trim()
                if (name.startsWith("Capacity") || name.startsWith("Computed") || name.contains("drain")) return@let
                val mah = m.groupValues[2].toDoubleOrNull()
                if (mah != null && mah > 0) out += RawPower(null, name.lowercase(), mah)
            }
        }
        return out
    }

    /** "u0a123" -> 10123, "1000" -> 1000. */
    private fun parseUid(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        val m = Regex("""u(\d+)a(\d+)""").find(s) ?: return null
        return m.groupValues[1].toInt() * 100_000 + 10_000 + m.groupValues[2].toInt()
    }

    private val partNames = mapOf(
        "scrn" to "Screen", "screen" to "Screen",
        "cell" to "Mobile network", "phone" to "Calls", "wifi" to "Wi-Fi",
        "blue" to "Bluetooth", "bluetooth" to "Bluetooth",
        "idle" to "Phone idle", "gnss" to "GPS / location", "gps" to "GPS / location",
        "cpu" to "Processor (system)", "camera" to "Camera", "flashlight" to "Flashlight",
        "video" to "Video", "audio" to "Audio", "sensors" to "Sensors",
        "ambi" to "Always On Display", "ambient display" to "Always On Display",
        "mem" to "Memory", "unacc" to "Unaccounted", "over" to "Over-counted",
        "user" to "Other users", "wakelock" to "Wakelocks",
    )

    private fun buildReport(context: Context, raw: List<RawPower>): DrainReport {
        val pm = context.packageManager
        val byKey = LinkedHashMap<String, DrainItem>()
        for (r in raw) {
            if (r.label == "over" || r.label == "over-counted") continue
            val item = if (r.uid != null) {
                val appId = r.uid % 100_000
                if (appId < 10_000) {
                    DrainItem("system", "Android system", r.mah, null, r.uid)
                } else {
                    val pkgs = pm.getPackagesForUid(r.uid)?.toList().orEmpty()
                    val pkg = pkgs.firstOrNull()
                    val label = pkg?.let { appLabel(pm, it) } ?: "App ${r.uid}"
                    DrainItem("uid:${r.uid}", label, r.mah, pkg ?: "uid.${r.uid}", r.uid)
                }
            } else {
                DrainItem("part:${r.label}", partNames[r.label] ?: r.label.replaceFirstChar { it.uppercase() }, r.mah, null, null)
            }
            val prev = byKey[item.key]
            byKey[item.key] = if (prev == null) item else prev.copy(mah = prev.mah + item.mah)
        }
        val items = byKey.values.filter { it.mah >= 0.5 }.sortedByDescending { it.mah }
        return DrainReport(items.sumOf { it.mah }, items)
    }

    fun appLabel(pm: PackageManager, pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
