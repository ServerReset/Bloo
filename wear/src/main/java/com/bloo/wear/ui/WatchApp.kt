package com.bloo.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open Bloo on your phone if this takes a while",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { vm.resync() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        label = { Text("Sync from phone") },
                        icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                    )
                }
            }

            WearScreen.SignedOut -> LoginScreen(vm, ui)

            WearScreen.Ready -> {
                val nav = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm, ui,
                            onSettings = { nav.navigate("settings") },
                            onTrips = { vin -> nav.navigate("trips/$vin") },
                            onReorder = { nav.navigate("reorder") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(vm, ui, onAddAccount = { nav.navigate("login") })
                    }
                    composable("login") {
                        LoginScreen(vm, ui)
                    }
                    composable("trips/{vin}") { entry ->
                        TripsScreen(vm, ui, entry.arguments?.getString("vin") ?: "")
                    }
                    composable("reorder") {
                        TileReorderScreen(vm, ui)
                    }
                }
            }
        }
    }
}
