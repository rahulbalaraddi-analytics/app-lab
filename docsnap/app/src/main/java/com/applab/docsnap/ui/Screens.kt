package com.applab.docsnap.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import com.applab.docsnap.data.UploadStatus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.applab.docsnap.data.Doc
import com.applab.docsnap.data.Draft
import com.applab.docsnap.data.Folder
import com.applab.docsnap.data.Mode
import com.applab.docsnap.data.Store
import com.applab.docsnap.scan.Metrics
import com.applab.docsnap.scan.PageMetric
import com.applab.docsnap.scan.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- shared pieces

@Composable
fun Page(dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Palette.Dark else Palette.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { content() }
}

@Composable
fun Header(title: String, kicker: String? = null, onBack: (() -> Unit)? = null, teal: Boolean = false, onHelp: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (teal) Palette.Teal else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (teal) Color.White else Palette.Ink
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ink)
            }
        } else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (kicker != null) Text(kicker, fontSize = 13.sp, color = if (teal) Color.White.copy(0.9f) else Palette.Quiet)
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink)
        }
        if (onHelp != null) HelpButton(onHelp, tint = ink)
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal),
    ) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Palette.Card, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(16.dp)
    ) { content() }
}

@Composable
fun QualityChip(q: Quality, mp: Double) {
    val (label, color) = when (q) {
        Quality.GOOD -> "GOOD" to Palette.Teal
        Quality.OK -> "OK" to Palette.Amber
        Quality.LOW -> "LOW" to Palette.Red
    }
    Text(
        "$label · %.1f MP".format(mp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun thumbnail(file: File, targetPx: Int = 240): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bmp
}

// ---------------------------------------------------------------- dialogs

@Composable
fun InitialsDialog(current: String, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your name") },
        text = {
            Column {
                Text("Used in every file name so phones never clash, e.g. SB.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.take(3) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = text.trim().length >= 2, onClick = { onDone(text.trim()) }) { Text("Save") }
        },
    )
}

@Composable
fun RecoveryDialog(draft: Draft, onContinue: () -> Unit, onDiscard: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Unfinished document") },
        text = {
            Text(
                if (!confirm) "${draft.folder.name}: ${draft.pages.size} pages were saved but not named yet."
                else "Delete these ${draft.pages.size} pages for good?"
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = {
            TextButton(onClick = { if (confirm) onDiscard() else confirm = true }) {
                Text(if (confirm) "Yes, delete" else "Discard", color = Palette.Red)
            }
        },
    )
}

// ---------------------------------------------------------------- home

/** What the upload worker is doing, for the Home card. */
data class UploadState(val running: Boolean, val waitingForNetwork: Boolean, val done: Int, val total: Int)

@Composable
fun HomeScreen(
    store: Store, tick: Int, upload: UploadState, refreshing: Boolean,
    onFolder: (Folder) -> Unit, onRefresh: () -> Unit, onUploadAll: () -> Unit,
    onSettings: () -> Unit, onFeedback: () -> Unit, onHelp: () -> Unit,
) {
    val today = remember(tick) { store.docsToday() }
    val waiting = remember(tick, upload) { store.waitingCount() }
    val folders = remember(tick) { store.folders() }
    Page {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DocSnap", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${store.rootFolderName} · ${store.initials}", fontSize = 14.sp, color = Palette.Quiet,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HelpButton(onHelp)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Palette.Teal, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardBox(Modifier.weight(1f)) {
                    Column {
                        Text("Scanned today", fontSize = 13.sp, color = Palette.Quiet)
                        Text("$today", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                CardBox(Modifier.weight(1f)) {
                    Column {
                        Text("Waiting to upload", fontSize = 13.sp, color = Palette.Quiet)
                        Text("$waiting", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (waiting > 0) Palette.Amber else Palette.Teal)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            UploadCard(store, waiting, upload, onUploadAll)

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FOLDERS FROM DRIVE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Quiet, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onRefresh, enabled = !refreshing, border = BorderStroke(1.5.dp, Palette.Teal),
                    shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, null, tint = Palette.Teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh", color = Palette.Teal, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(folders, key = { it.id }) { f ->
                    val count = remember(tick, f) { store.docs(f).size }
                    CardBox(onClick = { onFolder(f) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(52.dp).background(Palette.TealSoft, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(f.code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Teal) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("$count on this phone", fontSize = 14.sp, color = Palette.Quiet)
                            }
                        }
                    }
                }
                item {
                    val ago = if (store.foldersRefreshedAt == 0L) "never" else
                        android.text.format.DateUtils.getRelativeTimeSpanString(store.foldersRefreshedAt).toString()
                    Text(
                        if (folders.isEmpty()) "No folders yet. The owner creates folders inside the shared Drive folder, then tap Refresh."
                        else "Added a folder in Drive? Tap Refresh. Last refreshed $ago.",
                        fontSize = 14.sp, color = Palette.Quiet, modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            SmallAction("Report a problem", Icons.Filled.Feedback, Modifier.fillMaxWidth(), onFeedback)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UploadCard(store: Store, waiting: Int, upload: UploadState, onUploadAll: () -> Unit) {
    val status = when {
        upload.running -> "Uploading ${upload.done} of ${upload.total}… you can keep scanning"
        upload.waitingForNetwork -> if (store.allowMobileData) "Waiting for internet" else "Waiting for Wi-Fi — uploads start when connected"
        waiting == 0 && store.lastUploadAt > 0 -> "All uploaded · " +
            android.text.format.DateUtils.getRelativeTimeSpanString(store.lastUploadAt)
        waiting == 0 -> "Nothing to upload yet"
        store.autoUpload -> "Will upload by itself on Wi-Fi"
        else -> "Tap Upload all when on Wi-Fi"
    }
    CardBox {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status, fontSize = 15.sp, color = if (upload.running) Palette.Teal else Palette.Quiet, fontWeight = FontWeight.Bold)
            if (upload.running && upload.total > 0) {
                LinearProgressIndicator(
                    progress = { upload.done.toFloat() / upload.total }, color = Palette.Teal,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            Button(
                onClick = onUploadAll, enabled = waiting > 0 && !upload.running,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal),
            ) {
                Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload all ($waiting)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, Palette.Teal),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Palette.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- folder

@Composable
fun FolderScreen(store: Store, folder: Folder, tick: Int, onBack: () -> Unit, onStart: (Folder, Mode) -> Unit, onHelp: () -> Unit) {
    val docs = remember(tick, folder) { store.docs(folder) }
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Page {
        Column(Modifier.fillMaxSize()) {
            Header(folder.name, kicker = "SCANNING INTO", onBack = onBack, teal = true, onHelp = onHelp)
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeCard("Single page", "Photo → name → next. Fastest.", Icons.Filled.Description, true, Modifier.weight(1f)) {
                    onStart(folder, Mode.SINGLE)
                }
                ModeCard("Multi-page", "Many pages → Done → name.", Icons.Filled.FileCopy, false, Modifier.weight(1f)) {
                    onStart(folder, Mode.MULTI)
                }
            }
            Text(
                "${docs.size} DOCUMENTS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Quiet,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(docs, key = { it.fileName }) { d -> DocRow(d) { openPdf(context, d) } }
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, sub: String, icon: ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (filled) Palette.Teal else Palette.Card
    val fg = if (filled) Color.White else Palette.Teal
    Column(
        modifier
            .height(140.dp)
            .background(bg, RoundedCornerShape(16.dp))
            .border(2.dp, Palette.Teal, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(30.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(sub, fontSize = 13.sp, color = if (filled) Color.White else Palette.Ink)
    }
}

@Composable
private fun DocRow(d: Doc, onClick: () -> Unit) {
    CardBox(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.transcript, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.fileName, fontSize = 12.sp, color = Palette.Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (label, color) = when (d.status) {
                    UploadStatus.ON_DRIVE -> "On Drive" to Palette.Teal
                    UploadStatus.FAILED -> "Retry" to Palette.Red
                    UploadStatus.LOCAL -> "Waiting" to Palette.Amber
                }
                Text("${d.pages} page${if (d.pages == 1) "" else "s"} · ", fontSize = 13.sp, color = Palette.Quiet)
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.weight(1f))
                QualityChip(d.quality, d.minMp)
            }
        }
    }
}

private fun openPdf(context: android.content.Context, d: Doc) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", d.pdf)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

// ---------------------------------------------------------------- multi-page strip

@Composable
fun StripScreen(
    store: Store, draftId: String, tick: Int,
    onScanMore: () -> Unit, onDone: () -> Unit, onCancel: () -> Unit, onHelp: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    val draft = remember(tick, version) { store.draft(draftId) } ?: return
    val metrics = remember(draft) { draft.pages.map { Metrics.page(it) } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf(false) }
    BackHandler { confirmCancel = true }

    Page(dark = true) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MULTI-PAGE · ${draft.folder.name.uppercase()}", fontSize = 12.sp, color = Color(0xFFC9D1D6))
                    Text("${draft.pages.size} pages", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(
                    Modifier.background(Color(0xFF12312F), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF7DD3C8), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${draft.pages.size} saved on phone", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7DD3C8))
                }
                HelpButton(onHelp, tint = Color.White)
            }

            val low = metrics.count { it.quality == Quality.LOW }
            if (low > 0) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .background(Color(0xFF3B1F1C), RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFF87171))
                    Spacer(Modifier.width(8.dp))
                    Text("$low low-quality page(s). Swipe up to delete and scan closer.", color = Color.White, fontSize = 14.sp)
                }
            }

            // Big preview of the last page
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                draft.pages.lastOrNull()?.let { last ->
                    thumbnail(last, 900)?.let {
                        Image(it, contentDescription = "Last page", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                } ?: Text("No pages yet", color = Color.White)
            }

            Row(Modifier.padding(horizontal = 16.dp)) {
                Text("Swipe a page up to delete", fontSize = 13.sp, color = Color(0xFFC9D1D6))
            }
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(draft.pages.size, key = { draft.pages[it].name + draft.pages[it].lastModified() }) { i ->
                    val page = draft.pages[i]
                    SwipeUpThumb(page, i + 1, metrics.getOrNull(i)) {
                        val trashed = store.deletePage(draftId, page)
                        version++
                        scope.launch {
                            val r = snackbar.showSnackbar("Page ${i + 1} deleted", actionLabel = "Undo", withDismissAction = true)
                            if (r == SnackbarResult.ActionPerformed) { store.undoDelete(draftId, trashed); version++ }
                        }
                    }
                }
            }

            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onScanMore, modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color.White),
                ) { Text("Scan more", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onDone, enabled = draft.pages.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal),
                ) { Text("Done – ${draft.pages.size} pages", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }

    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("Leave this document?") },
        text = { Text("The ${draft.pages.size} pages stay saved. You can continue later, or delete them now.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep scanning") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false; onCancel() }) { Text("Delete pages", color = Palette.Red) } },
    )
}

@Composable
private fun SwipeUpThumb(page: File, number: Int, metric: PageMetric?, onDelete: () -> Unit) {
    var dy by remember(page.name) { mutableFloatStateOf(0f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { translationY = dy; alpha = (1f + dy / 300f).coerceIn(0.3f, 1f) }
            .pointerInput(page.name) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dy < -120f) onDelete(); dy = 0f },
                    onDragCancel = { dy = 0f },
                    onVerticalDrag = { _, amount -> dy = (dy + amount).coerceAtMost(0f) },
                )
            },
    ) {
        val border = if (metric?.quality == Quality.LOW) Color(0xFFF87171) else Color.Transparent
        Box(Modifier.size(60.dp, 80.dp).border(2.dp, border, RoundedCornerShape(4.dp)).background(Color(0xFFEDEBE4), RoundedCornerShape(4.dp))) {
            thumbnail(page, 160)?.let {
                Image(it, contentDescription = "Page $number", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(if (dy < -120f) "Release" else "$number", fontSize = 12.sp, color = if (dy < -120f) Color(0xFFF87171) else Color.White)
    }
}

// ---------------------------------------------------------------- phone check

@Composable
fun PhoneCheckScreen(store: Store, onBack: () -> Unit) {
    val context = LocalContext.current
    val camMp = remember { Metrics.backCameraMaxMp(context) }
    val stats = remember { store.pageStats() }
    BackHandler(onBack = onBack)
    Page {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Header("Phone check", onBack = onBack)
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${Build.MANUFACTURER} ${Build.MODEL}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Android ${Build.VERSION.RELEASE}", color = Palette.Quiet)
                    Text("Back camera max: %.1f MP".format(camMp), color = Palette.Quiet)
                }
            }
            Spacer(Modifier.height(12.dp))
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Scanned page quality", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (stats == null) {
                        Text("Scan 2–3 pages, then come back here.", color = Palette.Quiet)
                    } else {
                        val (avg, min) = stats
                        Text("Average %.1f MP · lowest %.1f MP".format(avg, min), color = Palette.Quiet)
                        Text("≈ %d DPI on A4".format((kotlin.math.sqrt(avg * 1_000_000 * 1.414) / 11.69).toInt()), color = Palette.Quiet)
                        val verdict = when {
                            avg >= PageMetric.GOOD_MP -> "GOOD for a lifetime archive" to Palette.Teal
                            avg >= PageMetric.OK_MP -> "OK — readable, not archive grade" to Palette.Amber
                            else -> "LOW — don't use this phone for scanning" to Palette.Red
                        }
                        Text(verdict.first, fontWeight = FontWeight.Bold, color = verdict.second, fontSize = 17.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("These numbers are saved to the analytics log for Claude.", fontSize = 13.sp, color = Palette.Quiet)
        }
    }
}

// ---------------------------------------------------------------- feedback

@Composable
fun FeedbackScreen(store: Store, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    Page {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Header("Report a problem", onBack = onBack)
            Text("What went wrong? Type it or use the keyboard mic.", color = Palette.Quiet)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(16.dp))
            BigButton("Send", enabled = text.isNotBlank()) {
                store.feedback(text)
                onBack()
            }
        }
    }
}
