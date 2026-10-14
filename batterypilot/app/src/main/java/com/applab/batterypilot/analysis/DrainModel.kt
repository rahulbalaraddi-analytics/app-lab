package com.applab.batterypilot.analysis

import com.applab.batterypilot.collect.Interval
import com.applab.batterypilot.data.BatterySample
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val HOUR = 3_600_000L
private const val MIN = 60_000L

/** A stretch between two readings while on battery. */
data class DrainInterval(
    val start: Long,
    val end: Long,
    val fromLevel: Int,
    val toLevel: Int,
    /** 0..1 share of the time the screen was on. */
    val screenFrac: Double,
) {
    val hours get() = (end - start).toDouble() / HOUR
    /** % per hour (positive = draining). */
    val rate get() = (fromLevel - toLevel) / hours
}

/**
 * On-device learning model of how this phone drains.
 *  - [hourly]: typical %/hr for each hour of the day (learned from your own history)
 *  - [screenOffRate] / [screenOnRate]: from a weighted linear regression rate = a + b·screenShare
 * Starts from sensible defaults and moves towards your real numbers as data comes in.
 */
data class DrainModel(
    val hourly: DoubleArray,
    val screenOffRate: Double,
    val screenOnRate: Double,
    /** Hours of on-battery data the model learned from. */
    val trainedHours: Double,
    /** Typical screen-on hours per day while on battery. */
    val screenHoursPerDay: Double,
    /** Average %/hr while on battery. */
    val avgRate: Double,
    /** Typical spread of the hourly rate (for the forecast range). */
    val rateStd: Double,
) {
    val confidence: String get() = when {
        trainedHours >= 40 -> "High"
        trainedHours >= 12 -> "Medium"
        else -> "Learning"
    }

    companion object {
        /** Defaults for a typical phone before we've seen any of your data. */
        fun prior(): DrainModel = DrainModel(
            hourly = DoubleArray(24) { h -> if (h in 7..22) 6.0 else 1.5 },
            screenOffRate = 1.2,
            screenOnRate = 13.0,
            trainedHours = 0.0,
            screenHoursPerDay = 4.0,
            avgRate = 6.0,
            rateStd = 2.0,
        )
    }
}

object Analysis {

    // ---------- intervals ----------

