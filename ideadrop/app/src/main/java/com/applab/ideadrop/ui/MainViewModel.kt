package com.applab.ideadrop.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applab.ideadrop.IdeaDropApp
import com.applab.ideadrop.audio.AudioPlayer
import com.applab.ideadrop.audio.ImportController
import com.applab.ideadrop.data.AppSettings
import com.applab.ideadrop.data.CallFolder
import com.applab.ideadrop.data.CallRecordingFile
import com.applab.ideadrop.data.Note
import com.applab.ideadrop.data.NoteRepository
import com.applab.ideadrop.data.NoteWithRecordings
import com.applab.ideadrop.data.Recording
import com.applab.ideadrop.export.ExportScope
import com.applab.ideadrop.export.MarkdownExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pending audio-cleanup question shown to the user. */
data class CleanupPrompt(val recordings: List<Recording>, val days: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IdeaDropApp
    private val repo = app.repository

    val notes: StateFlow<List<NoteWithRecordings>> =
        repo.notes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<String>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings> = app.settings.state
    val player = AudioPlayer(application)

    private val _cleanup = MutableStateFlow<CleanupPrompt?>(null)
    val cleanup: StateFlow<CleanupPrompt?> = _cleanup.asStateFlow()

    init {
        checkCleanup()
        // Calls list is loaded by the Calls tab (properties below aren't ready yet here)
    }

    fun note(id: Long): Flow<NoteWithRecordings?> = repo.note(id)

    fun save(note: Note) = viewModelScope.launch { repo.save(note) }
    fun setDone(note: Note, done: Boolean) = viewModelScope.launch { repo.setDone(note, done) }
    fun delete(noteId: Long) = viewModelScope.launch { repo.delete(noteId) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = app.settings.update(transform)

    // ---- Audio cleanup: never deletes without asking ----

    fun checkCleanup() = viewModelScope.launch {
        val s = settings.value
        if (s.audioRetentionDays <= 0 || System.currentTimeMillis() < s.cleanupSnoozeUntil) return@launch
        val old = repo.recordingsOlderThan(s.audioRetentionDays)
        _cleanup.value = if (old.isEmpty()) null else CleanupPrompt(old, s.audioRetentionDays)
    }

    fun confirmCleanup() = viewModelScope.launch {
        _cleanup.value?.let { repo.deleteAudio(it.recordings) }
        _cleanup.value = null
    }

    fun snoozeCleanup() {
        updateSettings { it.copy(cleanupSnoozeUntil = System.currentTimeMillis() + 7 * NoteRepository.DAY_MS) }
        _cleanup.value = null
    }

    fun dismissCleanup() { _cleanup.value = null }

    // ---- Call recordings folder ----

    private val _calls = MutableStateFlow<List<CallRecordingFile>>(emptyList())
    val calls: StateFlow<List<CallRecordingFile>> = _calls.asStateFlow()
    private val _callsLoading = MutableStateFlow(false)
    val callsLoading: StateFlow<Boolean> = _callsLoading.asStateFlow()

    fun callsConnected(): Boolean = CallFolder.hasAccess(getApplication(), settings.value.callsFolder)

    fun connectCallsFolder(tree: Uri) {
        runCatching { CallFolder.remember(getApplication(), tree) }
        updateSettings { it.copy(callsFolder = tree.toString()) }
        refreshCalls()
    }

    fun refreshCalls() = viewModelScope.launch {
        val tree = settings.value.callsFolder
        if (!callsConnected()) { _calls.value = emptyList(); return@launch }
        _callsLoading.value = true
        _calls.value = withContext(Dispatchers.IO) {
            runCatching { CallFolder.list(getApplication(), Uri.parse(tree)) }.getOrDefault(emptyList())
        }
        _callsLoading.value = false
    }

    /** Converts a recording from the connected folder. The original file is only read. */
    fun convertCall(context: Context, call: CallRecordingFile, language: String) =
        ImportController.startFromFolder(context, call.uri, call.fileName, language)

    // ---- Crash report from the last run ----

    private val _crash = MutableStateFlow(readCrash())
    val crash: StateFlow<String?> = _crash.asStateFlow()

    private fun readCrash(): String? = runCatching {
        getApplication<IdeaDropApp>().crashFile.takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun shareCrash(context: Context) {
        val text = _crash.value ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Send crash report"))
    }

    fun dismissCrash() {
        runCatching { getApplication<IdeaDropApp>().crashFile.delete() }
        _crash.value = null
    }

    // ---- Import audio (call recordings, voice notes…) ----

    /** Copies the picked/shared file into the app, then converts it to text in the background. */
    fun importAudio(context: Context, uri: Uri, language: String) = viewModelScope.launch {
        val app = getApplication<IdeaDropApp>()
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                val name = displayName(context, uri)
                val ext = name.substringAfterLast('.', "m4a").take(5).lowercase()
                val dir = File(app.filesDir, "audio").apply { mkdirs() }
                val file = File(dir, "import_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                file to name
            }.getOrNull()
        }
        if (copied == null) {
            android.widget.Toast.makeText(context, "Couldn't open that file", android.widget.Toast.LENGTH_LONG).show()
        } else {
            ImportController.start(context, copied.first, copied.second, language)
        }
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Audio"

    // ---- Export ----

    private suspend fun markdown(scope: ExportScope): String =
        MarkdownExporter.build(repo.allNotes(), scope, settings.value.exportIncludePrompt)

    /** Opens the Android share sheet (Claude app, Drive, WhatsApp, …) with a .md file. */
    fun share(context: Context, scope: ExportScope) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(dir, MarkdownExporter.fileName()).apply { writeText(markdown(scope)) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share ideas"))
    }

    /** Writes the export to a file the user picked (local backup on the phone). */
    fun saveTo(context: Context, uri: Uri, scope: ExportScope) = viewModelScope.launch {
        val text = markdown(scope)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
