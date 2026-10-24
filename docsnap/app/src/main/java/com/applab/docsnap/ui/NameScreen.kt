package com.applab.docsnap.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.applab.docsnap.data.Doc
import com.applab.docsnap.data.Draft
import com.applab.docsnap.data.Store
import com.applab.docsnap.scan.Metrics
import com.applab.docsnap.scan.Quality
import com.applab.docsnap.speech.PhoneSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The name is compulsory: there is no skip, and Back does not leave this screen.
 * Speech fills the text box; typing into it is the fallback.
 */
@Composable
fun NameScreen(store: Store, draft: Draft, onSaved: (Doc) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(store.language) }
    var text by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var lastChange by remember { mutableLongStateOf(0L) }
    var speech by remember { mutableStateOf<PhoneSpeech?>(null) }
    val lowPages = remember(draft) { draft.pages.count { Metrics.page(it).quality == Quality.LOW } }

    fun stopListening() {
        val s = speech ?: return
        speech = null
        scope.launch {
            val final = s.finish()
            if (!typed && final.isNotBlank()) text = final
            listening = false
            if (!s.gotText) store.event("speech_no_text", draft.id)
        }
    }

    fun startListening() {
        if (!PhoneSpeech.isAvailable(context)) {
            store.event("speech_unavailable", language)
            Toast.makeText(context, "Voice not available on this phone. Please type the name.", Toast.LENGTH_LONG).show()
            return
        }
        speech?.let { stopListening() }
        typed = false
        text = ""
        val s = PhoneSpeech(context, language, emptyList()) { live ->
            if (!typed) { text = live; lastChange = System.currentTimeMillis() }
        }
        speech = s
        listening = true
        lastChange = System.currentTimeMillis()
        s.startWithMic()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening() else Toast.makeText(context, "No mic permission. Please type the name.", Toast.LENGTH_LONG).show()
    }

    fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Mic opens by itself as soon as the document is scanned
    LaunchedEffect(draft.id) { startWithPermission() }

    // Stop after ~2.5 s of silence once something was heard (or 30 s max)
    LaunchedEffect(listening) {
        val started = System.currentTimeMillis()
        while (listening) {
            delay(300)
            val now = System.currentTimeMillis()
            val heard = text.isNotBlank()
            if ((heard && now - lastChange > 2_500) || now - started > 30_000) stopListening()
        }
    }

    DisposableEffect(Unit) { onDispose { speech?.let { s -> scope.launch { s.finish() } } } }

    BackHandler {
        Toast.makeText(context, "Name this document first — it can't be skipped.", Toast.LENGTH_SHORT).show()
    }

    val canSave = text.isNotBlank() && !saving && !listening
    val preview = if (text.isNotBlank()) store.previewFileName(draft, text) else "—"

    Page {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${draft.folder.name.uppercase()} · ${draft.pages.size} PAGE${if (draft.pages.size == 1) "" else "S"}",
                        fontSize = 13.sp, color = Palette.Quiet,
                    )
                    Text("Say the name", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                FilterChip(selected = language == "en-IN", onClick = {
                    language = "en-IN"; store.language = language; if (listening) startListening()
                }, label = { Text("EN") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = language == "kn-IN", onClick = {
                    language = "kn-IN"; store.language = language; if (listening) startListening()
                }, label = { Text("ಕನ್ನಡ") })
            }

            if (lowPages > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Warning: $lowPages page(s) are low quality. You can still save; consider rescanning closer.",
                    color = Palette.Red, fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp).background(if (listening) Palette.AmberSoft else Palette.TealSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { if (listening) stopListening() else startWithPermission() },
                        modifier = Modifier.size(104.dp).background(if (listening) Palette.Amber else Palette.Teal, CircleShape),
                    ) {
                        Icon(
                            if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (listening) "Stop listening" else "Start listening",
                            tint = Color.White, modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (listening) "Listening… stops after a short pause" else "Tap the mic to speak again",
                color = if (listening) Palette.Amber else Palette.Quiet, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    if (listening) stopListening()
                    typed = true
                    text = it
                },
                label = { Text("You said (tap to type or fix)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            Text("FILE NAME", fontSize = 12.sp, color = Palette.Quiet)
            Text(preview, fontFamily = FontFamily.Monospace, fontSize = 13.sp)

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { startWithPermission() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, Palette.Teal),
            ) { Text("Re-record", color = Palette.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(10.dp))
            if (saving) {
                Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                BigButton("Save & next", enabled = canSave) {
                    saving = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { store.finish(draft, text, if (typed) "typed" else "speech") }
                        }
                        saving = false
                        result.onSuccess(onSaved).onFailure {
                            store.event("save_failed", "${draft.id} ${it.message}")
                            Toast.makeText(context, "Could not save: ${it.message}. Nothing is lost — try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Palette.Quiet, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("No skip. Next stays locked until a name is saved.", fontSize = 13.sp, color = Palette.Quiet)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
