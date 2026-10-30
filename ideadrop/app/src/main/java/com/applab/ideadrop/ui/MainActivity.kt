package com.applab.ideadrop.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.applab.ideadrop.audio.RecordingController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.applab.ideadrop.ui.theme.IdeaDropTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_QUICK_RECORD = "quickRecord"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleQuickRecord(intent)
        handleSharedAudio(intent)
    }

    /** "Share → IdeaDrop" or "Open with IdeaDrop" on an audio file (e.g. a call recording). */
    private fun handleSharedAudio(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, android.net.Uri::class.java
            )
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        SharedAudio.pending.value = uri
    }

    /** Opened from "Record idea" (side key, lock screen, shortcut): start recording at once. */
    private fun handleQuickRecord(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_RECORD, false) != true) return
        intent.removeExtra(EXTRA_QUICK_RECORD)
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            RecordingController.start(this)
        } else {
            Toast.makeText(this, "Tap the mic once to allow recording. After that, Record idea starts instantly.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleQuickRecord(intent)
            handleSharedAudio(intent)
        }
        setContent {
            IdeaDropTheme {
                val nav = rememberNavController()
                val vm: MainViewModel = viewModel()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            openNote = { nav.navigate("note/$it") },
                            openSettings = { nav.navigate("settings") },
                        )
                    }
                    composable(
                        "note/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        NoteScreen(
                            vm = vm,
                            noteId = entry.arguments?.getLong("id") ?: 0L,
                            back = { nav.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(vm = vm, back = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
