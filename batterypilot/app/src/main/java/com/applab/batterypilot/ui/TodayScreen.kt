package com.applab.batterypilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.fixes.Fixes
import com.applab.batterypilot.ui.theme.Status
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun TodayScreen(snap: Snapshot, padding: PaddingValues, openFixes: () -> Unit, openSetup: () -> Unit) {
    val f = snap.forecast
    val latest = snap.latest
    val target = Fixes.hourLabel(snap.prefs.targetHour)
    val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val dayOver = hourNow >= snap.prefs.targetHour || hourNow < 5
    val charging = latest?.charging == true

    val statusColor = when {
        f == null || charging || dayOver -> MaterialTheme.colorScheme.primary
        f.levelAtTarget >= snap.prefs.bufferPct -> Status.Good
        f.levelAtTarget >= 0 -> Status.Warn
        else -> Status.Bad
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!snap.access.allDone) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Finish setup for full analysis", fontWeight = FontWeight.SemiBold)
                    Text(
                        buildList {
                            if (!snap.access.usage) add("• Usage access → screen time + per-app use")
                            if (!snap.access.writeSettings) add("• Modify system settings → one-tap fixes")
                            if (!snap.access.usbSetupDone) add("• One-time USB setup → exact per-app battery")
                            if (!snap.access.notifications) add("• Notifications → 11 PM heads-up")
                        }.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Gap(8)
                    Button(onClick = openSetup) { Text("Open setup") }
                }
            }
        }

        // ---- Hero: now + forecast ----
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${latest?.level ?: "—"}%", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    HGap(16)
                    Column(Modifier.weight(1f)) {
                        val headline = when {
                            latest == null -> "Taking the first reading…"
                            charging -> "Charging"
                            dayOver -> "Day done — charge overnight"
                            f == null -> "Learning your pattern…"
                            f.emptyAt != null -> "0% around ${time(f.emptyAt)}"
                            else -> "≈ ${f.levelAtTarget.roundToInt()}% left at $target"
                        }
                        Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = statusColor)
                        if (f != null && !charging && !dayOver) {
                            val verdict = when {
                                f.emptyAt != null -> "${duration(f.target - f.emptyAt)} short of $target"
                                f.levelAtTarget < snap.prefs.bufferPct -> "Makes it, but under your ${snap.prefs.bufferPct}% buffer"
                                else -> "On track for $target ✓"
                            }
                            Text(verdict, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Range ${f.lowAtTarget.coerceAtLeast(0.0).roundToInt()}–${f.highAtTarget.coerceAtLeast(0.0).roundToInt()}% · model: ${snap.model.confidence}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (f != null && !charging && !dayOver) {
                    Gap()
                    StatRow {
                        val pace = f.recentRate ?: f.sinceUnplugRate
                        Stat("Draining now", rate(pace), "last 90 min",
                            color = if (pace != null && pace > f.allowedRate) Status.Bad else Status.Good)
                        Stat("Allowed till $target", rate(f.allowedRate), "keeps ${snap.prefs.bufferPct}%")
                    }
                    if (f.todayFactor > 1.15 || f.todayFactor < 0.85) {
                        Gap(6)
                        Text(
                            if (f.todayFactor > 1) "Today is ${((f.todayFactor - 1) * 100).roundToInt()}% heavier than your usual day."
                            else "Today is ${((1 - f.todayFactor) * 100).roundToInt()}% lighter than your usual day.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // ---- Chart ----
        item {
            SectionCard("Today") {
                BatteryChart(snap.samples, f, snap.prefs.bufferPct, MaterialTheme.colorScheme.primary, statusColor)
                ChartLegend(MaterialTheme.colorScheme.primary, statusColor)
                if (snap.samples.size < 4) {
                    Gap(6)
                    Text("Readings are taken every ~15 min. The chart and forecast fill in over the next few hours.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- Key numbers ----
        item {
            SectionCard("Key numbers") {
                val u = snap.unplug
                StatRow {
                    Stat("Off charger", u?.let { time(it.time) } ?: "—", u?.let { "at ${it.level}%" })
                    Stat("Used since", u?.let { "${it.level - (latest?.level ?: it.level)}%" } ?: "—",
                        u?.let { duration(snap.now - it.time) })
                }
                Gap(8)
                StatRow {
                    Stat("Screen on", snap.screenOnMsSinceUnplug?.let { duration(it) } ?: "—",
                        snap.wakeupsSinceUnplug?.let { "$it unlocks/wakes" } ?: "needs usage access")
                    Stat("Temperature", latest?.let { "%.0f°C".format(it.tempC) } ?: "—",
                        latest?.let { if (it.tempC >= 40) "hot — drains faster" else "normal" },
                        color = if ((latest?.tempC ?: 0f) >= 40f) Status.Bad else Color.Unspecified)
                }
                Gap(8)
                StatRow {
                    Stat("Screen-on drain", rate(snap.model.screenOnRate), "learned")
                    Stat("Screen-off drain", rate(snap.model.screenOffRate), "learned",
                        color = if (snap.model.screenOffRate > 2.0) Status.Warn else Color.Unspecified)
                }
                Gap(8)
                val hl = snap.health
                StatRow {
                    Stat("Battery health", hl.healthPct?.let { "$it%" } ?: hl.healthLabel,
                        listOfNotNull(hl.estFullMah?.let { "≈$it mAh" }, hl.designMah?.let { "of $it" }).joinToString(" ").ifBlank { null })
                    Stat("Power saving", if (latest?.powerSave == true) "On" else "Off",
                        hl.cycleCount?.let { "$it charge cycles" })
                }
                if (snap.model.screenOffRate > 2.0) {
                    Gap(8)
                    Text("⚠ Screen-off drain above 2%/hr means apps or network are busy in your pocket. See Apps tab.",
                        style = MaterialTheme.typography.bodySmall, color = Status.Warn)
                }
                if ((hl.healthPct ?: 100) < 80) {
                    Gap(8)
                    Text("⚠ Battery has under 80% of its original capacity. Settings help, but a battery replacement is the real fix.",
                        style = MaterialTheme.typography.bodySmall, color = Status.Bad)
                }
            }
        }

        // ---- Where it's going ----
        if (snap.parts.isNotEmpty()) item {
            SectionCard("Where it's going") {
                snap.parts.take(7).forEach { ShareBar(it.label, it.pct) }
                Gap(4)
                Text(
                    if (snap.access.usbSetupDone) "Exact, since last full charge (same source as Settings → Battery)."
                    else "Estimate. The one-time USB setup gives exact numbers per part and app.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Top fixes ----
        val top = snap.fixes.filter { it.done != true }.take(3)
        if (top.isNotEmpty()) item {
            SectionCard("Biggest wins") {
                top.forEach { fx ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fx.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Chip("+${minutes(fx.extraMinutes)}", Status.Good)
                    }
                }
                Gap(8)
                OutlinedButton(onClick = openFixes, Modifier.fillMaxWidth()) { Text("Choose fixes") }
            }
        }
    }
}
