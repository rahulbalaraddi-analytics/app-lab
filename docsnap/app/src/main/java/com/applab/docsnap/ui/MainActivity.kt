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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.applab.docsnap.data.Draft
import com.applab.docsnap.data.Folder
import com.applab.docsnap.data.Mode
import com.applab.docsnap.data.Store
import com.applab.docsnap.drive.DriveApi
import com.applab.docsnap.drive.DriveAuth
import com.applab.docsnap.drive.UploadWorker
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Route survives the scanner activity and process death: "home", "folder:<id>",
    // "strip:<draftId>", "name:<draftId>", "phone", "feedback", "settings", "connect"
    var route by rememberSaveable { mutableStateOf("home") }
    var scanningDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var recovery by remember { mutableStateOf<Draft?>(null) }
    var help by remember { mutableStateOf<HelpTopic?>(null) }
    var tutorial by rememberSaveable { mutableStateOf(!store.tutorialSeen) }
    var refreshing by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(store.isConnected) }
    val scope = rememberCoroutineScope()
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    // ---------------- Google sign-in for Drive
    var pendingToken by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val cb = pendingToken
        pendingToken = null
        val token = if (res.resultCode == Activity.RESULT_OK) DriveAuth.tokenFromIntent(activity, res.data) else null
        if (token != null) cb?.invoke(token) else toast("Google sign-in was cancelled")
    }
    val withToken: WithToken = { block ->
        scope.launch {
            when (val o = DriveAuth.authorize(activity)) {
                is DriveAuth.Outcome.Token -> block(o.value)
                is DriveAuth.Outcome.NeedsUser -> {
                    pendingToken = block
                    authLauncher.launch(IntentSenderRequest.Builder(o.intent.intentSender).build())
                }
                is DriveAuth.Outcome.Failed -> toast("Google sign-in failed: ${o.message}")
            }
        }
    }

    fun refreshFolders(quiet: Boolean) {
        val rootId = store.rootFolderId ?: return
        refreshing = true
        val run: (String) -> Unit = { token ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { DriveApi(token).subfolders(rootId) } }
                    .onSuccess {
                        store.saveFolders(it.map { f -> f.id to f.name })
                        if (!quiet) toast("${store.folders().size} folders loaded")
                    }
                    .onFailure { if (!quiet) toast("Could not refresh: ${it.message}. Showing saved folders.") }
                refreshing = false
                tick++
            }
        }
        if (quiet) {
            // On app open: only if no Google screen is needed
            scope.launch {
                val t = DriveAuth.silentToken(activity)
                if (t != null) run(t) else refreshing = false
            }
        } else withToken(run)
    }

    fun uploadAll() {
        if (store.rootFolderId == null) return
        // Sign in once in the foreground so the background worker can get tokens silently
        withToken { _ ->
            UploadWorker.enqueue(activity, store.allowMobileData, force = true)
            if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            toast(if (store.allowMobileData) "Uploading in the background" else "Uploading on Wi-Fi in the background")
        }
    }

    // ---------------- upload status for the Home card
    val infos by remember { WorkManager.getInstance(activity).getWorkInfosForUniqueWorkFlow(UploadWorker.WORK_NAME) }
        .collectAsState(initial = emptyList())
    val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
    val upload = UploadState(
        running = info?.state == WorkInfo.State.RUNNING,
        waitingForNetwork = info?.state == WorkInfo.State.ENQUEUED,
        done = info?.progress?.getInt(UploadWorker.KEY_DONE, 0) ?: 0,
        total = info?.progress?.getInt(UploadWorker.KEY_TOTAL, 0) ?: 0,
    )
    LaunchedEffect(info?.state) { if (info?.state?.isFinished == true) tick++ }

    LaunchedEffect(Unit) {
        recovery = store.unfinishedDrafts().firstOrNull()
        if (store.isConnected) {
            store.writeDeviceInfo()
            refreshFolders(quiet = true)
        }
    }

    // ---------------- scanner
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
                toast("Could not save pages. Try again.")
                draft
            }
            route = if (draft.mode == Mode.SINGLE && updated.pages.isNotEmpty()) "name:$id" else "strip:$id"
        } else {
            // Cancelled: keep whatever was already saved
            route = when {
                draft.pages.isEmpty() -> { store.discardDraft(id); "folder:${draft.folder.id}" }
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
                toast("Scanner not ready: ${it.message}")
            }
    }

    fun startDoc(folder: Folder, mode: Mode) {
        val d = store.newDraft(folder, mode)
        launchScanner(d.id, if (mode == Mode.SINGLE) 1 else BATCH)
    }

    // ---------------- first launch: tutorial, then connect (mandatory)
    if (tutorial) {
        TutorialScreen {
            tutorial = false
            store.tutorialSeen = true
            if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        return
    }
    if (!connected || route == "connect") {
        ConnectScreen(
            store = store, withToken = withToken,
            onConnected = { connected = true; route = "home"; tick++; toast("Connected: ${store.folders().size} folders") },
            onBack = if (connected) ({ route = "settings" }) else null,
        )
        return
    }

    help?.let { t -> HelpSheet(t, onClose = { help = null }, onTutorial = { help = null; tutorial = true }) }

    recovery?.let { d ->
        RecoveryDialog(
            draft = d,
            onContinue = {
                recovery = null
                route = if (d.mode == Mode.SINGLE) "name:${d.id}" else "strip:${d.id}"
                store.event("draft_recovered", "${d.id} ${d.pages.size} pages")
            },
            onDiscard = { store.discardDraft(d.id); recovery = store.unfinishedDrafts().firstOrNull() },
        )
    }

    val r = route
    when {
        r == "home" -> HomeScreen(
            store = store, tick = tick, upload = upload, refreshing = refreshing,
            onFolder = { route = "folder:${it.id}" },
            onRefresh = { refreshFolders(quiet = false) },
            onUploadAll = { uploadAll() },
            onSettings = { route = "settings" },
            onFeedback = { route = "feedback" },
            onHelp = { help = HelpTopic.HOME },
        )
        r.startsWith("folder:") -> {
            val f = store.folder(r.removePrefix("folder:"))
            if (f == null) LaunchedEffect(r) { route = "home" } else FolderScreen(
                store = store, folder = f, tick = tick,
                onBack = { route = "home" },
                onStart = { folder, m -> startDoc(folder, m) },
                onHelp = { help = HelpTopic.FOLDER },
            )
        }
        r.startsWith("strip:") -> {
            val d = store.draft(r.removePrefix("strip:"))
            if (d == null) LaunchedEffect(r) { route = "home" } else StripScreen(
                store = store, draftId = d.id, tick = tick,
                onScanMore = { launchScanner(d.id, BATCH) },
                onDone = { route = "name:${d.id}" },
                onCancel = { store.discardDraft(d.id); route = "folder:${d.folder.id}" },
                onHelp = { help = HelpTopic.PAGES },
            )
        }
        r.startsWith("name:") -> {
            val d = store.draft(r.removePrefix("name:"))
            if (d == null) LaunchedEffect(r) { route = "home" } else NameScreen(
                store = store, draft = d,
                onSaved = { doc ->
                    Toast.makeText(activity, "Saved: ${doc.fileName}", Toast.LENGTH_SHORT).show()
                    tick++
                    route = "folder:${d.folder.id}"
                    // Single page: straight to the next paper
                    if (d.mode == Mode.SINGLE) startDoc(d.folder, Mode.SINGLE)
                },
                onHelp = { help = HelpTopic.NAME },
            )
        }
        r == "settings" -> SettingsScreen(
            store = store, onBack = { route = "home" },
            onChangeFolder = { route = "connect" },
            onPhone = { route = "phone" },
            onTutorial = { tutorial = true },
        )
        r == "phone" -> PhoneCheckScreen(store) { route = "settings" }
        r == "feedback" -> FeedbackScreen(store) { route = "home" }
        else -> LaunchedEffect(r) { route = "home" }
    }
}
