package com.applab.batterypilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.ui.theme.Status
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(snap: Snapshot, padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Before / after ----
        item {
            SectionCard("Did the fixes work?") {
                val ba = snap.beforeAfter
                if (ba == null) {
                    Text("Apply fixes from the Fixes tab. This compares your drain for 7 days before vs after.",
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Since ${dayLabel(ba.changeTime)}, ${time(ba.changeTime)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    val b = ba.before; val a = ba.after
                    fun pct(x: Double?, y: Double?): String {
                        if (x == null || y == null || x <= 0) return "—"
                        val d = (x - y) / x * 100
                        return if (d >= 0) "−${d.roundToInt()}%" else "+${(-d).roundToInt()}%"
                    }
                    fun hrs(v: Double?) = v?.let { "%.1f h".format(it) } ?: "—"
                    SimpleTable(
                        header = listOf("", "Before", "After", "Change"),
                        rows = listOf(
                            listOf("Avg drain", rate(b?.avgRate), rate(a?.avgRate), pct(b?.avgRate, a?.avgRate)),
                            listOf("Screen on", rate(b?.screenOnRate), rate(a?.screenOnRate), pct(b?.screenOnRate, a?.screenOnRate)),
                            listOf("Screen off", rate(b?.screenOffRate), rate(a?.screenOffRate), pct(b?.screenOffRate, a?.screenOffRate)),
                            listOf("Full lasts", hrs(b?.fullDayHours), hrs(a?.fullDayHours), ""),
                            listOf("Data", hrs(b?.hours), hrs(a?.hours), ""),
                        ),
                        weights = listOf(1.2f, 1.1f, 1.1f, 0.8f),
                        highlightCol = 3,
                    )
                    Gap(8)
                    val imp = ba.improvementPct
                    Text(
                        when {
                            b == null -> "No readings from before the change, so no comparison yet."
                            a == null || a.hours < 3 -> "Collecting ‘after’ data — check back after a few hours on battery."
                            imp == null -> "Need a bit more data on both sides."
                            imp >= 5 -> "✅ Draining ${imp.roundToInt()}% slower per hour than before."
                            imp > -5 -> "About the same so far. Screen time differs day to day — give it 2–3 days."
                            else -> "Draining faster than before (${(-imp).roundToInt()}%). Heavier use, heat or weak signal can do this."
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = when { imp == null -> MaterialTheme.colorScheme.onSurface; imp >= 5 -> Status.Good; imp > -5 -> Status.Warn; else -> Status.Bad },
                    )
                    Text("Screen-on / screen-off rates are the fairest comparison: they don't depend on how much you used the phone.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- Days ----
        if (snap.days.isNotEmpty()) item {
            SectionCard("Day by day") {
                SimpleTable(
                    header = listOf("Day", "Off at", "7 PM", "Lowest", "Drain"),
                    rows = snap.days.take(14).map { d ->
                        listOf(
                            dayLabel(d.dayStart).substringBeforeLast(' '),
                            d.unplugTime?.let { "${time(it).replace(" ", "").lowercase()} ${d.unplugLevel}%" } ?: "—",
                            d.levelAt7pm?.let { "$it%" } ?: "—",
                            "${d.lowest}%",
                            d.avgRate?.let { "%.1f".format(it) } ?: "—",
                        )
                    },
                    weights = listOf(1.0f, 1.35f, 0.7f, 0.8f, 0.75f),
                )
                Gap(6)
                Text("Drain = average %/hr on battery. Screen time per day: " +
                    snap.days.take(7).mapNotNull { d -> d.screenHours?.let { "%.1fh".format(it) } }.joinToString(", ").ifBlank { "needs usage access" },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Change log ----
        if (snap.fixLog.isNotEmpty()) item {
            SectionCard("Changes made") {
                SimpleTable(
                    header = listOf("When", "Change", "How"),
                    rows = snap.fixLog.take(25).map {
                        listOf("${dayLabel(it.time).substringBefore(' ')} ${time(it.time)}", it.title,
                            when (it.action) { "applied" -> "Auto"; "opened" -> "Settings"; "undone" -> "Undone"; else -> it.action })
                    },
                    weights = listOf(1.1f, 2.2f, 0.8f),
                )
            }
        }

        // ---- Model ----
        item {
            SectionCard("What the model has learned") {
                val m = snap.model
                Text("Learned from ${"%.0f".format(m.trainedHours)} hours on battery · confidence: ${m.confidence}",
                    style = MaterialTheme.typography.bodySmall)
                Gap(8)
                val peak = (7..22).maxByOrNull { m.hourly[it] } ?: 12
                SimpleTable(
                    header = listOf("Measure", "Value"),
                    rows = listOf(
                        listOf("Screen on", rate(m.screenOnRate)),
                        listOf("Screen off", rate(m.screenOffRate)),
                        listOf("Screen time / day", "%.1f h".format(m.screenHoursPerDay)),
                        listOf("Average", rate(m.avgRate)),
                        listOf("Heaviest hour", "${com.applab.batterypilot.fixes.Fixes.hourLabel(peak)} (${rate(m.hourly[peak])})"),
                    ),
                    weights = listOf(1f, 1f),
                )
            }
        }
    }
}
