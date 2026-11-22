package com.fieldproof.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fieldproof.app.FieldProofApp
import com.fieldproof.app.ui.observations.CaptureScreen
import com.fieldproof.app.ui.observations.ConflictsScreen
import com.fieldproof.app.ui.observations.DetailScreen
import com.fieldproof.app.ui.observations.ListScreen
import com.fieldproof.app.ui.settings.SettingsScreen

@Composable
fun FieldProofNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as FieldProofApp
    val container = app.container

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            val vm: ListViewModel = viewModel(factory = viewModelFactory { ListViewModel(container) })
            ListScreen(
                viewModel = vm,
                onCapture = { navController.navigate("capture") },
                onOpen = { navController.navigate("detail/$it") },
                onConflicts = { navController.navigate("conflicts") },
                onSettings = { navController.navigate("settings") },
            )
        }
        composable("capture") {
            val vm: CaptureViewModel = viewModel(factory = viewModelFactory { CaptureViewModel(container) })
            CaptureScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("detail/$id")
                },
            )
        }
        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm: DetailViewModel = viewModel(
                key = id,
                factory = viewModelFactory { DetailViewModel(container, id) },
            )
            DetailScreen(
                viewModel = vm,
                filesDir = app.filesDir,
                onBack = { navController.popBackStack() },
            )
        }
        composable("conflicts") {
            val vm: ConflictsViewModel = viewModel(factory = viewModelFactory { ConflictsViewModel(container) })
            ConflictsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate("detail/$it") },
            )
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
