package com.applab.docsnap.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.applab.docsnap.data.Draft
import com.applab.docsnap.data.Folder
import com.applab.docsnap.data.Mode
import com.applab.docsnap.data.Store
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** Multi-page documents are scanned in batches of this many pages; each batch is saved before the next. */
const val BATCH = 5

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent { DocSnapTheme { App(this, store) } }
    }
}

@Composable
private fun App(activity: Activity, store: Store) {
    // Route survives the scanner activity and process death: "home", "folder:<name>",
    // "strip:<draftId>", "name:<draftId>", "phone", "feedback"
    var route by rememberSaveable { mutableStateOf("home") }
    var scanningDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var askInitials by remember { mutableStateOf(store.initials.isBlank()) }
    var recovery by remember { mutableStateOf<Draft?>(null) }
    var help by remember { mutableStateOf<HelpTopic?>(null) }
    var tutorial by rememberSaveable { mutableStateOf(!store.tutorialSeen) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        recovery = store.unfinishedDrafts().firstOrNull()
        if (store.initials.isNotBlank()) store.writeDeviceInfo()
    }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val id = scanningDraft ?: return@rememberLauncherForActivityResult
        val draft = store.draft(id) ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.map { it.imageUri }.orEmpty()
            val updated = runCatching { store.addPages(id, pages) }.getOrElse {
                store.event("save_pages_failed", it.message ?: "")
                Toast.makeText(activity, "Could not save pages. Try again.", Toast.LENGTH_LONG).show()
                draft
            }
            route = if (draft.mode == Mode.SINGLE && updated.pages.isNotEmpty()) "name:$id" else "strip:$id"
        } else {
            // Cancelled: keep whatever was already saved
            route = when {
                draft.pages.isEmpty() -> { store.discardDraft(id); "folder:${draft.folder.name}" }
                draft.mode == Mode.SINGLE -> "name:$id"
                else -> "strip:$id"
            }
        }
        tick++
    }

    fun launchScanner(draftId: String, limit: Int) {
        scanningDraft = draftId
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setGalleryImportAllowed(true)
            .setPageLimit(limit)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener {
                store.event("scanner_failed", it.message ?: "")
                Toast.makeText(activity, "Scanner not ready: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    fun startDoc(folder: Folder, mode: Mode) {
        val d = store.newDraft(folder, mode)
        launchScanner(d.id, if (mode == Mode.SINGLE) 1 else BATCH)
    }

    fun folderOf(name: String) = store.folders().firstOrNull { it.name == name } ?: store.folders().first()

    if (tutorial) {
        TutorialScreen {
            tutorial = false
            store.tutorialSeen = true
            if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        return
    }

    help?.let { t -> HelpSheet(t, onClose = { help = null }, onTutorial = { help = null; tutorial = true }) }

    when {
        askInitials -> InitialsDialog(store.initials) {
            store.initials = it
            store.writeDeviceInfo()
            askInitials = false
        }
        recovery != null -> RecoveryDialog(
            draft = recovery!!,
            onContinue = {
                val d = recovery!!
                recovery = null
                route = if (d.mode == Mode.SINGLE) "name:${d.id}" else "strip:${d.id}"
                store.event("draft_recovered", "${d.id} ${d.pages.size} pages")
            },
            onDiscard = { store.discardDraft(recovery!!.id); recovery = store.unfinishedDrafts().firstOrNull() },
        )
    }

    val r = route
    when {
        r == "home" -> HomeScreen(
            store = store, tick = tick,
            onFolder = { route = "folder:${it.name}" },
            onPhone = { route = "phone" },
            onFeedback = { route = "feedback" },
            onHelp = { help = HelpTopic.HOME },
        )
        r.startsWith("folder:") -> FolderScreen(
            store = store, folder = folderOf(r.removePrefix("folder:")), tick = tick,
            onBack = { route = "home" },
            onStart = { f, m -> startDoc(f, m) },
            onHelp = { help = HelpTopic.FOLDER },
        )
        r.startsWith("strip:") -> {
            val d = store.draft(r.removePrefix("strip:"))
            if (d == null) route = "home" else StripScreen(
                store = store, draftId = d.id, tick = tick,
                onScanMore = { launchScanner(d.id, BATCH) },
                onDone = { route = "name:${d.id}" },
                onCancel = { store.discardDraft(d.id); route = "folder:${d.folder.name}" },
                onHelp = { help = HelpTopic.PAGES },
            )
        }
        r.startsWith("name:") -> {
            val d = store.draft(r.removePrefix("name:"))
            if (d == null) route = "home" else NameScreen(
                store = store, draft = d,
                onSaved = { doc ->
                    Toast.makeText(activity, "Saved: ${doc.fileName}", Toast.LENGTH_SHORT).show()
                    tick++
                    if (d.mode == Mode.SINGLE) {
                        // Single page: straight to the next paper
                        route = "folder:${d.folder.name}"
                        startDoc(d.folder, Mode.SINGLE)
                    } else {
                        route = "folder:${d.folder.name}"
                    }
                },
                onHelp = { help = HelpTopic.NAME },
            )
        }
        r == "phone" -> PhoneCheckScreen(store) { route = "home" }
        r == "feedback" -> FeedbackScreen(store) { route = "home" }
        else -> route = "home"
    }
}
