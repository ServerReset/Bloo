package com.bloo.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel

@Composable
fun SettingsScreen(vm: WearViewModel, ui: WearUi, onAddAccount: () -> Unit) {
    val state = rememberScalingLazyListState()
    var confirmSignOut by remember { mutableStateOf(false) }
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Settings", textAlign = TextAlign.Center) } }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Accounts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (ui.accounts.isEmpty()) {
                    Text("Synced from phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ui.accounts.forEach { email -> Text(email, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onAddAccount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Add account") },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            )
        }

        // Appearance is owned by the phone and synced down.
        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Theme: synced from phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Units: ${if (ui.settings?.useFahrenheit != false) "°F" else "°C"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ui.settings?.uiScale?.let {
                    Text("Text scale: ${"%.2f".format(it)}×", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (ui.phoneConnected) Icons.Filled.PhoneAndroid else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (ui.phoneConnected) "Phone connected" else "Standalone",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            FilledTonalButton(
                onClick = { vm.resync() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Re-sync from phone") },
                icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
            )
        }

        item {
            FilledTonalButton(
                onClick = { vm.refreshAll() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Refresh all cars") },
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            )
        }

        item {
            OutlinedButton(
                onClick = { if (confirmSignOut) vm.signOutAll() else confirmSignOut = true },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (confirmSignOut) "Tap again to confirm" else "Sign out") },
                icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
            )
        }

        item {
            Text(
                "Bloo for Wear OS · 0.1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
