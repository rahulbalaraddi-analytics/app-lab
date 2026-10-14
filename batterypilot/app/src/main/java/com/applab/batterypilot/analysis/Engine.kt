package com.applab.batterypilot.analysis

import android.content.Context
import com.applab.batterypilot.collect.Access
import com.applab.batterypilot.collect.AccessState
import com.applab.batterypilot.collect.BatteryReader
import com.applab.batterypilot.collect.BatteryStatsReader
import com.applab.batterypilot.collect.DrainReport
import com.applab.batterypilot.collect.HealthInfo
import com.applab.batterypilot.collect.UsageReader
import com.applab.batterypilot.collect.UsageWindow
import com.applab.batterypilot.data.AppDatabase
import com.applab.batterypilot.data.BatterySample
import com.applab.batterypilot.data.FixLog
import com.applab.batterypilot.data.Prefs
import com.applab.batterypilot.fixes.Fix
import com.applab.batterypilot.fixes.Fixes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

/** One row in the Apps tab. */
data class AppRow(
    val packageName: String,
    val label: String,
    /** % of the battery used since the last full charge that went to this app. */
    val pctOfUsed: Double?,
    val mah: Double?,
    val foregroundMin: Int,
    /** True when most of its drain happened with the app not on screen. */
    val backgroundHeavy: Boolean,
    /** "exact" from Android's battery stats, "estimate" from screen time. */
    val source: String,
)

/** A part of the phone (screen, mobile network...) and its share. */
data class PartRow(val label: String, val pct: Double)

data class Snapshot(
    val now: Long,
    val latest: BatterySample?,
    val access: AccessState,
    val samples: List<BatterySample>,
    val model: DrainModel,
    val forecast: Analysis.Forecast?,
    val health: HealthInfo,
    val unplug: BatterySample?,
    val screenOnMsSinceUnplug: Long?,
    val wakeupsSinceUnplug: Int?,
    val parts: List<PartRow>,
    val apps: List<AppRow>,
    val fixes: List<Fix>,
    val days: List<Analysis.DaySummary>,
    val beforeAfter: Analysis.BeforeAfter?,
    val fixLog: List<FixLog>,
    val prefs: PrefsView,
)

data class PrefsView(
    val targetHour: Int,
    val bufferPct: Int,
    val alertsOn: Boolean,
    val smartSaver: Boolean,
    val saverBelowPct: Int,
)

/** Pulls everything together. All on the phone. */
object Engine {

    suspend fun recordNow(context: Context, source: String): BatterySample? = withContext(Dispatchers.IO) {
        val s = BatteryReader.read(context, source) ?: return@withContext null
        val dao = AppDatabase.get(context).samples()
        val last = dao.latest()
        // Avoid duplicates from rapid triggers (app open + worker at the same moment).
        if (last == null || s.time - last.time > 60_000 || last.level != s.level || last.charging != s.charging) {
            dao.insert(s)
        }
        dao.deleteBefore(System.currentTimeMillis() - 60 * DAY)
        s
    }

