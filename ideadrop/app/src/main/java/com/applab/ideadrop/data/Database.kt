package com.applab.ideadrop.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One idea. Can hold several voice recordings ("speak again under the same bucket"). */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val category: String = "",
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One voice clip attached to a note. Audio may be cleaned up later; the transcript stays. */
@Entity(
    tableName = "recordings",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("noteId")],
)
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    /** Absolute path of the WAV file, or null once the audio has been cleaned up. */
    val filePath: String?,
    val durationMs: Long,
    val transcript: String,
    /** Legacy (v0.1 engine). Always false now. */
    val needsTranscription: Boolean = false,
    val createdAt: Long,
)

data class NoteWithRecordings(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val recordings: List<Recording>,
)

@Dao
interface NoteDao {
    @Transaction
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteWithRecordings>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteWithRecordings?>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY createdAt ASC")
    suspend fun getAll(): List<NoteWithRecordings>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT DISTINCT category FROM notes WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Insert suspend fun insert(note: Note): Long
    @Update suspend fun update(note: Note)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)

    @Insert suspend fun insertRecording(recording: Recording): Long
    @Update suspend fun updateRecording(recording: Recording)

    @Query("SELECT * FROM recordings WHERE noteId = :noteId")
    suspend fun recordingsFor(noteId: Long): List<Recording>

    @Query("SELECT * FROM recordings WHERE filePath IS NOT NULL AND createdAt < :before")
    suspend fun recordingsWithAudioBefore(before: Long): List<Recording>
}

@Database(entities = [Note::class, Recording::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ideadrop.db").build()
    }
}
