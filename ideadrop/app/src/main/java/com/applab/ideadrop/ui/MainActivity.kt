package com.applab.ideadrop.ui

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
