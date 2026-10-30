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
        audio: File?,
        durationMs: Long,
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
                filePath = audio?.absolutePath,
                durationMs = durationMs,
                transcript = transcript,
                createdAt = now,
            )
        )
        return noteId
    }

    /** A note made from an imported audio file (e.g. a call recording). */
    suspend fun createFromImport(transcript: String, audio: File, durationMs: Long, sourceName: String): Long {
        val now = System.currentTimeMillis()
        val noteId = dao.insert(
            Note(
                title = titleFromFileName(sourceName).ifBlank { autoTitle(transcript, now) },
                text = transcript,
                category = IMPORT_CATEGORY,
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
                createdAt = now,
            )
        )
        return noteId
    }

    /** "Speak again under the same bucket": adds a clip and appends its text to the note. */
    suspend fun appendRecording(
        noteId: Long,
        transcript: String,
        audio: File?,
        durationMs: Long,
    ) {
        val note = dao.get(noteId) ?: run {
            createFromRecording(transcript, audio, durationMs)
            return
        }
        val now = System.currentTimeMillis()
        dao.insertRecording(
            Recording(
                noteId = noteId,
                filePath = audio?.absolutePath,
                durationMs = durationMs,
                transcript = transcript,
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
        const val IMPORT_CATEGORY = "Calls"

        /** "Call recording Amma_241030_153012.m4a" → "Call recording Amma 241030 153012" */
        fun titleFromFileName(name: String): String =
            name.substringBeforeLast('.').replace(Regex("[_]+"), " ").replace(Regex("\\s+"), " ").trim().take(60)
        private const val TITLE_PREFIX = "Idea · "

        /** Openers that make bad titles ("I want to…", "Remind me to…"). */
        private val FILLER = Regex(
            "^(?:(?:um+|uh+|hmm+|so|okay|ok|hey|right|basically|actually|also|and|" +
                "i think|i was thinking|i want to|i wanna|i need to|i have to|i should|" +
                "we need to|we should|let's|remind me to|note to self|" +
                "i have an idea|an idea|idea|my idea is)\\b[,.:]?\\s*)+",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Short, readable title from the first sentence, without filler openers.
         * "I want to streamline the onboarding flow for the Guidewire project."
         *   → "Streamline the onboarding flow for the Guidewire…"
         */
        fun autoTitle(transcript: String, time: Long): String {
            val firstSentence = transcript.trim().split(Regex("(?<=[.!?])\\s+")).firstOrNull().orEmpty()
            val cleaned = firstSentence.replace(FILLER, "").trim().trimEnd('.', '!', '?', ',')
            val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                return TITLE_PREFIX + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(time))
            }
            val title = words.take(7).joinToString(" ")
            return title.replaceFirstChar { it.uppercase() } + if (words.size > 7) "…" else ""
        }

        private fun joinText(existing: String, addition: String): String = when {
            addition.isBlank() -> existing
            existing.isBlank() -> addition
            else -> existing.trimEnd() + "\n\n" + addition
        }
    }
}
