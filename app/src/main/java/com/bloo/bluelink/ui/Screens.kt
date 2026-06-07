package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.launch

@Composable
fun BlooApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            when (val screen = state.screen) {
                Screen.Login -> LoginScreen(state.loading, vm::login)
                Screen.Vehicles -> VehicleListScreen(state, vm)
                is Screen.Detail -> VehicleDetailScreen(screen.vehicle, state, vm)
            }
        }
    }
}

@Composable
private fun LoginScreen(loading: Boolean, onLogin: (String, String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Bloo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Hyundai Blue Link (US)", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Blue Link email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Service PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onLogin(email, password, pin) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Sign in")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Credentials are sent directly to Hyundai's telematics servers and " +
                "stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleListScreen(state: UiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My Vehicles") },
            actions = {
                IconButton(onClick = { vm.loadVehicles() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Sign out")
                }
            },
        )
        if (state.loading && state.vehicles.isEmpty()) {
            Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        } else {
            LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.vehicles) { v ->
                    Card(
                        onClick = { vm.openVehicle(v) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(v.model, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${if (v.isEv) "EV" else "ICE"} · VIN ${v.vin.takeLast(6)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDetailScreen(v: Vehicle, state: UiState, vm: AppViewModel) {
    var tempF by remember { mutableStateOf(72) }
    var defrost by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(v.name) },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { vm.refreshStatus(v, forceRefresh = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh status")
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state.status, state.loading)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandButton("Lock", Icons.Filled.Lock, Modifier.weight(1f), !state.loading) { vm.lock(v) }
                CommandButton("Unlock", Icons.Filled.LockOpen, Modifier.weight(1f), !state.loading) { vm.unlock(v) }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Climate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Temperature: $tempF°F", Modifier.weight(1f))
                        OutlinedButton(onClick = { if (tempF > 62) tempF-- }) { Text("-") }
                        Spacer(Modifier.height(0.dp))
                        OutlinedButton(
                            onClick = { if (tempF < 82) tempF++ },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("+") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Defrost", Modifier.weight(1f))
                        Switch(checked = defrost, onCheckedChange = { defrost = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CommandButton("Start", Icons.Filled.AcUnit, Modifier.weight(1f), !state.loading) {
                            vm.startClimate(v, tempF, defrost, minutes = 10)
                        }
                        CommandButton("Stop", Icons.Filled.PowerSettingsNew, Modifier.weight(1f), !state.loading) {
                            vm.stopClimate(v)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: VehicleStatus?, loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                status == null && loading -> Text("Fetching live status…")
                status == null -> Text("No status yet. Pull refresh to query the car.")
                else -> {
                    StatusRow("Doors", if (status.doorLock == true) "Locked" else "Unlocked")
                    StatusRow("Climate", if (status.airCtrlOn == true) "On" else "Off")
                    status.engine?.let { StatusRow("Engine", if (it) "Running" else "Off") }
                    status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                    status.evStatus?.batteryStatus?.let { StatusRow("EV charge", "$it%") }
                    val range = status.evStatus?.drvDistance?.firstOrNull()
                        ?.rangeByFuel?.totalAvailableRange?.value
                        ?: status.dte?.value
                    range?.let { StatusRow("Range", "${it.toInt()} mi") }
                    status.dateTime?.let { StatusRow("Updated", it) }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CommandButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = label)
        Spacer(Modifier.height(0.dp))
        Text("  $label")
    }
}
