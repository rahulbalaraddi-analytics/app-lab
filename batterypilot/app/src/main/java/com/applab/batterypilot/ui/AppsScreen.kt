package com.applab.batterypilot.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applab.batterypilot.analysis.AppRow
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.ui.theme.Status

private val FgColor = Color(0xFF1A73E8)
private val BgColor = Status.Warn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(snap: Snapshot, padding: PaddingValues, openSetup: () -> Unit) {
    val context = LocalContext.current
    val exact = snap.apps.firstOrNull()?.source == "exact"
    var onlyBg by rememberSaveable { mutableStateOf(false) }
    var openPkg by rememberSaveable { mutableStateOf<String?>(null) }
    val list = snap.apps.let { a -> if (onlyBg) a.filter { it.backgroundHeavy || it.reasons.isNotEmpty() } else a }
    val hasSplit = snap.apps.any { it.bgMah != null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard {
                Text(if (exact) "Battery per app since the last full charge" else "Screen time by app since you unplugged",
                    fontWeight = FontWeight.SemiBold)
                if (!exact) {
                    Text("Exact battery and background activity need the one-time USB setup.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    Button(onClick = openSetup) { Text(if (!snap.access.usage) "Grant usage access" else "Do USB setup") }
                }
            }
        }

        // ---- Foreground vs background summary ----
        if (exact) item {
            val apps = snap.apps
            val fg = apps.sumOf { it.fgMah ?: 0.0 }
            val bg = apps.sumOf { it.bgMah ?: 0.0 }
            val onScreen = apps.sumOf { (it.activity?.topMs ?: 0L) }
            val hidden = apps.sumOf { (it.activity?.hiddenMs ?: 0L) }
            val awake = apps.sumOf { (it.activity?.wakelockMs ?: 0L) }
            SectionCard("Foreground vs background (all apps)") {
                if (hasSplit && fg + bg > 0) {
                    SplitBar(fg, bg)
                    Gap(6)
                    Row {
                        Text("■ On screen ${pctOf(fg, fg + bg)}", color = FgColor, style = MaterialTheme.typography.labelMedium)
                        HGap(12)
                        Text("■ Background ${pctOf(bg, fg + bg)}", color = BgColor, style = MaterialTheme.typography.labelMedium)
                    }
                    Gap(8)
                }
                SimpleTable(
                    header = listOf("Measure", "Time"),
                    rows = listOf(
                        listOf("Apps on screen", duration(onScreen)),
                        listOf("Apps running hidden", duration(hidden)),
                        listOf("Kept phone awake (screen off)", duration(awake)),
                    ),
                    weights = listOf(1.6f, 1f),
                )
                if (!hasSplit) {
                    Gap(6)
                    Text("This phone doesn't report battery split by foreground/background, so times are shown instead.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (exact) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyBg, onClick = { onlyBg = false }, label = { Text("All apps") })
                FilterChip(selected = onlyBg, onClick = { onlyBg = true }, label = { Text("Background activity") })
            }
        }
        if (list.isEmpty()) item {
            Text(if (onlyBg) "No app is busy in the background. 👍" else "No app data yet.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list.take(40), key = { it.packageName }) { a ->
            AppItem(a) { if (a.source == "exact") openPkg = a.packageName else openAppInfo(context, a.packageName) }
        }
        item {
            Text("Tap an app for details.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        }
    }

    val open = snap.apps.firstOrNull { it.packageName == openPkg }
    if (open != null) {
        ModalBottomSheet(onDismissRequest = { openPkg = null }) {
            AppDetail(open, onRestrict = { openAppInfo(context, open.packageName) })
        }
    }
}

@Composable
private fun AppItem(a: AppRow, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        val trailing = if (a.source == "exact") "%.0f%%".format(a.pctOfUsed ?: 0.0) else minutes(a.foregroundMin)
        ShareBar(a.label, a.pctOfUsed ?: 0.0, trailing, color = if (a.backgroundHeavy) Status.Warn else MaterialTheme.colorScheme.primary)
        val act = a.activity
        if (a.source == "exact") {
            val bits = buildList {
                add("on screen ${minutes(a.foregroundMin)}")
                if (act != null && act.hiddenMs >= 60_000) add("hidden ${duration(act.hiddenMs)}")
                if (act != null && act.wakelockMs >= 60_000) add("awake ${duration(act.wakelockMs)}")
            }.joinToString(" · ")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(bits, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                a.bgShare?.let { Chip("${(it * 100).toInt()}% bg", if (it >= 0.4) BgColor else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (a.backgroundHeavy) { HGap(4); Chip("Background heavy", Status.Warn) }
            }
        }
    }
}

@Composable
private fun AppDetail(a: AppRow, onRestrict: () -> Unit) {
    val act = a.activity
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Text(a.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Since the last full charge", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap()

        // Verdict
        val verdict = when {
            a.backgroundHeavy -> "⚠ Drains mostly when you're not using it" to Status.Bad
            (a.bgShare ?: 0.0) >= 0.2 || a.reasons.isNotEmpty() -> "Some background activity" to Status.Warn
            else -> "Uses battery mainly while on screen ✓" to Status.Good
        }
        Text(verdict.first, fontWeight = FontWeight.SemiBold, color = verdict.second)
        Gap(8)

        if (a.fgMah != null && a.bgMah != null && a.fgMah + a.bgMah > 0) {
            SplitBar(a.fgMah, a.bgMah)
            Gap(4)
            Row {
                Text("■ On screen %.0f mAh".format(a.fgMah), color = FgColor, style = MaterialTheme.typography.labelMedium)
                HGap(12)
                Text("■ Background %.0f mAh".format(a.bgMah), color = BgColor, style = MaterialTheme.typography.labelMedium)
            }
            Gap()
        }

        SimpleTable(
            header = listOf("Measure", "Value"),
            rows = buildList {
                add(listOf("Battery used", "%.0f%% · %.0f mAh".format(a.pctOfUsed ?: 0.0, a.mah ?: 0.0)))
                add(listOf("On screen", minutes(a.foregroundMin)))
                if (act != null) {
                    add(listOf("Running in background", duration(act.backgroundMs)))
                    add(listOf("Foreground service", duration(act.fgServiceMs)))
                    add(listOf("Kept phone awake", "${duration(act.wakelockMs)} (${act.wakelockCount}×)"))
                    add(listOf("Background jobs", "${act.jobCount} · ${duration(act.jobMs)}"))
                    add(listOf("Mobile data", mb(act.mobileBytes)))
                    add(listOf("Wi-Fi data", mb(act.wifiBytes)))
                    add(listOf("Processor time", duration(act.cpuMs)))
                }
            },
            weights = listOf(1.3f, 1f),
        )
        Gap(8)
        Text(
            "Foreground service = shows a notification (music, maps, uploads, calls). " +
                "Kept phone awake = stopped the phone from sleeping with the screen off.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (a.reasons.isNotEmpty()) {
            Gap()
            Text("Why it drains in the background", fontWeight = FontWeight.SemiBold)
            a.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }

        Gap()
        Text("What to do", fontWeight = FontWeight.SemiBold)
        Text(
            if (a.backgroundHeavy || a.reasons.isNotEmpty())
                "App info → Battery → Restricted (or add to Sleeping apps). Notifications from it may arrive late."
            else "Nothing needed. Its battery use is from your screen time.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Gap(8)
        if (a.backgroundHeavy || a.reasons.isNotEmpty()) {
            Button(onClick = onRestrict, Modifier.fillMaxWidth()) { Text("Open App info → Battery") }
        } else {
            OutlinedButton(onClick = onRestrict, Modifier.fillMaxWidth()) { Text("Open App info") }
        }
    }
}

@Composable
private fun SplitBar(fg: Double, bg: Double) {
    val total = (fg + bg).coerceAtLeast(0.0001)
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
        val f = (fg / total).toFloat().coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.weight(f.coerceAtLeast(0.001f)).fillMaxHeight().background(FgColor))
        if (f < 1f) Box(Modifier.weight((1f - f).coerceAtLeast(0.001f)).fillMaxHeight().background(BgColor))
    }
}

private fun pctOf(x: Double, total: Double) = if (total > 0) "${(x / total * 100).toInt()}%" else "—"
private fun mb(b: Long) = if (b >= 1024L * 1024) "${b / (1024 * 1024)} MB" else "${b / 1024} KB"

private fun openAppInfo(context: android.content.Context, pkg: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
