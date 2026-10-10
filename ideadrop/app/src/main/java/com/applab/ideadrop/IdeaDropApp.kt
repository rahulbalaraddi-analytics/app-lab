package com.applab.ideadrop

import android.app.Application
import com.applab.ideadrop.data.AppDatabase
import com.applab.ideadrop.data.NoteRepository
import com.applab.ideadrop.data.SettingsStore
import com.applab.ideadrop.speech.SpeechModel
import com.applab.ideadrop.speech.transcribeWav
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Simple manual dependency container. */
class IdeaDropApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: NoteRepository private set
    lateinit var settings: SettingsStore private set
    lateinit var speechModel: SpeechModel private set

    override fun onCreate() {
        super.onCreate()
        repository = NoteRepository(AppDatabase.create(this).noteDao())
        settings = SettingsStore(this)
        speechModel = SpeechModel(this)
    }

    fun downloadModel() {
        appScope.launch {
            speechModel.download()
            transcribePending()
        }
    }

    /** Transcribes clips that were recorded before the speech model was available. */
    fun transcribePending() {
        appScope.launch {
            val model = speechModel.load() ?: return@launch
            repository.recordingsNeedingTranscription().forEach { rec ->
                val path = rec.filePath ?: return@forEach
                val text = runCatching { transcribeWav(model, File(path)) }.getOrDefault("")
                repository.applyTranscript(rec, text)
            }
        }
    }
}
