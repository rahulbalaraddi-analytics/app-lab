package com.applab.batterypilot.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applab.batterypilot.analysis.AppRow
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.ui.theme.Status

@Composable
fun AppsScreen(snap: Snapshot, padding: PaddingValues, openSetup: () -> Unit) {
    val context = LocalContext.current
    val exact = snap.apps.firstOrNull()?.source == "exact"
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard {
                Text(if (exact) "Battery used by each app since the last full charge" else "Screen time by app since you unplugged",
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (exact) "Exact numbers from Android. “Background heavy” = drains a lot while you're not using it → put it to sleep."
                    else "Without the one-time USB setup Android only shares screen time, not battery per app. Background drain (the hidden kind) needs the setup.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!exact) {
                    Gap(8)
                    Button(onClick = openSetup) { Text(if (!snap.access.usage) "Grant usage access" else "Do USB setup") }
                }
            }
        }
        if (snap.apps.isEmpty()) item {
            Text("No app data yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val heavy = snap.apps.count { it.backgroundHeavy }
        if (heavy > 0) item {
            Text("⚠ $heavy app${if (heavy > 1) "s" else ""} drain mostly in the background. Tap one → Battery → Restricted.",
                color = Status.Warn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        items(snap.apps.take(30), key = { it.packageName }) { a ->
            AppItem(a) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItem(a: AppRow, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        val trailing = if (a.source == "exact") "%.0f%%".format(a.pctOfUsed ?: 0.0) else minutes(a.foregroundMin)
        ShareBar(a.label, a.pctOfUsed ?: 0.0, trailing, color = if (a.backgroundHeavy) Status.Warn else MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val detail = buildList {
                if (a.mah != null) add("%.0f mAh".format(a.mah))
                if (a.source == "exact") add("on screen ${minutes(a.foregroundMin)}")
            }.joinToString(" · ")
            Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.backgroundHeavy) Chip("Background heavy", Status.Warn)
        }
    }
}
