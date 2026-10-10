package com.applab.ideadrop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applab.ideadrop.audio.RecState
import com.applab.ideadrop.audio.RecordingController
import com.applab.ideadrop.data.Recording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(vm: MainViewModel, noteId: Long, back: () -> Unit) {
    val data by remember(noteId) { vm.note(noteId) }.collectAsState(initial = null)
    val categories by vm.categories.collectAsStateWithLifecycle()
    val recState by RecordingController.state.collectAsStateWithLifecycle()
    val playing by vm.player.playingPath.collectAsStateWithLifecycle()
    val record = rememberRecorder()

    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Load from the database whenever it changes and there are no unsaved edits
    // (e.g. after a new voice clip was appended to this note).
    val note = data?.note
    LaunchedEffect(note?.updatedAt) {
        if (note != null && !dirty) {
            title = note.title; text = note.text; category = note.category
        }
    }

    fun saveIfDirty() {
        val n = note ?: return
        if (dirty) {
            vm.save(n.copy(title = title.ifBlank { n.title }, text = text, category = category.trim()))
            dirty = false
        }
    }

    fun leave() { saveIfDirty(); vm.player.stop(); back() }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Idea") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (dirty) IconButton(onClick = { saveIfDirty() }) { Icon(Icons.Default.Check, "Save") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
        bottomBar = {
            if (recState != RecState.Idle) {
                RecordingPanel(recState)
            } else if (note != null) {
                FilledTonalButton(
                    onClick = { saveIfDirty(); record(note.id) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add more by voice")
                }
            }
        },
    ) { padding ->
        if (note == null) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(note.createdAt)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; dirty = true },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !note.done, onClick = { vm.setDone(note, false) }, label = { Text("Pending") })
                FilterChip(selected = note.done, onClick = { vm.setDone(note, true) }, label = { Text("Done") })
            }

            OutlinedTextField(
                value = category,
                onValueChange = { category = it; dirty = true },
                label = { Text("Category / project") },
                placeholder = { Text("e.g. IG app, Investing, Home") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = categories.filter { it != category && it.contains(category, ignoreCase = true) }
            if (suggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        SuggestionChip(onClick = { category = s; dirty = true }, label = { Text(s) })
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; dirty = true },
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )

            HorizontalDivider()
            Text("Voice clips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            data?.recordings?.sortedBy { it.createdAt }?.forEach { rec ->
                ClipRow(rec, isPlaying = playing == rec.filePath, onToggle = { rec.filePath?.let(vm.player::toggle) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this idea?") },
            text = { Text("The text and its voice clips will be removed.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; vm.player.stop(); vm.delete(noteId); back() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ClipRow(rec: Recording, isPlaying: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onToggle, enabled = rec.filePath != null) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play")
        }
        Column(Modifier.weight(1f)) {
            val secs = rec.durationMs / 1000
            Text(
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(rec.createdAt)) +
                    " · %d:%02d".format(secs / 60, secs % 60),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (rec.filePath == null) "No audio · text kept" else "Audio saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
