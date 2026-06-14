package com.bloo.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.bloo.wear.WearScreen
import com.bloo.wear.WearViewModel

@Composable
fun WatchApp(vm: WearViewModel) {
    val ui by vm.ui.collectAsState()
    AppScaffold {
        when (ui.screen) {
            WearScreen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            WearScreen.SignedOut -> LoginScreen(vm, ui)

            WearScreen.Ready -> {
                val nav = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(vm, ui, onSettings = { nav.navigate("settings") })
                    }
                    composable("settings") {
                        SettingsScreen(vm, ui)
                    }
                }
            }
        }
    }
}
