package com.applab.ideadrop.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** One call recording in the phone's call-recordings folder. */
data class CallRecordingFile(
    val uri: Uri,
    val fileName: String,
    val contact: String,
    val modified: Long,
    val sizeBytes: Long,
)

/**
 * Reads the phone's call-recordings folder (Samsung: Internal storage › Recordings › Call)
 * after the user connects it once. Read-only: IdeaDrop never changes or deletes these files.
 */
object CallFolder {
    private const val EXTERNAL = "com.android.externalstorage.documents"

    /** Where the folder picker should open: Samsung's call recordings folder. */
    val samsungCallsFolder: Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL, "primary:Recordings/Call")

    /** Keep access after the phone restarts. */
    fun remember(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun hasAccess(context: Context, tree: String): Boolean =
        tree.isNotBlank() && context.contentResolver.persistedUriPermissions.any { it.uri.toString() == tree && it.isReadPermission }

    /** Newest first. Looks one level into sub-folders too (some phones file calls by date). */
    fun list(context: Context, tree: Uri): List<CallRecordingFile> {
        val out = mutableListOf<CallRecordingFile>()
        fun scan(parentDocId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 1) scan(id, depth + 1)
                        continue
                    }
                    val isAudio = mime.startsWith("audio/") ||
                        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
                    if (!isAudio) continue
                    out += CallRecordingFile(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        fileName = name,
                        contact = CallName.parse(name).contact,
                        modified = c.getLong(3),
                        sizeBytes = c.getLong(4),
                    )
                }
            }
        }
        scan(DocumentsContract.getTreeDocumentId(tree), 0)
        return out.sortedByDescending { it.modified }
    }

    private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "amr", "aac", "3gp", "ogg", "opus", "wav")
}
