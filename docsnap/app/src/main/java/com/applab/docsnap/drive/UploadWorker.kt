package com.applab.docsnap.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.applab.docsnap.Notifier
import com.applab.docsnap.data.Store
import java.io.File

/**
 * Uploads every named, not-yet-uploaded PDF to its Drive folder, then refreshes the
 * per-person index CSVs and the analytics logs. Runs in the background; the app never waits on it.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val notifier = Notifier(applicationContext)
        val rootId = store.rootFolderId ?: return Result.failure()

        val token = DriveAuth.silentToken(applicationContext)
        if (token == null) {
            store.event("upload_needs_signin", "")
            notifier.needsSignIn()
            return Result.failure()
        }
        val drive = DriveApi(token)

        var done = 0
        var failed = 0
        val touchedFolders = linkedMapOf<String, String>() // folderId -> name
        // Loop until nothing is left, so documents saved while uploading also go up.
        var round = 0
        while (round++ < 20) {
            val pending = store.pendingUploads().filter { it.attempts < MAX_ATTEMPTS_PER_DOC || inputData.getBoolean(KEY_FORCE, false) }
            if (pending.isEmpty()) break
            val total = done + failed + pending.size
            for (p in pending) {
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FAILED to failed))
                notifier.uploadProgress(done, total)
                try {
                    val existing = drive.findBySha(p.folderId, p.sha256)
                    val id = existing ?: drive.uploadFile(
                        p.folderId, p.fileName, p.pdf, "application/pdf",
                        mapOf("sha256" to p.sha256, "person" to store.initials, "app" to "docsnap"),
                    )
                    store.markUploaded(p, id, duplicate = existing != null)
                    touchedFolders[p.folderId] = p.folderName
                    done++
                } catch (e: Exception) {
                    failed++
                    store.markFailed(p, e.message ?: e.javaClass.simpleName)
                    if (e is DriveException && (e.code == 401 || e.code == 403)) break
                }
            }
            if (failed > 0) break
        }

        // Index CSVs: one per person per folder, rewritten from the phone's own records.
        for ((folderId, _) in touchedFolders) {
            runCatching {
                drive.upsertText(folderId, "_index_${store.initials}.csv", store.indexCsv(folderId).toByteArray(), "text/csv")
            }.onFailure { store.event("index_upload_failed", it.message ?: "") }
        }

        // Analytics: device info + all logs into _DocSnap_Analytics
        runCatching {
            store.writeDeviceInfo()
            val analyticsId = drive.ensureFolder(rootId, ANALYTICS_FOLDER)
            store.analyticsDir.listFiles()?.forEach { f: File ->
                val mime = if (f.name.endsWith(".json")) "application/json" else "text/csv"
                drive.upsertText(analyticsId, f.name, f.readBytes(), mime)
            }
        }.onFailure { store.event("analytics_upload_failed", it.message ?: "") }

        notifier.clearProgress()
        val folderLabel = touchedFolders.values.distinct().let { if (it.size == 1) it.first() else "${it.size} folders" }
        if (done > 0) notifier.uploadDone(done, done + failed, folderLabel)
        if (failed > 0) notifier.uploadFailed(failed, needsYou = runAttemptCount >= 3)
        store.lastUploadAt = System.currentTimeMillis()

        return when {
            failed == 0 -> Result.success(workDataOf(KEY_DONE to done, KEY_TOTAL to done))
            runAttemptCount < 5 -> Result.retry()
            else -> Result.failure(workDataOf(KEY_DONE to done, KEY_FAILED to failed))
        }
    }

    companion object {
        const val WORK_NAME = "docsnap-upload"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
        const val KEY_FORCE = "force"
        const val ANALYTICS_FOLDER = "_DocSnap_Analytics"
        private const val MAX_ATTEMPTS_PER_DOC = 10

        /** "Upload all" (manual) or after each save (auto). Waits for Wi-Fi unless mobile data is allowed. */
        fun enqueue(context: Context, allowMobileData: Boolean, force: Boolean = false) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .build()
                )
                .setInputData(workDataOf(KEY_FORCE to force))
                .build()
            // Manual "Upload all" queues a fresh run after any current one (new settings apply);
            // automatic uploads after a save just join the run that is already waiting.
            val policy = if (force) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, req)
        }
    }
}