    /** Light version for the background worker: model + forecast only. */
    suspend fun quickForecast(context: Context): Pair<DrainModel, Analysis.Forecast?> = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val samples = AppDatabase.get(context).samples().since(now - 14 * DAY)
        val usage = UsageReader.read(context, now - 14 * DAY, now)
        val ivs = Analysis.intervals(samples, usage?.screenOn)
        val model = Analysis.train(ivs, screenHoursPerDay = usage?.let { screenHoursPerDay(it, samples, now) })
        model to Analysis.forecast(samples, model, prefs.targetHour, prefs.bufferPct, now)
    }

    suspend fun snapshot(context: Context, withReport: Boolean = true): Snapshot = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val access = Access.state(context)
        val samples = db.samples().since(now - 30 * DAY)
        val usage14 = UsageReader.read(context, now - 14 * DAY, now)
        val recentSamples = samples.filter { it.time >= now - 14 * DAY }
        val ivs = Analysis.intervals(recentSamples, usage14?.screenOn)
        val model = Analysis.train(ivs, screenHoursPerDay = usage14?.let { screenHoursPerDay(it, recentSamples, now) })
        val forecast = Analysis.forecast(samples, model, prefs.targetHour, prefs.bufferPct, now)
        val unplug = Analysis.lastUnplug(samples)

        val sinceUnplug = unplug?.time ?: (now - 12 * HOUR)
        val usageNow = usage14?.let { UsageReader.read(context, sinceUnplug, now) }
        val report: DrainReport? = if (withReport) BatteryStatsReader.read(context) else null

        // Parts of the phone.
        val parts: List<PartRow> = if (report != null && report.totalMah > 0) {
            val appsMah = report.apps.sumOf { it.mah }
            val rows = report.parts.map { PartRow(it.label, it.mah / report.totalMah * 100) }.toMutableList()
            rows += PartRow("Apps (all)", appsMah / report.totalMah * 100)
            rows.sortedByDescending { it.pct }.filter { it.pct >= 1.0 }
        } else if (unplug != null && usageNow != null) {
            // Without exact stats: split today's drop into screen-on vs screen-off using the model.
            val screenH = usageNow.screenOn.sumOf { it.ms }.toDouble() / HOUR
            val totalH = (now - unplug.time).toDouble() / HOUR
            val onDrop = screenH * model.screenOnRate
            val offDrop = (totalH - screenH).coerceAtLeast(0.0) * model.screenOffRate
            val sum = onDrop + offDrop
            if (sum > 0) listOf(
                PartRow("Screen on (screen + apps you use)", onDrop / sum * 100),
                PartRow("Screen off (background, network, idle)", offDrop / sum * 100),
            ) else emptyList()
        } else emptyList()

        // Apps.
        val fgByPkg = usageNow?.apps?.associate { it.packageName to it.foregroundMs }.orEmpty()
        val pm = context.packageManager
        val apps: List<AppRow> = if (report != null && report.totalMah > 0) {
            val usedMah = report.totalMah
            report.apps.map { item ->
                val pkg = item.packageName!!
                val fgMs = fgByPkg[pkg] ?: 0L
                val pct = item.mah / usedMah * 100
                // How much of the drop its on-screen time explains.
                val dropSinceUnplug = unplug?.let { (it.level - (samples.lastOrNull()?.level ?: it.level)).coerceAtLeast(1) } ?: 50
                val fgExplains = fgMs.toDouble() / HOUR * model.screenOnRate / dropSinceUnplug * 100 * 0.6
                AppRow(pkg, item.label, pct, item.mah, (fgMs / 60_000).toInt(),
                    backgroundHeavy = pct >= 3.0 && pct > fgExplains * 1.5 + 2.0,
                    source = "exact")
            }.filter { it.pctOfUsed!! >= 0.5 }
        } else {
            val totalFg = fgByPkg.values.sum().coerceAtLeast(1L)
            fgByPkg.entries.sortedByDescending { it.value }.take(25).map { (pkg, ms) ->
                AppRow(pkg, BatteryStatsReader.appLabel(pm, pkg), ms.toDouble() / totalFg * 100, null,
                    (ms / 60_000).toInt(), backgroundHeavy = false, source = "estimate")
            }
        }

        val topBg = apps.filter { it.backgroundHeavy }.map { it.packageName to (it.pctOfUsed ?: 0.0) }
        val fixes = Fixes.build(context, access, model, report, topBg)

        val log = db.fixes().all()
        val lastChange = log.firstOrNull { it.action == "applied" || it.action == "opened" }
        val allIvs = Analysis.intervals(samples, usage14?.screenOn)
        val ba = lastChange?.let { Analysis.beforeAfter(allIvs, it.time) }
        val days = Analysis.days(samples.filter { it.time >= now - 14 * DAY }, usage14?.screenOn)

        Snapshot(
            now = now,
            latest = samples.lastOrNull(),
            access = access,
            samples = samples.filter { it.time >= now - 36 * HOUR },
            model = model,
            forecast = forecast,
            health = BatteryReader.health(context),
            unplug = unplug,
            screenOnMsSinceUnplug = usageNow?.screenOn?.sumOf { it.ms },
            wakeupsSinceUnplug = usageNow?.wakeups,
            parts = parts,
            apps = apps,
            fixes = fixes,
            days = days,
            beforeAfter = ba,
            fixLog = log,
            prefs = PrefsView(prefs.targetHour, prefs.bufferPct, prefs.alertsOn, prefs.smartSaver, prefs.saverBelowPct),
        )
    }

    /** Typical screen-on hours per day, over the days we have readings for. */
    private fun screenHoursPerDay(u: UsageWindow, samples: List<BatterySample>, now: Long): Double {
        val first = samples.firstOrNull()?.time ?: now
        val days = ((now - first).toDouble() / DAY).coerceIn(1.0, 14.0)
        val from = now - (days * DAY).toLong()
        return u.screenOn.sumOf { it.overlap(from, now) }.toDouble() / HOUR / days
    }
}
