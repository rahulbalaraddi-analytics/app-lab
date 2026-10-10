package com.applab.ideadrop.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.applab.ideadrop.audio.RecState
import com.applab.ideadrop.audio.RecordingController
import com.applab.ideadrop.export.ExportScope
import com.applab.ideadrop.ui.theme.RecordRed
import kotlinx.coroutines.delay

/** Returns a function that asks for mic permission (once) and starts recording. */
@Composable
fun rememberRecorder(): (noteId: Long?) -> Unit {
    val context = LocalContext.current
    var pendingNoteId by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            RecordingController.start(context, pendingNoteId)
        } else {
            Toast.makeText(context, "Microphone permission is needed to record ideas", Toast.LENGTH_LONG).show()
        }
    }
    return { noteId ->
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            RecordingController.start(context, noteId)
        } else {
            pendingNoteId = noteId
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        }
    }
}

/** Bottom panel while recording: timer, live text, big Stop button. */
@Composable
fun RecordingPanel(state: RecState) {
    val context = LocalContext.current
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is RecState.Recording -> {
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(state.startedAt) {
                        while (true) { now = System.currentTimeMillis(); delay(250) }
                    }
                    val secs = ((now - state.startedAt) / 1000).coerceAtLeast(0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(RecordRed, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "%d:%02d".format(secs / 60, secs % 60),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.noteId != null) {
                            Text("  · adding to note", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = state.liveText.ifBlank { "Listening… start speaking" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.liveText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { RecordingController.stop(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop & save", style = MaterialTheme.typography.titleMedium)
                    }
                }
                RecState.Saving -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Saving your idea…")
                }
                RecState.Idle -> {}
            }
        }
    }
}

/** Export: All (default) or Pending only → share or save to phone. */
@Composable
fun ExportDialog(
    onShare: (ExportScope) -> Unit,
    onSave: (ExportScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var scope by rememberSaveable { mutableStateOf(ExportScope.ALL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export ideas") },
        text = {
            Column {
                Text("One Markdown (.md) file you can share with Claude.")
                Spacer(Modifier.height(12.dp))
                ExportScope.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = scope == option, onClick = { scope = option })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scope == option, onClick = { scope = option })
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onShare(scope) }) { Text("Share") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                OutlinedButton(onClick = { onSave(scope) }) { Text("Save to phone") }
            }
        },
    )
}

/** Asks before deleting old audio. Text is always kept. */
@Composable
fun CleanupDialog(prompt: CleanupPrompt, onDelete: () -> Unit, onKeep: () -> Unit, onSettings: () -> Unit) {
    val count = prompt.recordings.size
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Free up space?") },
        text = {
            Text(
                "$count voice recording${if (count == 1) " is" else "s are"} older than ${prompt.days} days.\n\n" +
                    "Delete the audio? Your text notes stay exactly as they are."
            )
        },
        confirmButton = { Button(onClick = onDelete) { Text("Delete audio") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSettings) { Text("Change days") }
                TextButton(onClick = onKeep) { Text("Keep") }
            }
        },
    )
}
