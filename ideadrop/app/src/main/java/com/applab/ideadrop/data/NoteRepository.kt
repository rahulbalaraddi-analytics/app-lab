package com.applab.ideadrop.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    val notes: Flow<List<NoteWithRecordings>> = dao.observeAll()
    val categories: Flow<List<String>> = dao.observeCategories()

    fun note(id: Long): Flow<NoteWithRecordings?> = dao.observe(id)

    suspend fun allNotes(): List<NoteWithRecordings> = dao.getAll()

    /** Called by the recording service when a new idea is recorded. Saves with zero taps. */
    suspend fun createFromRecording(
        transcript: String,
        audio: File,
        durationMs: Long,
        needsTranscription: Boolean,
    ): Long {
        val now = System.currentTimeMillis()
        val noteId = dao.insert(
            Note(
                title = autoTitle(transcript, now),
                text = transcript,
                createdAt = now,
                updatedAt = now,
            )
        )
        dao.insertRecording(
            Recording(
                noteId = noteId,
                filePath = audio.absolutePath,
                durationMs = durationMs,
                transcript = transcript,
                needsTranscription = needsTranscription,
                createdAt = now,
            )
        )
        return noteId
    }

    /** "Speak again under the same bucket": adds a clip and appends its text to the note. */
    suspend fun appendRecording(
        noteId: Long,
        transcript: String,
        audio: File,
        durationMs: Long,
        needsTranscription: Boolean,
    ) {
        val note = dao.get(noteId) ?: run {
            createFromRecording(transcript, audio, durationMs, needsTranscription)
            return
        }
        val now = System.currentTimeMillis()
        dao.insertRecording(
            Recording(
                noteId = noteId,
                filePath = audio.absolutePath,
                durationMs = durationMs,
                transcript = transcript,
                needsTranscription = needsTranscription,
                createdAt = now,
            )
        )
        dao.update(note.copy(text = joinText(note.text, transcript), updatedAt = now))
    }

    suspend fun save(note: Note) = dao.update(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun setDone(note: Note, done: Boolean) =
        dao.update(note.copy(done = done, updatedAt = System.currentTimeMillis()))

    suspend fun delete(noteId: Long) {
        dao.recordingsFor(noteId).forEach { it.filePath?.let { p -> File(p).delete() } }
        dao.delete(noteId)
    }

    // ---- Transcribing clips recorded before the speech model was downloaded ----

    suspend fun recordingsNeedingTranscription(): List<Recording> = dao.recordingsNeedingTranscription()

    suspend fun applyTranscript(recording: Recording, transcript: String) {
        dao.updateRecording(recording.copy(transcript = transcript, needsTranscription = false))
        val note = dao.get(recording.noteId) ?: return
        val now = System.currentTimeMillis()
        val placeholder = note.title.startsWith(TITLE_PREFIX) && note.text.isBlank()
        dao.update(
            note.copy(
                text = joinText(note.text, transcript),
                title = if (placeholder) autoTitle(transcript, note.createdAt) else note.title,
                updatedAt = now,
            )
        )
    }

    // ---- Audio cleanup (always asked first, text is kept) ----

    suspend fun recordingsOlderThan(days: Int): List<Recording> {
        val cutoff = System.currentTimeMillis() - days * DAY_MS
        return dao.recordingsWithAudioBefore(cutoff)
    }

    suspend fun deleteAudio(recordings: List<Recording>) {
        recordings.forEach { rec ->
            rec.filePath?.let { File(it).delete() }
            dao.updateRecording(rec.copy(filePath = null))
        }
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        private const val TITLE_PREFIX = "Idea · "

        fun autoTitle(transcript: String, time: Long): String {
            val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                return TITLE_PREFIX + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(time))
            }
            val title = words.take(6).joinToString(" ")
            return title.replaceFirstChar { it.uppercase() } + if (words.size > 6) "…" else ""
        }

        private fun joinText(existing: String, addition: String): String = when {
            addition.isBlank() -> existing
            existing.isBlank() -> addition
            else -> existing.trimEnd() + "\n\n" + addition
        }
    }
}
