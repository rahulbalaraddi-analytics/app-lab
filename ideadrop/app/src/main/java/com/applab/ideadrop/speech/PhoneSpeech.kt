package com.applab.ideadrop.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Speech-to-text using the phone's built-in engine (Google / Samsung), the same one
 * behind the keyboard mic. Free, no download, works offline when the language pack
 * is installed, and supports English (India), Hindi and Kannada.
 *
 * Two modes:
 *  - AUDIO_SOURCE (Android 13+): the app records the audio itself and streams it to the
 *    engine, so the original recording is kept.
 *  - MIC: the engine uses the microphone directly (text only). Used when the phone's
 *    engine doesn't accept streamed audio.
 */
class PhoneSpeech(
    private val context: Context,
    private val language: String,
    private val myWords: List<String>,
    private val onLiveText: (String) -> Unit,
) {
    enum class Mode { AUDIO_SOURCE, MIC }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val segments = mutableListOf<String>()
    @Volatile private var partial = ""
    @Volatile private var active = false
    @Volatile var mode: Mode? = null; private set
    @Volatile var gotText = false; private set
    private var pipeRead: ParcelFileDescriptor? = null
    private var preferOffline = true
    private var restarts = 0
    private var ended = CompletableDeferred<Unit>()

    /** Called (main thread) if the engine rejects streamed audio. Caller then switches to [startWithMic]. */
    var onAudioSourceFailed: (() -> Unit)? = null

    fun text(): String = (segments + partial).filter { it.isNotBlank() }.joinToString(" ").trim()

    /** Starts AUDIO_SOURCE mode. Returns the stream to write 16 kHz mono PCM into, or null if unsupported. */
    fun startWithAudioSource(): OutputStream? {
        if (Build.VERSION.SDK_INT < 33) return null
        val (read, write) = ParcelFileDescriptor.createPipe()
        pipeRead = read
        mode = Mode.AUDIO_SOURCE
        active = true
        main.post { begin() }
        return ParcelFileDescriptor.AutoCloseOutputStream(write)
    }

    fun startWithMic() {
        closePipe()
        mode = Mode.MIC
        active = true
        ended = CompletableDeferred()
        main.post { begin() }
    }

    /** Stops listening and returns the full text. In AUDIO_SOURCE mode, close the audio stream first. */
    suspend fun finish(waitMs: Long = 4_000): String {
        active = false
        if (mode == Mode.MIC) main.post { recognizer?.stopListening() }
        withTimeoutOrNull(waitMs) { ended.await() }
        main.post { recognizer?.destroy(); recognizer = null }
        closePipe()
        partial = ""
        return segments.joinToString(" ").trim()
    }

    private fun begin() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        r.startListening(buildIntent())
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == MIXED) "en-IN" else language)
        if (language == MIXED && Build.VERSION.SDK_INT >= 34) {
            // Android 14+: detect and switch between English, Kannada and Hindi mid-conversation
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, MIXED_LANGUAGES)
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, MIXED_LANGUAGES)
        }
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
        if (Build.VERSION.SDK_INT >= 33) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            if (myWords.isNotEmpty()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(myWords))
            }
            if (mode == Mode.AUDIO_SOURCE) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipeRead)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
                // Keep one session going until the audio stream ends
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
        }
    }

    private fun addSegment(text: String?) {
        if (text.isNullOrBlank()) return
        segments += text.trim()
        gotText = true
        partial = ""
        onLiveText(text())
    }

    private fun firstResult(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partial = firstResult(partialResults).orEmpty()
            onLiveText(text())
        }

        override fun onSegmentResults(segmentResults: Bundle) = addSegment(firstResult(segmentResults))

        override fun onEndOfSegmentedSession() { ended.complete(Unit) }

        override fun onResults(results: Bundle?) {
            addSegment(firstResult(results))
            if (active) {
                // Engine ended after a pause: keep listening (same audio stream in AUDIO_SOURCE mode)
                main.postDelayed({ if (active) begin() }, 150)
            } else {
                ended.complete(Unit)
            }
        }

        override fun onError(error: Int) {
            val languageProblem = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            if (languageProblem && preferOffline) {
                // Offline pack missing for this language: fall back to the online engine
                preferOffline = false
                main.post { begin() }
                return
            }
            when (mode) {
                Mode.AUDIO_SOURCE -> {
                    val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (!gotText && active && !silence) {
                        closePipe() // unblocks the audio writer
                        onAudioSourceFailed?.invoke()
                    } else if (active && restarts < MAX_RESTARTS) {
                        restarts++
                        main.postDelayed({ if (active) begin() }, 200)
                    } else {
                        ended.complete(Unit)
                    }
                }
                Mode.MIC -> {
                    if (active && restarts < MAX_RESTARTS) {
                        restarts++
                        val wait = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600L else 200L
                        main.postDelayed({ if (active) begin() }, wait)
                    } else {
                        ended.complete(Unit)
                    }
                }
                null -> ended.complete(Unit)
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun closePipe() {
        runCatching { pipeRead?.close() }
        pipeRead = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        /** English + Kannada + Hindi, switching mid-sentence (Android 14+). */
        const val MIXED = "mixed"
        private val MIXED_LANGUAGES = arrayListOf("en-IN", "kn-IN", "hi-IN")
        private const val MAX_RESTARTS = 500

        fun isAvailable(context: Context) = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
