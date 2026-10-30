package com.applab.ideadrop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applab.ideadrop.audio.ImportController
import com.applab.ideadrop.audio.ImportState
import com.applab.ideadrop.audio.RecState
import com.applab.ideadrop.audio.RecordingController
import com.applab.ideadrop.data.NoteRepository
import com.applab.ideadrop.data.NoteWithRecordings
import com.applab.ideadrop.export.ExportScope
import com.applab.ideadrop.export.MarkdownExporter
import com.applab.ideadrop.speech.PhoneSpeech
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Filter(val label: String) { ALL("All"), PENDING("Pending"), DONE("Done") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, openNote: (Long) -> Unit, openSettings: () -> Unit) {
    val context = LocalContext.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    val recState by RecordingController.state.collectAsStateWithLifecycle()
    val cleanup by vm.cleanup.collectAsStateWithLifecycle()
    val record = rememberRecorder()

    val importState by ImportController.state.collectAsStateWithLifecycle()
    val sharedAudio by SharedAudio.pending.collectAsStateWithLifecycle()
    var pickedAudio by remember { mutableStateOf<android.net.Uri?>(null) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pickedAudio = uri
    }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var tab by rememberSaveable { mutableStateOf(0) } // 0 = Ideas, 1 = Calls
    val crash by vm.crash.collectAsStateWithLifecycle()
    var showExport by remember { mutableStateOf(false) }
    var saveScope by remember { mutableStateOf(ExportScope.ALL) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) vm.saveTo(context, uri, saveScope)
    }

    val ideas = notes.filter { it.note.category != NoteRepository.IMPORT_CATEGORY && it.note.sourceUri == null }
    val visible = ideas.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.PENDING -> !it.note.done
            Filter.DONE -> it.note.done
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IdeaDrop", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        enabled = importState !is ImportState.Working,
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Import audio")
                    }
                    IconButton(onClick = { showExport = true }, enabled = notes.isNotEmpty()) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export")
                    }
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (recState == RecState.Idle && tab == 0) {
                LargeFloatingActionButton(onClick = { record(null) }) {
                    Icon(Icons.Default.Mic, contentDescription = "Record idea", modifier = Modifier.size(36.dp))
                }
            }
        },
        bottomBar = {
            if (recState != RecState.Idle) RecordingPanel(recState)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            crash?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("IdeaDrop closed unexpectedly last time", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Send the report to Claude so it can be fixed.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            androidx.compose.material3.TextButton(onClick = { vm.dismissCrash() }) { Text("Dismiss") }
                            androidx.compose.material3.Button(onClick = { vm.shareCrash(context) }) { Text("Send report") }
                        }
                    }
                }
            }
            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab) {
                androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ideas") })
                androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Calls") })
            }
            ImportCard(
                state = importState,
                onCancel = { ImportController.cancel(context) },
                onOpen = openNote,
                onDismiss = { ImportController.clear() },
            )
            val engineAvailable = remember { PhoneSpeech.isAvailable(context) }
            if (!engineAvailable) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "No speech engine found on this phone. Install or enable \"Speech Recognition & " +
                            "Synthesis from Google\" in the Play Store, then reopen IdeaDrop.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (tab == 1) {
                CallsTab(
                    vm = vm,
                    notes = notes,
                    importState = importState,
                    openNote = openNote,
                    importOtherFile = { audioPicker.launch(arrayOf("audio/*")) },
                )
            } else {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Filter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }

            if (visible.isEmpty()) {
                EmptyState(hasAny = ideas.isNotEmpty())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.groupBy { dayLabel(it.note.createdAt) }.forEach { (day, items) ->
                        item(key = "h_$day") {
                            Text(
                                day,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(items, key = { it.note.id }) { item ->
                            NoteCard(
                                item = item,
                                onClick = { openNote(item.note.id) },
                                onDoneChange = { vm.setDone(item.note, it) },
                            )
                        }
                    }
                }
            }
            } // end Ideas tab
        }
    }

    (pickedAudio ?: sharedAudio)?.let { uri ->
        ImportLanguageDialog(
            onPick = { lang ->
                vm.importAudio(context, uri, lang)
                pickedAudio = null; SharedAudio.pending.value = null
            },
            onDismiss = { pickedAudio = null; SharedAudio.pending.value = null },
        )
    }

    if (showExport) {
        ExportDialog(
            onShare = { vm.share(context, it); showExport = false },
            onSave = { saveScope = it; showExport = false; saveLauncher.launch(MarkdownExporter.fileName()) },
            onDismiss = { showExport = false },
        )
    }
    cleanup?.let { prompt ->
        CleanupDialog(
            prompt = prompt,
            onDelete = vm::confirmCleanup,
            onKeep = vm::snoozeCleanup,
            onSettings = { vm.dismissCleanup(); openSettings() },
        )
    }
}

@Composable
private fun NoteCard(item: NoteWithRecordings, onClick: () -> Unit, onDoneChange: (Boolean) -> Unit) {
    val n = item.note
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
            Checkbox(checked = n.done, onCheckedChange = onDoneChange)
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeLabel(n.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.recordings.size > 1) {
                        Text(
                            "  · ${item.recordings.size} clips",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (n.category.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = onClick, label = { Text(n.category) }, modifier = Modifier.height(24.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    n.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (n.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val preview = n.text.ifBlank { "No text captured" }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasAny: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hasAny) "Nothing here" else "No ideas yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasAny) "Try another filter." else "Tap the mic and speak. IdeaDrop saves it with the date and time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayLabel(time: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = time
    val day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    return when (today - day) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(time))
    }
}

private fun timeLabel(time: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))
