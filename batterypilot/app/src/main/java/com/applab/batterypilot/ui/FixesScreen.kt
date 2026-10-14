package com.applab.batterypilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.fixes.Fix
import com.applab.batterypilot.fixes.Fixes
import com.applab.batterypilot.fixes.Mode
import com.applab.batterypilot.ui.theme.Status
import kotlin.math.roundToInt

@Composable
fun FixesScreen(snap: Snapshot, state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val open = snap.fixes.filter { it.done != true }
    val done = snap.fixes.filter { it.done == true }
    val chosen = open.filter { it.id in state.selected }
    val gainMin = chosen.sumOf { it.extraMinutes }
    val f = snap.forecast
    // Minutes short of target (incl. buffer), at the learned pace.
    val shortMin: Int? = f?.let {
        val missingPct = snap.prefs.bufferPct - it.levelAtTarget
        if (missingPct <= 0) 0 else (missingPct / snap.model.avgRate.coerceAtLeast(0.5) * 60).roundToInt()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard {
                    Text("Tick what you want, then Apply.", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Auto = BatteryPilot changes it (and can undo). Opens settings = it takes you to the exact switch.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (shortMin != null && shortMin > 0) {
                        Gap(8)
                        Text("You need ≈ +${minutes(shortMin)} to reach ${Fixes.hourLabel(snap.prefs.targetHour)} with ${snap.prefs.bufferPct}% left.",
                            color = Status.Bad, fontWeight = FontWeight.SemiBold)
                    }
                    Gap(8)
                    Row {
                        TextButton(onClick = vm::selectRecommended) { Text("Select recommended") }
                    }
                }
            }
            if (state.guidedQueue.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Next: ${state.guidedQueue.first().title}", fontWeight = FontWeight.SemiBold)
                        Text("${state.guidedQueue.size} left", style = MaterialTheme.typography.bodySmall)
                        Row {
                            Button(onClick = vm::openNextGuided) { Text("Open settings") }
                            HGap()
                            TextButton(onClick = vm::skipGuided) { Text("Stop") }
                        }
                    }
                }
            }
            items(open, key = { it.id }) { fx ->
                FixRow(fx, checked = fx.id in state.selected, onToggle = { vm.toggle(fx.id) },
                    canUndo = Fixes.canUndo(context, fx), onUndo = { vm.undo(fx) })
            }
            if (done.isNotEmpty()) {
                item { Text("Already done ✓", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall) }
                items(done, key = { "done_" + it.id }) { fx ->
                    FixRow(fx, checked = true, onToggle = {}, enabled = false,
                        canUndo = Fixes.canUndo(context, fx), onUndo = { vm.undo(fx) })
                }
            }
            item {
                Text(
                    "Savings are estimates from your own drain model (screen-on ${rate(snap.model.screenOnRate)}, screen-off ${rate(snap.model.screenOffRate)}, " +
                        "~${"%.1f".format(snap.model.screenHoursPerDay)} h screen/day). Check History after a day to see the real effect.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Sticky apply bar
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = padding.calculateBottomPadding()),
            tonalElevation = 6.dp, shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${chosen.size} selected", fontWeight = FontWeight.SemiBold)
                    Text(if (chosen.isEmpty()) "Tick fixes above" else "≈ +${minutes(gainMin)} per day",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shortMin != null && gainMin >= shortMin && gainMin > 0) Status.Good else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = vm::applySelected, enabled = chosen.isNotEmpty()) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun FixRow(
    fx: Fix,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    canUndo: Boolean,
    onUndo: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(fx.title, fontWeight = FontWeight.SemiBold)
                Text(fx.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(6)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (fx.done != true && fx.extraMinutes > 0) Chip("+${minutes(fx.extraMinutes)}/day", Status.Good)
                    Chip(if (fx.mode == Mode.AUTO) "Auto" else "Opens settings", MaterialTheme.colorScheme.primary)
                }
                if (fx.needs != null) {
                    Text("Becomes Auto after: ${fx.needs}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                if (canUndo) TextButton(onClick = onUndo, contentPadding = PaddingValues(0.dp)) { Text("Undo") }
            }
        }
    }
}
