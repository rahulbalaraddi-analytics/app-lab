package com.applab.ideadrop.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface RecState {
    data object Idle : RecState
    /** [noteId] is set when adding a clip to an existing note. */
    data class Recording(val startedAt: Long, val liveText: String, val noteId: Long?) : RecState
    data object Saving : RecState
}

/** App-wide recording state the UI observes. */
object RecordingController {
    private val _state = MutableStateFlow<RecState>(RecState.Idle)
    val state: StateFlow<RecState> = _state.asStateFlow()
    internal fun set(s: RecState) { _state.value = s }

    fun start(context: Context, noteId: Long? = null) {
        if (_state.value != RecState.Idle) return
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_NOTE_ID, noteId ?: -1L)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }
}

/**
 * Foreground service so recording keeps going with the screen locked (driving, bedtime).
 * Saves the note automatically when it stops — no taps needed.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (job == null) {
                    val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
                    goForeground()
                    job = scope.launch { record(noteId) }
                }
            }
            ACTION_STOP -> {
                stopRequested = true
                if (job == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("IdeaDrop is listening")
            .setContentText("Speak your idea. Tap Stop & save when done.")
            .setContentIntent(open)
            .addAction(0, "Stop & save", stop)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun record(noteId: Long?) {
        val app = application as IdeaDropApp
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            finish(); return
        }

        val startedAt = System.currentTimeMillis()
        RecordingController.set(RecState.Recording(startedAt, "", noteId))
        val settings = app.settings.state.value
        val speech = PhoneSpeech(this, settings.language, settings.myWordsList()) { live ->
            if (RecordingController.state.value is RecState.Recording) {
                RecordingController.set(RecState.Recording(startedAt, live, noteId))
            }
        }
        fun timeUp() = System.currentTimeMillis() - startedAt > MAX_DURATION_MS

        var audioFile: File? = null
        val tryKeepAudio = Build.VERSION.SDK_INT >= 33 && settings.audioStreaming != 0
        val stream = if (tryKeepAudio) speech.startWithAudioSource() else null

        if (stream != null) {
            // Record audio ourselves (kept for playback) and stream it to the speech engine
            val failed = AtomicBoolean(false)
            speech.onAudioSourceFailed = { failed.set(true) }
            val dir = File(filesDir, "audio").apply { mkdirs() }
            val file = File(dir, "idea_$startedAt.wav")
            val pcm = Channel<ByteArray>(256, BufferOverflow.DROP_OLDEST)
            val pipeWriter = scope.launch {
                try { for (chunk in pcm) stream.write(chunk) } catch (_: IOException) {
                } finally { runCatching { stream.close() } }
            }
            val minBuf = AudioRecord.getMinBufferSize(
                PhoneSpeech.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                PhoneSpeech.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192) * 2,
            )
            val writer = WavWriter(file, PhoneSpeech.SAMPLE_RATE)
            try {
                recorder.startRecording()
                val buf = ByteArray(3200) // 100 ms
                while (scope.isActive && !stopRequested && !failed.get() && !timeUp()) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    writer.write(buf, n)
                    pcm.trySend(buf.copyOf(n))
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                writer.close()
                pcm.close()
            }
            if (failed.get()) {
                // This phone's engine doesn't accept streamed audio: text-only from now on
                app.settings.update { it.copy(audioStreaming = 0) }
                file.delete()
                speech.startWithMic()
                while (scope.isActive && !stopRequested && !timeUp()) delay(100)
            } else {
                audioFile = file
                // Let the last audio reach the engine (never block forever if it stalls)
                withTimeoutOrNull(1_500) { pipeWriter.join() }
            }
        } else {
            // Text only: the engine listens to the microphone directly
            speech.startWithMic()
            while (scope.isActive && !stopRequested && !timeUp()) delay(100)
        }

        RecordingController.set(RecState.Saving)
        val text = speech.finish()
        if (audioFile != null && speech.gotText && settings.audioStreaming != 1) {
            app.settings.update { it.copy(audioStreaming = 1) }
        }
        val durationMs = System.currentTimeMillis() - startedAt

        if (durationMs < MIN_DURATION_MS && text.isBlank()) {
            audioFile?.delete() // accidental tap
        } else if (noteId != null) {
            app.repository.appendRecording(noteId, text, audioFile, durationMs)
        } else {
            app.repository.createFromRecording(text, audioFile, durationMs)
        }
        finish()
    }

    private fun finish() {
        RecordingController.set(RecState.Idle)
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRequested = true
        scope.cancel()
        if (RecordingController.state.value !is RecState.Idle) RecordingController.set(RecState.Idle)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.applab.ideadrop.START"
        const val ACTION_STOP = "com.applab.ideadrop.STOP"
        const val EXTRA_NOTE_ID = "noteId"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val MAX_DURATION_MS = 10 * 60 * 1000L
        private const val MIN_DURATION_MS = 800L
    }
}
