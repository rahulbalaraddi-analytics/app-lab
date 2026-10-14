package com.applab.batterypilot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applab.batterypilot.analysis.Snapshot
import com.applab.batterypilot.collect.Access
import com.applab.batterypilot.fixes.Fixes
import com.applab.batterypilot.ui.theme.Status

@Composable
fun SetupScreen(snap: Snapshot, vm: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val a = snap.access
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh(false) }
    val ignoringOpt = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Your goal") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last till", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { vm.setTargetHour(snap.prefs.targetHour - 1) }) { Text("−", fontSize = 22.sp) }
                    Text(Fixes.hourLabel(snap.prefs.targetHour), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.setTargetHour(snap.prefs.targetHour + 1) }) { Text("+", fontSize = 22.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("With this much left", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { vm.setBuffer(snap.prefs.bufferPct - 5) }) { Text("−", fontSize = 22.sp) }
                    Text("${snap.prefs.bufferPct}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.setBuffer(snap.prefs.bufferPct + 5) }) { Text("+", fontSize = 22.sp) }
                }
            }
        }

        item {
            SectionCard("Automatic help") {
                ToggleRow("Heads-up notification", "Once a day, if the forecast says you won't make it",
                    snap.prefs.alertsOn, enabled = true) { vm.setAlerts(it) }
                ToggleRow("Smart saver", "Turns on Power saving by itself only when you won't make it (after 3 PM, or under 40%)",
                    snap.prefs.smartSaver, enabled = a.secureSettings) { vm.setSmartSaver(it) }
                Gap(6)
                Text("Power saving below", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 20, 30, 40).forEach { p ->
                        FilterChip(
                            selected = snap.prefs.saverBelowPct == p,
                            onClick = { vm.setSaverBelow(p) },
                            enabled = a.secureSettings,
                            label = { Text(if (p == 0) "Off" else "$p%") },
                        )
                    }
                }
                if (!a.secureSettings) {
                    Text("Needs the one-time USB setup below.", style = MaterialTheme.typography.labelSmall, color = Status.Warn)
                }
            }
        }

        item {
            SectionCard("Permissions") {
                Step("1. Usage access", "Screen-on time and which apps you use", a.usage) {
                    open(context, Access.usageAccessIntent(context), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Step("2. Modify system settings", "Lets ‘Auto’ fixes change brightness and timeout", a.writeSettings) {
                    open(context, Access.writeSettingsIntent(context))
                }
                Step("3. Notifications", "For the 11 PM heads-up", a.notifications) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Step("4. Keep running in background", "So readings continue every 15 min (Samsung can put apps to sleep)", ignoringOpt) {
                    requestIgnoreOptimizations(context)
                }
                Text("Also: Settings → Battery → Background usage limits → Never sleeping apps → add BatteryPilot.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard("5. One-time USB setup (5 min, optional)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (a.usbSetupDone) "Done ✓" else "Not done yet", fontWeight = FontWeight.SemiBold,
                        color = if (a.usbSetupDone) Status.Good else Status.Warn)
                }
                Text("Unlocks: exact battery % per app (incl. hidden background drain), one-tap Power saving, 60 Hz, scanning off, Smart saver.",
                    style = MaterialTheme.typography.bodySmall)
                Gap(8)
                Text(
                    "On the phone:\n" +
                        "• Settings → About phone → Software information → tap Build number 7 times\n" +
                        "• Settings → Developer options → USB debugging ON\n\n" +
                        "On a laptop (home laptop, not office):\n" +
                        "• Install Android ‘platform-tools’ (free, from Google)\n" +
                        "• Connect the phone by USB, tap Allow on the phone\n" +
                        "• Run these in a terminal inside the platform-tools folder:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Gap(6)
                Text(
                    Access.ADB_COMMANDS.joinToString("\n"),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(10.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                )
                Row {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("adb", Access.ADB_COMMANDS.joinToString("\n")))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy commands") }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, Access.ADB_COMMANDS.joinToString("\n"))
                        context.startActivity(Intent.createChooser(share, "Send commands to laptop"))
                    }) { Text("Send to laptop") }
                }
                Text("Stays granted through app updates and restarts. USB debugging can be turned off afterwards.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(6)
                OutlinedButton(onClick = { vm.refresh(false) }) { Text("Check again") }
            }
        }

        item {
            SectionCard("Privacy") {
                Text("Everything is read and analysed on this phone. No internet permission, no account, nothing uploaded.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Step(title: String, sub: String, done: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (done) Chip("✓ Done", Status.Good) else Button(onClick = onGrant) { Text("Allow") }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun open(context: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (e: Exception) {
        }
    }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreOptimizations(context: Context) {
    open(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
}
