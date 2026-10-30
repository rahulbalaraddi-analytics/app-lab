package com.applab.ideadrop.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.applab.ideadrop.IdeaDropApp
import com.applab.ideadrop.R
import com.applab.ideadrop.speech.PhoneSpeech
import com.applab.ideadrop.ui.MainActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val name: String, val progress: Float, val liveText: String) : ImportState
    data class Done(val name: String, val noteId: Long?, val message: String) : ImportState
}

/** App-wide state of the "Import audio" job, observed by the UI. */
object ImportController {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    internal fun set(s: ImportState) { _state.value = s }
    fun clear() { if (_state.value is ImportState.Done) _state.value = ImportState.Idle }

    /** A file the app copied in (picked or shared). It becomes the note's audio clip. */
    fun start(context: Context, file: File, displayName: String, language: String) =
        launch(context, Uri.fromFile(file).toString(), displayName, language, sourceUri = null)

    /** A recording in the connected call-recordings folder. Read in place, never copied or changed. */
    fun startFromFolder(context: Context, uri: Uri, displayName: String, language: String) =
        launch(context, uri.toString(), displayName, language, sourceUri = uri.toString())

    private fun launch(context: Context, uri: String, displayName: String, language: String, sourceUri: String?) {
        if (_state.value is ImportState.Working) return
        val intent = Intent(context, ImportService::class.java)
            .setAction(ImportService.ACTION_START)
            .putExtra(ImportService.EXTRA_URI, uri)
            .putExtra(ImportService.EXTRA_SOURCE, sourceUri)
            .putExtra(ImportService.EXTRA_NAME, displayName)
            .putExtra(ImportService.EXTRA_LANGUAGE, language)
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancel(context: Context) {
        context.startService(Intent(context, ImportService::class.java).setAction(ImportService.ACTION_CANCEL))
    }
}

/**
 * Turns an audio file (e.g. a call recording) into a text note: decodes it and streams it
 * to the phone's speech engine. Runs as a foreground service so long calls keep going
 * with the screen off.
 */
class ImportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    @Volatile private var stream: java.io.OutputStream? = null
    private lateinit var notifications: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Audio"
                // Android requires startForeground() after every startForegroundService(), even
                // when there's nothing new to do — skipping it closes the app.
                try {
                    goForeground(name)
                } catch (t: Throwable) {
                    ImportController.set(ImportState.Done(name, null, "Couldn't start: ${t.javaClass.simpleName} ${t.message ?: ""}"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (job != null) return START_NOT_STICKY // already converting
                val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
                if (uri == null) { finishService(); return START_NOT_STICKY }
                val source = intent.getStringExtra(EXTRA_SOURCE)
                val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: PhoneSpeech.MIXED
                cancelled.set(false)
                job = scope.launch {
                    try {
                        run(uri, source, name, language)
                    } catch (t: Throwable) {
                        // Never crash the app: report the problem on the Home screen instead
                        ImportController.set(ImportState.Done(name, null, "Couldn't convert: ${t.javaClass.simpleName} ${t.message ?: ""}"))
                        finishService()
                    }
                }
            }
            ACTION_CANCEL -> {
                cancelled.set(true)
                runCatching { stream?.close() } // unblocks a write waiting on the engine
                if (job == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(name: String, progress: Int): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(
            this, 2, Intent(this, ImportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Converting audio to text")
            .setContentText(name)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun goForeground(name: String) {
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audio import", NotificationManager.IMPORTANCE_LOW)
        )
        val n = notification(name, 0)
        // Try the newest service type first, fall back to older ones (some phones reject a type)
        val types = buildList {
            if (Build.VERSION.SDK_INT >= 35) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            add(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        var lastError: Throwable? = null
        for (t in types) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, n, t); return
            } catch (e: Throwable) { lastError = e }
        }
        throw lastError ?: IllegalStateException("Couldn't start background work")
    }

    private suspend fun run(uri: Uri, sourceUri: String?, name: String, language: String) {
        // Files the app copied in are deleted on failure; folder recordings are never touched
        val ownCopy: File? = if (uri.scheme == "file") File(uri.path!!) else null
        val audioPath = ownCopy?.absolutePath ?: uri.toString()
        val app = application as IdeaDropApp
        ImportController.set(ImportState.Working(name, 0f, ""))
        var progress = 0f
        var lastShown = -1

        val speech = PhoneSpeech(this, language, app.settings.state.value.myWordsList()) { live ->
            ImportController.set(ImportState.Working(name, progress, live))
        }
        val engineRefused = AtomicBoolean(false)
        speech.onAudioSourceFailed = { engineRefused.set(true) }

        val result: ImportState.Done = try {
            val decoder = AudioFileDecoder(this, uri)
            val stream = speech.startWithAudioSource()
                ?: throw UnsupportedOperationException("Needs Android 13 or newer")
            this.stream = stream
            try {
                decoder.decode(
                    keepGoing = { scope.isActive && !cancelled.get() && !engineRefused.get() },
                    onProgress = { p ->
                        progress = p
                        val pct = (p * 100).toInt()
                        if (pct != lastShown) {
                            lastShown = pct
                            notifications.notify(NOTIFICATION_ID, notification(name, pct))
                            ImportController.set(ImportState.Working(name, p, speech.text()))
                        }
                    },
                    // Blocks while the engine catches up, so the file is read at the engine's pace
                    onPcm = { pcm -> stream.write(pcm) },
                )
            } catch (_: IOException) {
                // Pipe closed: the engine stopped reading (refused or cancelled)
            } finally {
                runCatching { stream.close() }
            }
            val text = speech.finish(waitMs = 30_000)
            when {
                cancelled.get() -> {
                    ownCopy?.delete()
                    ImportState.Done(name, null, "Cancelled")
                }
                engineRefused.get() && text.isBlank() -> {
                    ownCopy?.delete()
                    ImportState.Done(
                        name, null,
                        "Your phone's speech engine can't read audio files. " +
                            "Try installing \"Speech Recognition & Synthesis from Google\" and set it as default."
                    )
                }
                else -> {
                    val noteId = app.repository.createFromImport(
                        transcript = text,
                        audioPath = audioPath,
                        durationMs = decoder.durationMs,
                        sourceName = name,
                        sourceUri = sourceUri,
                    )
                    ImportState.Done(
                        name, noteId,
                        if (text.isBlank()) "No speech recognised. Audio saved; try another language in Settings."
                        else "Converted to text"
                    )
                }
            }
        } catch (e: Exception) {
            speech.finish(waitMs = 1_000)
            ownCopy?.delete()
            ImportState.Done(name, null, "Couldn't read this file: ${e.message ?: "unknown error"}")
        }

        ImportController.set(result)
        finishService()
    }

    private fun finishService() {
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelled.set(true)
        scope.cancel()
        if (ImportController.state.value is ImportState.Working) {
            ImportController.set(ImportState.Done("", null, "Stopped"))
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.applab.ideadrop.IMPORT"
        const val ACTION_CANCEL = "com.applab.ideadrop.IMPORT_CANCEL"
        const val EXTRA_URI = "uri"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_NAME = "name"
        const val EXTRA_LANGUAGE = "language"
        private const val CHANNEL_ID = "import"
        private const val NOTIFICATION_ID = 2
    }
}
