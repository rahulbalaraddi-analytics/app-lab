package com.applab.batterypilot

import com.applab.batterypilot.analysis.Analysis
import com.applab.batterypilot.collect.BatteryStatsReader
import com.applab.batterypilot.data.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AnalysisTest {
    private val tz = TimeZone.getTimeZone("Asia/Kolkata")
    private val H = 3_600_000L

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance(tz).apply {
        set(2026, Calendar.OCTOBER, 14, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun s(t: Long, level: Int, charging: Boolean = false, screen: Boolean = false) = BatterySample(
        time = t, level = level, charging = charging, plugged = if (charging) 2 else 0, tempC = 30f,
        voltageMv = 4000, currentUa = null, chargeCounterUah = null, screenOn = screen, powerSave = false, source = "test",
    )

    /** Her real day: 100% at 7:30 AM, ~8.3%/hr → 0% around 7:30 PM. */
    private fun heavyDay(untilHour: Int): List<BatterySample> {
        val out = mutableListOf(s(at(6), 100, charging = true))
        var t = at(7, 30)
        var lv = 100.0
        while (t <= at(untilHour)) {
            out += s(t, lv.toInt(), screen = (t / (15 * 60_000)) % 2 == 0L)
            t += 15 * 60_000
            lv -= 8.3 / 4
        }
        return out
    }

    @Test
    fun checkinParserReadsAppsAndParts() {
        val lines = listOf(
            "9,0,l,pws,4500,1234.5,1100,1300",
            "9,10234,l,pwi,uid,210.5,0,120.1,0",
            "9,0,l,pwi,scrn,400.0,0,0,0",
            "9,0,l,pwi,cell,150.25,0,0,0",
            "9,1000,l,pwi,uid,90.0,0,0,0",
            "9,0,u,pwi,scrn,10.0,0,0,0", // not 'since charged' → ignored
            "garbage line",
        )
        val raw = BatteryStatsReader.parseCheckin(lines)
        assertEquals(4, raw.size)
        assertEquals(10234, raw.first { it.label == "uid" && it.mah == 210.5 }.uid)
        assertTrue(raw.any { it.label == "scrn" && it.uid == null && it.mah == 400.0 })
    }

    @Test
    fun humanParserReadsUidLines() {
        val lines = listOf(
            "  Estimated power use (mAh):",
            "    Capacity: 4500, Computed drain: 1200, actual drain: 1100-1300",
            "    Screen: 400 Excluded from smearing",
            "    UID u0a234: 210.5 ( cpu=50.1 wifi=3.2 )",
            "    Uid 1000: 90.0 ( cpu=80 )",
        )
        val raw = BatteryStatsReader.parseHuman(lines)
        assertTrue(raw.any { it.uid == 10234 && it.mah == 210.5 })
        assertTrue(raw.any { it.uid == 1000 })
        assertTrue(raw.any { it.label == "screen" && it.mah == 400.0 })
    }

    @Test
    fun lastUnplugFindsMorning() {
        val samples = heavyDay(12)
        val u = Analysis.lastUnplug(samples)
        assertNotNull(u)
        assertEquals(at(7, 30), u!!.time)
        assertNull(Analysis.lastUnplug(samples + s(at(12, 30), 50, charging = true)))
    }

    @Test
    fun modelLearnsRateAndForecastMissesElevenPm() {
        val samples = heavyDay(16)
        val ivs = Analysis.intervals(samples, null)
        val model = Analysis.train(ivs, tz)
        // Learned average should be close to the true 8.3%/hr.
        assertEquals(8.3, model.avgRate, 1.0)
        val f = Analysis.forecast(samples, model, targetHour = 23, bufferPct = 10, now = at(16), tz = tz)!!
        assertTrue("should not make 11 PM", !f.makesIt)
        val empty = f.emptyAt!!
        // Real pace says ~7:30 PM; accept 6:00–9:30 PM.
        assertTrue(empty in at(18)..at(21, 30))
        assertTrue(f.allowedRate < 6.0)
    }

    @Test
    fun forecastMakesItOnALightDay() {
        val out = mutableListOf(s(at(6), 100, charging = true))
        var t = at(7, 30); var lv = 100.0
        while (t <= at(15)) { out += s(t, lv.toInt()); t += 15 * 60_000; lv -= 4.0 / 4 }
        val model = Analysis.train(Analysis.intervals(out, null), tz)
        val f = Analysis.forecast(out, model, 23, 10, now = at(15), tz = tz)!!
        assertTrue("light day should make it, got ${f.levelAtTarget}", f.makesIt)
    }

    @Test
    fun beforeAfterShowsImprovement() {
        val before = heavyDay(18)
        // Next day after fixes: 5.5%/hr.
        val next = mutableListOf(s(at(6) + 24 * H, 100, charging = true))
        var t = at(7, 30) + 24 * H; var lv = 100.0
        while (t <= at(18) + 24 * H) { next += s(t, lv.toInt()); t += 15 * 60_000; lv -= 5.5 / 4 }
        val all = Analysis.intervals(before + next, null)
        val ba = Analysis.beforeAfter(all, at(6) + 24 * H)
        val imp = ba.improvementPct!!
        assertTrue("improvement was $imp", imp in 20.0..45.0)
    }
}
