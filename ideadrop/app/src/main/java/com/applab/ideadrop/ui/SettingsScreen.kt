package com.applab.ideadrop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val RETENTION_OPTIONS = listOf(7, 15, 30, 60, 90, 0)
private val LANGUAGES = listOf(
    "en-IN" to "English (India)",
    "en-US" to "English (US)",
    "hi-IN" to "हिन्दी Hindi",
    "kn-IN" to "ಕನ್ನಡ Kannada",
    com.applab.ideadrop.speech.PhoneSpeech.MIXED to "Mixed (English + ಕನ್ನಡ + हिन्दी)",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, back: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) } // "start" | "end"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Audio cleanup")
            Text(
                "Ask to delete voice recordings older than:",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RETENTION_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = s.audioRetentionDays == days,
                        onClick = { vm.updateSettings { it.copy(audioRetentionDays = days, cleanupSnoozeUntil = 0) } },
                        label = { Text(if (days == 0) "Never" else "$days days") },
                    )
                }
            }
            Hint("IdeaDrop always asks before deleting anything. Text notes are always kept.")

            HorizontalDivider()
            Section("Bedtime window")
            Hint("Coming in the next update: auto-listening only during this window, so the mic is never on all day.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { editing = "start" }) { Text("From ${clock(s.bedtimeStartMin)}") }
                OutlinedButton(onClick = { editing = "end" }) { Text("To ${clock(s.bedtimeEndMin)}") }
            }

            HorizontalDivider()
            Section("Speech")
            Text("Language", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (code, label) ->
                    FilterChip(
                        selected = s.language == code,
                        onClick = { vm.updateSettings { it.copy(language = code) } },
                        label = { Text(label) },
                    )
                }
            }
            Hint(
                "Uses your phone's built-in speech engine. For offline use, install the language pack: " +
                    "Settings → General management → Samsung keyboard / Google voice typing → Offline speech recognition."
            )
            var words by remember { mutableStateOf(s.myWords) }
            OutlinedTextField(
                value = words,
                onValueChange = { words = it; vm.updateSettings { st -> st.copy(myWords = it) } },
                label = { Text("My words") },
                placeholder = { Text("Guidewire, PolicyCenter, Hebbal, Thanisandra") },
                supportingText = { Text("Names and terms you say often, separated by commas. Helps the engine spell them right.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep original audio", style = MaterialTheme.typography.bodyLarge)
                    Hint(
                        when (s.audioStreaming) {
                            1 -> "Working on this phone ✓"
                            0 -> "This phone's speech engine can't share the mic, so only text is saved. Tap Retry after a phone/app update."
                            else -> "Will be checked on your first recording."
                        }
                    )
                }
                if (s.audioStreaming == 0) {
                    TextButton(onClick = { vm.updateSettings { it.copy(audioStreaming = -1) } }) { Text("Retry") }
                }
            }

            HorizontalDivider()
            Section("Quick record (side key & lock screen)")
            Hint(
                "IdeaDrop adds a second app icon called \"Record idea\". Opening it starts recording straight away.\n\n" +
                    "• Side key: Settings → Advanced features → Side button → Double press → Open app → Record idea\n" +
                    "• Lock screen: Settings → Lock screen → Shortcuts → choose Record idea\n" +
                    "• Home screen: long-press the IdeaDrop icon → drag \"Record idea\" out"
            )

            HorizontalDivider()
            Section("Export")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add instruction for Claude", style = MaterialTheme.typography.bodyLarge)
                    Hint("Adds one line at the top asking Claude to make an actionable checklist.")
                }
                Switch(
                    checked = s.exportIncludePrompt,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(exportIncludePrompt = on) } },
                )
            }

            HorizontalDivider()
            Hint("IdeaDrop 0.4 · Your notes stay on your phone unless you export them.")
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { which ->
        val initial = if (which == "start") s.bedtimeStartMin else s.bedtimeEndMin
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (which == "start") "Bedtime starts" else "Bedtime ends") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    vm.updateSettings { if (which == "start") it.copy(bedtimeStartMin = m) else it.copy(bedtimeEndMin = m) }
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth(),
)

private fun clock(min: Int): String {
    val h = min / 60
    val m = min % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ampm)
}
