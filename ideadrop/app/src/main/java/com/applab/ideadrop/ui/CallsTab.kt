package com.applab.ideadrop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applab.ideadrop.audio.ImportController
import com.applab.ideadrop.audio.ImportState
import com.applab.ideadrop.data.CallFolder
import com.applab.ideadrop.data.CallRecordingFile
import com.applab.ideadrop.data.NoteRepository
import com.applab.ideadrop.data.NoteWithRecordings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Calls tab: every call recording on the phone, with a "To text" button on each.
 * Converted ones are kept as their own list, separate from ideas.
 */
@Composable
fun CallsTab(
    vm: MainViewModel,
    notes: List<NoteWithRecordings>,
    importState: ImportState,
    openNote: (Long) -> Unit,
    importOtherFile: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calls by vm.calls.collectAsStateWithLifecycle()
    val loading by vm.callsLoading.collectAsStateWithLifecycle()
    var showConverted by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<CallRecordingFile?>(null) }

    val connected = remember(settings.callsFolder) { vm.callsConnected() }
    LaunchedEffect(settings.callsFolder) { vm.refreshCalls() }
    // When a conversion finishes, the list updates on its own (notes flow); refresh files too
    LaunchedEffect(importState is ImportState.Done) { if (importState is ImportState.Done) vm.refreshCalls() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) vm.connectCallsFolder(tree)
    }

    val callNotes = notes.filter { it.note.category == NoteRepository.IMPORT_CATEGORY || it.note.sourceUri != null }
    val noteBySource = callNotes.mapNotNull { n -> n.note.sourceUri?.let { it to n.note.id } }.toMap()

    Column(Modifier.fillMaxSize()) {
        if (!connected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Show your call recordings here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "One time only: tap Connect. The phone opens the Recordings › Call folder — " +
                            "tap \"Use this folder\", then \"Allow\". IdeaDrop only reads the recordings; it never changes or deletes them.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { folderPicker.launch(CallFolder.samsungCallsFolder) }) { Text("Connect call recordings") }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = !showConverted, onClick = { showConverted = false }, label = { Text("All recordings (${calls.size})") })
                FilterChip(selected = showConverted, onClick = { showConverted = true }, label = { Text("Converted (${callNotes.size})") })
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { vm.refreshCalls() }) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (showConverted || !connected) {
                if (callNotes.isEmpty() && connected) {
                    item { Hint("Nothing converted yet. Tap \"To text\" on a recording.") }
                }
                items(callNotes, key = { "n${it.note.id}" }) { n ->
                    Card(
                        onClick = { openNote(n.note.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(n.note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                n.note.text.ifBlank { "No speech recognised" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                if (calls.isEmpty() && !loading) {
                    item {
                        Hint(
                            "No recordings found in this folder. If your calls are saved somewhere else, " +
                                "use \"Change folder\" below."
                        )
                    }
                }
                items(calls, key = { it.uri.toString() }) { call ->
                    val noteId = noteBySource[call.uri.toString()]
                    val working = (importState as? ImportState.Working)?.takeIf { it.name == call.fileName }
                    CallRow(
                        call = call,
                        convertedNoteId = noteId,
                        working = working,
                        busy = importState is ImportState.Working,
                        onConvert = { pending = call },
                        onOpen = openNote,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = importOtherFile, enabled = importState !is ImportState.Working) {
                        Text("Other audio file")
                    }
                    if (connected) {
                        TextButton(onClick = { folderPicker.launch(CallFolder.samsungCallsFolder) }) { Text("Change folder") }
                    }
                }
            }
        }
    }

    pending?.let { call ->
        ImportLanguageDialog(
            onPick = { lang -> vm.convertCall(context, call, lang); pending = null },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun CallRow(
    call: CallRecordingFile,
    convertedNoteId: Long?,
    working: ImportState.Working?,
    busy: Boolean,
    onConvert: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(call.contact, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(call.modified)) +
                        " · " + "%.1f MB".format(call.sizeBytes / 1_048_576f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (working != null) {
                    Text(
                        "Converting… ${(working.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                convertedNoteId != null -> TextButton(onClick = { onOpen(convertedNoteId) }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open text")
                }
                working != null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> FilledTonalButton(onClick = onConvert, enabled = !busy) { Text("To text") }
            }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(vertical = 12.dp),
)
