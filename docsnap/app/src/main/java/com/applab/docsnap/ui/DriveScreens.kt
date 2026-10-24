package com.applab.docsnap.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applab.docsnap.data.Store
import com.applab.docsnap.drive.DriveApi
import com.applab.docsnap.drive.DriveException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs [block] with a Drive access token, showing Google's account screen first if needed. */
typealias WithToken = (block: (String) -> Unit) -> Unit

// ---------------------------------------------------------------- connect (first launch)

@Composable
fun ConnectScreen(store: Store, withToken: WithToken, onConnected: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(store.accountEmail) }
    var link by remember { mutableStateOf("") }
    var initials by remember { mutableStateOf(store.initials) }
    var language by remember { mutableStateOf(store.language) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, isError
    if (onBack != null) BackHandler(onBack = onBack)

    fun signIn() {
        busy = true; message = null
        withToken { token ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { DriveApi(token).myEmail() } }
                    .onSuccess { email = it }
                    .onFailure { message = "Sign-in worked but Drive did not answer: ${it.message}" to true }
                busy = false
            }
        }
    }

    fun connect() {
        val id = DriveApi.folderIdFromLink(link)
        if (id == null) { message = "That doesn't look like a Drive folder link. Copy it again from Drive or WhatsApp." to true; return }
        if (initials.trim().length < 2) { message = "Type your initials first (2–3 letters)." to true; return }
        busy = true; message = null
        withToken { token ->
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val api = DriveApi(token)
                        val me = api.myEmail()
                        val (root, canAdd) = api.folder(id)
                        Triple(me, root to canAdd, api.subfolders(id))
                    }
                }
                busy = false
                result.onSuccess { (me, rootInfo, subs) ->
                    val (root, canAdd) = rootInfo
                    email = me
                    if (!canAdd) {
                        message = "$me can only view \"${root.name}\". Ask the owner to share it as Editor." to true
                        return@onSuccess
                    }
                    store.initials = initials.trim()
                    store.language = language
                    store.saveConnection(root.id, root.name, me)
                    store.saveFolders(subs.map { it.id to it.name })
                    store.writeDeviceInfo()
                    onConnected()
                }.onFailure { e ->
                    message = when ((e as? DriveException)?.code) {
                        404 -> "Folder not found for ${email.ifBlank { "this account" }}. Ask the owner to share it with this Gmail as Editor."
                        401, 403 -> "Google said no access. Sign in again with the Gmail the folder was shared with."
                        else -> "Could not connect: ${e.message}. Check the internet and try again."
                    } to true
                }
            }
        }
    }

    Page {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(20.dp))
            Text(if (store.isConnected) "CHANGE DRIVE FOLDER" else "FIRST TIME ONLY", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Teal)
            Text("Connect to the family Drive folder", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Spacer(Modifier.height(18.dp))

            StepCard(1, "Sign in with Google", done = email.isNotBlank()) {
                if (email.isNotBlank()) {
                    Text(email, color = Palette.Quiet, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = ::signIn, enabled = !busy, border = BorderStroke(1.dp, Palette.Teal)) {
                        Text("Use another Gmail", color = Palette.Teal)
                    }
                } else {
                    Text("Use the Gmail the family folder was shared with.", color = Palette.Quiet, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    BigButton("Sign in with Google", enabled = !busy, onClick = ::signIn)
                }
            }
            Spacer(Modifier.height(12.dp))

            StepCard(2, "Paste the folder link", done = false) {
                OutlinedTextField(
                    value = link, onValueChange = { link = it }, singleLine = true,
                    placeholder = { Text("https://drive.google.com/drive/folders/…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { link = it.trim() }
                }, border = BorderStroke(1.dp, Palette.Teal)) { Text("Paste", color = Palette.Teal) }
                Spacer(Modifier.height(6.dp))
                Text("The owner sends this link on WhatsApp. Everyone pastes the same link.", fontSize = 13.sp, color = Palette.Quiet)
            }
            Spacer(Modifier.height(12.dp))

            StepCard(3, "About you", done = false) {
                OutlinedTextField(
                    value = initials, onValueChange = { initials = it.take(3) }, singleLine = true,
                    label = { Text("Your initials (e.g. SB)") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("I will speak names in", fontSize = 14.sp, color = Palette.Quiet)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = language == "en-IN", onClick = { language = "en-IN" }, label = { Text("English") })
                    FilterChip(selected = language == "kn-IN", onClick = { language = "kn-IN" }, label = { Text("ಕನ್ನಡ") })
                }
            }

            message?.let { (text, isError) ->
                Spacer(Modifier.height(12.dp))
                Text(text, color = if (isError) Palette.Red else Palette.Teal, fontSize = 15.sp)
            }
            Spacer(Modifier.height(20.dp))
            if (busy) {
                Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                BigButton("Connect & load folders", enabled = link.isNotBlank(), onClick = ::connect)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, done: Boolean, content: @Composable () -> Unit) {
    CardBox {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).background(if (done) Palette.Teal else Palette.TealSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(if (done) "✓" else "$n", color = if (done) Color.White else Palette.Teal, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit, onChangeFolder: () -> Unit, onPhone: () -> Unit, onTutorial: () -> Unit) {
    var auto by remember { mutableStateOf(store.autoUpload) }
    var mobile by remember { mutableStateOf(store.allowMobileData) }
    BackHandler(onBack = onBack)
    Page {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Header("Settings", onBack = onBack)
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Drive folder", fontSize = 13.sp, color = Palette.Quiet)
                    Text(store.rootFolderName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(store.accountEmail, color = Palette.Quiet)
                    Text("You: ${store.initials}", color = Palette.Quiet)
                }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow("Upload by itself on Wi-Fi", "Off = only when you tap Upload all", auto) { auto = it; store.autoUpload = it }
            Spacer(Modifier.height(10.dp))
            ToggleRow("Allow mobile data", "Off = uploads wait for Wi-Fi", mobile) { mobile = it; store.allowMobileData = it }
            Spacer(Modifier.height(16.dp))
            SettingsButton("Change Drive folder or Gmail", onChangeFolder)
            SettingsButton("Phone check (camera quality)", onPhone)
            SettingsButton("Watch the tutorial again", onTutorial)
            Spacer(Modifier.height(16.dp))
            Text("DocSnap ${store.appVersion}", fontSize = 13.sp, color = Palette.Quiet)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(sub, fontSize = 13.sp, color = Palette.Quiet)
            }
            Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Teal))
        }
    }
}

@Composable
private fun SettingsButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp).padding(bottom = 0.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, Palette.Teal),
    ) { Text(text, color = Palette.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    Spacer(Modifier.height(10.dp))
}
