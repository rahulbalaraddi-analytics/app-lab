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

data class DrainReport(
    /** Sum of all items, mAh, since the last full charge. */
    val totalMah: Double,
    val items: List<DrainItem>,
) {
    val apps get() = items.filter { it.packageName != null }
    val parts get() = items.filter { it.packageName == null }
}

/** Raw line from Android's battery stats: (uid or null, label, mAh). */
data class RawPower(val uid: Int?, val label: String, val mah: Double)

/**
 * Exact per-app battery use, the same numbers as the phone's own Battery screen.
 * Needs the DUMP permission from the one-time USB setup; returns null without it.
 */
object BatteryStatsReader {

    suspend fun read(context: Context): DrainReport? = withContext(Dispatchers.IO) {
        if (!Access.canDump(context)) return@withContext null
        val raw = run(listOf("dumpsys", "batterystats", "--charged", "--checkin"))
            ?.let { parseCheckin(it) }?.takeIf { it.isNotEmpty() }
            ?: run(listOf("dumpsys", "batterystats", "--checkin"))
                ?.let { parseCheckin(it) }?.takeIf { it.isNotEmpty() }
            ?: run(listOf("dumpsys", "batterystats", "--charged"))
                ?.let { parseHuman(it) }?.takeIf { it.isNotEmpty() }
            ?: return@withContext null
        buildReport(context, raw)
    }

    private suspend fun run(cmd: List<String>): List<String>? = withTimeoutOrNull(25_000) {
        runCatching {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val keep = ArrayList<String>()
            BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                var inPowerSection = false
                while (true) {
                    val line = r.readLine() ?: break
                    // Keep only what we parse: the output can be several MB of history.
                    if (line.contains(",pwi,") || line.contains(",pws,")) {
                        keep += line
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

    private val uidLine = Regex("""^\s+(?:UID|Uid)\s+(\S+):\s+([\d.]+)""")
    private val partLine = Regex("""^\s+([A-Za-z][A-Za-z /]+?):\s+([\d.]+)""")

    /** Human-readable "Estimated power use (mAh):" section (fallback). */
    fun parseHuman(lines: List<String>): List<RawPower> {
        val out = ArrayList<RawPower>()
        for (line in lines) {
            uidLine.find(line)?.let { m ->
                val uid = parseUid(m.groupValues[1])
                val mah = m.groupValues[2].toDoubleOrNull()
                if (uid != null && mah != null && mah > 0) out += RawPower(uid, "uid", mah)
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