    /** Turns readings into on-battery intervals. Skips gaps (phone off / app killed) and charging. */
    fun intervals(samples: List<BatterySample>, screen: List<Interval>?): List<DrainInterval> {
        val out = ArrayList<DrainInterval>()
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.charging || b.charging) continue
            val dt = b.time - a.time
            if (dt < 5 * MIN || dt > 3 * HOUR) continue
            if (b.level > a.level) continue // charged in between without us seeing it
            val frac = if (screen != null) {
                screen.sumOf { it.overlap(a.time, b.time) }.toDouble() / dt
            } else {
                ((if (a.screenOn) 1.0 else 0.0) + (if (b.screenOn) 1.0 else 0.0)) / 2
            }
            out += DrainInterval(a.time, b.time, a.level, b.level, frac.coerceIn(0.0, 1.0))
        }
        return out
    }

    /** Time and level when the phone was last taken off the charger (or first reading on battery). */
    fun lastUnplug(samples: List<BatterySample>): BatterySample? {
        if (samples.isEmpty()) return null
        if (samples.last().charging) return null
        var idx = samples.lastIndex
        while (idx > 0 && !samples[idx - 1].charging) idx--
        return samples[idx]
    }

    // ---------- learning ----------

    fun train(
        intervals: List<DrainInterval>,
        tz: TimeZone = TimeZone.getDefault(),
        screenHoursPerDay: Double? = null,
    ): DrainModel {
        val prior = DrainModel.prior()
        if (intervals.isEmpty()) return prior.copy(screenHoursPerDay = screenHoursPerDay ?: prior.screenHoursPerDay)

        // Hour-of-day profile: spread each interval over the clock hours it covers.
        val sumW = DoubleArray(24)
        val sumR = DoubleArray(24)
        val cal = Calendar.getInstance(tz)
        for (iv in intervals) {
            var t = iv.start
            while (t < iv.end) {
                cal.timeInMillis = t
                val h = cal.get(Calendar.HOUR_OF_DAY)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val next = min(cal.timeInMillis + HOUR, iv.end)
                val w = (next - t).toDouble() / HOUR
                sumW[h] += w
                sumR[h] += w * iv.rate
                t = next
            }
        }
        val priorWeight = 1.0 // hours of "pretend" data per hour slot
        val hourly = DoubleArray(24) { h ->
            ((sumR[h] + priorWeight * prior.hourly[h]) / (sumW[h] + priorWeight)).coerceIn(0.0, 40.0)
        }

        // Regression: rate = a + b * screenShare (weighted by hours, with gentle pull to defaults).
        var sw = 0.0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        fun add(x: Double, y: Double, w: Double) {
            sw += w; sx += w * x; sy += w * y; sxx += w * x * x; sxy += w * x * y
        }
        intervals.forEach { add(it.screenFrac, it.rate, it.hours) }
        add(0.0, prior.screenOffRate, 1.0)
        add(1.0, prior.screenOnRate, 1.0)
        val det = sw * sxx - sx * sx
        var b = if (abs(det) > 1e-9) (sw * sxy - sx * sy) / det else prior.screenOnRate - prior.screenOffRate
        var a = (sy - b * sx) / sw
        a = a.coerceIn(0.1, 10.0)
        b = b.coerceIn(1.0, 60.0)

        val hours = intervals.sumOf { it.hours }
        val avg = intervals.sumOf { it.rate * it.hours } / hours
        val variance = intervals.sumOf { it.hours * (it.rate - avg) * (it.rate - avg) } / hours
        val days = max(1.0, hours / 14.0) // ~14 on-battery hours per day
        val screenHrs = screenHoursPerDay
            ?: (intervals.sumOf { it.screenFrac * it.hours } / days)

        return DrainModel(
            hourly = hourly,
            screenOffRate = a,
            screenOnRate = a + b,
            trainedHours = hours,
            screenHoursPerDay = screenHrs.coerceIn(0.0, 16.0),
            avgRate = avg.coerceIn(0.1, 40.0),
            // Hour-scale spread is much smaller than 15-min noise; scale it down.
            rateStd = (sqrt(variance) / 2).coerceIn(0.5, 6.0),
        )
    }

    // ---------- forecast ----------

    data class Forecast(
        val now: Long,
        val level: Int,
        val target: Long,
        /** Expected % at the target time (can be below 0 → empty earlier). */
        val levelAtTarget: Double,
        val lowAtTarget: Double,
        val highAtTarget: Double,
        /** When we expect 0%, null if it lasts past the target. */
        val emptyAt: Long?,
        /** Average %/hr allowed from now to keep [bufferPct] at the target. */
        val allowedRate: Double,
        /** %/hr over the last ~90 min (null if not enough data). */
        val recentRate: Double?,
        /** %/hr since unplugging. */
        val sinceUnplugRate: Double?,
        /** How heavy today is versus your usual (1.0 = typical). */
        val todayFactor: Double,
        val path: List<Pair<Long, Double>>,
    ) {
        val makesIt get() = levelAtTarget >= 0
    }

    fun targetTime(now: Long, targetHour: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, targetHour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // After the target (e.g. 1 AM) → aim for tonight's target only if it's still ahead.
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun forecast(
        samples: List<BatterySample>,
        model: DrainModel,
        targetHour: Int,
        bufferPct: Int,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
    ): Forecast? {
        val last = samples.lastOrNull() ?: return null
        val level = last.level
        val target = targetTime(now, targetHour, tz)
        val cal = Calendar.getInstance(tz)

        // Recent pace: last ~90 minutes on battery.
        val recent = samples.filter { it.time >= now - 90 * MIN && !it.charging }
        val recentRate = if (recent.size >= 2 && recent.last().time - recent.first().time >= 30 * MIN) {
            val h = (recent.last().time - recent.first().time).toDouble() / HOUR
            ((recent.first().level - recent.last().level) / h).coerceAtLeast(0.0)
        } else null

        // Today vs usual: actual drop since unplug divided by what the hourly profile expected.
        val unplug = lastUnplug(samples)
        var todayFactor = 1.0
        var sinceUnplugRate: Double? = null
        if (unplug != null && now - unplug.time >= 45 * MIN) {
            val actual = (unplug.level - level).toDouble()
            sinceUnplugRate = actual / ((now - unplug.time).toDouble() / HOUR)
            val expected = expectedDrop(model, unplug.time, now, cal)
            if (expected > 1.0 && now - unplug.time >= 2 * HOUR) {
                // Shrink towards 1.0 when we have little evidence.
                val raw = actual / expected
                val w = min(1.0, (now - unplug.time).toDouble() / (6 * HOUR))
                todayFactor = (1.0 + w * (raw - 1.0)).coerceIn(0.6, 1.8)
            }
        }

        // Walk forward in 5-minute steps.
        val step = 5 * MIN
        val path = ArrayList<Pair<Long, Double>>()
        var lv = level.toDouble()
        var lo = lv
        var hi = lv
        var t = now
        var emptyAt: Long? = null
        path += t to lv
        val stepsPerHour = HOUR.toDouble() / step
        while (t < target) {
            cal.timeInMillis = t
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val base = model.hourly[h] * todayFactor
            val elapsedH = (t - now).toDouble() / HOUR
            val rate = if (recentRate != null) {
                val w = exp(-elapsedH / 1.5) * 0.6
                w * recentRate + (1 - w) * base
            } else base
            lv -= rate / stepsPerHour
            lo -= (rate + model.rateStd) / stepsPerHour
            hi -= max(0.0, rate - model.rateStd) / stepsPerHour
            t += step
            if (emptyAt == null && lv <= 0) emptyAt = t
            if ((t - now) % (15 * MIN) == 0L || t >= target) path += min(t, target) to max(lv, 0.0)
        }
        val hoursLeft = max((target - now).toDouble() / HOUR, 0.1)
        return Forecast(
            now = now, level = level, target = target,
            levelAtTarget = lv, lowAtTarget = lo, highAtTarget = hi,
            emptyAt = emptyAt,
            allowedRate = ((level - bufferPct) / hoursLeft).coerceAtLeast(0.0),
            recentRate = recentRate,
            sinceUnplugRate = sinceUnplugRate,
            todayFactor = todayFactor,
            path = path,
        )
    }

    private fun expectedDrop(model: DrainModel, from: Long, to: Long, cal: Calendar): Double {
        var t = from
        var drop = 0.0
        while (t < to) {
            cal.timeInMillis = t
            val next = min(t + 5 * MIN, to)
            drop += model.hourly[cal.get(Calendar.HOUR_OF_DAY)] * (next - t).toDouble() / HOUR
            t = next
        }
        return drop
    }

    // ---------- before / after ----------

    data class Side(
        val hours: Double,
        val avgRate: Double,
        val screenOnRate: Double,
        val screenOffRate: Double,
    ) {
        /** Hours a full battery would last at this pace. */
        val fullDayHours get() = if (avgRate > 0) 100 / avgRate else 0.0
    }

    data class BeforeAfter(val changeTime: Long, val before: Side?, val after: Side?) {
        /** % less drain per hour after the change (positive = better). Uses screen-adjusted rates. */
        val improvementPct: Double?
            get() {
                val b = before ?: return null
                val a = after ?: return null
                if (a.hours < 3 || b.hours < 3) return null
                return (b.avgRate - a.avgRate) / b.avgRate * 100
            }
    }

    fun side(intervals: List<DrainInterval>): Side? {
        if (intervals.isEmpty()) return null
        val m = train(intervals)
        val hours = intervals.sumOf { it.hours }
        return Side(hours, m.avgRate, m.screenOnRate, m.screenOffRate)
    }

    fun beforeAfter(all: List<DrainInterval>, changeTime: Long, lookbackDays: Int = 7): BeforeAfter {
        val before = all.filter { it.end <= changeTime && it.start >= changeTime - lookbackDays * 24 * HOUR }
        val after = all.filter { it.start >= changeTime }
        return BeforeAfter(changeTime, side(before), side(after))
    }

    // ---------- day summaries ----------

    data class DaySummary(
        val dayStart: Long,
        val unplugTime: Long?,
        val unplugLevel: Int?,
        val levelAt7pm: Int?,
        val lowest: Int,
        val lowestTime: Long,
        /** When it first fell to 20% or below (null = never). */
        val hit20At: Long?,
        val avgRate: Double?,
        val screenHours: Double?,
    )

    fun days(
        samples: List<BatterySample>,
        screen: List<Interval>?,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<DaySummary> {
        if (samples.isEmpty()) return emptyList()
        val cal = Calendar.getInstance(tz)
        val groups = samples.groupBy {
            cal.timeInMillis = it.time
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        val all = intervals(samples, screen)
        return groups.toSortedMap(compareByDescending<Long> { it }).map { (day, list) ->
            val dayEnd = day + 24 * HOUR
            val firstUnplug = list.zipWithNext().firstOrNull { (a, b) -> a.charging && !b.charging }?.second
                ?: list.firstOrNull { !it.charging && it == list.first() }
            val seven = day + 19 * HOUR
            val at7 = list.filter { abs(it.time - seven) <= 45 * MIN }.minByOrNull { abs(it.time - seven) }?.level
            val low = list.minBy { it.level }
            val dayIv = all.filter { it.start >= day && it.end <= dayEnd }
            val h = dayIv.sumOf { it.hours }
            val screenHrs = screen?.let { s -> s.sumOf { it.overlap(day, dayEnd) }.toDouble() / HOUR }
            DaySummary(
                dayStart = day,
                unplugTime = firstUnplug?.time,
                unplugLevel = firstUnplug?.level,
                levelAt7pm = at7,
                lowest = low.level,
                lowestTime = low.time,
                hit20At = list.firstOrNull { !it.charging && it.level <= 20 }?.time,
                avgRate = if (h >= 1) dayIv.sumOf { it.rate * it.hours } / h else null,
                screenHours = screenHrs,
            )
        }
    }
}
